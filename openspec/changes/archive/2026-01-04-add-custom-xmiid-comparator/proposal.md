# Add Custom XMI ID Comparator Support

## Summary

Add the ability for external model tests to provide custom XMI ID comparison functions, enabling transformation-specific ID matching logic for consumer projects that use judo-tatami-test-utils.

## Motivation

Different consumer projects produce XMI IDs with varying structures and semantics. The current XMI ID comparison in `ModelComparator` uses a fixed logic that may not work for all transformation types.

Consumer projects need to:
1. Provide custom XMI ID extraction logic for their specific model types
2. Implement domain-specific ID matching rules
3. Handle ID format differences between ETL and Zeta transformations

## Current Implementation

`ModelComparator.java` provides XMI ID comparison via:

```java
// System property to enable XMI ID comparison
public static final String PROP_XMI_ID_COMPARISON = "judo.test.comparison.xmiIds";

// Fixed XMI ID extraction
private static String getXmiId(EObject obj) {
    Resource resource = obj.eResource();
    if (resource instanceof XMLResource) {
        return ((XMLResource) resource).getID(obj);
    }
    return null;
}
```

No customization point exists for consumer projects.

## Proposed Solution

Add a `XmiIdExtractor` functional interface for custom XMI ID comparison:

```java
@FunctionalInterface
public interface XmiIdExtractor {
    /**
     * Extracts XMI ID for an EObject for comparison purposes.
     *
     * @param obj the object to get XMI ID for
     * @return XMI ID string, or null to use default extraction
     */
    String extractXmiId(EObject obj);
}
```

Extend `compareXmiIds()` and related methods to accept optional `XmiIdExtractor`:

```java
public static List<Difference> compareXmiIds(Resource expected, Resource actual,
                                              XmiIdExtractor extractor)
```

### Scope

- **Affects**: Both XMI ID comparison AND structural element identifier matching
- **Depth**: All elements recursively during deep comparison
- **Fallback**: When custom extractor returns `null`, use current default logic

## Use Cases

Consumer projects can implement custom extractors:

```java
// In consumer project's test
XmiIdExtractor customExtractor = obj -> {
    if (obj instanceof MyDomainElement) {
        // Custom ID extraction for domain-specific types
        return ((MyDomainElement) obj).getBusinessKey();
    }
    // Return null to fall back to default XMI ID extraction
    return null;
};

ModelComparator.compareXmiIds(expected, actual, customExtractor);
```

## Scope

### In Scope

- Add `XmiIdExtractor` functional interface to `ModelComparator`
- Add overloaded `compareXmiIds()` accepting custom extractor
- Add overloaded `compare(Resource, Resource, ComparisonMode, XmiIdExtractor)` for structural comparison
- Apply custom extractor recursively to all elements during comparison
- Fall back to current default logic when extractor returns null
- Expose default XMI ID extraction for composition via `defaultXmiId()`

### Out of Scope

- Transformation-specific extractors (consumer projects implement their own)
- Modifying existing comparison modes

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| API complexity increase | Low | Optional parameter, defaults to current behavior |
| Custom extractors may hide real differences | Medium | Document that null fallback uses default logic |
| Breaking consumer projects | Low | Backward compatible - no changes required for existing code |

## Success Criteria

1. Consumer projects can provide custom `XmiIdExtractor` to XMI ID comparison
2. Custom extractor applied recursively to all elements
3. Null return falls back to current XMI ID extraction
4. Existing tests continue to work without changes (backward compatible)
