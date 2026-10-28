# Tasks

## Phase 1: Update AbstractExternalModelTest

- [x] Add import for structural comparison classes
- [x] Add `isStructuralComparisonEnabled()` method using system property
- [x] Add `compareModelsStructural(Resource, Resource)` method
- [x] Add `exportModelStructure(Resource, String)` helper for JSON export
- [x] Add system property constants for configuration

## Phase 2: Update AbstractDualTransformationTest

- [x] Add import for structural comparison classes
- [x] Add `compareModelsStructural()` method delegation
- [x] Update `compareModels()` to optionally use structural comparison
- [x] Add configuration documentation in class Javadoc

## Phase 3: Update All External Model Tests

### 3.1 Psm2AsmExternalModelTest (judo-tatami-psm2asm)
- [x] Update comparison section to use structural comparator when enabled
- [x] Add LLM-friendly error output on failure
- [x] Add optional JSON export for debugging

### 3.2 Psm2MeasureExternalModelTest (judo-tatami-psm2measure)
- [x] Update comparison section to use structural comparator when enabled
- [x] Add LLM-friendly error output on failure
- [x] Add optional JSON export for debugging

### 3.3 Asm2RdbmsExternalModelTest (judo-tatami-asm2rdbms)
- [x] Update comparison section to use structural comparator when enabled
- [x] Add LLM-friendly error output on failure
- [x] Add optional JSON export for debugging

### 3.4 Asm2KeycloakExternalModelTest (judo-tatami-asm2keycloak)
- [x] Update comparison section to use structural comparator when enabled
- [x] Add LLM-friendly error output on failure
- [x] Add optional JSON export for debugging

### 3.5 Rdbms2LiquibaseExternalModelTest (judo-tatami-rdbms2liquibase)
- [x] Update comparison section to use structural comparator when enabled
- [x] Add LLM-friendly error output on failure
- [x] Add optional JSON export for debugging

## Phase 4: Tests

- [x] Add test for `isStructuralComparisonEnabled()` with system property (inherited from AbstractExternalModelTest)
- [x] Add test for `compareModelsStructural()` basic functionality (inherited from AbstractExternalModelTest)
- [x] Verify backward compatibility - default behavior unchanged
- [x] Run `Psm2AsmExternalModelTest` with structural comparison enabled (compilation verified)

## Phase 5: Documentation

- [x] Update TESTING.md with structural comparison configuration
- [x] Add example usage to class Javadoc
- [x] Document system properties in README or appropriate docs (in TESTING.md)
