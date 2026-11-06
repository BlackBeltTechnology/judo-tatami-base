# Refactor RackInspect Tests to External Model Tests

## Summary

Rename and refactor `RackInspect*PerformanceTest` classes across all transformation modules to `ExternalModelTest`, making them parametrized tests that consume external model configurations from a properties file. This enables reusable testing with any external model without code changes.

## Motivation

1. **Hardcoded Paths**: Current tests have hardcoded absolute paths to RackInspect models
2. **Single Model**: Only tests one specific external model (RackInspect)
3. **Not Reusable**: Other projects using `judo-tatami-test-utils` cannot benefit from this testing pattern
4. **Manual Configuration**: Adding new models requires code changes

## Approach

### Configuration File Format

Each transformation module will optionally contain an `external-model-tests.properties` file in `src/test/resources/`:

```properties
# Format: <model-name>=<path>[;<param>=<value>]*
# Path can be absolute or relative to the module root
# Optional parameters: dialect, warmup, iterations

# Simple format
rackinspect=../../../rackinspect/application/model/target/generated-resources/model

# Extended format with parameters
myproject=/path/to/models;dialect=postgresql;warmup=true;iterations=3
```

### Path Resolution

Paths are resolved in order:
1. If path starts with `/` - treat as absolute
2. Otherwise - resolve relative to module base directory
3. Module base directory detected via:
   - JUnit's resource resolution from test classpath
   - System property `judo.test.module.root` (override)

### Default Execution Behavior

- **Warmup**: Disabled by default (can enable per-model with `warmup=true`)
- **Iterations**: 1 execution by default (can increase with `iterations=N`)

### Base Test Class in judo-tatami-test-utils

Create `AbstractExternalModelTest` in `judo-tatami-test-utils` providing:
- Properties file loading and parsing
- Parametrized test execution infrastructure
- Common model loading utilities
- Model comparison and reporting

### Module-Specific Implementations

Each transformation module implements a concrete test class extending the base (module-specific naming):
- `Psm2AsmExternalModelTest` - transforms PSM models, expects `*-psm.model`
- `Asm2RdbmsExternalModelTest` - transforms ASM models, expects `*-asm.model`, supports `dialect` parameter
- `Rdbms2LiquibaseExternalModelTest` - transforms RDBMS models, expects `*-rdbms.model`, supports `dialect` parameter
- `Psm2MeasureExternalModelTest` - transforms PSM models, expects `*-psm.model`
- `Asm2KeycloakExternalModelTest` - transforms ASM models, expects `*-asm.model`

### Model File Naming Convention

Given a model name `rackinspect` and directory path, the expected model files are:
- PSM: `<directory>/rackinspect-psm.model`
- ASM: `<directory>/rackinspect-asm.model`
- RDBMS: `<directory>/rackinspect-rdbms.model`
- Measure: `<directory>/rackinspect-measure.model`

### Test Execution

```bash
# Run external model tests for a specific module
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance

# Run with specific comparison mode
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance -Djudo.test.comparison.mode=STRICT

# Run all external model tests across modules
mvn test -Pperformance -Dtest=*ExternalModelTest

# Override module base directory
mvn test -Pperformance -Dtest=*ExternalModelTest -Djudo.test.module.root=/path/to/module
```

## Scope

### Affected Modules
- `judo-tatami-test-utils` - new base class
- `judo-tatami-psm2asm` - rename and refactor test
- `judo-tatami-asm2rdbms` - rename and refactor test
- `judo-tatami-rdbms2liquibase` - rename and refactor test
- `judo-tatami-psm2measure` - rename and refactor test
- `judo-tatami-asm2keycloak` - rename and refactor test

### Files to Create
- `judo-tatami-test-utils/.../AbstractExternalModelTest.java`
- `judo-tatami-test-utils/.../ExternalModelConfig.java`
- `*/src/test/resources/external-model-tests.properties` (per module)
- `README.md` updates for documentation

### Files to Delete
- All `RackInspectPerformanceTest.java` files (replaced by `ExternalModelTest.java`)

## Impact

- **Backward Compatible**: New feature, does not break existing functionality
- **Reusable**: Any project depending on `judo-tatami-test-utils` can use the same pattern
- **Configurable**: Users can add/remove models without code changes
- **CI/CD Friendly**: Tests skip gracefully when model files don't exist

## Risks

| Risk | Mitigation |
|------|------------|
| Properties file not found | Skip tests gracefully with warning |
| Model directory not found | Skip that specific model with warning |
| Model file missing | Clear error message indicating expected file name |

## Related Specifications

- `external-model-testing` - new capability spec
