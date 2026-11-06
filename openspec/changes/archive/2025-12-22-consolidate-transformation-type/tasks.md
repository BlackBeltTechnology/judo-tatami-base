# Implementation Tasks

## Phase 1: Update judo-tatami-psm2asm Module (Pilot)

- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmAccessPointTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmDataTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmDerivedTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmInheritanceTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmNamespaceTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmServiceTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Psm2AsmTypeTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `AccessPointTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `OperationTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `AbstractDualTransformationTest.java`
- [x] Update `TransformationType` → `TransformationMode` in performance tests (`Psm2AsmPerformanceTest.java`, `RealisticPerformanceTest.java`)
- [x] Delete `judo-tatami-psm2asm/src/test/java/.../psm2asm/TransformationType.java`
- [x] Run tests: `mvn test -pl judo-tatami-psm2asm` to verify changes

## Phase 2: Update judo-tatami-asm2rdbms Module

- [x] Update `TransformationType` → `TransformationMode` in `Asm2RdbmsTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Asm2RdbmsInheritanceTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Asm2RdbmsTypeMappingTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Asm2RdbmsMappingTestBase.java`
- [x] Update `TransformationType` → `TransformationMode` in `Asm2RdbmsRelationMappingTest.java`
- [x] Update `TransformationType` → `TransformationMode` in performance tests (`Asm2RdbmsPerformanceTest.java`, `RealisticPerformanceTest.java`)
- [x] Delete `judo-tatami-asm2rdbms/src/test/java/.../asm2rdbms/TransformationType.java`
- [x] Run tests: `mvn test -pl judo-tatami-asm2rdbms` to verify changes

## Phase 3: Update judo-tatami-rdbms2liquibase Module

- [x] Update `TransformationType` → `TransformationMode` in `Rdbms2LiquibaseTest.java`
- [x] Update `TransformationType` → `TransformationMode` in `Rdbms2LiquibaseContentTest.java`
- [x] Update `TransformationType` → `TransformationMode` in performance tests (`Rdbms2LiquibasePerformanceTest.java`, `RealisticPerformanceTest.java`)
- [x] Delete `judo-tatami-rdbms2liquibase/src/test/java/.../rdbms2liquibase/TransformationType.java`
- [x] Run tests: `mvn test -pl judo-tatami-rdbms2liquibase` to verify changes

## Phase 4: Update judo-tatami-asm2keycloak Module

- [x] Update `TransformationType` → `TransformationMode` in `Asm2KeycloakTest.java`
- [x] Update `TransformationType` → `TransformationMode` in performance tests (`Asm2KeycloakPerformanceTest.java`, `RealisticPerformanceTest.java`)
- [x] Delete `judo-tatami-asm2keycloak/src/test/java/.../asm2keycloak/TransformationType.java`
- [x] Run tests: `mvn test -pl judo-tatami-asm2keycloak` to verify changes

## Phase 5: Update judo-tatami-psm2measure Module

- [x] Update `TransformationType` → `TransformationMode` in `Psm2MeasureTest.java`
- [x] Update `TransformationType` → `TransformationMode` in performance tests (`Psm2MeasurePerformanceTest.java`, `RealisticPerformanceTest.java`)
- [x] Delete `judo-tatami-psm2measure/src/test/java/.../psm2measure/TransformationType.java`
- [x] Run tests: `mvn test -pl judo-tatami-psm2measure` to verify changes

## Phase 6: Update Documentation

- [x] Update `openspec/AGENTS.md` - replace `TransformationType` examples with `TransformationMode`
- [x] Update `CONTRIBUTING.md` - replace test pattern examples
- [x] Update `docs/migration/dual-engine-testing.md` - comprehensive update of all examples and references
- [x] Update `docs/migration/etl-to-zeta-migration.md` - update enum setup references
- [x] Update `docs/transformations/README.md` - update test examples
- [x] Update archived OpenSpec changes:
  - `openspec/changes/document-etl-zeta-migration/proposal.md`
  - `openspec/changes/document-etl-zeta-migration/design.md`
  - `openspec/changes/document-etl-zeta-migration/tasks.md`
  - `openspec/changes/document-etl-zeta-migration/specs/migration-documentation/spec.md`
  - `openspec/changes/archive/2025-12-13-add-zeta-dual-transformation/tasks.md`
  - `openspec/changes/archive/2025-12-13-add-zeta-dual-transformation/specs/zeta-transformations/spec.md`
  - `openspec/changes/archive/2025-12-13-add-etl-zeta-model-comparison/design.md`

## Phase 7: Validation

- [x] Verify no references to `TransformationType` remain in Java: `rg "TransformationType" --type java`
- [x] Verify documentation consistency: Only remaining references are in the consolidate-transformation-type change's own historical documentation
- [ ] Run full build: `mvn clean install` to verify all modules compile and pass tests
- [ ] Run OpenSpec validation: `openspec validate consolidate-transformation-type --strict`
- [ ] Review all changes for completeness

## Notes

- Each phase can be executed independently
- Tests must pass after each phase before proceeding
- No production code changes required - all changes are in test code and documentation
- Total estimated files to modify: ~40-45 files (25 test files + 15-20 documentation files)

## Summary of Changes

### Java Files Updated (Test Code)
- **judo-tatami-asm2rdbms**: 5 files updated, 1 deleted
  - `Asm2RdbmsTest.java`, `Asm2RdbmsMappingTestBase.java`, `Asm2RdbmsInheritanceTest.java`, `Asm2RdbmsTypeMappingTest.java`, `Asm2RdbmsRelationMappingTest.java`
  - Deleted: `TransformationType.java`
- **judo-tatami-rdbms2liquibase**: 2 files updated, 1 deleted
  - `Rdbms2LiquibaseTest.java`, `Rdbms2LiquibaseContentTest.java`
  - Deleted: `TransformationType.java`
- **judo-tatami-asm2keycloak**: 1 file updated, 1 deleted
  - `Asm2KeycloakTest.java`
  - Deleted: `TransformationType.java`
- **judo-tatami-psm2measure**: 1 file updated, 1 deleted
  - `Psm2MeasureTest.java`
  - Deleted: `TransformationType.java`

### Documentation Files Updated
- `openspec/AGENTS.md`
- `CONTRIBUTING.md`
- `docs/migration/dual-engine-testing.md`
- `docs/migration/etl-to-zeta-migration.md`
- `docs/transformations/README.md`
- `openspec/changes/document-etl-zeta-migration/proposal.md`
- `openspec/changes/document-etl-zeta-migration/design.md`
- `openspec/changes/document-etl-zeta-migration/tasks.md`
- `openspec/changes/document-etl-zeta-migration/specs/migration-documentation/spec.md`
- `openspec/changes/archive/2025-12-13-add-zeta-dual-transformation/tasks.md`
- `openspec/changes/archive/2025-12-13-add-zeta-dual-transformation/specs/zeta-transformations/spec.md`
- `openspec/changes/archive/2025-12-13-add-etl-zeta-model-comparison/design.md`

### Key Pattern Changes
- `@EnumSource(TransformationType.class)` → `@EnumSource(TransformationMode.class)`
- `TransformationType transformationType` → `TransformationMode transformationMode`
- `transformationType == TransformationType.ZETA` → `transformationMode.isZeta()`
- Import: `hu.blackbelt.judo.tatami.core.TransformationMode`
