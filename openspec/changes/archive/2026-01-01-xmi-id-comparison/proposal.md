# XMI ID Comparison for ETL/Zeta Model Equivalence

## Status: Phase 2 Complete

**Phase 1 Implementation Date:** 2025-12-31
**Phase 2 Implementation Date:** 2026-01-01

### Phase 1 Changes (ModelComparator):
- Added `ModelComparator.getXmiId()` method
- Added `ModelComparator.buildXmiIdMap()` method
- Added `ModelComparator.compareXmiIds()` method
- Added `ModelComparator.assertXmiIdsEquivalent()` method
- Added `MissingXmiId`, `ExtraXmiId`, `XmiIdTypeMismatch` difference classes
- Added `judo.test.comparison.xmiIds` system property (default: false)
- XMI ID comparison is called automatically in `assertEquivalent(Resource, Resource, ComparisonMode)`

### Phase 2 Changes (Zeta XMI ID Generation):
- Updated `Psm2MeasureRuleNames` - renamed rule constants to match ETL ID suffixes
- Updated `Psm2MeasureZetaTransformation` - added post-processing to apply XMI IDs
- Updated `MeasureRules` - added XMI ID storage for BaseMeasure, DerivedMeasure, BaseMeasureTerm
- Updated `UnitRules` - added XMI ID storage for Unit and DurationUnit (with type suffix)
- Updated `Psm2MeasureZetaTraceTest` - updated to use new rule names

**XMI ID Formats (matching ETL):**
- BaseMeasure: `(psm/<sourceId>)/BaseMeasure`
- DerivedMeasure: `(psm/<sourceId>)/DerivedMeasure`
- BaseMeasureTerm: `(<derivedMeasureId>)_((psm/<sourceBaseMeasureId>)/BaseMeasureTerm)`
- Unit: `(psm/<sourceId>)/Unit`
- DurationUnit: `(psm/<sourceId>)/DurationUnit<Type>` (e.g., `DurationUnitNanosecond`)

**Test Results:** All psm2measure and psm2asm tests pass with `-Djudo.test.comparison.xmiIds=true`

## Summary

Add XMI ID comparison to `ModelComparator` to ensure ETL and Zeta transformations produce models with identical XMI IDs for corresponding elements. This is critical for ensuring equivalent resolution behavior between transformation engines.

## Background

### EMF XMI ID Mechanism

In EMF (Eclipse Modeling Framework), XMI IDs are used for:
1. **Element identification** - Unique identifiers for model elements in XMI serialization
2. **Cross-references** - Resolving references between elements (uses XMI ID as URI fragment)
3. **Traceability** - Tracking source-to-target element mappings in transformations

XMI IDs are stored at the Resource level, not as model attributes:

```java
// Set XMI ID on an element
XMLResource resource = (XMLResource) eObject.eResource();
resource.setID(eObject, "myId");

// Get XMI ID from an element
String id = resource.getID(eObject);

// Get URI fragment (may be XMI ID or computed path)
String fragment = resource.getURIFragment(eObject);
```

### Current Behavior

**ETL Transformations:**
- Use `t.setId("(psm/" + s.getId() + ")/RuleName")` to set XMI IDs
- Pattern: `(psm/<sourceElementId>)/<targetRuleName>`
- Example: `(psm/demo.measures.Mass)/BaseMeasure`

**Zeta Transformations:**
- Currently do NOT set XMI IDs (helper's `setId()` is a no-op)
- Elements get auto-generated URI fragments based on containment path

**ModelComparator:**
- Matches elements by `name`, `id` (model attribute), `uuid`, or `source`
- Does NOT compare XMI IDs
- Does NOT verify that equivalent elements have matching XMI IDs

### Problem

Without XMI ID comparison:
1. Cannot verify that Zeta produces identical traceability information as ETL
2. Cross-references may resolve differently between ETL and Zeta models
3. External systems depending on XMI IDs may behave differently

## Proposed Solution

### Phase 1: XMI ID Comparison in ModelComparator

Add a new comparison mode and methods to compare XMI IDs:

```java
public enum ComparisonMode {
    STRICT,           // All attributes, references, and XMI IDs must match
    STRUCTURAL,       // Structure must match, annotations tolerated, XMI IDs compared
    LENIENT,          // Major structure must match, XMI IDs optional
    XMI_ID_STRICT     // NEW: STRUCTURAL + strict XMI ID matching
}
```

#### New Methods

```java
/**
 * Gets the XMI ID for an EObject from its containing resource.
 * Returns null if the object has no XMI ID or is not in a resource.
 */
public static String getXmiId(EObject obj) {
    Resource resource = obj.eResource();
    if (resource instanceof XMLResource) {
        return ((XMLResource) resource).getID(obj);
    }
    return null;
}

/**
 * Builds a map of XMI ID -> EObject for all elements in a resource.
 * Only includes elements that have explicit XMI IDs set.
 */
public static Map<String, EObject> buildXmiIdMap(Resource resource) {
    Map<String, EObject> map = new LinkedHashMap<>();
    if (resource instanceof XMLResource) {
        XMLResource xmlResource = (XMLResource) resource;
        TreeIterator<EObject> iter = resource.getAllContents();
        while (iter.hasNext()) {
            EObject obj = iter.next();
            String id = xmlResource.getID(obj);
            if (id != null) {
                map.put(id, obj);
            }
        }
    }
    return map;
}

/**
 * Compares XMI IDs between two resources.
 * Returns differences where:
 * - An XMI ID exists in expected but not in actual
 * - An XMI ID exists in actual but not in expected
 * - Elements with matching XMI IDs have different types/names
 */
public static List<Difference> compareXmiIds(Resource expected, Resource actual) {
    List<Difference> differences = new ArrayList<>();

    Map<String, EObject> expectedIds = buildXmiIdMap(expected);
    Map<String, EObject> actualIds = buildXmiIdMap(actual);

    // Find missing XMI IDs (in expected but not in actual)
    for (Map.Entry<String, EObject> entry : expectedIds.entrySet()) {
        String xmiId = entry.getKey();
        EObject expectedObj = entry.getValue();

        if (!actualIds.containsKey(xmiId)) {
            differences.add(new MissingXmiId(xmiId, getObjectIdentifier(expectedObj)));
        } else {
            // XMI ID exists in both - verify element types match
            EObject actualObj = actualIds.get(xmiId);
            if (!expectedObj.eClass().getName().equals(actualObj.eClass().getName())) {
                differences.add(new XmiIdTypeMismatch(xmiId,
                    expectedObj.eClass().getName(),
                    actualObj.eClass().getName()));
            }
        }
    }

    // Find extra XMI IDs (in actual but not in expected)
    for (Map.Entry<String, EObject> entry : actualIds.entrySet()) {
        String xmiId = entry.getKey();
        if (!expectedIds.containsKey(xmiId)) {
            differences.add(new ExtraXmiId(xmiId, getObjectIdentifier(entry.getValue())));
        }
    }

    return differences;
}
```

#### New Difference Classes

```java
/**
 * Indicates an XMI ID is missing from the actual model.
 */
public static class MissingXmiId extends Difference {
    private final String xmiId;
    private final String elementDescription;

    @Override
    public String describe() {
        return "XMI ID missing: '" + xmiId + "' for element " + elementDescription;
    }
}

/**
 * Indicates an unexpected XMI ID in the actual model.
 */
public static class ExtraXmiId extends Difference {
    private final String xmiId;
    private final String elementDescription;

    @Override
    public String describe() {
        return "XMI ID unexpected: '" + xmiId + "' for element " + elementDescription;
    }
}

/**
 * Indicates elements with same XMI ID have different types.
 */
public static class XmiIdTypeMismatch extends Difference {
    private final String xmiId;
    private final String expectedType;
    private final String actualType;

    @Override
    public String describe() {
        return "XMI ID '" + xmiId + "' type mismatch: expected " + expectedType + " but was " + actualType;
    }
}
```

#### Updated assertEquivalent Method

```java
public static void assertEquivalent(Resource expected, Resource actual, ComparisonMode mode) {
    if (!isComparisonEnabled()) {
        return;
    }

    // Existing structural comparison
    // ... (current implementation)

    // Add XMI ID comparison for STRICT and XMI_ID_STRICT modes
    if (mode == ComparisonMode.STRICT || mode == ComparisonMode.XMI_ID_STRICT) {
        List<Difference> xmiDifferences = compareXmiIds(expected, actual);
        if (!xmiDifferences.isEmpty()) {
            StringBuilder sb = new StringBuilder("XMI ID mismatches:\n");
            for (Difference diff : xmiDifferences) {
                sb.append("  ").append(diff.describe()).append("\n");
            }
            throw new AssertionError(sb.toString());
        }
    }
}
```

### Phase 2: Enable XMI IDs in Zeta Transformations

Update `Psm2AsmHelper.setId()` and similar methods to actually set XMI IDs:

```java
/**
 * Set the XMI ID on an element.
 * Uses the same ID format as ETL transformations for equivalence.
 */
public static void setId(EObject element, String id) {
    Resource resource = element.eResource();
    if (resource instanceof XMLResource) {
        ((XMLResource) resource).setID(element, id);
    }
}
```

**Important**: This requires elements to be added to the resource BEFORE setting the XMI ID.

### Configuration

Add system property for XMI ID comparison:
```
judo.test.comparison.xmiIds=true|false  # Enable/disable XMI ID comparison
```

## Implementation Steps

1. **Add XMLResource import to ModelComparator**
   ```java
   import org.eclipse.emf.ecore.xmi.XMLResource;
   ```

2. **Add new Difference classes** (MissingXmiId, ExtraXmiId, XmiIdTypeMismatch)

3. **Add getXmiId() method**

4. **Add buildXmiIdMap() method**

5. **Add compareXmiIds() method**

6. **Update ComparisonMode enum** (add XMI_ID_STRICT)

7. **Update assertEquivalent(Resource, Resource, ComparisonMode)** to call compareXmiIds()

8. **Add system property** for enabling/disabling XMI ID comparison

9. **Update Zeta transformations** to set XMI IDs (Phase 2)

## Test Plan

1. **Unit Tests for ModelComparator**
   - Test getXmiId() returns correct ID
   - Test buildXmiIdMap() includes all elements with IDs
   - Test compareXmiIds() detects missing, extra, and mismatched IDs

2. **Integration Tests**
   - Run existing ETL/Zeta equivalence tests with XMI ID comparison enabled
   - Verify tests fail if Zeta doesn't set XMI IDs (expected initially)
   - After Phase 2, verify tests pass with matching XMI IDs

3. **Transformation Tests**
   - Test each transformation module (psm2asm, asm2rdbms, psm2measure, etc.)
   - Verify XMI IDs match between ETL and Zeta outputs

## Risks and Considerations

1. **Resource attachment timing**: XMI IDs can only be set after element is added to resource
2. **Performance**: Building XMI ID map adds O(n) traversal
3. **Empty models**: Handle case where neither model has XMI IDs
4. **Partial XMI IDs**: Handle case where only some elements have XMI IDs

## Open Questions

1. Should XMI ID comparison be opt-in or opt-out?
2. Should missing XMI IDs in both models be treated as equivalent?
3. Should the comparison verify XMI ID format consistency?
