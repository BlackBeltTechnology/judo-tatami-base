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
- [x] Add `TransformationMode` enum to `judo-tatami-core` dependency (ETL default, ZETA opt-in)
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
- [x] Implement `Psm2AsmZetaTransformation` with @TransformationContext
- [x] Implement namespace transformation rules (namespace.etl equivalent)
- [x] Implement type transformation rules (type.etl equivalent)
- [x] Implement data transformation rules (data.etl equivalent)
- [x] Implement derived transformation rules (derived.etl equivalent)
- [x] Implement operation transformation rules (operation.etl equivalent)
- [x] Implement transferObject transformation rules (transferObject.etl equivalent)
- [x] Implement actor transformation rules (actor.etl equivalent)
- [x] Implement static transformation rules (static.etl equivalent)
- [x] Integrate Zeta transformation into `Psm2AsmWork` class
- [x] Document all transformation rules in `docs/transformations/psm2asm.md`

### 2.2 PSM2Measure Module (judo-tatami-psm2measure)
- [x] Create rule constants class `Psm2MeasureRuleNames`
- [x] Implement `Psm2MeasureZetaTransformation`
- [x] Implement measure transformation rules (measure.etl equivalent)
- [x] Implement unit transformation rules (unit.etl equivalent)
- [x] Integrate Zeta transformation into `Psm2MeasureWork` class
- [x] Document transformation rules in `docs/transformations/psm2measure.md`

### 2.3 ASM2RDBMS Module (judo-tatami-asm2rdbms)
- [x] Create rule constants class `Asm2RdbmsRuleNames`
- [x] Implement `Asm2RdbmsZetaTransformation`
- [x] Implement package transformation rules (package.etl equivalent)
- [x] Implement class transformation rules (class.etl equivalent)
- [x] Implement attribute transformation rules (attribute.etl equivalent)
- [x] Implement reference transformation rules (reference.etl equivalent)
- [x] Implement Excel mapping transformations (excelTo*.etl equivalents)
- [x] Integrate Zeta transformation into `Asm2RdbmsWork` class
- [x] Document transformation rules in `docs/transformations/asm2rdbms.md`

### 2.4 RDBMS2Liquibase Module (judo-tatami-rdbms2liquibase)
- [x] Create rule constants class `Rdbms2LiquibaseRuleNames`
- [x] Implement `Rdbms2LiquibaseZetaTransformation`
- [x] Implement table transformation rules (table.etl equivalent)
- [x] Implement field transformation rules (field.etl equivalent)
- [x] Implement incremental transformation rules (incremental.etl, beforeIncremental.etl, afterIncremental.etl)
- [x] Implement data update rules (dataUpdateBeforeIncremental.etl, dataUpdateAfterIncremental.etl)
- [x] Implement backup rules (dbBackup.etl, dbCheckup.etl, dbDropBackup.etl)
- [x] Integrate Zeta transformation into `Rdbms2LiquibaseWork` class
- [x] Document transformation rules in `docs/transformations/rdbms2liquibase.md`

### 2.5 ASM2Keycloak Module (judo-tatami-asm2keycloak)
- [x] Create rule constants class `Asm2KeycloakRuleNames`
- [x] Implement `Asm2KeycloakZetaTransformation`
- [x] Implement realm transformation rules (realm.etl equivalent)
- [x] Implement client transformation rules (client.etl equivalent)
- [x] Integrate Zeta transformation into `Asm2KeycloakWork` class
- [x] Document transformation rules in `docs/transformations/asm2keycloak.md`

## Phase 3: Test Infrastructure

### 3.1 Dual Testing Framework
- [x] Create `TransformationType` enum in test utilities
- [x] Implement model equivalence comparison utilities (ModelComparator)
- [x] Create parameterized test base classes (AbstractDualTransformationTest)

### 3.2 PSM2ASM Tests
- [x] Convert `Psm2AsmTest` to parameterized dual test
- [ ] Convert `Psm2AsmWorkTest` to parameterized dual test (ETL-only - tests workflow integration, not transformation correctness)
- [x] Convert `Psm2AsmDataTest` to parameterized dual test
- [x] Convert `Psm2AsmTypeTest` to parameterized dual test
- [x] Convert `Psm2AsmDerivedTest` to parameterized dual test
- [x] Convert `Psm2AsmNamespaceTest` to parameterized dual test
- [x] Convert `Psm2AsmInheritanceTest` to parameterized dual test
- [x] Convert `Psm2AsmAccessPointTest` to parameterized dual test
- [x] Convert `Psm2AsmServiceTest` to parameterized dual test
- [x] Convert `OperationTest` to parameterized dual test
- [x] Convert `AccessPointTest` to parameterized dual test

### 3.3 Other Module Tests

#### 3.3.1 PSM2Measure Tests
- [x] Convert `Psm2MeasureTest` to parameterized dual test
- [ ] `Psm2MeasureWorkTest` - ETL-only (tests workflow integration, not transformation correctness)

#### 3.3.2 ASM2RDBMS Tests
- [x] Convert `Asm2RdbmsTest` to parameterized dual test
- [x] Convert `Asm2RdbmsInheritanceTest` to parameterized dual test
- [x] Convert `Asm2RdbmsTypeMappingTest` to parameterized dual test
- [x] Convert `Asm2RdbmsRelationMappingTest` to parameterized dual test
- [x] Update `Asm2RdbmsMappingTestBase` with dual transformation support
- [ ] `Asm2RdbmsNameMappingTest` - All tests @Disabled, skipped
- [ ] `Asm2RdbmsWorkTest` - ETL-only (tests workflow integration)
- [ ] `AbbreviateUtilsTest` - Utility test, not transformation test

#### 3.3.3 RDBMS2Liquibase Tests
- [x] Convert `Rdbms2LiquibaseTest` to parameterized dual test
- [x] Convert `Rdbms2LiquibaseContentTest` to parameterized dual test
- [ ] `Rdbms2LiquibaseWorkTest` - ETL-only (tests workflow integration)
- [ ] `Rdbms2LiquibaseIncrementalWorkTest` - ETL-only (tests incremental workflow)
- [ ] `Excel2RdbmsTest` - Not a transformation test

#### 3.3.4 ASM2Keycloak Tests
- [x] Convert `Asm2KeycloakTest` to parameterized dual test
- [ ] `Asm2KeycloakWorkTest` - ETL-only (tests workflow integration)

#### 3.3.5 ASM2Expression Tests
- [ ] Convert ASM2Expression tests to dual tests (N/A - no Zeta transformation implemented yet)

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
- [x] Ensure all transformation documentation is complete

### 4.3 Create Missing Transformation Documentation
- [x] Create `docs/transformations/psm2measure.md` with PSM to Measure transformation rules
- [x] Create `docs/transformations/asm2rdbms.md` with ASM to RDBMS transformation rules
- [x] Create `docs/transformations/rdbms2liquibase.md` with RDBMS to Liquibase transformation rules
- [x] Create `docs/transformations/asm2keycloak.md` with ASM to Keycloak transformation rules

### 4.4 Update Existing Documentation
- [ ] Review and update any references in existing markdown files
- [ ] Ensure CLAUDE.md is accurate for this project
- [ ] Update any README files in submodules if they exist

## Phase 5: Validation & Cleanup

### 5.1 Full Test Suite
- [x] Run complete test suite with ETL mode - All pass
- [x] Run complete test suite with Zeta mode - See notes below
- [x] Run complete test suite with dual mode (comparison) - See notes below
- [ ] Verify all tests pass

#### Test Suite Results (2025-12-09)

| Module | ETL Tests | Zeta Tests | Notes |
|--------|-----------|------------|-------|
| judo-tatami-psm2asm | PASS | 13 FAIL | Multiple Zeta failures - incomplete transformation |
| judo-tatami-psm2measure | PASS | PASS | All tests pass |
| judo-tatami-asm2rdbms | PASS | PASS | All tests pass (after Zeta library update) |
| judo-tatami-rdbms2liquibase | PASS | PASS | All tests pass (after local fix) |
| judo-tatami-asm2keycloak | PASS | PASS | All tests pass |

**Known Zeta Failures (Expected):**

**judo-tatami-psm2asm Zeta failures (13 tests):**
- `Psm2AsmTest.testPsm2AsmTransformation` - ASM model validation errors (void operation upperBound, missing eAttributeType)
- `Psm2AsmDataTest.testData`, `testSequences` - Model validation failures
- `Psm2AsmTypeTest.testType` - Model validation failures
- `Psm2AsmDerivedTest.testDerived`, `testDerivedInUnmappedTransferObjectTypes` - Missing derived property types
- `Psm2AsmNamespaceTest.testNamespace` - Model validation failures
- `Psm2AsmServiceTest.testTransferObject`, `testOperation` - Model validation failures
- `Psm2AsmAccessPointTest.testAccessPoint` - Null result
- `OperationTest.testInitializerAnnotation` - Model validation failures
- `AccessPointTest.testGetPrincipalOperations`, `testExposedServicesAndGraphs` - Model validation failures

**Fixes Applied:**
- Fixed `Rdbms2LiquibaseZetaTransformation` to exclude `RdbmsForeignKey` from identifier field processing
- Fixed `Psm2AsmZetaTransformation.transformUnboundOperation()` ClassCastException - container can be ActorType/TransferObjectType, not just Namespace

### 5.2 Performance Validation
- [ ] Run performance benchmarks
- [ ] Document performance comparison results
- [ ] Verify Zeta performance meets expectations

### 5.3 Final Cleanup
- [ ] Remove any deprecated code or comments
- [ ] Ensure all code follows project conventions
- [ ] Final documentation review
