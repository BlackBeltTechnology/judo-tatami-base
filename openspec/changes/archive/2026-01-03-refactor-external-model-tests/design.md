# Design: External Model Test Framework

## Architecture Overview

```
judo-tatami-test-utils
├── AbstractExternalModelTest.java    # Base test class with common logic
├── ExternalModelConfig.java          # Model configuration record
└── ExternalModelTestSupport.java     # Helper utilities

judo-tatami-psm2asm
├── src/test/java/.../ExternalModelTest.java
└── src/test/resources/external-model-tests.properties

(similar structure for other transformation modules)
```

## Component Design

### ExternalModelConfig (Record)

```java
public record ExternalModelConfig(
    String modelName,           // e.g., "rackinspect"
    Path modelDirectory,        // Resolved absolute path
    boolean exists,             // Whether the directory exists
    Map<String, String> parameters  // Optional parameters (dialect, warmup, iterations)
) {
    public Path getModelFile(String modelType) {
        // e.g., returns rackinspect-psm.model for modelType="psm"
        return modelDirectory.resolve(modelName + "-" + modelType + ".model");
    }

    public String getDialect() {
        return parameters.getOrDefault("dialect", "hsqldb");
    }

    public boolean isWarmupEnabled() {
        return Boolean.parseBoolean(parameters.getOrDefault("warmup", "false"));
    }

    public int getIterations() {
        return Integer.parseInt(parameters.getOrDefault("iterations", "1"));
    }
}
```

### AbstractExternalModelTest

```java
@Tag("performance")
public abstract class AbstractExternalModelTest {

    // Load properties file from classpath, resolve paths relative to module base
    protected static Stream<ExternalModelConfig> loadModelConfigs(String propertiesFile, Class<?> testClass) {
        // 1. Load properties from classpath
        // 2. Parse extended format: modelName=path;param1=value1;param2=value2
        // 3. Resolve paths (absolute or relative to module base)
        // 4. Return stream of configs
    }

    // Resolve module base directory
    protected static Path resolveModuleBaseDir(Class<?> testClass) {
        // 1. Check system property: judo.test.module.root
        // 2. Use JUnit resource resolution: testClass.getResource("/") -> navigate to module root
        // 3. Fallback to user.dir
    }

    // Common test structure - no warmup by default, 1 iteration by default
    protected void executeExternalModelTest(
        ExternalModelConfig config,
        Function<Path, Object> modelLoader,
        BiFunction<Object, TransformationMode, Object> transformer,
        BiFunction<Object, Object, ModelComparator.ComparisonResult> comparator
    ) {
        // 1. Optional warmup (if config.isWarmupEnabled())
        // 2. Execute ETL transformation (config.getIterations() times)
        // 3. Execute ZETA transformation (config.getIterations() times)
        // 4. Compare results
        // 5. Report
    }

    // Reporting utilities
    protected void printResults(...) { }
}
```

### Module-Specific Test Implementation

```java
// Example: Psm2AsmExternalModelTest.java
public class Psm2AsmExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        executeExternalModelTest(
            config,
            path -> loadPsmModel(path),
            (model, mode) -> executeTransformation((PsmModel) model, mode),
            (etl, zeta) -> ModelComparator.compare(...)
        );
    }

    static Stream<ExternalModelConfig> externalModels() {
        return loadModelConfigs("external-model-tests.properties", Psm2AsmExternalModelTest.class);
    }

    private PsmModel loadPsmModel(Path modelFile) { ... }
    private AsmModel executeTransformation(PsmModel model, TransformationMode mode) { ... }
}

// Example: Asm2RdbmsExternalModelTest.java (uses dialect parameter)
public class Asm2RdbmsExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        String dialect = config.getDialect();  // From parameters or default "hsqldb"
        executeExternalModelTest(
            config,
            path -> loadAsmModel(path),
            (model, mode) -> executeTransformation((AsmModel) model, mode, dialect),
            (etl, zeta) -> ModelComparator.compare(...)
        );
    }
    ...
}
```

## Properties File Processing

### Resolution Order for Paths

1. If path starts with `/` - treat as absolute
2. Otherwise - resolve relative to module root directory
3. Use `Paths.get(moduleDir, relativePath).normalize().toAbsolutePath()`

### Example Properties File

```properties
# external-model-tests.properties
#
# Configure external models for ETL/ZETA comparison testing.
# Format: <model-name>=<path>[;<param>=<value>]*
#
# Path Resolution:
#   - Absolute paths: start with /
#   - Relative paths: resolved from module root
#
# Optional Parameters:
#   - dialect: Database dialect (default: hsqldb) - for RDBMS transformations
#   - warmup: Enable warmup run (default: false)
#   - iterations: Number of test iterations (default: 1)
#
# Expected model files in directory:
#   - <model-name>-psm.model (for PSM transformations)
#   - <model-name>-asm.model (for ASM transformations)
#   - <model-name>-rdbms.model (for RDBMS transformations)

# Simple format - RackInspect model (relative path)
rackinspect=../../../rackinspect/application/model/target/generated-resources/model

# Extended format with parameters
# myproject=/opt/projects/myproject/model;dialect=postgresql;warmup=true;iterations=3
```

## JUnit 5 Parametrized Test Structure

```java
@ParameterizedTest(name = "{0}")
@MethodSource("externalModels")
void testExternalModel(ExternalModelConfig config) {
    // Test implementation
}

static Stream<ExternalModelConfig> externalModels() {
    List<ExternalModelConfig> configs = loadModelConfigs("external-model-tests.properties");

    // Filter to only existing models
    return configs.stream()
        .filter(ExternalModelConfig::exists)
        .filter(c -> c.getModelFile("psm").toFile().exists());
}
```

## Test Skip Behavior

| Condition | Behavior |
|-----------|----------|
| Properties file missing | Test class skipped (0 test cases) |
| No entries in properties | Test class skipped (0 test cases) |
| Model directory not found | That specific test case skipped (with warning logged) |
| Model file missing in existing directory | **Test FAILS** with clear error message indicating expected file path |

The distinction: if the directory doesn't exist, we assume the model is not available and skip gracefully. But if the directory exists and the model file is missing, this is likely a configuration error and should fail explicitly.

## Reusability in External Projects

External projects can:

1. Add dependency on `judo-tatami-test-utils`
2. Create their own `ExternalModelTest` extending `AbstractExternalModelTest`
3. Provide `external-model-tests.properties` in their test resources
4. Override specific methods if needed (e.g., custom transformation setup)

## Performance Profile Integration

Tests are tagged with `@Tag("performance")` and excluded from normal builds:

```xml
<!-- pom.xml -->
<profiles>
    <profile>
        <id>performance</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <groups>performance</groups>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```
