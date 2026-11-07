# Proposal: Add Selectable Transformation Mode for Abstract Model Comparison Test

## Summary

Add transformation mode selection capability to `AbstractExternalModelTest` allowing tests to run with ZETA-only, ETL-only, or BOTH (comparison) modes controlled via system property or test configuration.

## Motivation

Currently, external model tests (`Asm2RdbmsExternalModelTest`, `Psm2AsmExternalModelTest`, etc.) always run both ETL and ZETA transformations and compare results. This is useful for validation but has drawbacks:

1. **Slow test execution** - Running both transformations doubles execution time
2. **No ZETA-only mode** - After achieving parity, teams may want to run only ZETA (recommended engine)
3. **No ETL-only mode** - Legacy/backward compatibility testing requires running only ETL
4. **Code duplication** - Each test class duplicates transformation mode handling logic
5. **Polluted profiling metrics** - When profiling with JFR/async-profiler, running both engines pollutes JVM metrics with ETL overhead (Epsilon interpreter, script parsing), making it impossible to accurately profile ZETA transformation performance

## Solution

Leverage the existing `TransformationMode` enum from `judo-tatami-core` and add built-in support in `AbstractExternalModelTest` to:

1. Read transformation mode from system property `judo.test.transformation.mode`
2. Provide `getConfiguredTransformationMode()` method for subclasses
3. Provide helper methods: `shouldRunEtl()`, `shouldRunZeta()`, `shouldCompareResults()`

The existing `TransformationMode` enum already supports:
- `ZETA` - Use Zeta Java transformation (recommended)
- `ETL` - Use Epsilon ETL transformation (legacy)
- `DUAL` - Run both and compare results (current behavior)

## Scope

### In Scope
- Add transformation mode configuration to `AbstractExternalModelTest`
- Add system property `judo.test.transformation.mode` for mode selection
- Update existing external model tests to use the new helper methods
- Default to `DUAL` mode for backward compatibility

### Out of Scope
- Changes to transformation execution logic (already in place)
- Changes to the `TransformationMode` enum (reuse existing from judo-tatami-core)
- Performance optimizations

## Acceptance Criteria

1. Running `mvn test -Djudo.test.transformation.mode=ZETA` executes only ZETA transformation
2. Running `mvn test -Djudo.test.transformation.mode=ETL` executes only ETL transformation
3. Running `mvn test -Djudo.test.transformation.mode=DUAL` (or no property) runs both and compares
4. All existing tests continue to pass with default behavior
5. Test execution time reduces by ~50% when running single-engine mode
6. JVM profiling with ZETA mode shows clean metrics without ETL/Epsilon interpreter overhead

## Profiling Recommendation

When profiling ZETA transformation performance, **always use ZETA mode** to avoid polluting JVM metrics:

```bash
# Profile ZETA transformation only (recommended)
mvn test -Djudo.test.transformation.mode=ZETA -Djudo.test.profiler.enabled=true

# This avoids mixing ETL metrics (Epsilon interpreter, script parsing, etc.)
# with ZETA metrics (pure Java transformation code)
```

Using DUAL mode during profiling will include:
- Epsilon ETL engine initialization
- Script parsing and interpretation overhead
- EOL/ETL context creation
- These pollute flamegraphs and make ZETA bottleneck analysis difficult
