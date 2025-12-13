# Tasks

## Implementation Status

**COMPLETE** - Enhanced ModelComparator with comparison modes, typed differences, and configuration support.

All 5 transformation modules have been updated with the enhanced `ModelComparator` class.

## Phase 1: Enhanced ModelComparator ✅ COMPLETE

### 1.1 Core Comparison Logic
- [x] Enhanced `ModelComparator` class in all modules (psm2asm, psm2measure, asm2rdbms, rdbms2liquibase, asm2keycloak)
- [x] Implement `ComparisonMode` enum (STRICT, STRUCTURAL, LENIENT)
- [x] Implement order-independent collection comparison using set-based matching
- [x] Implement element identifier extraction (by name, ID annotation, or type)
- [x] Implement recursive element traversal with cycle detection
- [x] Implement bidirectional completeness check (no missing/extra elements)

### 1.2 Difference Reporting
- [x] Create `Difference` abstract class with path and description
- [x] Create `MissingElement`, `ExtraElement`, `ValueMismatch`, `TypeMismatch` subclasses
- [x] Create `ComparisonResult` class with `getSummary()` and `getDetailedReport()` methods
- [x] Implement configurable max differences limit via `judo.test.comparison.maxDifferences`
- [x] Implement diff report file output option via `judo.test.comparison.reportFile`

### 1.3 Edge Case Handling
- [x] Skip transient and derived features in comparison
- [x] Handle empty vs null collections as equivalent
- [x] Implement epsilon-based floating point comparison
- [x] Handle EAnnotation ordering differences (skipped in STRUCTURAL mode)
- [x] Ignore EMF internal IDs, compare by structural identity

## Phase 2: Test Infrastructure Integration ✅ COMPLETE

### 2.1 Base Test Class Enhancement
- [x] Add `storeTransformationResult()` method to store ETL/Zeta results
- [x] Add `compareStoredResults()` method to run comparison after both complete
- [x] Add system property support for enabling/disabling comparison
- [x] Add system property support for comparison mode selection
- [x] Add `getComparisonMode()` method for subclass customization

### 2.2 Comparison Test Methods
- [x] Create `assertModelsEquivalent()` utility method in AbstractDualTransformationTest
- [x] Update `testDualEquivalence()` to use configured mode
- [x] Add stored result getters for caching

## Phase 3: Test Updates ✅ COMPLETE

### 3.1 Equivalence Tests Added to All Modules
- [x] `Psm2AsmTest.testEtlAndZetaEquivalence()` - Compares ETL and Zeta ASM outputs
- [x] `Psm2MeasureTest.testEtlAndZetaEquivalence()` - Compares ETL and Zeta Measure outputs
- [x] `Asm2RdbmsTest.testEtlAndZetaEquivalence()` - Compares ETL and Zeta RDBMS outputs
- [x] `Rdbms2LiquibaseTest.testEtlAndZetaEquivalence()` - Compares ETL and Zeta Liquibase outputs
- [x] `Asm2KeycloakTest.testEtlAndZetaEquivalence()` - Compares ETL and Zeta Keycloak outputs

### 3.2 Dual Transformation Test Infrastructure
- [x] Update `Psm2AsmDualTransformationTest` with enhanced comparison
- [x] Add STRICT mode test variant
- [x] `AbstractDualTransformationTest` base class with comparison infrastructure

## Phase 4: Configuration and CI ✅ COMPLETE

### 4.1 Configuration
- [x] Add `judo.test.comparison.enabled` system property (default: true)
- [x] Add `judo.test.comparison.mode` system property (default: STRUCTURAL)
- [x] Add `judo.test.comparison.maxDifferences` property (default: 50)
- [x] Add `judo.test.comparison.reportFile` property for output file
- [x] Configure Maven surefire plugin to pass properties to tests

### 4.2 CI Integration
- [x] Properties configured in parent pom.xml with defaults
- [x] Comparison enabled by default in all builds
- [x] Document how to run comparison tests locally

## Phase 5: Documentation ✅ COMPLETE

### 5.1 Documentation Updates
- [x] Update `AbstractDualTransformationTest` javadoc with configuration section
- [x] Document `ModelComparator` usage in class javadoc
- [x] Add ETL-Zeta Model Comparison section to `AGENTS.md`
- [x] Update this `tasks.md` with completion status

## Verification ✅ COMPLETE

- [x] All modules compile successfully
- [x] Comparison correctly identifies differences (tested with Psm2AsmDualTransformationTest)
- [x] Comparison correctly uses comparison modes (STRICT shows annotation differences, STRUCTURAL skips them)
- [x] Truncation and detailed reporting work correctly

## Summary

| Module | ModelComparator Updated | Status |
|--------|------------------------|--------|
| judo-tatami-psm2asm | ✅ | Enhanced with modes, typed differences |
| judo-tatami-psm2measure | ✅ | Enhanced with modes, typed differences |
| judo-tatami-asm2rdbms | ✅ | Enhanced with modes, typed differences |
| judo-tatami-rdbms2liquibase | ✅ | Enhanced with modes, typed differences |
| judo-tatami-asm2keycloak | ✅ | Enhanced with modes, typed differences |

### Files Modified

#### ModelComparator Enhancements (all modules)
1. `judo-tatami-psm2asm/src/test/java/.../ModelComparator.java` - Enhanced
2. `judo-tatami-psm2measure/src/test/java/.../util/ModelComparator.java` - Enhanced
3. `judo-tatami-asm2rdbms/src/test/java/.../util/ModelComparator.java` - Enhanced
4. `judo-tatami-rdbms2liquibase/src/test/java/.../util/ModelComparator.java` - Enhanced
5. `judo-tatami-asm2keycloak/src/test/java/.../util/ModelComparator.java` - Enhanced

#### Equivalence Tests Added (all modules)
6. `judo-tatami-psm2asm/src/test/java/.../Psm2AsmTest.java` - Added `testEtlAndZetaEquivalence()`
7. `judo-tatami-psm2measure/src/test/java/.../Psm2MeasureTest.java` - Added `testEtlAndZetaEquivalence()`
8. `judo-tatami-asm2rdbms/src/test/java/.../Asm2RdbmsTest.java` - Added `testEtlAndZetaEquivalence()`
9. `judo-tatami-rdbms2liquibase/src/test/java/.../Rdbms2LiquibaseTest.java` - Added `testEtlAndZetaEquivalence()`
10. `judo-tatami-asm2keycloak/src/test/java/.../Asm2KeycloakTest.java` - Added `testEtlAndZetaEquivalence()`

#### Test Infrastructure
11. `judo-tatami-psm2asm/src/test/java/.../AbstractDualTransformationTest.java` - Enhanced
12. `judo-tatami-psm2asm/src/test/java/.../Psm2AsmDualTransformationTest.java` - Updated

#### Configuration & Documentation
13. `pom.xml` - Added configuration properties
14. `AGENTS.md` - Added documentation

### Usage Examples

```bash
# Run with default STRUCTURAL mode
mvn test -Dtest=Psm2AsmDualTransformationTest

# Run with STRICT mode (fails on annotation differences)
mvn test -Dtest=Psm2AsmDualTransformationTest -Djudo.test.comparison.mode=STRICT

# Disable comparison
mvn test -Djudo.test.comparison.enabled=false

# Increase max differences
mvn test -Djudo.test.comparison.maxDifferences=200

# Save report to file
mvn test -Djudo.test.comparison.reportFile=target/diff-report.txt
```
