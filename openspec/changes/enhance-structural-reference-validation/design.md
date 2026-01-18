# Design: Structural Model Comparison Utilities

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    ModelChecksumCalculator                       │
│  (Single model → ModelNode tree with checksums)                  │
├─────────────────────────────────────────────────────────────────┤
│  calculate(Resource) → ModelNode                                 │
│  toJson(ModelNode) → String                                      │
│  fromJson(String) → ModelNode          // NEW: Load from JSON    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         ModelNode                                │
│  (Containment tree with checksums and resolvable references)     │
├─────────────────────────────────────────────────────────────────┤
│  eObject, type, identifier, path, checksum                       │
│  attributes, containments, references                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   StructuralModelComparator                      │
│  (Two ModelNode trees → ComparisonResult)                        │
├─────────────────────────────────────────────────────────────────┤
│  compare(ModelNode expected, ModelNode actual) → ComparisonResult│
│  formatForLLM(ComparisonResult) → String   // NEW: LLM output    │
└─────────────────────────────────────────────────────────────────┘
```

## Data Structures

### 1. ModelNode - Containment Structure Node

```java
/**
 * Represents a node in the model's containment hierarchy.
 * Each node mirrors an EObject and stores its structural checksum.
 */
public class ModelNode {
    /** Direct reference to the source EObject */
    private final EObject eObject;

    /** EClass type name (e.g., "EClass", "EPackage") */
    private final String type;

    /** Element identifier (e.g., "MyClass", "myPackage") */
    private final String identifier;

    /** Containment path from root (e.g., "EPackage:root/EPackage:sub/EClass:MyClass") */
    private final String path;

    /** Structural checksum (type + attributes + children + references) */
    private String checksum;

    /** Attribute values keyed by attribute name */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /** Contained children keyed by containment reference name */
    private final Map<String, List<ModelNode>> containments = new LinkedHashMap<>();

    /** Non-containment references keyed by reference name */
    private final Map<String, List<ReferenceNode>> references = new LinkedHashMap<>();

    public ModelNode(EObject eObject, String path) {
        this.eObject = eObject;
        this.type = eObject != null ? eObject.eClass().getName() : "Root";
        this.identifier = eObject != null ? getIdentifier(eObject) : "";
        this.path = path;
    }

    public EObject getEObject() { return eObject; }
    public String getType() { return type; }
    public String getIdentifier() { return identifier; }
    public String getPath() { return path; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public Map<String, Object> getAttributes() { return attributes; }
    public Map<String, List<ModelNode>> getContainments() { return containments; }
    public Map<String, List<ReferenceNode>> getReferences() { return references; }

    public void addAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public void addContainment(String refName, ModelNode child) {
        containments.computeIfAbsent(refName, k -> new ArrayList<>()).add(child);
    }

    public void addReference(String refName, ReferenceNode ref) {
        references.computeIfAbsent(refName, k -> new ArrayList<>()).add(ref);
    }

    /** Recursively find node by full path */
    public ModelNode findByPath(String targetPath) {
        if (this.path.equals(targetPath)) {
            return this;
        }
        for (List<ModelNode> children : containments.values()) {
            for (ModelNode child : children) {
                ModelNode found = child.findByPath(targetPath);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
```

### 2. ReferenceNode - Non-Containment Reference

```java
/**
 * Represents a non-containment reference to another model element.
 * Can resolve to the actual ModelNode using the containment path.
 */
public class ReferenceNode {
    private final String targetChecksum;
    private final String targetPath;
    private final String targetIdentifier;
    private transient ModelNode resolved;

    public ReferenceNode(ModelNode target) {
        this.targetChecksum = target.getChecksum();
        this.targetPath = target.getPath();
        this.targetIdentifier = target.getIdentifier();
    }

    public String getTargetChecksum() { return targetChecksum; }
    public String getTargetPath() { return targetPath; }
    public String getTargetIdentifier() { return targetIdentifier; }

    public ModelNode resolve(ModelNode root) {
        if (resolved == null) {
            resolved = root.findByPath(targetPath);
        }
        return resolved;
    }

    public EObject resolveEObject(ModelNode root) {
        ModelNode node = resolve(root);
        return node != null ? node.getEObject() : null;
    }
}
```

### 3. ComparisonResult

```java
/**
 * Result of comparing two model structures.
 */
public class ComparisonResult {
    private final boolean match;
    private final List<Difference> differences;
    private final ModelNode expectedRoot;
    private final ModelNode actualRoot;

    public ComparisonResult(ModelNode expected, ModelNode actual, List<Difference> differences) {
        this.expectedRoot = expected;
        this.actualRoot = actual;
        this.differences = differences;
        this.match = differences.isEmpty();
    }

    public boolean isMatch() { return match; }
    public List<Difference> getDifferences() { return Collections.unmodifiableList(differences); }
    public ModelNode getExpectedRoot() { return expectedRoot; }
    public ModelNode getActualRoot() { return actualRoot; }

    public List<Difference> getDifferences(DifferenceType type) {
        return differences.stream()
            .filter(d -> d.getType() == type)
            .collect(Collectors.toList());
    }
}
```

### 4. CalculatorOptions - Feature Ignore Configuration

```java
/**
 * Configuration options for ModelChecksumCalculator.
 * Allows ignoring specific features and customizing identifier extraction.
 */
public class CalculatorOptions {
    private final Set<FeaturePattern> ignoredFeatures = new HashSet<>();
    private Function<EObject, String> identifierResolver = CalculatorOptions::defaultIdentifier;

    /**
     * Ignore a feature during checksum calculation.
     * @param pattern Feature pattern in format "package.Class#feature"
     *                Supports wildcards: *.Class#feature, package.*#feature, *.*#feature
     * @return this for fluent chaining
     */
    public CalculatorOptions ignore(String pattern) {
        ignoredFeatures.add(new FeaturePattern(pattern));
        return this;
    }

    /**
     * Ignore multiple features.
     */
    public CalculatorOptions ignoreAll(Collection<String> patterns) {
        patterns.forEach(this::ignore);
        return this;
    }

    /**
     * Set custom identifier resolver for EObjects.
     * The resolver extracts the identifier used in ModelNode paths.
     * @param resolver function that extracts identifier from EObject
     * @return this for fluent chaining
     */
    public CalculatorOptions withIdentifierResolver(Function<EObject, String> resolver) {
        this.identifierResolver = resolver;
        return this;
    }

    public Function<EObject, String> getIdentifierResolver() {
        return identifierResolver;
    }

    public Set<FeaturePattern> getIgnoredFeatures() {
        return Collections.unmodifiableSet(ignoredFeatures);
    }

    public boolean isIgnored(EClass eClass, EStructuralFeature feature) {
        for (FeaturePattern pattern : ignoredFeatures) {
            if (pattern.matches(eClass, feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Default identifier resolver.
     * Uses 'name' attribute if available, otherwise EClass name + containment index.
     */
    private static String defaultIdentifier(EObject obj) {
        // Try 'name' attribute first
        EStructuralFeature nameFeature = obj.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            Object name = obj.eGet(nameFeature);
            if (name != null) {
                return String.valueOf(name);
            }
        }
        // Fall back to type + index in container
        EObject container = obj.eContainer();
        if (container != null) {
            EReference containmentRef = obj.eContainmentFeature();
            if (containmentRef != null && containmentRef.isMany()) {
                List<?> siblings = (List<?>) container.eGet(containmentRef);
                int index = siblings.indexOf(obj);
                return obj.eClass().getName() + "_" + index;
            }
        }
        return obj.eClass().getName();
    }
}
```

### 5. FeaturePattern - Pattern Matching for Features

```java
/**
 * Parses and matches feature patterns like "package.Class#feature".
 * Supports wildcards for package, class, and feature.
 */
public class FeaturePattern {
    private final String packagePattern;  // "*" for any
    private final String classPattern;    // "*" for any
    private final String featurePattern;  // feature name or "*"

    /**
     * Parse pattern from string.
     * Format: "package.Class#feature"
     * Examples:
     *   - "ecore.EClass#abstract" - exact match
     *   - "*.EClass#abstract" - any package
     *   - "ecore.*#name" - any class in package
     *   - "*.*#uuid" - global (all features named uuid)
     */
    public FeaturePattern(String pattern) {
        int hashIndex = pattern.indexOf('#');
        if (hashIndex == -1) {
            throw new IllegalArgumentException(
                "Invalid pattern: must contain '#'. Expected format: package.Class#feature");
        }

        String typePattern = pattern.substring(0, hashIndex);
        this.featurePattern = pattern.substring(hashIndex + 1);

        int dotIndex = typePattern.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException(
                "Invalid pattern: must contain '.'. Expected format: package.Class#feature");
        }

        this.packagePattern = typePattern.substring(0, dotIndex);
        this.classPattern = typePattern.substring(dotIndex + 1);
    }

    public boolean matches(EClass eClass, EStructuralFeature feature) {
        // Match package
        EPackage pkg = eClass.getEPackage();
        String pkgName = pkg != null ? pkg.getName() : "";
        if (!matchesWildcard(packagePattern, pkgName)) {
            return false;
        }

        // Match class
        if (!matchesWildcard(classPattern, eClass.getName())) {
            return false;
        }

        // Match feature
        return matchesWildcard(featurePattern, feature.getName());
    }

    private boolean matchesWildcard(String pattern, String value) {
        return "*".equals(pattern) || pattern.equals(value);
    }

    @Override
    public String toString() {
        return packagePattern + "." + classPattern + "#" + featurePattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeaturePattern that = (FeaturePattern) o;
        return Objects.equals(packagePattern, that.packagePattern)
            && Objects.equals(classPattern, that.classPattern)
            && Objects.equals(featurePattern, that.featurePattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packagePattern, classPattern, featurePattern);
    }
}
```

### 6. Difference

```java
/**
 * Represents a single difference between two model structures.
 */
public class Difference {
    public enum DifferenceType {
        MISSING,              // Element in expected but not actual
        EXTRA,                // Element in actual but not expected
        ATTRIBUTE_MISMATCH,   // Attribute values differ
        REFERENCE_MISMATCH,   // Non-containment reference targets differ
        TYPE_MISMATCH         // Element types differ
    }

    private final String path;
    private final DifferenceType type;
    private final String description;
    private final ModelNode expectedNode;
    private final ModelNode actualNode;

    public Difference(String path, DifferenceType type, String description,
                      ModelNode expected, ModelNode actual) {
        this.path = path;
        this.type = type;
        this.description = description;
        this.expectedNode = expected;
        this.actualNode = actual;
    }

    public String getPath() { return path; }
    public DifferenceType getType() { return type; }
    public String getDescription() { return description; }

    public EObject getExpectedObject() {
        return expectedNode != null ? expectedNode.getEObject() : null;
    }

    public EObject getActualObject() {
        return actualNode != null ? actualNode.getEObject() : null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", type, path, description);
    }
}
```

---

## ModelChecksumCalculator Implementation

```java
/**
 * Builds structural representation of EMF models with checksums.
 * Handles containment hierarchy and non-containment references separately.
 * Supports ignoring specific features via CalculatorOptions.
 */
public class ModelChecksumCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(ModelChecksumCalculator.class);

    private Map<String, ModelNode> pathIndex;
    private CalculatorOptions options;
    private Map<String, Integer> ignoreMatchCounts;

    /**
     * Calculates the structural representation of a model with default options.
     */
    public ModelNode calculate(Resource resource) {
        return calculate(resource, new CalculatorOptions());
    }

    /**
     * Calculates the structural representation of a model.
     * @param resource the EMF resource to analyze
     * @param options configuration including ignored features
     * @return root ModelNode of the containment tree
     */
    public ModelNode calculate(Resource resource, CalculatorOptions options) {
        this.options = options;
        this.pathIndex = new HashMap<>();
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
        ModelNode root = new ModelNode(null, "");

        for (EObject content : resource.getContents()) {
            ModelNode node = buildNode(content, "", allNodes);
            root.addContainment("contents", node);
        }

        return root;
    }

    private ModelNode buildNode(EObject obj, String parentPath, List<ModelNode> allNodes) {
        // Use custom identifier resolver from options
        String identifier = options.getIdentifierResolver().apply(obj);
        String nodeId = obj.eClass().getName() + ":" + identifier;
        String path = parentPath.isEmpty() ? nodeId : parentPath + "/" + nodeId;

        ModelNode node = new ModelNode(obj, path);
        pathIndex.put(path, node);
        allNodes.add(node);

        EClass eClass = obj.eClass();

        // Collect attributes (skip ignored, derived, and transient)
        for (EAttribute attr : eClass.getEAllAttributes()) {
            if (attr.isDerived() || attr.isTransient()) {
                continue;  // Skip derived and transient attributes
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
        for (EReference ref : eClass.getEAllReferences()) {
            if (!ref.isContainment()) continue;

            Object value = obj.eGet(ref);
            if (value == null) continue;

            if (ref.isMany()) {
                for (EObject child : (Collection<EObject>) value) {
                    ModelNode childNode = buildNode(child, path, allNodes);
                    node.addContainment(ref.getName(), childNode);
                }
            } else {
                ModelNode childNode = buildNode((EObject) value, path, allNodes);
                node.addContainment(ref.getName(), childNode);
            }
        }

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
        // Reverse for bottom-up (leaves first)
        Collections.reverse(allNodes);

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
        digest.update(node.getType().getBytes(UTF_8));

        // Sorted attributes
        node.getAttributes().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                digest.update(e.getKey().getBytes(UTF_8));
                digest.update(String.valueOf(e.getValue()).getBytes(UTF_8));
            });

        // Sorted containment checksums
        node.getContainments().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                digest.update(e.getKey().getBytes(UTF_8));
                e.getValue().stream()
                    .map(ModelNode::getChecksum)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEach(cs -> digest.update(cs.getBytes(UTF_8)));
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
            if (ref.isDerived() || ref.isTransient()) continue;  // Skip derived and transient
            if (!shouldIncludeReference(ref)) continue;
            if (isIgnored(eClass, ref)) continue;  // Skip ignored references

            Object value = obj.eGet(ref, true);  // Resolve proxies lazily
            if (value == null) continue;

            if (ref.isMany()) {
                for (EObject target : (Collection<EObject>) value) {
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

        // Resolve proxy if needed (lazy resolution)
        if (target.eIsProxy()) {
            target = EcoreUtil.resolve(target, currentResource);
            if (target.eIsProxy()) {
                LOG.warn("Could not resolve proxy reference at {}", node.getPath());
                return;
            }
        }

        String targetPath = getFullPath(target);
        ModelNode targetNode = pathIndex.get(targetPath);
        if (targetNode != null) {
            node.addReference(ref.getName(), new ReferenceNode(targetNode));
        }
    }

    private void computeFullChecksum(ModelNode node) {
        if (node.getReferences().isEmpty()) return;

        MessageDigest digest = getDigest();
        digest.update(node.getChecksum().getBytes(UTF_8));

        node.getReferences().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                digest.update(e.getKey().getBytes(UTF_8));
                e.getValue().stream()
                    .map(ReferenceNode::getTargetChecksum)
                    .sorted()
                    .forEach(cs -> digest.update(cs.getBytes(UTF_8)));
            });

        node.setChecksum(bytesToHex(digest.digest()));
    }

    private boolean shouldIncludeReference(EReference ref) {
        EReference opposite = ref.getEOpposite();
        if (opposite == null) return true;
        if (ref.isContainer()) return false;
        if (opposite.isContainer()) return true;
        return ref.getName().compareTo(opposite.getName()) <= 0;
    }

    // JSON Export
    public String toJson(ModelNode root) {
        return toJson(root, new JsonOptions());
    }

    public String toJson(ModelNode root, JsonOptions options) {
        StringBuilder sb = new StringBuilder();
        writeJson(root, sb, 0, options);
        return sb.toString();
    }

    private void writeJson(ModelNode node, StringBuilder sb, int indent, JsonOptions options) {
        String pad = "  ".repeat(indent);
        sb.append(pad).append("{\n");
        sb.append(pad).append("  \"type\": \"").append(escapeJson(node.getType())).append("\",\n");
        sb.append(pad).append("  \"identifier\": \"").append(escapeJson(node.getIdentifier())).append("\",\n");
        sb.append(pad).append("  \"path\": \"").append(escapeJson(node.getPath())).append("\",\n");
        sb.append(pad).append("  \"checksum\": \"").append(node.getChecksum()).append("\"");

        if (options.includeAttributes && !node.getAttributes().isEmpty()) {
            sb.append(",\n").append(pad).append("  \"attributes\": {");
            boolean first = true;
            for (Map.Entry<String, Object> e : node.getAttributes().entrySet()) {
                if (!first) sb.append(",");
                sb.append("\n").append(pad).append("    \"").append(escapeJson(e.getKey()))
                  .append("\": \"").append(escapeJson(String.valueOf(e.getValue()))).append("\"");
                first = false;
            }
            sb.append("\n").append(pad).append("  }");
        }

        if (!node.getReferences().isEmpty()) {
            sb.append(",\n").append(pad).append("  \"references\": {");
            boolean firstRef = true;
            for (Map.Entry<String, List<ReferenceNode>> e : node.getReferences().entrySet()) {
                if (!firstRef) sb.append(",");
                sb.append("\n").append(pad).append("    \"").append(escapeJson(e.getKey())).append("\": [");
                boolean firstTarget = true;
                for (ReferenceNode ref : e.getValue()) {
                    if (!firstTarget) sb.append(",");
                    sb.append("\n").append(pad).append("      {\"path\": \"")
                      .append(escapeJson(ref.getTargetPath())).append("\", \"checksum\": \"")
                      .append(ref.getTargetChecksum()).append("\"}");
                    firstTarget = false;
                }
                sb.append("\n").append(pad).append("    ]");
                firstRef = false;
            }
            sb.append("\n").append(pad).append("  }");
        }

        if (!node.getContainments().isEmpty()) {
            sb.append(",\n").append(pad).append("  \"containments\": {");
            boolean firstCont = true;
            for (Map.Entry<String, List<ModelNode>> e : node.getContainments().entrySet()) {
                if (!firstCont) sb.append(",");
                sb.append("\n").append(pad).append("    \"").append(escapeJson(e.getKey())).append("\": [");
                boolean firstChild = true;
                for (ModelNode child : e.getValue()) {
                    if (!firstChild) sb.append(",");
                    sb.append("\n");
                    writeJson(child, sb, indent + 3, options);
                    firstChild = false;
                }
                sb.append("\n").append(pad).append("    ]");
                firstCont = false;
            }
            sb.append("\n").append(pad).append("  }");
        }

        sb.append("\n").append(pad).append("}");
    }

    public static class JsonOptions {
        public boolean includeAttributes = true;
        public boolean includeChecksums = true;
        public int maxDepth = Integer.MAX_VALUE;
    }
}
```

---

## ModelComparator Implementation

```java
/**
 * Compares two ModelNode structures and returns differences.
 * Uses checksum shortcuts to skip identical subtrees.
 */
public class ModelComparator {

    /**
     * Compares two model structures.
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
            diffs.add(new Difference(path, DifferenceType.TYPE_MISMATCH,
                "Type differs: expected '" + expected.getType() + "' but was '" + actual.getType() + "'",
                expected, actual));
            return;
        }

        // Compare attributes
        compareAttributes(expected, actual, path, diffs);

        // Compare containments
        compareContainments(expected, actual, path, diffs);

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
                diffs.add(new Difference(path, DifferenceType.ATTRIBUTE_MISMATCH,
                    "Attribute '" + attr + "' differs: expected '" + expVal + "' but was '" + actVal + "'",
                    expected, actual));
            }
        }
    }

    private void compareContainments(ModelNode expected, ModelNode actual, String path, List<Difference> diffs) {
        Set<String> allRefs = new HashSet<>();
        allRefs.addAll(expected.getContainments().keySet());
        allRefs.addAll(actual.getContainments().keySet());

        for (String refName : allRefs) {
            List<ModelNode> expChildren = expected.getContainments().getOrDefault(refName, List.of());
            List<ModelNode> actChildren = actual.getContainments().getOrDefault(refName, List.of());

            // Build maps by identifier
            Map<String, ModelNode> expMap = expChildren.stream()
                .collect(Collectors.toMap(ModelNode::getIdentifier, n -> n, (a, b) -> a));
            Map<String, ModelNode> actMap = actChildren.stream()
                .collect(Collectors.toMap(ModelNode::getIdentifier, n -> n, (a, b) -> a));

            // Missing elements
            for (ModelNode exp : expChildren) {
                ModelNode act = actMap.get(exp.getIdentifier());
                if (act == null) {
                    diffs.add(new Difference(exp.getPath(), DifferenceType.MISSING,
                        "Element missing: " + exp.getType() + ":" + exp.getIdentifier(),
                        exp, null));
                } else {
                    compareNodes(exp, act, diffs);
                }
            }

            // Extra elements
            for (ModelNode act : actChildren) {
                if (!expMap.containsKey(act.getIdentifier())) {
                    diffs.add(new Difference(act.getPath(), DifferenceType.EXTRA,
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
            List<ReferenceNode> expRefs = expected.getReferences().getOrDefault(refName, List.of());
            List<ReferenceNode> actRefs = actual.getReferences().getOrDefault(refName, List.of());

            // Compare sorted checksums (order independent)
            List<String> expChecksums = expRefs.stream()
                .map(ReferenceNode::getTargetChecksum)
                .sorted()
                .collect(Collectors.toList());
            List<String> actChecksums = actRefs.stream()
                .map(ReferenceNode::getTargetChecksum)
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

                diffs.add(new Difference(path, DifferenceType.REFERENCE_MISMATCH, desc, expected, actual));
            }
        }
    }
}
```

---

## TDD Test Design

### Test Files

- `ModelChecksumCalculatorTest.java` - Tests for structure building and checksums
- `ModelComparatorTest.java` - Tests for comparison logic
- `ModelNodeTest.java` - Tests for ModelNode operations
- `ReferenceNodeTest.java` - Tests for ReferenceNode resolution

### Tests for ModelChecksumCalculator

```java
@Test
void calculate_buildsContainmentTree() {
    Resource resource = createModelWithPackageAndClass();
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    ModelNode root = calc.calculate(resource);

    assertNotNull(root.getContainments().get("contents"));
    assertEquals(1, root.getContainments().get("contents").size());
}

@Test
void calculate_identicalModels_sameChecksum() {
    Resource model1 = createSimpleModel();
    Resource model2 = createSimpleModel();
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    assertEquals(calc.calculate(model1).getChecksum(), calc.calculate(model2).getChecksum());
}

@Test
void calculate_differentAttribute_differentChecksum() {
    Resource model1 = createModelWithAbstract(true);
    Resource model2 = createModelWithAbstract(false);
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    assertNotEquals(calc.calculate(model1).getChecksum(), calc.calculate(model2).getChecksum());
}

@Test
void calculate_multiValuedReferenceOrder_sameChecksum() {
    Resource model1 = createModelWithSuperTypes("Base1", "Base2");
    Resource model2 = createModelWithSuperTypes("Base2", "Base1");
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    assertEquals(calc.calculate(model1).getChecksum(), calc.calculate(model2).getChecksum());
}

@Test
void toJson_producesValidJson() {
    Resource resource = createSimpleModel();
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode root = calc.calculate(resource);

    String json = calc.toJson(root);

    assertTrue(json.contains("\"type\""));
    assertTrue(json.contains("\"checksum\""));
}

@Test
void calculate_withIgnoredAttribute_excludesFromChecksum() {
    Resource model1 = createModelWithAbstract(true);
    Resource model2 = createModelWithAbstract(false);
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    CalculatorOptions options = new CalculatorOptions()
        .ignore("ecore.EClass#abstract");

    // Without ignore: different checksums
    assertNotEquals(calc.calculate(model1).getChecksum(),
                   calc.calculate(model2).getChecksum());

    // With ignore: same checksums
    assertEquals(calc.calculate(model1, options).getChecksum(),
                calc.calculate(model2, options).getChecksum());
}

@Test
void calculate_withIgnoredReference_excludesFromChecksum() {
    Resource model1 = createModelWithSuperTypes("Base1", "Base2");
    Resource model2 = createModelWithSuperTypes("Base1");
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    CalculatorOptions options = new CalculatorOptions()
        .ignore("ecore.EClass#eSuperTypes");

    // With ignore: same checksums (only difference was eSuperTypes)
    assertEquals(calc.calculate(model1, options).getChecksum(),
                calc.calculate(model2, options).getChecksum());
}

@Test
void calculate_wildcardPackage_matchesAnyPackage() {
    CalculatorOptions options = new CalculatorOptions()
        .ignore("*.EClass#abstract");

    FeaturePattern pattern = options.getIgnoredFeatures().iterator().next();
    EClass eClass = EcoreFactory.eINSTANCE.createEClass();

    assertTrue(pattern.matches(eClass, EcorePackage.Literals.ECLASS__ABSTRACT));
}

@Test
void calculate_wildcardClass_matchesAnyClass() {
    CalculatorOptions options = new CalculatorOptions()
        .ignore("ecore.*#name");

    FeaturePattern pattern = options.getIgnoredFeatures().iterator().next();
    EClass eClass = EcoreFactory.eINSTANCE.createEClass();
    EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();

    assertTrue(pattern.matches(eClass, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
    assertTrue(pattern.matches(eAttr.eClass(), EcorePackage.Literals.ENAMED_ELEMENT__NAME));
}

@Test
void calculate_globalWildcard_matchesAllFeatures() {
    CalculatorOptions options = new CalculatorOptions()
        .ignore("*.*#name");

    FeaturePattern pattern = options.getIgnoredFeatures().iterator().next();
    EClass anyClass = EcoreFactory.eINSTANCE.createEClass();

    assertTrue(pattern.matches(anyClass, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
}
```

### Tests for ModelComparator

#### Basic Difference Type Tests

```java
@Test
void compare_identicalModels_noErrors() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createComplexModel());
    ModelNode actual = calc.calculate(createComplexModel());

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertTrue(result.isMatch());
    assertTrue(result.getDifferences().isEmpty());
}

@Test
void compare_missingElement_reportsDifference() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithClasses("A", "B"));
    ModelNode actual = calc.calculate(createModelWithClasses("A"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertEquals(1, result.getDifferences(DifferenceType.MISSING).size());

    Difference diff = result.getDifferences().get(0);
    assertNotNull(diff.getExpectedObject());
    assertNull(diff.getActualObject());
    assertTrue(diff.getDescription().contains("B"));
}

@Test
void compare_extraElement_reportsDifference() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithClasses("A"));
    ModelNode actual = calc.calculate(createModelWithClasses("A", "B"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertEquals(1, result.getDifferences(DifferenceType.EXTRA).size());

    Difference diff = result.getDifferences().get(0);
    assertNull(diff.getExpectedObject());
    assertNotNull(diff.getActualObject());
    assertTrue(diff.getDescription().contains("B"));
}

@Test
void compare_typeMismatch_reportsDifference() {
    // Create model where element has different EClass type
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithInterface("Foo"));
    ModelNode actual = calc.calculate(createModelWithClass("Foo"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertEquals(1, result.getDifferences(DifferenceType.TYPE_MISMATCH).size());

    Difference diff = result.getDifferences().get(0);
    assertTrue(diff.getDescription().contains("EInterface"));
    assertTrue(diff.getDescription().contains("EClass"));
}

@Test
void compare_attributeMismatch_includesValues() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithAbstract(true));
    ModelNode actual = calc.calculate(createModelWithAbstract(false));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    Difference diff = result.getDifferences(DifferenceType.ATTRIBUTE_MISMATCH).get(0);
    assertTrue(diff.getDescription().contains("abstract"));
    assertTrue(diff.getDescription().contains("true"));
    assertTrue(diff.getDescription().contains("false"));
}

@Test
void compare_referenceMismatch_differentTargets() {
    // Class1 references Base1 vs Class1 references Base2
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithSuperType("Child", "Base1"));
    ModelNode actual = calc.calculate(createModelWithSuperType("Child", "Base2"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertEquals(1, result.getDifferences(DifferenceType.REFERENCE_MISMATCH).size());
}

@Test
void compare_referenceMismatch_missingReference() {
    // Child has superType vs Child has no superType
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithSuperType("Child", "Base"));
    ModelNode actual = calc.calculate(createModelWithClass("Child"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertTrue(result.getDifferences(DifferenceType.REFERENCE_MISMATCH).size() > 0);
}

@Test
void compare_referenceMismatch_extraReference() {
    // Child has no superType vs Child has superType
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createModelWithClass("Child"));
    ModelNode actual = calc.calculate(createModelWithSuperType("Child", "Base"));

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(expected, actual);

    assertFalse(result.isMatch());
    assertTrue(result.getDifferences(DifferenceType.REFERENCE_MISMATCH).size() > 0);
}
```

#### Bidirectional Reference Tests

```java
@Test
void compare_bidirectional_ownerSideOnly() {
    // EReference with opposite - only owner side affects checksum
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    // Create package with EClass containing bidirectional references
    // Parent.children <-> Child.parent
    Resource model1 = createBidirectionalModel("Parent", "Child1", "Child2");
    Resource model2 = createBidirectionalModel("Parent", "Child1", "Child2");

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_bidirectional_containerNotInChecksum() {
    // Container reference (eContainer direction) should not be in checksum
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource resource = createContainmentModel();
    ModelNode root = calc.calculate(resource);

    // Find child node
    ModelNode childNode = root.findByPath("EPackage:pkg/EClass:Parent/EStructuralFeature:child");

    // Container reference should not be in references map
    assertFalse(childNode.getReferences().containsKey("eContainer"));
}

@Test
void compare_bidirectional_alphabeticalOwner() {
    // For non-container bidirectional, alphabetically first is owner
    // e.g., "left" <-> "right" - "left" is owner
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource resource = createSymmetricBidirectionalModel();
    ModelNode root = calc.calculate(resource);

    // "left" reference should be included, "right" should not
    ModelNode node = root.findByPath("EPackage:pkg/EClass:Node");
    assertTrue(node.getReferences().containsKey("left") ||
               !node.getReferences().containsKey("right"));
}

@Test
void compare_bidirectional_sameChecksumRegardlessOfTraversal() {
    // Building from either side should produce same checksum
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createBidirectionalFromParent();
    Resource model2 = createBidirectionalFromChild();

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}
```

#### Multi-Layer Containment Tests

```java
@Test
void compare_multiLayerContainment_identicalStructure() {
    // EPackage -> EPackage -> EClass -> EAttribute
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepContainmentModel(4);
    Resource model2 = createDeepContainmentModel(4);

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_multiLayerContainment_differenceAtLeaf() {
    // Difference at deepest level
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepContainmentWithAttribute("value1");
    Resource model2 = createDeepContainmentWithAttribute("value2");

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    // Path should show full containment chain
    Difference diff = result.getDifferences().get(0);
    assertTrue(diff.getPath().contains("/"));
    assertEquals(3, diff.getPath().split("/").length); // 3 levels deep
}

@Test
void compare_multiLayerContainment_differenceAtMiddle() {
    // Difference at middle layer
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepContainmentWithMiddleAttribute("pkg", "subpkg1", "Class1");
    Resource model2 = createDeepContainmentWithMiddleAttribute("pkg", "subpkg2", "Class1");

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    // Should report missing subpkg1 and extra subpkg2
    assertEquals(2, result.getDifferences().size());
}

@Test
void compare_multiLayerContainment_missingSubtree() {
    // Missing entire subtree
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepContainmentModel(4);
    Resource model2 = createDeepContainmentModel(2); // Missing 2 levels

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    assertTrue(result.getDifferences(DifferenceType.MISSING).size() > 0);
}

@Test
void compare_multiLayerContainment_pathCorrectness() {
    // Verify paths are built correctly for deep structures
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource resource = createPackageWithSubpackageAndClass("root", "sub", "MyClass");
    ModelNode root = calc.calculate(resource);

    ModelNode classNode = root.findByPath("EPackage:root/EPackage:sub/EClass:MyClass");
    assertNotNull(classNode);
    assertEquals("EPackage:root/EPackage:sub/EClass:MyClass", classNode.getPath());
}

@Test
void compare_multiLayerContainment_checksumPropagation() {
    // Change at leaf should change all parent checksums
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepContainmentWithAttribute("value1");
    Resource model2 = createDeepContainmentWithAttribute("value2");

    ModelNode root1 = calc.calculate(model1);
    ModelNode root2 = calc.calculate(model2);

    // All checksums in the containment chain should differ
    assertNotEquals(root1.getChecksum(), root2.getChecksum());

    // Leaf nodes differ
    ModelNode leaf1 = root1.findByPath("EPackage:pkg/EClass:Cls/EAttribute:attr");
    ModelNode leaf2 = root2.findByPath("EPackage:pkg/EClass:Cls/EAttribute:attr");
    assertNotEquals(leaf1.getChecksum(), leaf2.getChecksum());
}
```

#### Inheritance Tests

```java
@Test
void compare_inheritance_singleSuperType() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createInheritanceModel("Child", "Parent");
    Resource model2 = createInheritanceModel("Child", "Parent");

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_inheritance_differentSuperType() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createInheritanceModel("Child", "Parent1");
    Resource model2 = createInheritanceModel("Child", "Parent2");

    assertNotEquals(calc.calculate(model1).getChecksum(),
                    calc.calculate(model2).getChecksum());
}

@Test
void compare_inheritance_multipleSuperTypes_orderIndependent() {
    // Multiple inheritance - order should not matter
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createMultiInheritanceModel("Child", "Base1", "Base2", "Base3");
    Resource model2 = createMultiInheritanceModel("Child", "Base3", "Base1", "Base2");

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_inheritance_differentMultipleSuperTypes() {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createMultiInheritanceModel("Child", "Base1", "Base2");
    Resource model2 = createMultiInheritanceModel("Child", "Base1", "Base3");

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    assertEquals(1, result.getDifferences(DifferenceType.REFERENCE_MISMATCH).size());
}

@Test
void compare_inheritance_diamondPattern() {
    // Diamond inheritance: D extends B,C which both extend A
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDiamondInheritance();
    Resource model2 = createDiamondInheritance();

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_inheritance_chainedInheritance() {
    // A -> B -> C -> D (inheritance chain)
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createInheritanceChain("A", "B", "C", "D");
    Resource model2 = createInheritanceChain("A", "B", "C", "D");

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_inheritance_brokenChain() {
    // Missing link in inheritance chain
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createInheritanceChain("A", "B", "C", "D");
    Resource model2 = createInheritanceChain("A", "B", "D"); // Missing C

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
}

@Test
void compare_inheritance_superTypeWithDifferentAttributes() {
    // Same inheritance structure but supertype has different attributes
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createInheritanceWithSuperAttribute("Base", "attr1", "Child");
    Resource model2 = createInheritanceWithSuperAttribute("Base", "attr2", "Child");

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    // Should detect attribute difference in Base, not in Child
    Difference diff = result.getDifferences().get(0);
    assertTrue(diff.getPath().contains("Base"));
}
```

#### Checksum Optimization Tests

```java
@Test
void compare_checksumOptimization_skipsIdenticalSubtrees() {
    // Large model with one difference - should be fast
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expected = calc.calculate(createLargeModel(1000));
    ModelNode actual = calc.calculate(createLargeModelWithOneDiff(1000));

    ModelComparator comparator = new ModelComparator();
    long start = System.currentTimeMillis();
    ComparisonResult result = comparator.compare(expected, actual);
    long duration = System.currentTimeMillis() - start;

    assertFalse(result.isMatch());
    assertTrue(duration < 1000, "Should complete quickly due to checksum optimization");
}

@Test
void compare_checksumOptimization_identicalLargeModel() {
    // Completely identical large model - should be very fast
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode model = calc.calculate(createLargeModel(5000));

    ModelComparator comparator = new ModelComparator();
    long start = System.currentTimeMillis();
    ComparisonResult result = comparator.compare(model, model);
    long duration = System.currentTimeMillis() - start;

    assertTrue(result.isMatch());
    assertTrue(duration < 100, "Identical models should short-circuit immediately");
}
```

#### Combined Scenario Tests

```java
@Test
void compare_combined_multiLayerWithInheritance() {
    // Complex model with deep containment and inheritance
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createComplexModelWithInheritance();
    Resource model2 = createComplexModelWithInheritance();

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}

@Test
void compare_combined_multipleDifferenceTypes() {
    // Model with MISSING, EXTRA, and ATTRIBUTE_MISMATCH
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createModelWithMultipleDifferences(1);
    Resource model2 = createModelWithMultipleDifferences(2);

    ModelComparator comparator = new ModelComparator();
    ComparisonResult result = comparator.compare(
        calc.calculate(model1), calc.calculate(model2));

    assertFalse(result.isMatch());
    assertTrue(result.getDifferences(DifferenceType.MISSING).size() > 0);
    assertTrue(result.getDifferences(DifferenceType.EXTRA).size() > 0);
    assertTrue(result.getDifferences(DifferenceType.ATTRIBUTE_MISMATCH).size() > 0);
}

@Test
void compare_combined_bidirectionalInDeepContainment() {
    // Bidirectional references at various containment levels
    ModelChecksumCalculator calc = new ModelChecksumCalculator();

    Resource model1 = createDeepBidirectionalModel();
    Resource model2 = createDeepBidirectionalModel();

    assertEquals(calc.calculate(model1).getChecksum(),
                 calc.calculate(model2).getChecksum());
}
```

---

## Example Usage

```java
// Basic usage (no ignores)
ModelChecksumCalculator calc = new ModelChecksumCalculator();
ModelNode etlStructure = calc.calculate(etlResource);
ModelNode zetaStructure = calc.calculate(zetaResource);

// With ignored features
CalculatorOptions options = new CalculatorOptions()
    .ignore("asm.EAnnotation#details")      // Ignore annotation details
    .ignore("*.EClass#instanceClassName")   // Ignore instanceClassName on all EClass
    .ignore("*.*#uuid");                    // Ignore uuid globally

ModelNode etlStructure = calc.calculate(etlResource, options);
ModelNode zetaStructure = calc.calculate(zetaResource, options);

// Compare
ModelComparator comparator = new ModelComparator();
ComparisonResult result = comparator.compare(etlStructure, zetaStructure);

if (!result.isMatch()) {
    System.out.println("Found " + result.getDifferences().size() + " differences:");

    for (Difference diff : result.getDifferences()) {
        System.out.println(diff);  // [ATTRIBUTE_MISMATCH] EPackage:pkg/EClass:Foo: ...

        // Direct access to EObjects for debugging
        EObject expected = diff.getExpectedObject();
        EObject actual = diff.getActualObject();

        if (expected != null) {
            System.out.println("  Expected: " + EcoreUtil.getURI(expected));
        }
        if (actual != null) {
            System.out.println("  Actual: " + EcoreUtil.getURI(actual));
        }
    }
}

// Export structures for offline analysis
Files.writeString(Path.of("etl-structure.json"), calc.toJson(etlStructure));
Files.writeString(Path.of("zeta-structure.json"), calc.toJson(zetaStructure));

// NEW: Load structure from JSON (for caching/offline analysis)
String savedJson = Files.readString(Path.of("etl-structure.json"));
ModelNode loadedStructure = calc.fromJson(savedJson);

// NEW: Format differences for LLM processing
String llmOutput = comparator.formatForLLM(result);
System.out.println(llmOutput);
```

---

## NEW: JSON Deserialization (fromJson)

### Purpose

The `fromJson()` method allows loading a previously exported ModelNode tree without requiring the original EMF Resource. This enables:

1. **Caching**: Save expensive structure calculations for reuse across test runs
2. **Offline Analysis**: Analyze structures without EMF dependencies
3. **Comparison Across Sessions**: Compare models from different build/test runs
4. **LLM Processing**: Pass model structure to LLM agents via JSON

### API

```java
public class ModelChecksumCalculator {
    /**
     * Loads a ModelNode tree from JSON.
     *
     * Note: EObject references will be null since the original Resource is not available.
     * The structure can still be compared, but difference.getExpectedObject() will return null.
     *
     * @param json JSON string from toJson()
     * @return reconstructed ModelNode tree
     * @throws IllegalArgumentException if JSON is invalid
     */
    public ModelNode fromJson(String json) {
        return fromJson(json, null);
    }

    /**
     * Loads a ModelNode tree from JSON with optional EObject re-matching.
     *
     * When a Resource is provided, the calculator attempts to re-match each loaded
     * ModelNode with an EObject from the Resource by comparing checksums. This enables
     * direct EObject access even when loading from cached JSON structures.
     *
     * Re-matching algorithm:
     * 1. Load the JSON structure normally
     * 2. If resource is not null, calculate checksums for all EObjects in the Resource
     * 3. For each loaded ModelNode, find an EObject with matching checksum
     * 4. If found, set the EObject reference on the ModelNode
     * 5. If not found (structure changed), EObject remains null
     *
     * @param json JSON string from toJson()
     * @param resource optional Resource for EObject re-matching (can be null)
     * @return reconstructed ModelNode tree with optional EObject references
     * @throws IllegalArgumentException if JSON is invalid
     */
    public ModelNode fromJson(String json, Resource resource) {
        // Implementation:
        // 1. Parse JSON into ModelNode tree
        // 2. If resource != null:
        //    - Build checksum-to-EObject map from Resource
        //    - Walk ModelNode tree and match by checksum
        // 3. Return the tree
    }
}
```

### Re-matching Use Cases

**Scenario 1: Cached baseline comparison**
```java
// First run: Calculate and cache ETL structure
ModelNode etlStructure = calc.calculate(etlResource);
Files.writeString(Path.of("baseline.json"), calc.toJson(etlStructure));

// Later runs: Load baseline and compare with new ZETA output
String baselineJson = Files.readString(Path.of("baseline.json"));
ModelNode baseline = calc.fromJson(baselineJson);  // No Resource needed for comparison
ModelNode zetaStructure = calc.calculate(zetaResource);
ComparisonResult result = comparator.compare(baseline, zetaStructure);
```

**Scenario 2: Re-match for debugging**
```java
// Load cached structure WITH Resource for EObject access
ModelNode rematchedBaseline = calc.fromJson(baselineJson, etlResource);

// Now differences have resolvable EObjects
for (Difference diff : result.getDifferences()) {
    EObject expected = diff.getExpectedObject();  // Non-null if re-matched!
    if (expected != null) {
        System.out.println("URI: " + EcoreUtil.getURI(expected));
    }
}
```

### JSON Format

The JSON format produced by `toJson()` and consumed by `fromJson()`:

```json
{
  "type": "EPackage",
  "identifier": "model",
  "path": "EPackage:model",
  "checksum": "abc123...",
  "attributes": {
    "name": "model",
    "nsURI": "http://example/model"
  },
  "references": {
    "eSuperPackage": []
  },
  "containments": {
    "eClassifiers": [
      {
        "type": "EClass",
        "identifier": "Customer",
        "path": "EPackage:model/EClass:Customer",
        "checksum": "def456...",
        "attributes": {
          "name": "Customer",
          "abstract": false
        },
        "references": {
          "eSuperTypes": [
            {"path": "EPackage:model/EClass:BaseEntity", "checksum": "ghi789..."}
          ]
        },
        "containments": {}
      }
    ]
  }
}
```

### Limitations

When loaded from JSON:
- `ModelNode.getEObject()` returns `null`
- `Difference.getExpectedObject()` returns `null` for loaded nodes
- `Difference.getActualObject()` returns `null` for loaded nodes
- Structure can still be compared and differences reported via path/identifier

---

## NEW: LLM-Friendly Output Format (formatForLLM)

### Purpose

The `formatForLLM()` method produces structured XML output with tags that LLM agents can easily parse and process. This enables automated problem detection, analysis, and fix suggestions.

### API

```java
public class StructuralModelComparator {
    /**
     * Formats comparison result for LLM processing.
     *
     * Produces structured XML with tags for easy parsing:
     * - <model-comparison> root element
     * - <summary> with status and counts
     * - <differences> with individual <difference> elements
     * - <analysis> with affected paths and root causes
     *
     * @param result comparison result from compare()
     * @return XML-formatted string for LLM consumption
     */
    public String formatForLLM(ComparisonResult result) {
        return formatForLLM(result, new FormatOptions());
    }

    /**
     * Formats comparison result with custom options.
     *
     * @param result comparison result
     * @param options formatting options
     * @return XML-formatted string
     */
    public String formatForLLM(ComparisonResult result, FormatOptions options) {
        // Implementation
    }
}

public class FormatOptions {
    private boolean includeSuggestions = true;
    private boolean includeAnalysis = true;
    private int maxDifferences = 100;
    private boolean prettyPrint = true;

    public FormatOptions includeSuggestions(boolean include) { ... }
    public FormatOptions includeAnalysis(boolean include) { ... }
    public FormatOptions maxDifferences(int max) { ... }
    public FormatOptions prettyPrint(boolean pretty) { ... }
}
```

### Output Format

```xml
<model-comparison>
  <summary>
    <status>DIFFERENCES_FOUND</status>
    <total-differences>3</total-differences>
    <by-type>
      <missing>1</missing>
      <extra>1</extra>
      <attribute-mismatch>1</attribute-mismatch>
      <reference-mismatch>0</reference-mismatch>
      <type-mismatch>0</type-mismatch>
    </by-type>
  </summary>

  <differences>
    <difference type="MISSING" severity="error">
      <path>EPackage:model/EClass:Customer/EAttribute:email</path>
      <element-type>EAttribute</element-type>
      <identifier>email</identifier>
      <description>Element exists in expected model but missing in actual model</description>
      <context>
        <parent-path>EPackage:model/EClass:Customer</parent-path>
        <parent-type>EClass</parent-type>
      </context>
      <suggestion>Add the missing EAttribute 'email' to EClass 'Customer'</suggestion>
    </difference>

    <difference type="EXTRA" severity="warning">
      <path>EPackage:model/EClass:Order/EAttribute:internalId</path>
      <element-type>EAttribute</element-type>
      <identifier>internalId</identifier>
      <description>Element exists in actual model but not in expected model</description>
      <context>
        <parent-path>EPackage:model/EClass:Order</parent-path>
        <parent-type>EClass</parent-type>
      </context>
      <suggestion>Remove unexpected EAttribute 'internalId' from EClass 'Order' or add ignore pattern</suggestion>
    </difference>

    <difference type="ATTRIBUTE_MISMATCH" severity="error">
      <path>EPackage:model/EClass:Product</path>
      <element-type>EClass</element-type>
      <identifier>Product</identifier>
      <attribute-name>abstract</attribute-name>
      <expected-value>true</expected-value>
      <actual-value>false</actual-value>
      <description>Attribute 'abstract' has different values</description>
      <suggestion>Set 'abstract' to 'true' on EClass 'Product'</suggestion>
    </difference>

    <difference type="REFERENCE_MISMATCH" severity="error">
      <path>EPackage:model/EClass:Child</path>
      <element-type>EClass</element-type>
      <identifier>Child</identifier>
      <reference-name>eSuperTypes</reference-name>
      <expected-targets>
        <target path="EPackage:model/EClass:Base1" identifier="Base1"/>
      </expected-targets>
      <actual-targets>
        <target path="EPackage:model/EClass:Base2" identifier="Base2"/>
      </actual-targets>
      <description>Reference 'eSuperTypes' points to different targets</description>
      <suggestion>Update 'eSuperTypes' reference to point to 'Base1' instead of 'Base2'</suggestion>
    </difference>
  </differences>

  <analysis>
    <affected-paths>
      <path>EPackage:model/EClass:Customer</path>
      <path>EPackage:model/EClass:Order</path>
      <path>EPackage:model/EClass:Product</path>
      <path>EPackage:model/EClass:Child</path>
    </affected-paths>
    <root-causes>
      <cause>Missing transformation rule for 'email' attribute</cause>
      <cause>Extra element 'internalId' may be generated incorrectly</cause>
    </root-causes>
  </analysis>
</model-comparison>
```

### Tag Reference

| Tag | Description |
|-----|-------------|
| `<model-comparison>` | Root element containing all comparison data |
| `<summary>` | High-level overview of comparison results |
| `<status>` | MATCH or DIFFERENCES_FOUND |
| `<total-differences>` | Total count of differences |
| `<by-type>` | Breakdown by difference type |
| `<differences>` | Container for individual differences |
| `<difference>` | Single difference with `type` and `severity` attributes |
| `<path>` | Full containment path to the element |
| `<element-type>` | EClass name of the element |
| `<identifier>` | Element identifier (name) |
| `<description>` | Human-readable description |
| `<context>` | Parent element information |
| `<suggestion>` | Actionable fix recommendation |
| `<attribute-name>` | For ATTRIBUTE_MISMATCH: the attribute that differs |
| `<expected-value>` | For ATTRIBUTE_MISMATCH: value in expected model |
| `<actual-value>` | For ATTRIBUTE_MISMATCH: value in actual model |
| `<reference-name>` | For REFERENCE_MISMATCH: the reference that differs |
| `<expected-targets>` | For REFERENCE_MISMATCH: targets in expected model |
| `<actual-targets>` | For REFERENCE_MISMATCH: targets in actual model |
| `<analysis>` | Aggregated analysis section |
| `<affected-paths>` | List of all affected element paths |
| `<root-causes>` | Potential root cause suggestions |

### Severity Levels

| Severity | Description | Examples |
|----------|-------------|----------|
| `error` | Structural difference that likely indicates a bug | MISSING, TYPE_MISMATCH |
| `warning` | Difference that may be intentional | EXTRA (new elements) |
| `info` | Minor difference | Small attribute value changes |

### No Differences Output

When models match:

```xml
<model-comparison>
  <summary>
    <status>MATCH</status>
    <total-differences>0</total-differences>
  </summary>
  <differences/>
</model-comparison>
```

---

## NEW: Incremental Model Handling (saveJson/loadJson)

### Purpose

The `saveJson()` and `loadJson()` methods enable persisting and restoring comparison results. This supports incremental model handling workflows such as:

1. **Regression Detection**: Compare current differences against a saved baseline
2. **CI/CD Integration**: Persist state between pipeline stages
3. **Audit Trail**: Track model differences over time
4. **Incremental Validation**: Only process new or changed differences

### API

```java
public class StructuralModelComparator {
    /**
     * Saves a comparison result to a JSON file.
     *
     * The saved file includes:
     * - Timestamp of the comparison
     * - Model checksums (expected and actual root checksums)
     * - All differences with full details
     * - Schema version for compatibility
     *
     * @param result the comparison result to save
     * @param path the file path to write to
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if result or path is null
     */
    public void saveJson(ComparisonResult result, Path path) throws IOException {
        // Implementation
    }

    /**
     * Loads a previously saved comparison result from a JSON file.
     *
     * Note: Loaded results will not have EObject references (they were not serialized).
     * The differences can still be analyzed by path, type, and description.
     *
     * @param path the file path to read from
     * @return the loaded comparison result
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if path is null or file doesn't exist
     * @throws JsonParseException if JSON is malformed or incompatible version
     */
    public ComparisonResult loadJson(Path path) throws IOException {
        // Implementation
    }
}
```

### Saved JSON Format

```json
{
  "schemaVersion": "1.0",
  "timestamp": "2024-01-18T23:45:00.123Z",
  "isMatch": false,
  "expectedModelChecksum": "abc123def456...",
  "actualModelChecksum": "789xyz012abc...",
  "differenceCount": 3,
  "differences": [
    {
      "type": "MISSING",
      "severity": "error",
      "path": "EPackage:model/EClass:Customer/EAttribute:email",
      "elementType": "EAttribute",
      "identifier": "email",
      "description": "Element exists in expected model but missing in actual model",
      "parentPath": "EPackage:model/EClass:Customer",
      "parentType": "EClass"
    },
    {
      "type": "ATTRIBUTE_MISMATCH",
      "severity": "error",
      "path": "EPackage:model/EClass:Product",
      "elementType": "EClass",
      "identifier": "Product",
      "description": "Attribute 'abstract' has different values",
      "attributeName": "abstract",
      "expectedValue": "true",
      "actualValue": "false"
    },
    {
      "type": "REFERENCE_MISMATCH",
      "severity": "error",
      "path": "EPackage:model/EClass:Child",
      "elementType": "EClass",
      "identifier": "Child",
      "description": "Reference 'eSuperTypes' points to different targets",
      "referenceName": "eSuperTypes",
      "expectedTargets": ["EPackage:model/EClass:Base1"],
      "actualTargets": ["EPackage:model/EClass:Base2"]
    }
  ]
}
```

### Use Cases

**Scenario 1: Regression Detection in CI**
```java
StructuralModelComparator comparator = new StructuralModelComparator();
Path baselinePath = Path.of("baseline-diff.json");

// Load baseline from previous successful build
ComparisonResult baseline = comparator.loadJson(baselinePath);

// Calculate current comparison
ModelNode etl = calc.calculate(etlResource);
ModelNode zeta = calc.calculate(zetaResource);
ComparisonResult current = comparator.compare(etl, zeta);

// Detect regressions
Set<String> baselinePaths = baseline.getDifferences().stream()
    .map(Difference::getPath)
    .collect(Collectors.toSet());

List<Difference> newRegressions = current.getDifferences().stream()
    .filter(d -> !baselinePaths.contains(d.getPath()))
    .toList();

if (!newRegressions.isEmpty()) {
    System.err.println("BUILD FAILED: " + newRegressions.size() + " new differences!");
    System.exit(1);
}

// Update baseline if requested
if (shouldUpdateBaseline) {
    comparator.saveJson(current, baselinePath);
}
```

**Scenario 2: Tracking Resolved Differences**
```java
ComparisonResult baseline = comparator.loadJson(baselinePath);
ComparisonResult current = comparator.compare(etl, zeta);

Set<String> currentPaths = current.getDifferences().stream()
    .map(Difference::getPath)
    .collect(Collectors.toSet());

List<Difference> resolved = baseline.getDifferences().stream()
    .filter(d -> !currentPaths.contains(d.getPath()))
    .toList();

System.out.println("Resolved " + resolved.size() + " previous differences:");
resolved.forEach(d -> System.out.println("  - " + d.getPath()));
```

### Version Compatibility

The `schemaVersion` field ensures forward compatibility:
- Version 1.0: Initial format
- Future versions may add fields but will maintain backward compatibility
- Loading a newer version file with an older library will throw `JsonParseException`
