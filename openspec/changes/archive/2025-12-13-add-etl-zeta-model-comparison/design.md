# Design: ETL-Zeta Model Comparison

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Dual Transformation Test                      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────────┐  │
│  │  Source     │──────│    ETL      │──────│  ETL Result     │  │
│  │  Model      │      │  Transform  │      │  (ASM Model)    │  │
│  │  (PSM)      │      └─────────────┘      └────────┬────────┘  │
│  │             │                                     │          │
│  │             │      ┌─────────────┐      ┌────────▼────────┐  │
│  │             │──────│   Zeta      │──────│  Zeta Result    │  │
│  └─────────────┘      │  Transform  │      │  (ASM Model)    │  │
│                       └─────────────┘      └────────┬────────┘  │
│                                                      │          │
│                       ┌─────────────────────────────▼────────┐  │
│                       │         ModelComparator               │  │
│                       │  ┌─────────────────────────────────┐  │  │
│                       │  │  Order-Independent Comparison   │  │  │
│                       │  │  - Set-based collection match   │  │  │
│                       │  │  - Recursive element traversal  │  │  │
│                       │  │  - Bidirectional completeness   │  │  │
│                       │  └─────────────────────────────────┘  │  │
│                       └──────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Component Design

### 1. EnhancedModelComparator

```java
public class EnhancedModelComparator {
    
    public enum ComparisonMode {
        STRICT,      // All attributes must match
        STRUCTURAL,  // Structure must match, annotations can differ
        LENIENT      // Major structure must match
    }
    
    /**
     * Compare two models for equivalence with order-independent collection matching.
     */
    public ComparisonResult compare(EObject expected, EObject actual, ComparisonMode mode);
    
    /**
     * Assert models are equivalent, throwing detailed exception on mismatch.
     */
    public void assertEquivalent(EObject expected, EObject actual, ComparisonMode mode);
}
```

### 2. Order-Independent Collection Comparison

The key algorithm for collection comparison:

```
FUNCTION compareCollections(expected: List<EObject>, actual: List<EObject>):
    IF expected.size != actual.size:
        RETURN size mismatch error
    
    # Build lookup maps by identifying features
    expectedMap = mapByIdentifier(expected)
    actualMap = mapByIdentifier(actual)
    
    # Check all expected elements exist in actual
    FOR each (id, expectedElem) in expectedMap:
        IF id NOT IN actualMap:
            RETURN missing element error
        actualElem = actualMap[id]
        difference = compareElements(expectedElem, actualElem)
        IF difference:
            RETURN difference
    
    # Check no extra elements in actual
    FOR each (id, actualElem) in actualMap:
        IF id NOT IN expectedMap:
            RETURN extra element error
    
    RETURN equivalent
```

### 3. Element Identification Strategy

Elements are identified by:
1. **Name attribute** - If element has `name` feature
2. **ID annotation** - If element has EMF ID annotation
3. **Type + position** - Fallback for unnamed elements

```java
private String getElementIdentifier(EObject element) {
    // Try name first
    EStructuralFeature nameFeature = element.eClass().getEStructuralFeature("name");
    if (nameFeature != null) {
        Object name = element.eGet(nameFeature);
        if (name != null) {
            return element.eClass().getName() + ":" + name;
        }
    }
    
    // Try ID annotation
    String id = getIdAnnotation(element);
    if (id != null) {
        return element.eClass().getName() + "#" + id;
    }
    
    // Fallback to type-based matching
    return element.eClass().getName();
}
```

### 4. Recursive Traversal

```java
private void compareRecursively(EObject expected, EObject actual, 
                                 String path, List<Difference> differences) {
    // Compare class type
    if (!expected.eClass().equals(actual.eClass())) {
        differences.add(new TypeMismatch(path, expected.eClass(), actual.eClass()));
        return;
    }
    
    // Compare all structural features
    for (EStructuralFeature feature : expected.eClass().getEAllStructuralFeatures()) {
        if (shouldSkipFeature(feature)) continue;
        
        String featurePath = path + "." + feature.getName();
        Object expectedVal = expected.eGet(feature);
        Object actualVal = actual.eGet(feature);
        
        if (feature instanceof EAttribute) {
            compareAttribute(expectedVal, actualVal, featurePath, differences);
        } else if (feature instanceof EReference) {
            EReference ref = (EReference) feature;
            if (ref.isContainment()) {
                compareContainment(expectedVal, actualVal, featurePath, differences);
            } else {
                compareReference(expectedVal, actualVal, featurePath, differences);
            }
        }
    }
}
```

### 5. Difference Reporting

```java
public class ComparisonResult {
    private List<Difference> differences;
    
    public boolean isEquivalent() {
        return differences.isEmpty();
    }
    
    public String getSummary() {
        // Brief overview: "3 differences: 2 missing elements, 1 value mismatch"
    }
    
    public String getDetailedReport() {
        // Full path-based diff output
    }
}

public abstract class Difference {
    String path;
    abstract String describe();
}

public class MissingElement extends Difference { ... }
public class ExtraElement extends Difference { ... }
public class ValueMismatch extends Difference { ... }
public class TypeMismatch extends Difference { ... }
```

## Test Integration

### Option 1: Enhanced Parameterized Test (Recommended)

```java
@ParameterizedTest
@EnumSource(TransformationMode.class)
void testTransformation(TransformationMode mode) {
    // Run transformation
    AsmModel result = transform(psmModel, mode);

    // Store result for comparison
    storeResult(mode, result);

    // Basic validation
    assertTrue(result.isValid());
}

@Test
void testEtlZetaEquivalence() {
    AsmModel etlResult = getStoredResult(TransformationMode.ETL);
    AsmModel zetaResult = getStoredResult(TransformationMode.ZETA);

    EnhancedModelComparator.assertEquivalent(
        etlResult.getResource(),
        zetaResult.getResource(),
        ComparisonMode.STRUCTURAL
    );
}
```

### Option 2: Combined Test Execution

```java
@Test
void testDualTransformationEquivalence() {
    // Run ETL
    AsmModel etlModel = buildAsmModel().build();
    runEtlTransformation(psmModel, etlModel);
    
    // Run Zeta  
    AsmModel zetaModel = buildAsmModel().build();
    runZetaTransformation(psmModel, zetaModel);
    
    // Compare
    EnhancedModelComparator.assertEquivalent(
        etlModel.getResource(),
        zetaModel.getResource(),
        ComparisonMode.STRUCTURAL
    );
}
```

## Configuration

### System Properties

```properties
# Enable/disable comparison (default: true in CI, false locally)
judo.test.comparison.enabled=true

# Comparison mode (STRICT, STRUCTURAL, LENIENT)
judo.test.comparison.mode=STRUCTURAL

# Maximum differences to report before truncating
judo.test.comparison.maxDifferences=50

# Save diff report to file
judo.test.comparison.reportFile=target/comparison-report.txt
```

## Edge Cases

### 1. Transient/Derived Features
Skip features marked as `transient` or `derived` as they may differ legitimately.

### 2. ID Annotations
The transformation may generate different internal IDs. Compare by structural identity, not EMF IDs.

### 3. Annotation Ordering
EAnnotations may appear in different order. Use set-based comparison for annotation lists.

### 4. Empty vs Null Collections
Treat empty collections and null as equivalent for optional multi-valued features.

### 5. Floating Point Comparison
Use epsilon-based comparison for floating point attributes.
