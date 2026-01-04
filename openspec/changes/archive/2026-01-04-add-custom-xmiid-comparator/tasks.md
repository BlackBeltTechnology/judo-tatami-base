# Tasks

## Phase 1: Core API

- [x] 1.1 Add `XmiIdExtractor` functional interface to `ModelComparator`
- [x] 1.2 Expose `defaultXmiId(EObject)` as public static method
- [x] 1.3 Add overloaded `compareXmiIds(Resource, Resource, XmiIdExtractor)` method

## Phase 2: Internal Refactoring

- [x] 2.1 Refactor `buildXmiIdMap()` to accept `XmiIdExtractor` parameter
- [x] 2.2 Update XMI ID extraction to use custom extractor with null fallback
- [x] 2.3 Ensure extractor applied recursively to all elements
- [x] 2.4 Add `compare(Resource, Resource, ComparisonMode, XmiIdExtractor)` for structural comparison

## Phase 3: Validation

- [x] 3.1 Run all external model tests to verify backward compatibility
- [x] 3.2 Verify compilation succeeds
- [x] 3.3 Document usage in `ModelComparator` javadoc

## Summary

All tasks completed. The following API additions were made to `ModelComparator`:

```java
// New functional interface
@FunctionalInterface
public interface XmiIdExtractor {
    String extractXmiId(EObject obj);
}

// New methods
public static String defaultXmiId(EObject obj)
public static Map<String, EObject> buildXmiIdMap(Resource resource, XmiIdExtractor extractor)
public static List<Difference> compareXmiIds(Resource expected, Resource actual, XmiIdExtractor extractor)
public static ComparisonResult compare(Resource expected, Resource actual, ComparisonMode mode, XmiIdExtractor extractor)
```

Backward compatibility verified - all 5 external model tests pass.
