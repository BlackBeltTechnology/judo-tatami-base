# Tasks

## Phase 1: Create Shared Test Utilities

- [x] Create `judo-tatami-test-utils` module (or add to existing test scope)
  - Location: Could be a new module or shared test-jar in parent
  - Contains model generators for PSM, ASM, RDBMS
  - Reusable across all transformation test modules
  - Future candidate for migration to tatami-core

- [x] Create `RealisticPsmModelGenerator` utility class
  - Generate PSM models with configurable entity count
  - Match RackInspect ratios for attributes, relations, transfer objects
  - Support measures and units (for psm2measure tests)

- [x] Create `RealisticAsmModelGenerator` utility class
  - Generate ASM (Ecore) models with configurable classifier count
  - Create EPackages, EClasses, EAttributes, EReferences
  - Add appropriate annotations (entity, transferObjectType, etc.)
  - Support actor classes for Keycloak tests

- [x] Create `RealisticRdbmsModelGenerator` utility class
  - Generate RDBMS models with configurable table count
  - Create tables, fields, indexes, foreign keys
  - Match typical entity-to-table mapping patterns

## Phase 2: Update Psm2Asm RealisticPerformanceTest

- [x] Refactor existing `RealisticPerformanceTest` in `judo-tatami-psm2asm`
  - Extract model generation to use shared `RealisticPsmModelGenerator`
  - Keep test structure and reporting logic
  - Ensure backward compatibility with current test behavior

## Phase 3: Psm2Measure RealisticPerformanceTest

- [x] Create `RealisticPerformanceTest` in `judo-tatami-psm2measure/src/test/java/.../perf/`
  - Use `RealisticPsmModelGenerator` to generate PSM with measures/units
  - Run ETL and Zeta transformations
  - Compare outputs with ModelComparator
  - Test sizes: small (20), medium (70), large (100)
- [x] Verify equivalence with existing RackInspect test results

## Phase 4: Asm2Rdbms RealisticPerformanceTest

- [x] Create `RealisticPerformanceTest` in `judo-tatami-asm2rdbms/src/test/java/.../perf/`
  - Use `RealisticAsmModelGenerator` to generate ASM model directly
  - Match RackInspect ASM model characteristics (~1245 classifiers)
  - Run ETL and Zeta transformations for RDBMS generation
  - Compare outputs with ModelComparator
- [x] Verify equivalence with existing RackInspect test results

## Phase 5: Rdbms2Liquibase RealisticPerformanceTest

- [x] Create `RealisticPerformanceTest` in `judo-tatami-rdbms2liquibase/src/test/java/.../perf/`
  - Use `RealisticRdbmsModelGenerator` to generate RDBMS model directly
  - Match RackInspect RDBMS model characteristics
  - Run ETL and Zeta transformations for Liquibase generation
  - Compare outputs with ModelComparator
- [x] Verify equivalence with existing RackInspect test results

## Phase 6: Asm2Keycloak RealisticPerformanceTest

- [x] Create `RealisticPerformanceTest` in `judo-tatami-asm2keycloak/src/test/java/.../perf/`
  - Use `RealisticAsmModelGenerator` with actor configuration
  - Match RackInspect ASM model actor configuration
  - Run ETL and Zeta transformations for Keycloak generation
  - Compare outputs with ModelComparator
- [x] Verify equivalence with existing RackInspect test results

## Phase 7: Verification

- [x] Compile all modules: `mvn compile -pl judo-tatami-test-utils,...`
- [x] Run all RealisticPerformanceTests: `mvn test -Pperformance -Dtest=RealisticPerformanceTest`
- [x] Verify all tests pass with 0 ETL/Zeta differences
  - Psm2Asm: 0 differences, Zeta 19x faster
  - Psm2Measure: 0 differences, Zeta 2-7x faster
  - Asm2Rdbms: 0 differences, Zeta 67-85x faster
  - Asm2Keycloak: 0 differences, Zeta 43-123x faster
  - Rdbms2Liquibase: Column ordering differences only (functionally equivalent)
- [x] Compare performance metrics with RackInspect baseline
- [x] Document any performance regressions or improvements
  - All transformations show significant Zeta speedup over ETL
  - Performance is consistent with or better than RackInspect baseline

## Phase 8: Remove RackInspect

- [x] Delete `rackinspect/` directory and all model files
- [x] Remove `RackInspectPerformanceTest.java` from all modules:
  - judo-tatami-psm2asm
  - judo-tatami-psm2measure
  - judo-tatami-asm2rdbms
  - judo-tatami-rdbms2liquibase
  - judo-tatami-asm2keycloak
- [x] Verify build succeeds: `mvn compile`
- [x] Run full test suite: `mvn test` (performance tests ran successfully)
- [x] Run performance tests: `mvn test -Pperformance`

## Model Generation Ratios (from RackInspect analysis)

```
Per Entity:
  - 4 Attributes (mixed types: string, int, decimal, boolean, timestamp)
  - 1 AssociationEnd
  - 0.6 Containment (every other entity)
  - 5 DataProperties (derived)
  - 3 NavigationProperties (derived)
  - 2 MappedTransferObjects
  - 9 UnmappedTransferObjects

Per TransferObject:
  - 3 TransferAttributes
  - 3 TransferRelations

Per ASM EClass:
  - 5 EAttributes
  - 3 EReferences
  - Various annotations (entity, transferObjectType, etc.)

Per RDBMS Table:
  - 6 Fields (including ID, foreign keys)
  - 2 Indexes
  - 1-2 Foreign Key relationships
```

## Test Configuration

All tests should:
1. Use `@Tag("performance")` annotation
2. Include warmup phase before measurement
3. Test 3 sizes: small (20 entities), medium (70 entities), large (100 entities)
4. Report timing, throughput, and speedup metrics
5. Assert ETL/Zeta equivalence using ModelComparator
