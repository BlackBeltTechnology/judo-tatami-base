# Tasks

## Phase 1: Add Configuration Support to AbstractExternalModelTest

- [x] Add system property constant `PROP_TRANSFORMATION_MODE = "judo.test.transformation.mode"`
- [x] Add `getConfiguredTransformationMode()` static method that reads from system property
- [x] Add `shouldRunEtl()` helper method
- [x] Add `shouldRunZeta()` helper method
- [x] Add `shouldCompareResults()` helper method
- [x] Add logging of configured mode in test header methods
- [x] Add warning when profiling is enabled but mode is DUAL (pollutes metrics)

## Phase 2: Update External Model Tests

### Asm2RdbmsExternalModelTest
- [x] Refactor to use `shouldRunEtl()` / `shouldRunZeta()` / `shouldCompareResults()`
- [x] Skip ETL transformation when `shouldRunEtl()` returns false
- [x] Skip ZETA transformation when `shouldRunZeta()` returns false
- [x] Skip comparison when `shouldCompareResults()` returns false

### Psm2AsmExternalModelTest
- [x] Refactor to use `shouldRunEtl()` / `shouldRunZeta()` / `shouldCompareResults()`
- [x] Skip ETL transformation when `shouldRunEtl()` returns false
- [x] Skip ZETA transformation when `shouldRunZeta()` returns false
- [x] Skip comparison when `shouldCompareResults()` returns false

### Other External Model Tests (parallel)
- [x] Update `Psm2MeasureExternalModelTest` similarly
- [x] Update `Rdbms2LiquibaseExternalModelTest` similarly
- [x] Update `Asm2KeycloakExternalModelTest` similarly

## Phase 3: Verification

- [x] Run tests with `-Djudo.test.transformation.mode=ZETA` - verify only ZETA runs
- [x] Run tests with `-Djudo.test.transformation.mode=ETL` - verify only ETL runs
- [x] Run tests with `-Djudo.test.transformation.mode=DUAL` - verify both run and compare
- [x] Run tests without property - verify default DUAL behavior (same as DUAL)
- [x] Measure execution time reduction in single-engine modes

## Results

Verified with `Psm2MeasureExternalModelTest`:

| Mode | ETL Time | ZETA Time | Comparison |
|------|----------|-----------|------------|
| **ZETA** | skipped | 674ms | skipped |
| **ETL** | 861ms | skipped | skipped |
| **DUAL** | 930ms | 539ms | ✅ Equivalent |

Single-engine modes (ZETA/ETL) skip the comparison step, reducing total test time.
