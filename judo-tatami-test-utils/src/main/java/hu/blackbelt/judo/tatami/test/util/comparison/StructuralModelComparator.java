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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares two ModelNode structures and returns differences.
 * <p>
 * Uses checksum shortcuts to skip identical subtrees for performance.
 * Comparison is order-independent for multi-valued references and containments.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ModelChecksumCalculator calc = new ModelChecksumCalculator();
 * ModelNode expected = calc.calculate(etlResource);
 * ModelNode actual = calc.calculate(zetaResource);
 *
 * StructuralModelComparator comparator = new StructuralModelComparator();
 * ComparisonResult result = comparator.compare(expected, actual);
 *
 * if (!result.isMatch()) {
 *     for (Difference diff : result.getDifferences()) {
 *         System.out.println(diff);
 *     }
 * }
 * }</pre>
 */
public class StructuralModelComparator {

    /**
     * Compares two model structures.
     *
     * @param expected the expected model structure (e.g., ETL output)
     * @param actual the actual model structure (e.g., ZETA output)
     * @return comparison result with differences
     */
    public ComparisonResult compare(ModelNode expected, ModelNode actual) {
        List<Difference> differences = new ArrayList<>();
        compareNodes(expected, actual, differences);
        return new ComparisonResult(expected, actual, differences);
    }

    private void compareNodes(ModelNode expected, ModelNode actual, List<Difference> diffs) {
        // Quick checksum check - skip if identical
        if (Objects.equals(expected.getChecksum(), actual.getChecksum())) {
            return;
        }

        String path = expected.getPath();

        // Type mismatch
        if (!expected.getType().equals(actual.getType())) {
            diffs.add(new Difference(path, Difference.DifferenceType.TYPE_MISMATCH,
                    "Type differs: expected '" + expected.getType() + "' but was '" + actual.getType() + "'",
                    expected, actual));
            return;
        }

        // Compare attributes
        compareAttributes(expected, actual, path, diffs);

        // Compare containments
        compareContainments(expected, actual, diffs);

        // Compare references
        compareReferences(expected, actual, path, diffs);
    }

    private void compareAttributes(ModelNode expected, ModelNode actual, String path, List<Difference> diffs) {
        Set<String> allAttrs = new HashSet<>();
        allAttrs.addAll(expected.getAttributes().keySet());
        allAttrs.addAll(actual.getAttributes().keySet());

        for (String attr : allAttrs) {
            Object expVal = expected.getAttributes().get(attr);
            Object actVal = actual.getAttributes().get(attr);

            if (!Objects.equals(expVal, actVal)) {
                diffs.add(new Difference(path, Difference.DifferenceType.ATTRIBUTE_MISMATCH,
                        "Attribute '" + attr + "' differs: expected '" + expVal + "' but was '" + actVal + "'",
                        expected, actual));
            }
        }
    }

    private void compareContainments(ModelNode expected, ModelNode actual, List<Difference> diffs) {
        Set<String> allRefs = new HashSet<>();
        allRefs.addAll(expected.getContainments().keySet());
        allRefs.addAll(actual.getContainments().keySet());

        for (String refName : allRefs) {
            List<ModelNode> expChildren = expected.getContainments().getOrDefault(refName, Collections.emptyList());
            List<ModelNode> actChildren = actual.getContainments().getOrDefault(refName, Collections.emptyList());

            // Build maps by identifier
            Map<String, ModelNode> expMap = expChildren.stream()
                    .collect(Collectors.toMap(ModelNode::getIdentifier, n -> n, (a, b) -> a));
            Map<String, ModelNode> actMap = actChildren.stream()
                    .collect(Collectors.toMap(ModelNode::getIdentifier, n -> n, (a, b) -> a));

            // Missing elements (in expected but not in actual)
            for (ModelNode exp : expChildren) {
                ModelNode act = actMap.get(exp.getIdentifier());
                if (act == null) {
                    diffs.add(new Difference(exp.getPath(), Difference.DifferenceType.MISSING,
                            "Element missing: " + exp.getType() + ":" + exp.getIdentifier(),
                            exp, null));
                } else {
                    // Recursively compare matching elements
                    compareNodes(exp, act, diffs);
                }
            }

            // Extra elements (in actual but not in expected)
            for (ModelNode act : actChildren) {
                if (!expMap.containsKey(act.getIdentifier())) {
                    diffs.add(new Difference(act.getPath(), Difference.DifferenceType.EXTRA,
                            "Extra element: " + act.getType() + ":" + act.getIdentifier(),
                            null, act));
                }
            }
        }
    }

    private void compareReferences(ModelNode expected, ModelNode actual, String path, List<Difference> diffs) {
        Set<String> allRefs = new HashSet<>();
        allRefs.addAll(expected.getReferences().keySet());
        allRefs.addAll(actual.getReferences().keySet());

        for (String refName : allRefs) {
            List<ReferenceNode> expRefs = expected.getReferences().getOrDefault(refName, Collections.emptyList());
            List<ReferenceNode> actRefs = actual.getReferences().getOrDefault(refName, Collections.emptyList());

            // Compare sorted checksums (order independent)
            List<String> expChecksums = expRefs.stream()
                    .map(ReferenceNode::getTargetChecksum)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
            List<String> actChecksums = actRefs.stream()
                    .map(ReferenceNode::getTargetChecksum)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            if (!expChecksums.equals(actChecksums)) {
                Set<String> expSet = new HashSet<>(expChecksums);
                Set<String> actSet = new HashSet<>(actChecksums);

                Set<String> missing = new HashSet<>(expSet);
                missing.removeAll(actSet);

                Set<String> extra = new HashSet<>(actSet);
                extra.removeAll(expSet);

                String desc = String.format("Reference '%s' differs: %d missing, %d extra targets",
                        refName, missing.size(), extra.size());

                diffs.add(new Difference(path, Difference.DifferenceType.REFERENCE_MISMATCH, desc, expected, actual));
            }
        }
    }

    // LLM-Friendly Output Format

    private static final String SCHEMA_VERSION = "1.0";

    /**
     * Formats comparison result as LLM-friendly XML output.
     *
     * @param result the comparison result to format
     * @return XML string formatted for LLM consumption
     * @throws IllegalArgumentException if result is null
     */
    public String formatForLLM(ComparisonResult result) {
        return formatForLLM(result, null);
    }

    /**
     * Formats comparison result as LLM-friendly XML output with options.
     *
     * @param result the comparison result to format
     * @param options formatting options (may be null for defaults)
     * @return XML string formatted for LLM consumption
     * @throws IllegalArgumentException if result is null
     */
    public String formatForLLM(ComparisonResult result, FormatOptions options) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (options == null) {
            options = new FormatOptions();
        }

        StringBuilder sb = new StringBuilder();
        String nl = options.isPrettyPrint() ? "\n" : "";
        String indent = options.isPrettyPrint() ? "  " : "";

        sb.append("<model-comparison>").append(nl);

        // Summary section
        sb.append(indent).append("<summary>").append(nl);
        sb.append(indent).append(indent).append("<status>")
                .append(result.isMatch() ? "MATCH" : "MISMATCH")
                .append("</status>").append(nl);
        sb.append(indent).append(indent).append("<total-differences>")
                .append(result.getDifferenceCount())
                .append("</total-differences>").append(nl);

        // Breakdown by type
        if (!result.isMatch()) {
            sb.append(indent).append(indent).append("<breakdown>").append(nl);
            for (Difference.DifferenceType type : Difference.DifferenceType.values()) {
                int count = result.getDifferences(type).size();
                if (count > 0) {
                    sb.append(indent).append(indent).append(indent).append("<")
                            .append(type.name().toLowerCase()).append(">")
                            .append(count)
                            .append("</").append(type.name().toLowerCase()).append(">").append(nl);
                }
            }
            sb.append(indent).append(indent).append("</breakdown>").append(nl);
        }
        sb.append(indent).append("</summary>").append(nl);

        // Differences section
        if (!result.isMatch()) {
            sb.append(indent).append("<differences>").append(nl);

            int maxDiffs = options.getMaxDifferences();
            List<Difference> diffs = result.getDifferences();
            int count = 0;

            for (Difference diff : diffs) {
                if (count >= maxDiffs) {
                    sb.append(indent).append(indent)
                            .append("<truncated remaining=\"").append(diffs.size() - count).append("\"/>")
                            .append(nl);
                    break;
                }

                sb.append(indent).append(indent)
                        .append("<difference type=\"").append(diff.getType()).append("\">")
                        .append(nl);

                sb.append(indent).append(indent).append(indent)
                        .append("<path>").append(escapeXml(diff.getPath())).append("</path>")
                        .append(nl);

                sb.append(indent).append(indent).append(indent)
                        .append("<description>").append(escapeXml(diff.getDescription())).append("</description>")
                        .append(nl);

                // Add context based on difference type
                if (options.isIncludeAnalysis()) {
                    sb.append(indent).append(indent).append(indent)
                            .append("<context>").append(nl);
                    addDifferenceContext(sb, diff, indent + indent + indent + indent, nl);
                    sb.append(indent).append(indent).append(indent)
                            .append("</context>").append(nl);
                }

                // Add suggestion
                if (options.isIncludeSuggestions()) {
                    sb.append(indent).append(indent).append(indent)
                            .append("<suggestion>").append(getSuggestion(diff)).append("</suggestion>")
                            .append(nl);
                }

                sb.append(indent).append(indent).append("</difference>").append(nl);
                count++;
            }

            sb.append(indent).append("</differences>").append(nl);
        }

        sb.append("</model-comparison>");
        return sb.toString();
    }

    private void addDifferenceContext(StringBuilder sb, Difference diff, String indent, String nl) {
        switch (diff.getType()) {
            case ATTRIBUTE_MISMATCH:
                if (diff.getExpectedNode() != null && diff.getActualNode() != null) {
                    sb.append(indent).append("<expected>").append(nl);
                    for (Map.Entry<String, Object> e : diff.getExpectedNode().getAttributes().entrySet()) {
                        sb.append(indent).append("  <attr name=\"").append(escapeXml(e.getKey())).append("\">")
                                .append(escapeXml(String.valueOf(e.getValue()))).append("</attr>").append(nl);
                    }
                    sb.append(indent).append("</expected>").append(nl);
                    sb.append(indent).append("<actual>").append(nl);
                    for (Map.Entry<String, Object> e : diff.getActualNode().getAttributes().entrySet()) {
                        sb.append(indent).append("  <attr name=\"").append(escapeXml(e.getKey())).append("\">")
                                .append(escapeXml(String.valueOf(e.getValue()))).append("</attr>").append(nl);
                    }
                    sb.append(indent).append("</actual>").append(nl);
                }
                break;
            case REFERENCE_MISMATCH:
                if (diff.getExpectedNode() != null) {
                    sb.append(indent).append("<expected-refs>").append(nl);
                    for (Map.Entry<String, List<ReferenceNode>> e : diff.getExpectedNode().getReferences().entrySet()) {
                        for (ReferenceNode ref : e.getValue()) {
                            sb.append(indent).append("  <ref name=\"").append(escapeXml(e.getKey()))
                                    .append("\" target=\"").append(escapeXml(ref.getTargetPath())).append("\"/>").append(nl);
                        }
                    }
                    sb.append(indent).append("</expected-refs>").append(nl);
                }
                if (diff.getActualNode() != null) {
                    sb.append(indent).append("<actual-refs>").append(nl);
                    for (Map.Entry<String, List<ReferenceNode>> e : diff.getActualNode().getReferences().entrySet()) {
                        for (ReferenceNode ref : e.getValue()) {
                            sb.append(indent).append("  <ref name=\"").append(escapeXml(e.getKey()))
                                    .append("\" target=\"").append(escapeXml(ref.getTargetPath())).append("\"/>").append(nl);
                        }
                    }
                    sb.append(indent).append("</actual-refs>").append(nl);
                }
                break;
            case MISSING:
                if (diff.getExpectedNode() != null) {
                    sb.append(indent).append("<missing-element type=\"")
                            .append(escapeXml(diff.getExpectedNode().getType()))
                            .append("\" id=\"").append(escapeXml(diff.getExpectedNode().getIdentifier()))
                            .append("\"/>").append(nl);
                }
                break;
            case EXTRA:
                if (diff.getActualNode() != null) {
                    sb.append(indent).append("<extra-element type=\"")
                            .append(escapeXml(diff.getActualNode().getType()))
                            .append("\" id=\"").append(escapeXml(diff.getActualNode().getIdentifier()))
                            .append("\"/>").append(nl);
                }
                break;
            case TYPE_MISMATCH:
                if (diff.getExpectedNode() != null && diff.getActualNode() != null) {
                    sb.append(indent).append("<expected-type>")
                            .append(escapeXml(diff.getExpectedNode().getType()))
                            .append("</expected-type>").append(nl);
                    sb.append(indent).append("<actual-type>")
                            .append(escapeXml(diff.getActualNode().getType()))
                            .append("</actual-type>").append(nl);
                }
                break;
        }
    }

    private String getSuggestion(Difference diff) {
        switch (diff.getType()) {
            case MISSING:
                return "Add the missing element to the actual model";
            case EXTRA:
                return "Remove the extra element from the actual model or add it to the expected model";
            case ATTRIBUTE_MISMATCH:
                return "Update the attribute value to match the expected value";
            case REFERENCE_MISMATCH:
                return "Update the reference targets to match the expected references";
            case TYPE_MISMATCH:
                return "Ensure both models use the same element type at this path";
            default:
                return "Review the difference and update accordingly";
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // JSON Persistence

    /**
     * Saves a comparison result to JSON file.
     *
     * @param result the comparison result to save
     * @param outputPath the file path to write to
     * @throws IllegalArgumentException if result or path is null
     * @throws IOException if the file cannot be written
     */
    public void saveJson(ComparisonResult result, Path outputPath) throws IOException {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath cannot be null");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schemaVersion\": \"").append(SCHEMA_VERSION).append("\",\n");
        sb.append("  \"timestamp\": \"").append(Instant.now().toString()).append("\",\n");
        sb.append("  \"isMatch\": ").append(result.isMatch()).append(",\n");

        // Checksums for quick equality checks
        if (result.getExpectedRoot() != null && result.getExpectedRoot().getChecksum() != null) {
            sb.append("  \"expectedChecksum\": \"").append(result.getExpectedRoot().getChecksum()).append("\",\n");
        }
        if (result.getActualRoot() != null && result.getActualRoot().getChecksum() != null) {
            sb.append("  \"actualChecksum\": \"").append(result.getActualRoot().getChecksum()).append("\",\n");
        }

        // Differences
        sb.append("  \"differenceCount\": ").append(result.getDifferenceCount()).append(",\n");
        sb.append("  \"differences\": [\n");

        boolean first = true;
        for (Difference diff : result.getDifferences()) {
            if (!first) sb.append(",\n");
            sb.append("    {\n");
            sb.append("      \"type\": \"").append(diff.getType()).append("\",\n");
            sb.append("      \"path\": \"").append(escapeJsonString(diff.getPath())).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJsonString(diff.getDescription())).append("\"");

            // Add expected/actual node info if available
            if (diff.getExpectedNode() != null) {
                sb.append(",\n      \"expectedType\": \"").append(diff.getExpectedNode().getType()).append("\"");
                sb.append(",\n      \"expectedId\": \"").append(escapeJsonString(diff.getExpectedNode().getIdentifier())).append("\"");
            }
            if (diff.getActualNode() != null) {
                sb.append(",\n      \"actualType\": \"").append(diff.getActualNode().getType()).append("\"");
                sb.append(",\n      \"actualId\": \"").append(escapeJsonString(diff.getActualNode().getIdentifier())).append("\"");
            }

            sb.append("\n    }");
            first = false;
        }

        sb.append("\n  ]\n");
        sb.append("}");

        Files.writeString(outputPath, sb.toString());
    }

    /**
     * Loads a comparison result from JSON file.
     *
     * @param inputPath the file path to read from
     * @return the loaded comparison result
     * @throws IllegalArgumentException if path is null or content is invalid
     * @throws IOException if the file cannot be read
     */
    public ComparisonResult loadJson(Path inputPath) throws IOException {
        if (inputPath == null) {
            throw new IllegalArgumentException("inputPath cannot be null");
        }
        if (!Files.exists(inputPath)) {
            throw new IOException("File does not exist: " + inputPath);
        }

        String content = Files.readString(inputPath);
        if (content.trim().isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try {
            Map<String, Object> json = parseJson(content);

            // Version check
            String version = (String) json.get("schemaVersion");
            if (version != null && !version.startsWith("1.")) {
                throw new IllegalArgumentException("Incompatible schema version: " + version);
            }

            // Parse fields
            Object isMatchObj = json.get("isMatch");
            if (!(isMatchObj instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid isMatch field: expected boolean");
            }

            Object diffsObj = json.get("differences");
            if (!(diffsObj instanceof List)) {
                throw new IllegalArgumentException("Invalid differences field: expected array");
            }

            @SuppressWarnings("unchecked")
            List<Object> diffsList = (List<Object>) diffsObj;
            List<Difference> differences = new ArrayList<>();

            for (Object diffObj : diffsList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> diffMap = (Map<String, Object>) diffObj;

                String typeStr = (String) diffMap.get("type");
                String path = (String) diffMap.get("path");
                String description = (String) diffMap.get("description");

                Difference.DifferenceType type = Difference.DifferenceType.valueOf(typeStr);
                differences.add(new Difference(path, type, description, null, null));
            }

            // Create result without root nodes (they're not serialized)
            return new ComparisonResult(null, null, differences);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Use the same simple JSON parser approach as ModelChecksumCalculator
        JsonParser parser = new JsonParser(json.trim());
        return parser.parseObject();
    }

    private String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Simple JSON parser for loading comparison results.
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
}
