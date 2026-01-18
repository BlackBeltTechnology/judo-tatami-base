# Structural Model Comparison Utilities

**Status:** in_progress

## Summary

Create two separate utility classes for structural model comparison:

1. **ModelChecksumCalculator** - Builds a structural representation (ModelNode tree) of an EMF model with checksums. Can export to JSON and load from JSON. Supports ignoring specific features.

2. **StructuralModelComparator** - Takes two ModelNode trees (from ModelChecksumCalculator) and compares them, returning differences with resolvable EObjects. Provides LLM-friendly structured output format.

## Motivation

When comparing ETL and ZETA transformation outputs, we need:
1. Deep structural comparison beyond simple identifier matching
2. Clear separation between structure calculation and comparison
3. Reusable model structure for multiple comparisons
4. Direct access to differing EObjects for debugging
5. Ability to serialize model structures for analysis
6. **Ability to ignore known differences** (e.g., computed fields, timestamps)

### Current Limitations

The existing comparison:
- Only compares by identifier, not structural content
- Doesn't preserve the containment structure for reuse
- Cannot export comparison data
- Differences don't provide direct EObject access
- **Cannot ignore specific attributes or references**

## Approach

### Architecture

```
ModelChecksumCalculator (calculates structure)
├── calculate(Resource) → ModelNode
├── calculate(Resource, CalculatorOptions) → ModelNode
├── toJson(ModelNode) → String
├── fromJson(String) → ModelNode                    // Load from JSON (no EObjects)
├── fromJson(String, Resource) → ModelNode          // Load + re-match EObjects
└── CalculatorOptions
    └── ignoredFeatures: Set<String>  // "package.Class#feature" format

StructuralModelComparator (compares structures)
├── compare(ModelNode expected, ModelNode actual) → ComparisonResult
├── formatForLLM(ComparisonResult) → String           // LLM-friendly output
├── formatForLLM(ComparisonResult, FormatOptions) → String
├── saveJson(ComparisonResult, Path) → void           // Save for incremental handling
└── loadJson(Path) → ComparisonResult                 // Load saved comparison

ModelNode (containment structure)
├── eObject: EObject (resolvable reference)
├── type, identifier, path, checksum
├── attributes: Map<String, Object>
├── containments: Map<String, List<ModelNode>>
└── references: Map<String, List<ReferenceNode>>

ReferenceNode (non-containment reference)
├── targetChecksum, targetPath, targetIdentifier
└── resolve(ModelNode root) → ModelNode

ComparisonResult
├── isMatch(): boolean
├── getDifferences(): List<Difference>
└── getDifferences(DifferenceType): List<Difference>

Difference
├── path, type, description
├── expectedNode, actualNode
├── getExpectedObject(): EObject
└── getActualObject(): EObject
```

### Ignored Features

Features can be ignored during checksum calculation using the pattern:
```
package.Class#feature
```

**Examples:**
- `ecore.EClass#abstract` - Ignore the `abstract` attribute on all EClass elements
- `asm.EAnnotation#details` - Ignore the `details` attribute on EAnnotation
- `psm.EntityType#superEntityTypes` - Ignore the `superEntityTypes` reference

**Wildcard Support:**
- `*.EClass#abstract` - Ignore on any EClass regardless of package
- `ecore.*#name` - Ignore `name` on all types in ecore package
- `*.*#uuid` - Ignore `uuid` attribute on all elements (global)

### Behavior Clarifications

**Element Identifiers:**
- Custom identifier resolver via `CalculatorOptions.withIdentifierResolver(Function<EObject, String>)`
- Default: uses `name` attribute if available, otherwise `EClassName_index`

**Cross-Resource References:**
- Cross-resource references are ignored (skipped during checksum calculation)
- Only references within the same Resource are included

**Derived and Transient Features:**
- Both derived and transient attributes/references are excluded from checksums
- These are computed values that may differ between implementations

**Proxy Resolution:**
- Proxies are resolved lazily using `EcoreUtil.resolve()`
- Unresolved proxies are skipped with a warning log

### Separation of Concerns

**ModelChecksumCalculator** (single model):
- Builds containment tree from Resource
- Computes checksums (type + attributes + children + references)
- Handles bidirectional reference ownership
- **Skips ignored features from checksum calculation**
- Exports to JSON for debugging

**ModelComparator** (two models):
- Takes two ModelNode roots as input
- Compares structures recursively
- Uses checksum shortcuts for identical subtrees
- Returns ComparisonResult with differences

### Usage Pattern

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

// Analyze differences
for (Difference diff : result.getDifferences()) {
    System.out.println(diff);
    EObject expected = diff.getExpectedObject();
    EObject actual = diff.getActualObject();
}

// Optional: Export for debugging
String json = calc.toJson(etlStructure);

// NEW: Load structure from JSON (for caching/offline analysis)
ModelNode loadedStructure = calc.fromJson(json);

// NEW: Format differences for LLM processing
String llmOutput = comparator.formatForLLM(result);
System.out.println(llmOutput);
```

### LLM-Friendly Output Format

The `formatForLLM()` method produces structured output with XML-like tags that LLM agents can easily parse and process. This enables automated problem detection and analysis.

**Output Format:**

```xml
<model-comparison>
  <summary>
    <status>DIFFERENCES_FOUND</status>
    <total-differences>3</total-differences>
    <by-type>
      <missing>1</missing>
      <extra>1</extra>
      <attribute-mismatch>1</attribute-mismatch>
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

**Tag Descriptions:**

| Tag | Description |
|-----|-------------|
| `<model-comparison>` | Root element containing all comparison data |
| `<summary>` | High-level overview of comparison results |
| `<differences>` | Container for all individual differences |
| `<difference>` | Single difference with type and severity attributes |
| `<path>` | Full containment path to the element |
| `<context>` | Parent element information for navigation |
| `<suggestion>` | Actionable fix recommendation for LLM agents |
| `<analysis>` | Aggregated analysis of affected areas |

**Severity Levels:**
- `error` - Structural difference that likely indicates a bug
- `warning` - Difference that may be intentional (extra elements)
- `info` - Minor difference (attribute value changes)

### JSON Serialization (fromJson)

The `fromJson()` method allows loading a previously exported ModelNode tree. This is useful for:
- **Caching**: Save expensive structure calculations for reuse
- **Offline Analysis**: Analyze structures without the original EMF resources
- **Comparison Across Sessions**: Compare models from different runs
- **LLM Processing**: Pass model structure to LLM agents via JSON

**API:**

```java
// Load without Resource (EObject references will be null)
ModelNode fromJson(String json)

// Load with Resource for EObject re-matching (optional)
ModelNode fromJson(String json, Resource resource)
```

**Example:**

```java
// Export structure
String json = calc.toJson(etlStructure);
Files.writeString(Path.of("etl-structure.json"), json);

// Later: Load without original Resource (offline analysis)
String loadedJson = Files.readString(Path.of("etl-structure.json"));
ModelNode loadedStructure = calc.fromJson(loadedJson);

// Or: Load WITH Resource to re-match EObjects by checksum
ModelNode rematchedStructure = calc.fromJson(loadedJson, etlResource);
// Now difference.getExpectedObject() returns actual EObjects!

// Compare with new calculation
ModelNode newZetaStructure = calc.calculate(zetaResource);
ComparisonResult result = comparator.compare(rematchedStructure, newZetaStructure);
```

**Re-matching Behavior:**

When a Resource is provided to `fromJson()`:
1. The JSON structure is loaded as normal
2. For each ModelNode, the calculator searches the Resource for an EObject with matching checksum
3. If found, `ModelNode.getEObject()` returns the matched EObject
4. If not found (structure changed), EObject remains null for that node

This enables workflows where you:
- Cache model structures as JSON
- Later reload and re-match against the same or updated Resource
- Get direct EObject access for differences even with cached structures

### Incremental Model Handling (saveJson/loadJson)

The `StructuralModelComparator` provides `saveJson()` and `loadJson()` methods for persisting and restoring comparison results. This enables incremental model handling workflows:

**API:**

```java
// Save comparison result for later use
void saveJson(ComparisonResult result, Path path)

// Load previously saved comparison result
ComparisonResult loadJson(Path path)
```

**Use Cases:**

1. **Incremental Validation**: Track differences across builds
2. **Regression Detection**: Compare current differences against baseline
3. **CI/CD Integration**: Persist comparison state between pipeline stages
4. **Audit Trail**: Maintain history of model differences

**Example - Incremental Workflow:**

```java
StructuralModelComparator comparator = new StructuralModelComparator();
Path diffPath = Path.of("model-diff-baseline.json");

// First run: Compare and save baseline differences
ComparisonResult initial = comparator.compare(etlStructure, zetaStructure);
comparator.saveJson(initial, diffPath);

// Later runs: Load baseline and compare with current
ComparisonResult baseline = comparator.loadJson(diffPath);
ComparisonResult current = comparator.compare(etlStructure, zetaStructure);

// Detect new differences (regression detection)
List<Difference> newDiffs = findNewDifferences(baseline, current);
List<Difference> resolvedDiffs = findResolvedDifferences(baseline, current);

if (!newDiffs.isEmpty()) {
    System.err.println("REGRESSION: " + newDiffs.size() + " new differences!");
    System.out.println(comparator.formatForLLM(current));
}
```

**Saved JSON Format:**

```json
{
  "timestamp": "2024-01-18T23:45:00Z",
  "isMatch": false,
  "differenceCount": 3,
  "differences": [
    {
      "type": "MISSING",
      "severity": "error",
      "path": "EPackage:model/EClass:Customer/EAttribute:email",
      "elementType": "EAttribute",
      "identifier": "email",
      "description": "Element exists in expected model but missing in actual model",
      "parentPath": "EPackage:model/EClass:Customer"
    }
  ],
  "expectedModelChecksum": "abc123...",
  "actualModelChecksum": "def456..."
}
```

## Impact

### Modules Affected
- `judo-tatami-test-utils` - New ModelChecksumCalculator and ModelComparator classes

### Breaking Changes
- None - new utility classes, existing code unchanged

### Dependencies
- None required (JSON export uses simple string building)

## Risks

### Risk 1: Memory Overhead
**Mitigation**: ModelNode structure is lightweight, only stores checksums and paths

### Risk 2: Two-Phase API Complexity
**Mitigation**: Clear separation makes each class simpler and more testable

### Risk 3: Reference Resolution Performance
**Mitigation**: Build path-to-node index during calculation for O(1) lookups

### Risk 4: Over-Ignoring Features
**Mitigation**: Log warnings when ignored features match many elements

## Success Criteria

1. ModelChecksumCalculator produces identical structures for identical models
2. StructuralModelComparator detects all structural differences
3. Differences include complete containment paths
4. EObjects are directly accessible from differences
5. JSON export works correctly
6. Performance acceptable for models with 10,000+ elements
7. **Ignored features are excluded from checksum calculation**
8. **Wildcard patterns work correctly**
9. **fromJson() correctly reconstructs ModelNode tree from JSON**
10. **formatForLLM() produces well-structured XML output with all required tags**
11. **LLM output includes actionable suggestions for each difference**
