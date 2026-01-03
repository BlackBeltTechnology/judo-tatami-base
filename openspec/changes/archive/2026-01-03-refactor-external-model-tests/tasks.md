# Tasks for refactor-external-model-tests

## Phase 1: Core Framework in judo-tatami-test-utils

- [x] **1.1 Create ExternalModelConfig record**
  - File: `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/ExternalModelConfig.java`
  - Fields: modelName, modelDirectory (Path), exists (boolean), parameters (Map<String, String>)
  - Method: `getModelFile(String modelType)` returns path to specific model file
  - Method: `getDialect()` returns dialect parameter or default "hsqldb"
  - Method: `isWarmupEnabled()` returns warmup parameter or default false
  - Method: `getIterations()` returns iterations parameter or default 1
  - Override `toString()` to return modelName (for test naming)

- [x] **1.2 Create AbstractExternalModelTest base class**
  - File: `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/AbstractExternalModelTest.java`
  - Static method: `loadModelConfigs(String propertiesFile, Class<?> testClass)` returns `Stream<ExternalModelConfig>`
  - Static method: `resolveModuleBaseDir(Class<?> testClass)` - uses JUnit resource resolution + system property override
  - Protected method: `printResults(...)` for formatted output
  - Protected method: `countElements(ResourceSet)` for statistics
  - Protected method: `checkModelExists(config)` for skip behavior
  - Default behavior: No warmup, 1 iteration
  - Note: `@Tag("performance")` on concrete subclasses (not on abstract class)

- [x] **1.3 Add path and parameter parsing logic**
  - Parse extended format: `modelName=path;param1=value1;param2=value2`
  - Resolve relative paths from module root (detect via JUnit classpath resolution)
  - Support system property override: `judo.test.module.root`
  - Support absolute paths (start with `/`)
  - Normalize and canonicalize paths

## Phase 2: Module-Specific Implementations

- [x] **2.1 judo-tatami-psm2asm Psm2AsmExternalModelTest**
  - Renamed `RackInspectPerformanceTest.java` to `Psm2AsmExternalModelTest.java`
  - Extends `AbstractExternalModelTest`
  - Implements `@ParameterizedTest(name = "{0}")` with `@MethodSource("externalModels")`
  - Model type: PSM (`*-psm.model`)
  - Created `src/test/resources/external-model-tests.properties`

- [x] **2.2 judo-tatami-asm2rdbms Asm2RdbmsExternalModelTest**
  - Renamed `RackInspectPerformanceTest.java` to `Asm2RdbmsExternalModelTest.java`
  - Extends `AbstractExternalModelTest`
  - Model type: ASM (`*-asm.model`)
  - Uses `config.getDialect()` for database dialect parameter
  - Created `src/test/resources/external-model-tests.properties`

- [x] **2.3 judo-tatami-rdbms2liquibase Rdbms2LiquibaseExternalModelTest**
  - Renamed `RackInspectPerformanceTest.java` to `Rdbms2LiquibaseExternalModelTest.java`
  - Extends `AbstractExternalModelTest`
  - Model type: RDBMS (`*-rdbms_<dialect>.model`)
  - Uses `config.getDialect()` for database dialect parameter
  - Created `src/test/resources/external-model-tests.properties`

- [x] **2.4 judo-tatami-psm2measure Psm2MeasureExternalModelTest**
  - Renamed `RackInspectPerformanceTest.java` to `Psm2MeasureExternalModelTest.java`
  - Extends `AbstractExternalModelTest`
  - Model type: PSM (`*-psm.model`)
  - Created `src/test/resources/external-model-tests.properties`

- [x] **2.5 judo-tatami-asm2keycloak Asm2KeycloakExternalModelTest**
  - Renamed `RackInspectPerformanceTest.java` to `Asm2KeycloakExternalModelTest.java`
  - Extends `AbstractExternalModelTest`
  - Model type: ASM (`*-asm.model`)
  - Created `src/test/resources/external-model-tests.properties`

## Phase 3: Documentation

- [x] **3.1 Update README.md**
  - Added section "External Model Testing"
  - Documented properties file format
  - Documented model file naming convention
  - Documented execution commands
  - Documented comparison modes

- [x] **3.2 Add example properties file template**
  - Properties files created in each module serve as examples
  - Include commented examples for different path styles

## Phase 4: Cleanup and Validation

- [x] **4.1 Delete old RackInspect test files**
  - All `RackInspectPerformanceTest.java` files deleted
  - Replaced by new `*ExternalModelTest.java` files

- [x] **4.2 Run all external model tests**
  - Verified compilation: `mvn compile test-compile` - SUCCESS
  - Verified skip behavior when model directory doesn't exist

- [x] **4.3 Verify graceful skip behavior**
  - Test with non-existent model directory: SKIPPED (1 test skipped via `Assumptions.assumeTrue`)
  - Warning logged: "Model directory does not exist, skipping: ..."

- [x] **4.4 Verify fail behavior**
  - Test with existing directory but missing model file: FAIL with clear error message
  - Uses `assertTrue()` with descriptive message indicating expected file name

## Dependencies

- Phase 1 must complete before Phase 2
- Phase 2 tasks (2.1-2.5) can be done in parallel
- Phase 3 can start after Phase 2.1 is complete
- Phase 4 requires all other phases complete

## Notes

- All tests are tagged with `@Tag("performance")` and excluded from normal builds
- Existing test functionality (warmup, timing, comparison) is preserved
- Properties file uses standard Java Properties format (key=value, # comments)
- Skip behavior uses JUnit 5 `Assumptions.assumeTrue()` for graceful handling

## Implementation Summary

### New Files Created:
- `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/ExternalModelConfig.java`
- `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/AbstractExternalModelTest.java`
- `judo-tatami-psm2asm/src/test/java/.../perf/Psm2AsmExternalModelTest.java`
- `judo-tatami-psm2asm/src/test/resources/external-model-tests.properties`
- `judo-tatami-asm2rdbms/src/test/java/.../perf/Asm2RdbmsExternalModelTest.java`
- `judo-tatami-asm2rdbms/src/test/resources/external-model-tests.properties`
- `judo-tatami-rdbms2liquibase/src/test/java/.../perf/Rdbms2LiquibaseExternalModelTest.java`
- `judo-tatami-rdbms2liquibase/src/test/resources/external-model-tests.properties`
- `judo-tatami-psm2measure/src/test/java/.../perf/Psm2MeasureExternalModelTest.java`
- `judo-tatami-psm2measure/src/test/resources/external-model-tests.properties`
- `judo-tatami-asm2keycloak/src/test/java/.../perf/Asm2KeycloakExternalModelTest.java`
- `judo-tatami-asm2keycloak/src/test/resources/external-model-tests.properties`

### Files Deleted:
- `judo-tatami-psm2asm/src/test/java/.../perf/RackInspectPerformanceTest.java`
- `judo-tatami-asm2rdbms/src/test/java/.../perf/RackInspectPerformanceTest.java`
- `judo-tatami-rdbms2liquibase/src/test/java/.../perf/RackInspectPerformanceTest.java`
- `judo-tatami-psm2measure/src/test/java/.../perf/RackInspectPerformanceTest.java`
- `judo-tatami-asm2keycloak/src/test/java/.../perf/RackInspectPerformanceTest.java`

### Files Modified:
- `README.md` - Added "External Model Testing" section
