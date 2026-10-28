## Why

The 5 `*DiscoveryComparisonTest` classes always run model comparison, ignoring the `judo.test.comparison.enabled` system property. All other test classes in the project respect this flag via `ModelComparator.isComparisonEnabled()`. Adding this guard allows running discovery tests in performance-only mode (no comparison, just ETL vs Zeta timing).

## What Changes

- Add `ModelComparator.isComparisonEnabled()` guard to the comparison block in all 5 `*DiscoveryComparisonTest` classes
- When `judo.test.comparison.enabled=false`, the tests skip model comparison and only report performance results
- Follows the exact same pattern used in all other dual-transformation tests (e.g., `Psm2AsmDualTransformationTest`, `Psm2AsmDataTest`, etc.)

## Capabilities

### New Capabilities

- `discovery-test-comparison-guard`: Add `isComparisonEnabled()` guard to DiscoveryComparisonTest classes so they can run in performance-only mode

### Modified Capabilities

## Impact

- `judo-tatami-psm2asm/src/test/.../perf/Psm2AsmDiscoveryComparisonTest.java`
- `judo-tatami-psm2measure/src/test/.../perf/Psm2MeasureDiscoveryComparisonTest.java`
- `judo-tatami-asm2rdbms/src/test/.../perf/Asm2RdbmsDiscoveryComparisonTest.java`
- `judo-tatami-rdbms2liquibase/src/test/.../perf/Rdbms2LiquibaseDiscoveryComparisonTest.java`
- `judo-tatami-asm2keycloak/src/test/.../perf/Asm2KeycloakDiscoveryComparisonTest.java`
