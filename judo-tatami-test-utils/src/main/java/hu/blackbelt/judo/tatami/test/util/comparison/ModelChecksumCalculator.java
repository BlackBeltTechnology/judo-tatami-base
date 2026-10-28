package hu.blackbelt.judo.tatami.test.util.comparison;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Builds structural representation of EMF models with checksums.
 * <p>
 * Handles containment hierarchy and non-containment references separately.
 * Supports ignoring specific features via {@link CalculatorOptions}.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ModelChecksumCalculator calc = new ModelChecksumCalculator();
 * ModelNode root = calc.calculate(resource);
 *
 * // With options
 * CalculatorOptions options = new CalculatorOptions()
 *     .ignore("*.EAnnotation#details");
 * ModelNode root = calc.calculate(resource, options);
 *
 * // Export to JSON
 * String json = calc.toJson(root);
 * }</pre>
 */
public class ModelChecksumCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(ModelChecksumCalculator.class);

    private Map<String, ModelNode> pathIndex;
    private Map<EObject, String> objectPathIndex;
    private CalculatorOptions options;
    private Map<String, Integer> ignoreMatchCounts;

    /**
     * Calculates the structural representation of a model with default options.
     *
     * @param resource the EMF resource to analyze
     * @return root ModelNode of the containment tree
     */
    public ModelNode calculate(Resource resource) {
        return calculate(resource, new CalculatorOptions());
    }

    /**
     * Calculates the structural representation of a model.
     *
     * @param resource the EMF resource to analyze
     * @param options configuration including ignored features
     * @return root ModelNode of the containment tree
     */
    public ModelNode calculate(Resource resource, CalculatorOptions options) {
        this.options = options;
        this.pathIndex = new HashMap<>();
        this.objectPathIndex = new HashMap<>();
        this.ignoreMatchCounts = new HashMap<>();

        // Phase 1: Build containment tree
        List<ModelNode> allNodes = new ArrayList<>();
        ModelNode root = buildContainmentTree(resource, allNodes);

        // Phase 2: Compute checksums bottom-up
        computeChecksums(allNodes);

        // Log warnings for patterns that matched many elements
        logIgnoreWarnings();

        return root;
    }

    private void logIgnoreWarnings() {
        for (Map.Entry<String, Integer> entry : ignoreMatchCounts.entrySet()) {
            if (entry.getValue() > 100) {
                LOG.warn("Ignore pattern '{}' matched {} elements - verify this is intended",
                        entry.getKey(), entry.getValue());
            }
        }
    }

    private ModelNode buildContainmentTree(Resource resource, List<ModelNode> allNodes) {
        ModelNode root = new ModelNode(null, "", "");

        for (EObject content : resource.getContents()) {
            ModelNode node = buildNode(content, "", allNodes);
            root.addContainment("contents", node);
        }

        allNodes.add(root);
        return root;
    }

    private ModelNode buildNode(EObject obj, String parentPath, List<ModelNode> allNodes) {
        // Use custom identifier resolver from options
        String identifier = options.getIdentifierResolver().apply(obj);
        String nodeId = obj.eClass().getName() + ":" + identifier;
        String path = parentPath.isEmpty() ? nodeId : parentPath + "/" + nodeId;

        ModelNode node = new ModelNode(obj, path, identifier);
        pathIndex.put(path, node);
        objectPathIndex.put(obj, path);

        EClass eClass = obj.eClass();

        // Collect attributes (skip ignored, derived, and transient)
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (attr.isDerived() || attr.isTransient()) {
                continue;
            }
            if (isIgnored(eClass, attr)) {
                continue;
            }
            Object value = obj.eGet(attr);
            if (value != null) {
                node.addAttribute(attr.getName(), value);
            }
        }

        // Process containment references → build children (containments are never ignored)
        // Note: Children are added to allNodes BEFORE this node, ensuring leaves-first order
        for (EReference ref : eClass.getEAllReferences()) {
            if (!ref.isContainment()) continue;

            Object value = obj.eGet(ref);
            if (value == null) continue;

            if (ref.isMany()) {
                @SuppressWarnings("unchecked")
                Collection<EObject> children = (Collection<EObject>) value;
                for (EObject child : children) {
                    ModelNode childNode = buildNode(child, path, allNodes);
                    node.addContainment(ref.getName(), childNode);
                }
            } else {
                ModelNode childNode = buildNode((EObject) value, path, allNodes);
                node.addContainment(ref.getName(), childNode);
            }
        }

        // Add node AFTER children are processed to ensure leaves-first order for bottom-up checksum
        allNodes.add(node);

        return node;
    }

    private boolean isIgnored(EClass eClass, EStructuralFeature feature) {
        if (options.isIgnored(eClass, feature)) {
            // Track match counts for warning
            for (FeaturePattern pattern : options.getIgnoredFeatures()) {
                if (pattern.matches(eClass, feature)) {
                    ignoreMatchCounts.merge(pattern.toString(), 1, Integer::sum);
                }
            }
            return true;
        }
        return false;
    }

    private void computeChecksums(List<ModelNode> allNodes) {
        // allNodes is already in leaves-first order due to post-order insertion in buildNode
        // (children are added before their parents)

        // Phase 1: Compute containment checksums
        for (ModelNode node : allNodes) {
            computeContainmentChecksum(node);
        }

        // Phase 2: Add non-containment references
        for (ModelNode node : allNodes) {
            addReferences(node);
        }

        // Phase 3: Recompute with references
        for (ModelNode node : allNodes) {
            computeFullChecksum(node);
        }
    }

    private void computeContainmentChecksum(ModelNode node) {
        MessageDigest digest = getDigest();

        // Type
        digest.update(node.getType().getBytes(StandardCharsets.UTF_8));

        // Sorted attributes
        node.getAttributes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    digest.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                    digest.update(String.valueOf(e.getValue()).getBytes(StandardCharsets.UTF_8));
                });

        // Sorted containment checksums
        node.getContainments().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    digest.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                    e.getValue().stream()
                            .map(ModelNode::getChecksum)
                            .filter(Objects::nonNull)
                            .sorted()
                            .forEach(cs -> digest.update(cs.getBytes(StandardCharsets.UTF_8)));
                });

        node.setChecksum(bytesToHex(digest.digest()));
    }

    private void addReferences(ModelNode node) {
        EObject obj = node.getEObject();
        if (obj == null) return;

        Resource currentResource = obj.eResource();
        EClass eClass = obj.eClass();

        for (EReference ref : eClass.getEAllReferences()) {
            if (ref.isContainment()) continue;
            if (ref.isDerived() || ref.isTransient()) continue;
            if (!shouldIncludeReference(ref)) continue;
            if (isIgnored(eClass, ref)) continue;

            Object value = obj.eGet(ref, true);  // Resolve proxies lazily
            if (value == null) continue;

            if (ref.isMany()) {
                @SuppressWarnings("unchecked")
                Collection<EObject> targets = (Collection<EObject>) value;
                for (EObject target : targets) {
                    addReferenceIfSameResource(node, ref, target, currentResource);
                }
            } else {
                addReferenceIfSameResource(node, ref, (EObject) value, currentResource);
            }
        }
    }

    private void addReferenceIfSameResource(ModelNode node, EReference ref,
                                            EObject target, Resource currentResource) {
        // Skip cross-resource references
        if (target.eResource() != currentResource) {
            return;
        }

        // Resolve proxy if needed
        if (target.eIsProxy()) {
            target = EcoreUtil.resolve(target, currentResource);
            if (target.eIsProxy()) {
                LOG.warn("Could not resolve proxy reference at {}", node.getPath());
                return;
            }
        }

        String targetPath = objectPathIndex.get(target);
        if (targetPath != null) {
            ModelNode targetNode = pathIndex.get(targetPath);
            if (targetNode != null) {
                node.addReference(ref.getName(), new ReferenceNode(targetNode));
            }
        }
    }

    private void computeFullChecksum(ModelNode node) {
        if (node.getReferences().isEmpty()) return;

        MessageDigest digest = getDigest();
        digest.update(node.getChecksum().getBytes(StandardCharsets.UTF_8));

        node.getReferences().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    digest.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                    e.getValue().stream()
                            .map(ReferenceNode::getTargetChecksum)
                            .filter(Objects::nonNull)
                            .sorted()
                            .forEach(cs -> digest.update(cs.getBytes(StandardCharsets.UTF_8)));
                });

        node.setChecksum(bytesToHex(digest.digest()));
    }

    private boolean shouldIncludeReference(EReference ref) {
        EReference opposite = ref.getEOpposite();
        if (opposite == null) return true;
        if (ref.isContainer()) return false;
        if (opposite.isContainer()) return true;
        // For non-container bidirectional, use alphabetically first
        return ref.getName().compareTo(opposite.getName()) <= 0;
    }

    private MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // JSON Export

    /**
     * Exports a ModelNode tree to JSON with default options.
     *
     * @param root the root node to export
     * @return JSON string representation
     */
    public String toJson(ModelNode root) {
        return toJson(root, new JsonOptions());
    }

    /**
     * Exports a ModelNode tree to JSON.
     *
     * @param root the root node to export
     * @param jsonOptions export options
     * @return JSON string representation
     */
    public String toJson(ModelNode root, JsonOptions jsonOptions) {
        StringBuilder sb = new StringBuilder();
        writeJson(root, sb, 0, jsonOptions);
        return sb.toString();
    }

    private void writeJson(ModelNode node, StringBuilder sb, int indent, JsonOptions jsonOptions) {
        String pad = jsonOptions.isPrettyPrint() ? "  ".repeat(indent) : "";
        String nl = jsonOptions.isPrettyPrint() ? "\n" : "";
        String sep = jsonOptions.isPrettyPrint() ? " " : "";

        sb.append(pad).append("{").append(nl);
        sb.append(pad).append("  \"type\":").append(sep).append("\"").append(escapeJson(node.getType())).append("\",").append(nl);
        sb.append(pad).append("  \"identifier\":").append(sep).append("\"").append(escapeJson(node.getIdentifier())).append("\",").append(nl);
        sb.append(pad).append("  \"path\":").append(sep).append("\"").append(escapeJson(node.getPath())).append("\"");

        if (jsonOptions.isIncludeChecksums() && node.getChecksum() != null) {
            sb.append(",").append(nl);
            sb.append(pad).append("  \"checksum\":").append(sep).append("\"").append(node.getChecksum()).append("\"");
        }

        if (jsonOptions.isIncludeAttributes() && !node.getAttributes().isEmpty()) {
            sb.append(",").append(nl).append(pad).append("  \"attributes\":").append(sep).append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : node.getAttributes().entrySet()) {
                if (!first) sb.append(",");
                sb.append(nl).append(pad).append("    \"").append(escapeJson(e.getKey()))
                        .append("\":").append(sep).append("\"").append(escapeJson(String.valueOf(e.getValue()))).append("\"");
                first = false;
            }
            sb.append(nl).append(pad).append("  }");
        }

        if (jsonOptions.isIncludeReferences() && !node.getReferences().isEmpty()) {
            sb.append(",").append(nl).append(pad).append("  \"references\":").append(sep).append("{");
            boolean firstRef = true;
            for (Map.Entry<String, List<ReferenceNode>> e : node.getReferences().entrySet()) {
                if (!firstRef) sb.append(",");
                sb.append(nl).append(pad).append("    \"").append(escapeJson(e.getKey())).append("\":").append(sep).append("[");
                boolean firstTarget = true;
                for (ReferenceNode ref : e.getValue()) {
                    if (!firstTarget) sb.append(",");
                    sb.append(nl).append(pad).append("      {\"path\":").append(sep).append("\"")
                            .append(escapeJson(ref.getTargetPath())).append("\",").append(sep).append("\"checksum\":").append(sep).append("\"")
                            .append(ref.getTargetChecksum()).append("\"}");
                    firstTarget = false;
                }
                sb.append(nl).append(pad).append("    ]");
                firstRef = false;
            }
            sb.append(nl).append(pad).append("  }");
        }

        if (!node.getContainments().isEmpty() && indent < jsonOptions.getMaxDepth()) {
            sb.append(",").append(nl).append(pad).append("  \"containments\":").append(sep).append("{");
            boolean firstCont = true;
            for (Map.Entry<String, List<ModelNode>> e : node.getContainments().entrySet()) {
                if (!firstCont) sb.append(",");
                sb.append(nl).append(pad).append("    \"").append(escapeJson(e.getKey())).append("\":").append(sep).append("[");
                boolean firstChild = true;
                for (ModelNode child : e.getValue()) {
                    if (!firstChild) sb.append(",");
                    sb.append(nl);
                    writeJson(child, sb, indent + 3, jsonOptions);
                    firstChild = false;
                }
                sb.append(nl).append(pad).append("    ]");
                firstCont = false;
            }
            sb.append(nl).append(pad).append("  }");
        }

        sb.append(nl).append(pad).append("}");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // JSON Deserialization

    /**
     * Loads a ModelNode tree from JSON.
     * <p>
     * EObject references in the loaded nodes will be null.
     * Use {@link #fromJson(String, Resource)} to re-match EObjects.
     * </p>
     *
     * @param json the JSON string to parse
     * @return root ModelNode of the loaded tree
     * @throws IllegalArgumentException if JSON is null, empty, or invalid
     */
    public ModelNode fromJson(String json) {
        return fromJson(json, null);
    }

    /**
     * Loads a ModelNode tree from JSON with optional EObject re-matching.
     * <p>
     * If a Resource is provided, the loaded nodes will be matched to EObjects
     * in the resource by comparing checksums. This enables round-trip scenarios
     * where a saved JSON representation can be loaded and linked back to live model elements.
     * </p>
     *
     * @param json the JSON string to parse
     * @param resource optional Resource for EObject re-matching (may be null)
     * @return root ModelNode of the loaded tree
     * @throws IllegalArgumentException if JSON is null, empty, or invalid
     */
    public ModelNode fromJson(String json, Resource resource) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON cannot be null or empty");
        }

        try {
            Map<String, Object> jsonMap = parseJsonObject(json.trim());
            ModelNode root = loadNode(jsonMap);

            // Re-match EObjects if resource provided
            if (resource != null) {
                ModelNode calculatedRoot = calculate(resource);
                rematchEObjects(root, calculatedRoot);
            }

            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ModelNode loadNode(Map<String, Object> jsonMap) {
        String type = (String) jsonMap.get("type");
        String identifier = (String) jsonMap.get("identifier");
        String path = (String) jsonMap.get("path");
        String checksum = (String) jsonMap.get("checksum");

        if (type == null || path == null) {
            throw new IllegalArgumentException("Missing required fields 'type' or 'path'");
        }

        // Create node with null EObject (will be re-matched if resource provided)
        ModelNode node = new LoadedModelNode(type, path, identifier, checksum);

        // Load attributes
        Map<String, Object> attrs = (Map<String, Object>) jsonMap.get("attributes");
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                Object value = entry.getValue();
                // Try to convert string "true"/"false" to boolean
                if (value instanceof String) {
                    String strVal = (String) value;
                    if ("true".equals(strVal)) {
                        value = true;
                    } else if ("false".equals(strVal)) {
                        value = false;
                    }
                }
                node.addAttribute(entry.getKey(), value);
            }
        }

        // Load containments
        Map<String, Object> containments = (Map<String, Object>) jsonMap.get("containments");
        if (containments != null) {
            if (!(containments instanceof Map)) {
                throw new IllegalArgumentException("containments must be an object");
            }
            for (Map.Entry<String, Object> entry : containments.entrySet()) {
                List<Object> children = (List<Object>) entry.getValue();
                for (Object child : children) {
                    ModelNode childNode = loadNode((Map<String, Object>) child);
                    node.addContainment(entry.getKey(), childNode);
                }
            }
        }

        // Load references
        Map<String, Object> references = (Map<String, Object>) jsonMap.get("references");
        if (references != null) {
            for (Map.Entry<String, Object> entry : references.entrySet()) {
                List<Object> refs = (List<Object>) entry.getValue();
                for (Object ref : refs) {
                    Map<String, Object> refMap = (Map<String, Object>) ref;
                    String targetPath = (String) refMap.get("path");
                    String targetChecksum = (String) refMap.get("checksum");
                    node.addReference(entry.getKey(), new LoadedReferenceNode(targetPath, targetChecksum));
                }
            }
        }

        return node;
    }

    private void rematchEObjects(ModelNode loaded, ModelNode calculated) {
        // Match by checksum
        if (Objects.equals(loaded.getChecksum(), calculated.getChecksum())) {
            // Copy EObject reference if checksums match
            if (loaded instanceof LoadedModelNode) {
                ((LoadedModelNode) loaded).setEObject(calculated.getEObject());
            }
        }

        // Recursively match children
        for (Map.Entry<String, List<ModelNode>> entry : loaded.getContainments().entrySet()) {
            String refName = entry.getKey();
            List<ModelNode> loadedChildren = entry.getValue();
            List<ModelNode> calcChildren = calculated.getContainments().getOrDefault(refName, Collections.emptyList());

            // Match by identifier
            Map<String, ModelNode> calcByIdentifier = new HashMap<>();
            for (ModelNode calc : calcChildren) {
                calcByIdentifier.put(calc.getIdentifier(), calc);
            }

            for (ModelNode loadedChild : loadedChildren) {
                ModelNode calcChild = calcByIdentifier.get(loadedChild.getIdentifier());
                if (calcChild != null) {
                    rematchEObjects(loadedChild, calcChild);
                }
            }
        }
    }

    // Simple JSON parser (no external dependencies)

    private Map<String, Object> parseJsonObject(String json) {
        JsonParser parser = new JsonParser(json);
        return parser.parseObject();
    }

    /**
     * Simple JSON parser for loading ModelNode structures.
     */
    private static class JsonParser {
        private final String json;
        private int pos = 0;

        JsonParser(String json) {
            this.json = json;
        }

        Map<String, Object> parseObject() {
            skipWhitespace();
            if (pos >= json.length() || json.charAt(pos) != '{') {
                throw new IllegalArgumentException("Expected '{' at position " + pos);
            }
            pos++;

            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();

            if (pos < json.length() && json.charAt(pos) == '}') {
                pos++;
                return map;
            }

            while (pos < json.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos >= json.length() || json.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' at position " + pos);
                }
                pos++;
                skipWhitespace();
                Object value = parseValue();
                map.put(key, value);

                skipWhitespace();
                if (pos >= json.length()) break;
                char c = json.charAt(pos);
                if (c == '}') {
                    pos++;
                    return map;
                } else if (c == ',') {
                    pos++;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }

            throw new IllegalArgumentException("Unterminated object");
        }

        List<Object> parseArray() {
            skipWhitespace();
            if (json.charAt(pos) != '[') {
                throw new IllegalArgumentException("Expected '[' at position " + pos);
            }
            pos++;

            List<Object> list = new ArrayList<>();
            skipWhitespace();

            if (pos < json.length() && json.charAt(pos) == ']') {
                pos++;
                return list;
            }

            while (pos < json.length()) {
                skipWhitespace();
                Object value = parseValue();
                list.add(value);

                skipWhitespace();
                if (pos >= json.length()) break;
                char c = json.charAt(pos);
                if (c == ']') {
                    pos++;
                    return list;
                } else if (c == ',') {
                    pos++;
                }
            }

            throw new IllegalArgumentException("Unterminated array");
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= json.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = json.charAt(pos);
            if (c == '"') {
                return parseString();
            } else if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (Character.isDigit(c) || c == '-') {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
        }

        String parseString() {
            skipWhitespace();
            if (pos >= json.length() || json.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected '\"' at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                } else if (c == '\\') {
                    pos++;
                    if (pos < json.length()) {
                        char escaped = json.charAt(pos);
                        switch (escaped) {
                            case '"':
                            case '\\':
                            case '/':
                                sb.append(escaped);
                                break;
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case 'u':
                                if (pos + 4 < json.length()) {
                                    String hex = json.substring(pos + 1, pos + 5);
                                    sb.append((char) Integer.parseInt(hex, 16));
                                    pos += 4;
                                }
                                break;
                            default:
                                sb.append(escaped);
                        }
                        pos++;
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IllegalArgumentException("Expected boolean at position " + pos);
        }

        Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at position " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (json.charAt(pos) == '-') pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            if (pos < json.length() && json.charAt(pos) == '.') {
                pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
                return Double.parseDouble(json.substring(start, pos));
            }
            return Long.parseLong(json.substring(start, pos));
        }

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }

    /**
     * ModelNode loaded from JSON with mutable EObject reference.
     */
    private static class LoadedModelNode extends ModelNode {
        private final String loadedType;
        private EObject matchedEObject;

        LoadedModelNode(String type, String path, String identifier, String checksum) {
            super(null, path, identifier);
            this.loadedType = type;
            setChecksum(checksum);
        }

        @Override
        public String getType() {
            return loadedType;
        }

        @Override
        public EObject getEObject() {
            return matchedEObject;
        }

        void setEObject(EObject eObject) {
            this.matchedEObject = eObject;
        }
    }

    /**
     * ReferenceNode loaded from JSON with stored path and checksum.
     */
    private static class LoadedReferenceNode extends ReferenceNode {
        LoadedReferenceNode(String targetPath, String targetChecksum) {
            super(targetChecksum, targetPath, "");
        }
    }
}
