## Why

Five discovery comparison tests (`Psm2Asm`, `Psm2Measure`, `Asm2Rdbms`, `Asm2Keycloak`, `Rdbms2Liquibase`) each duplicate ~240 LOC of identical orchestration: `@TestFactory`, warmup, timed ETL/ZETA, `ModelComparator`, result recording, summary printing, and JSON export. `AbstractDualComparisonTest<S,T>` in `judo-tatami-test-utils` already provides all of this. Migrating eliminates ~800 LOC of duplication and ensures behavioral consistency across all dual comparison tests.

Additionally, `AbstractDualComparisonTest` has a design gap: `executeEtl(S)` and `executeZeta(S)` don't receive `ExternalModelConfig`, forcing tests that need config parameters (dialect, behaviours) to stash state in fields. This should be fixed before the first consumers migrate.

## What Changes

- Add `ExternalModelConfig` parameter to `executeEtl` and `executeZeta` abstract methods in `AbstractDualComparisonTest`
- Migrate 5 discovery comparison tests from `AbstractExternalModelTest` to `AbstractDualComparisonTest<S,T>`
- Each migrated test reduces from ~240-260 LOC to ~60-70 LOC

## Capabilities

### New Capabilities
- `dual-comparison-config-propagation`: Pass `ExternalModelConfig` to `executeEtl`/`executeZeta` so subclasses can access config parameters (dialect, behaviours, etc.) without field-stashing workarounds

### Modified Capabilities

## Impact

- `judo-tatami-test-utils`: API change to `AbstractDualComparisonTest` (add config parameter to 2 abstract methods)
- `judo-tatami-psm2asm`: Rewrite `Psm2AsmDiscoveryComparisonTest`
- `judo-tatami-psm2measure`: Rewrite `Psm2MeasureDiscoveryComparisonTest`
- `judo-tatami-asm2rdbms`: Rewrite `Asm2RdbmsDiscoveryComparisonTest`
- `judo-tatami-asm2keycloak`: Rewrite `Asm2KeycloakDiscoveryComparisonTest`
- `judo-tatami-rdbms2liquibase`: Rewrite `Rdbms2LiquibaseDiscoveryComparisonTest`
- No production code changes; test-only
