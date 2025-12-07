# Tasks

## Phase 1: Infrastructure Setup

### 1.1 Maven Configuration
- [x] Add `judo-zeta-version` property to parent pom.xml: `1.0.0.20251207_081454_0779b890_develop`
- [x] Add Zeta dependency management entries (zeta-annotations, transformation-core, validation-core)
- [ ] Update all module pom.xml files to use `${judo-zeta-version}` property
- [x] Verify build compiles successfully

### 1.2 Base Infrastructure
- [x] Create `TransformationMode` enum (ETL, ZETA, DUAL)
- [x] Create `TransformationComparator` utility for model comparison (ModelComparator)
- [x] Create `AbstractDualTransformationTest` base class
- [ ] Create `ModelGenerator` utility for performance tests

### 1.3 Work Class Transformation Mode Integration
- [x] Create `judo-tatami-common` module for shared code
- [x] Create `TransformationMode` enum in common module (ETL default, ZETA opt-in)
- [x] Add `transformationMode` field to `Psm2AsmWorkParameter`
- [x] Add `transformationMode` field to `Psm2MeasureWorkParameter`
- [x] Add `transformationMode` field to `Asm2RdbmsWorkParameter`
- [x] Add `transformationMode` field to `Rdbms2LiquibaseWorkParameter`
- [x] Add `transformationMode` field to `Asm2KeycloakWorkParameter`
- [x] Update `Psm2AsmWork.execute()` to dispatch based on transformation mode
- [x] Update `Psm2MeasureWork.execute()` to dispatch based on transformation mode
- [x] Update `Asm2RdbmsWork.execute()` to dispatch based on transformation mode
- [x] Update `Rdbms2LiquibaseWork.execute()` to dispatch based on transformation mode
- [x] Update `Asm2KeycloakWork.execute()` to dispatch based on transformation mode
- [x] Add system property support: `-Djudo.transformation.mode=ETL|ZETA`

### 1.3 Documentation Setup
- [x] Create `docs/transformations/` directory structure
- [x] Create documentation template for transformation rules

## Phase 2: Transformation Implementation

### 2.1 PSM2ASM Module (judo-tatami-psm2asm)
- [x] Create rule constants class `Psm2AsmRuleNames`
- [ ] Implement `Psm2AsmZetaTransformation` with @TransformationContext
- [ ] Implement namespace transformation rules (namespace.etl equivalent)
- [ ] Implement type transformation rules (type.etl equivalent)
- [ ] Implement data transformation rules (data.etl equivalent)
- [ ] Implement derived transformation rules (derived.etl equivalent)
- [ ] Implement operation transformation rules (operation.etl equivalent)
- [ ] Implement transferObject transformation rules (transferObject.etl equivalent)
- [ ] Implement actor transformation rules (actor.etl equivalent)
- [ ] Implement static transformation rules (static.etl equivalent)
- [ ] Create `Psm2AsmZetaWork` work class
- [x] Document all transformation rules in `docs/transformations/psm2asm.md`

### 2.2 PSM2Measure Module (judo-tatami-psm2measure)
- [ ] Create rule constants class `Psm2MeasureRuleNames`
- [ ] Implement `Psm2MeasureZetaTransformation`
- [ ] Implement measure transformation rules (measure.etl equivalent)
- [ ] Implement unit transformation rules (unit.etl equivalent)
- [ ] Create `Psm2MeasureZetaWork` work class
- [ ] Document transformation rules in `docs/transformations/psm2measure.md`

### 2.3 ASM2RDBMS Module (judo-tatami-asm2rdbms)
- [ ] Create rule constants class `Asm2RdbmsRuleNames`
- [ ] Implement `Asm2RdbmsZetaTransformation`
- [ ] Implement package transformation rules (package.etl equivalent)
- [ ] Implement class transformation rules (class.etl equivalent)
- [ ] Implement attribute transformation rules (attribute.etl equivalent)
- [ ] Implement reference transformation rules (reference.etl equivalent)
- [ ] Implement Excel mapping transformations (excelTo*.etl equivalents)
- [ ] Create `Asm2RdbmsZetaWork` work class
- [ ] Document transformation rules in `docs/transformations/asm2rdbms.md`

### 2.4 RDBMS2Liquibase Module (judo-tatami-rdbms2liquibase)
- [ ] Create rule constants class `Rdbms2LiquibaseRuleNames`
- [ ] Implement `Rdbms2LiquibaseZetaTransformation`
- [ ] Implement table transformation rules (table.etl equivalent)
- [ ] Implement field transformation rules (field.etl equivalent)
- [ ] Implement incremental transformation rules (incremental.etl, beforeIncremental.etl, afterIncremental.etl)
- [ ] Implement data update rules (dataUpdateBeforeIncremental.etl, dataUpdateAfterIncremental.etl)
- [ ] Implement backup rules (dbBackup.etl, dbCheckup.etl, dbDropBackup.etl)
- [ ] Create `Rdbms2LiquibaseZetaWork` work class
- [ ] Document transformation rules in `docs/transformations/rdbms2liquibase.md`

### 2.5 ASM2Keycloak Module (judo-tatami-asm2keycloak)
- [ ] Create rule constants class `Asm2KeycloakRuleNames`
- [ ] Implement `Asm2KeycloakZetaTransformation`
- [ ] Implement realm transformation rules (realm.etl equivalent)
- [ ] Implement client transformation rules (client.etl equivalent)
- [ ] Create `Asm2KeycloakZetaWork` work class
- [ ] Document transformation rules in `docs/transformations/asm2keycloak.md`

## Phase 3: Test Infrastructure

### 3.1 Dual Testing Framework
- [x] Create `TransformationType` enum in test utilities
- [x] Implement model equivalence comparison utilities (ModelComparator)
- [x] Create parameterized test base classes (AbstractDualTransformationTest)

### 3.2 PSM2ASM Tests
- [ ] Convert `Psm2AsmTest` to parameterized dual test
- [ ] Convert `Psm2AsmWorkTest` to parameterized dual test
- [ ] Convert `Psm2AsmDataTest` to parameterized dual test
- [ ] Convert `Psm2AsmTypeTest` to parameterized dual test
- [ ] Convert `Psm2AsmDerivedTest` to parameterized dual test
- [ ] Convert `Psm2AsmNamespaceTest` to parameterized dual test
- [ ] Convert `Psm2AsmInheritanceTest` to parameterized dual test
- [ ] Convert `Psm2AsmAccessPointTest` to parameterized dual test
- [ ] Convert `Psm2AsmServiceTest` to parameterized dual test
- [ ] Convert `OperationTest` to parameterized dual test
- [ ] Convert `AccessPointTest` to parameterized dual test

### 3.3 Other Module Tests
- [ ] Convert `Psm2MeasureTest` and `Psm2MeasureWorkTest` to dual tests
- [ ] Convert ASM2RDBMS tests to dual tests
- [ ] Convert RDBMS2Liquibase tests to dual tests
- [ ] Convert ASM2Keycloak tests to dual tests
- [ ] Convert ASM2Expression tests to dual tests (if applicable)

### 3.4 Performance Tests

#### 3.4.1 Performance Test Infrastructure
- [ ] Create `AbstractTransformationPerformanceTest` base class in `judo-tatami-common`
  - [ ] Add `PerformanceResult` data class with timing metrics
  - [ ] Implement `measureTransformation()` with warmup iterations
  - [ ] Implement `compareAndReport()` for ETL vs Zeta comparison
  - [ ] Add constants: WARMUP_ITERATIONS=3, MEASUREMENT_ITERATIONS=5
- [ ] Create `PsmModelGenerator` for generating large PSM models
  - [ ] Configurable entity count (default 100)
  - [ ] Configurable attributes per entity (default 5)
  - [ ] Configurable relations per entity (default 2)
  - [ ] Configurable enumerations count (default 10)
- [ ] Create `AsmModelGenerator` for generating large ASM models
- [ ] Create `RdbmsModelGenerator` for generating large RDBMS models

#### 3.4.2 Module Performance Tests
- [ ] Implement `Psm2AsmPerformanceTest` in `judo-tatami-psm2asm/src/test/java/.../perf/`
  - [ ] Parameterized test with model sizes: 100, 1000, 10000
  - [ ] Compare ETL vs Zeta execution time
  - [ ] Assert Zeta is within 20% of ETL performance
  - [ ] Target: 10,000 entities in <4000ms (vs ETL ~5000ms)
- [ ] Implement `Psm2MeasurePerformanceTest` in `judo-tatami-psm2measure`
  - [ ] Target: 1,000 measures in <400ms (vs ETL ~500ms)
- [ ] Implement `Asm2RdbmsPerformanceTest` in `judo-tatami-asm2rdbms`
  - [ ] Target: 10,000 classes in <6000ms (vs ETL ~8000ms)
- [ ] Implement `Rdbms2LiquibasePerformanceTest` in `judo-tatami-rdbms2liquibase`
  - [ ] Target: 10,000 tables in <2500ms (vs ETL ~3000ms)
- [ ] Implement `Asm2KeycloakPerformanceTest` in `judo-tatami-asm2keycloak`
  - [ ] Target: 100 actors in <150ms (vs ETL ~200ms)

#### 3.4.3 Performance Test Configuration
- [ ] Add `@Tag("performance")` and `@Disabled` annotations to performance tests
- [ ] Configure Maven Surefire to exclude performance tests by default
- [ ] Add `-Dtest.performance=true` flag to enable performance tests
- [ ] Add `-Dperformance.model.size=N` for custom model sizes

#### 3.4.4 CI Integration
- [ ] Create `.github/workflows/performance.yml` for nightly performance runs
- [ ] Configure performance report artifact upload
- [ ] Add performance regression detection (fail if >20% slower than baseline)

## Phase 4: Documentation

### 4.1 Convert AsciiDoc to Markdown
- [x] Convert `README.adoc` to `README.md`
- [x] Convert `CONTRIBUTING.adoc` to `CONTRIBUTING.md`
- [x] Convert `.github/CIFLOW.adoc` to `.github/CIFLOW.md`
- [x] Convert PlantUML diagrams to Mermaid in converted files

### 4.2 Update Project Documentation
- [x] Update `AGENTS.md` with judo-tatami-base specific content (not PSM content)
- [x] Create transformation overview in `docs/transformations/README.md`
- [ ] Ensure all transformation documentation is complete

### 4.3 Update Existing Documentation
- [ ] Review and update any references in existing markdown files
- [ ] Ensure CLAUDE.md is accurate for this project
- [ ] Update any README files in submodules if they exist

## Phase 5: Validation & Cleanup

### 5.1 Full Test Suite
- [ ] Run complete test suite with ETL mode
- [ ] Run complete test suite with Zeta mode
- [ ] Run complete test suite with dual mode (comparison)
- [ ] Verify all tests pass

### 5.2 Performance Validation
- [ ] Run performance benchmarks
- [ ] Document performance comparison results
- [ ] Verify Zeta performance meets expectations

### 5.3 Final Cleanup
- [ ] Remove any deprecated code or comments
- [ ] Ensure all code follows project conventions
- [ ] Final documentation review
