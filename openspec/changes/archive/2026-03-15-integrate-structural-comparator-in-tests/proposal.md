# Integrate Structural Comparator in Abstract Model Tests

**Status:** complete

## Summary

Add support for the new `StructuralModelComparator` in `AbstractExternalModelTest` and `AbstractDualTransformationTest`, enabling checksum-based structural comparison with LLM-friendly error output. All external model tests will be updated to use the new comparator.

## Why

The current `ModelComparator` used in abstract test classes provides identifier-based comparison but lacks:
1. Checksum-based subtree optimization (skip comparing identical subtrees)
2. Direct EObject resolution from differences
3. LLM-friendly structured output for automated analysis
4. JSON export/import for baseline comparisons

The new `StructuralModelComparator` (from the recently completed `enhance-structural-reference-validation` proposal) addresses all these limitations and is already available in `judo-tatami-test-utils`.

## What Changes

### AbstractExternalModelTest

Add a new method `compareModelsStructural()` that uses `StructuralModelComparator`:
- Calculates checksums for both models
- Compares structures and reports differences
- Outputs LLM-friendly format on failure
- Supports system property to select comparator

### AbstractDualTransformationTest

Add structural comparison support:
- New `compareModelsStructural()` method
- System property `judo.test.comparison.structural=true` to enable
- Falls back to existing `ModelComparator` when disabled

### All External Model Tests

Update all 5 external model tests to use structural comparison when enabled:

1. **Psm2AsmExternalModelTest** (`judo-tatami-psm2asm`)
   - PSM to ASM transformation comparison

2. **Psm2MeasureExternalModelTest** (`judo-tatami-psm2measure`)
   - PSM to Measure transformation comparison

3. **Asm2RdbmsExternalModelTest** (`judo-tatami-asm2rdbms`)
   - ASM to RDBMS transformation comparison

4. **Asm2KeycloakExternalModelTest** (`judo-tatami-asm2keycloak`)
   - ASM to Keycloak transformation comparison

5. **Rdbms2LiquibaseExternalModelTest** (`judo-tatami-rdbms2liquibase`)
   - RDBMS to Liquibase transformation comparison

Each test will get:
- Checksum-based comparison
- LLM-friendly error output
- Optional JSON baseline export

## Approach

### System Property Configuration

```
# Enable structural comparison (default: false for backward compatibility)
-Djudo.test.comparison.structural=true

# Export model structure JSON for debugging
-Djudo.test.structural.exportJson=true
-Djudo.test.structural.outputDir=target/comparison
```

### Integration Pattern

```java
// In AbstractExternalModelTest
protected ComparisonResult compareModelsStructural(Resource expected, Resource actual) {
    ModelChecksumCalculator calc = new ModelChecksumCalculator();
    ModelNode expectedNode = calc.calculate(expected);
    ModelNode actualNode = calc.calculate(actual);

    StructuralModelComparator comparator = new StructuralModelComparator();
    ComparisonResult result = comparator.compare(expectedNode, actualNode);

    if (!result.isMatch()) {
        log.error(comparator.formatForLLM(result));
    }

    return result;
}
```

### Backward Compatibility

- Structural comparison is opt-in via system property
- Existing tests continue using `ModelComparator` by default
- Both comparators can run in parallel for validation

## Impact

### Modules Affected
- `judo-tatami-test-utils` - Add helper methods to abstract test classes
- `judo-tatami-psm2asm` - Update `Psm2AsmExternalModelTest` to use new comparator
- `judo-tatami-psm2measure` - Update `Psm2MeasureExternalModelTest` to use new comparator
- `judo-tatami-asm2rdbms` - Update `Asm2RdbmsExternalModelTest` to use new comparator
- `judo-tatami-asm2keycloak` - Update `Asm2KeycloakExternalModelTest` to use new comparator
- `judo-tatami-rdbms2liquibase` - Update `Rdbms2LiquibaseExternalModelTest` to use new comparator

### Breaking Changes
- None - opt-in via system property

### Dependencies
- Uses existing `StructuralModelComparator` and `ModelChecksumCalculator` from `judo-tatami-test-utils`

## Risks

### Risk 1: Performance Overhead
**Mitigation**: Checksum calculation is O(n) and comparison skips identical subtrees via checksum matching, so overall performance should be similar or better.

### Risk 2: Different Results from Old Comparator
**Mitigation**: Run both comparators initially to validate equivalent behavior, then switch.

## Success Criteria

1. `AbstractExternalModelTest` provides `compareModelsStructural()` method
2. `AbstractDualTransformationTest` supports structural comparison via system property
3. `Psm2AsmExternalModelTest` can use structural comparison when enabled
4. LLM-friendly output is produced on comparison failures
5. JSON export works for debugging model differences
6. Backward compatibility maintained - existing tests work unchanged
