# Testing Guide - ZETA Transformation Testing

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Profile performance | [PROFILING.md](PROFILING.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |

---

## Test Workflow

### Phase 1: Compile Check
```bash
mvn compile -pl judo-tatami-<module> -q
```

### Phase 2: Run Benchmark (Performance Profile)
```bash
mvn test -pl judo-tatami-<module> -Dtest=<Module>ExternalModelTest -Pperformance
```

### Phase 3: Strict Comparison with XMI IDs
```bash
mvn test -pl judo-tatami-<module> -Dtest=<Module>ExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true \
    -Pperformance
```

### Phase 4: Structural Comparison (Checksum-Based)
```bash
mvn test -pl judo-tatami-<module> -Dtest=<Module>ExternalModelTest \
    -Djudo.test.comparison.structural=true \
    -Djudo.test.structural.exportJson=true \
    -Pperformance
```

---

## Comparison Approaches

The test framework supports two complementary comparison approaches:

### 1. ModelComparator (Default)

The `ModelComparator` class provides comprehensive EMF model comparison with three modes:

| Mode | Description | Use Case |
|------|-------------|----------|
| **STRICT** | All attributes, references, and containments must match exactly. Annotations compared order-independently. | Final validation |
| **STRUCTURAL** | Element structure must match. Annotation differences tolerated. | Default mode |
| **LENIENT** | Most permissive. Basic structural comparison only. | Debugging |

**What ModelComparator checks:**
- **Attributes** - All `EAttribute` values compared using `Objects.equals()`
- **Containments** - All containment `EReference` features compared recursively
- **References** - Non-containment references compared by object identifier (order-independent)
- **Container hierarchy** - Implicitly validated through containment comparison

**Skipped features:**
- Derived features (computed values)
- Transient features
- In LENIENT mode: `documentation`, `comment`, `description`

### 2. StructuralModelComparator (Checksum-Based)

The `StructuralModelComparator` provides checksum-based structural comparison with optimizations:

| Feature | Description |
|---------|-------------|
| **Checksum-based subtree optimization** | Skip comparing identical subtrees when checksums match |
| **LLM-friendly output** | Structured XML output for automated analysis |
| **JSON export** | Export model structures for debugging and baseline comparison |

---

## System Properties

### Transformation Mode Properties

| Property | Default | Description |
|----------|---------|-------------|
| `judo.test.transformation.mode` | `DUAL` | Which engine to run: ZETA, ETL, or DUAL |

**Transformation mode values:**

| Value | Description | When to Use |
|-------|-------------|-------------|
| `ZETA` | Run only ZETA (Java) transformation | **Profiling**, production, fast iteration |
| `ETL` | Run only ETL (Epsilon) transformation | Legacy testing, debugging ETL issues |
| `DUAL` | Run both and compare results | Validation, parity testing (default) |

**Usage examples:**
```bash
# Fast ZETA-only test run
mvn test -Djudo.test.transformation.mode=ZETA -Pperformance

# Profiling (always use ZETA to avoid polluting metrics)
mvn test -Djudo.test.transformation.mode=ZETA -Djudo.test.profiler.enabled=true

# Full validation with comparison
mvn test -Djudo.test.transformation.mode=DUAL -Pperformance
```

### ModelComparator Properties

| Property | Default | Description |
|----------|---------|-------------|
| `judo.test.comparison.enabled` | `true` | Enable/disable comparison |
| `judo.test.comparison.mode` | `STRUCTURAL` | Comparison mode: STRICT, STRUCTURAL, LENIENT |
| `judo.test.comparison.maxDifferences` | `50` | Max differences to report |
| `judo.test.comparison.xmiIds` | `false` | Enable XMI ID comparison |
| `judo.test.comparison.reportFile` | - | Output file for diff report |

### StructuralModelComparator Properties

| Property | Default | Description |
|----------|---------|-------------|
| `judo.test.comparison.structural` | `false` | Enable structural comparison |
| `judo.test.structural.exportJson` | `false` | Export model structures to JSON |
| `judo.test.structural.outputDir` | `target/comparison` | Output directory for JSON exports |

---

## External Model Test Implementation

The `AbstractExternalModelTest` base class provides infrastructure for parametrized testing against external models.

### Test Configuration (properties file)

Create `src/test/resources/external-model-tests.properties`:

```properties
# Simple format
rackinspect=../../../../rackinspect/application/model/target/generated-resources/model

# Extended format with parameters
mymodel=/path/to/models;warmup=true;iterations=3;parallel=true;dialect=postgresql
```

### Implementing an External Model Test

Reference implementation: `Asm2RdbmsExternalModelTest.java`

```java
@Slf4j
@Tag("performance")
public class MyExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        // Skip if model directory doesn't exist
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("MY_TRANSFORM", config);

        // Load and transform with ETL
        MyModel etlResult = executeTransformation(loadModel(config), TransformationMode.ETL);

        // Load and transform with ZETA
        MyModel zetaResult = executeTransformation(loadModel(config), TransformationMode.ZETA);

        // Get resources for comparison
        var etlResource = etlResult.getResourceSet().getResources().get(0);
        var zetaResource = zetaResult.getResourceSet().getResources().get(0);

        // Export model structures if enabled
        exportModelStructures(etlResource, zetaResource, config.modelName() + "-mytransform");

        // Compare using appropriate method
        if (isStructuralComparisonEnabled()) {
            log.info("Using structural comparison (checksum-based)");
            ComparisonResult structuralResult = compareModelsStructural(etlResource, zetaResource);

            if (structuralResult.isMatch()) {
                log.info("SUCCESS: ETL and Zeta models are STRUCTURALLY EQUIVALENT");
            } else {
                log.error("Structural comparison found {} difference(s)",
                        structuralResult.getDifferenceCount());
                fail("ETL and Zeta models are not structurally equivalent");
            }
        } else {
            log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());
            ModelComparator.ComparisonResult result = ModelComparator.compare(
                    etlResource.getContents().get(0),
                    zetaResource.getContents().get(0),
                    ModelComparator.getConfiguredMode()
            );

            if (result.isEquivalent()) {
                log.info("SUCCESS: ETL and Zeta models are EQUIVALENT");
            } else {
                log.error("Models have {} difference(s):\n{}",
                        result.getDifferenceList().size(), result.getSummary());
                fail("ETL and Zeta models are not equivalent:\n" + result.getDetailedReport());
            }
        }
    }

    public static Stream<ExternalModelConfig> externalModels() {
        return loadModelConfigs(MyExternalModelTest.class);
    }
}
```

---

## Structural Model Comparison Utilities

For deep structural comparison of EMF models (e.g., ETL vs ZETA output), use the comparison utilities in `judo-tatami-test-utils`.

### Quick Start

```java
import hu.blackbelt.judo.tatami.test.util.comparison.*;

// Calculate structural checksums for both models
ModelChecksumCalculator calc = new ModelChecksumCalculator();
ModelNode etlModel = calc.calculate(etlResource);
ModelNode zetaModel = calc.calculate(zetaResource);

// Compare structures
StructuralModelComparator comparator = new StructuralModelComparator();
ComparisonResult result = comparator.compare(etlModel, zetaModel);

if (!result.isMatch()) {
    // Print differences
    for (Difference diff : result.getDifferences()) {
        System.out.println(diff);
    }

    // Or get LLM-friendly output
    String llmOutput = comparator.formatForLLM(result);
    System.out.println(llmOutput);
}
```

### Using AbstractExternalModelTest Helper Methods

```java
// Compare models using structural comparison
ComparisonResult result = compareModelsStructural(etlResource, zetaResource);

// Export model structures to JSON (if enabled via system property)
exportModelStructures(etlResource, zetaResource, "my-comparison");

// Check if structural comparison is enabled
if (isStructuralComparisonEnabled()) {
    // Use structural comparison
} else {
    // Use ModelComparator
}
```

### Ignoring Known Differences

```java
CalculatorOptions options = new CalculatorOptions()
    .ignore("*.EAnnotation#details")        // Ignore annotation details
    .ignore("ecore.EClass#abstract")        // Ignore specific attribute
    .ignore("*.*#derived");                 // Ignore all derived features

ModelNode model = calc.calculate(resource, options);
```

### Pattern Syntax for Ignoring Features

| Pattern | Matches |
|---------|---------|
| `ecore.EClass#name` | Exact match: EClass.name in ecore package |
| `*.EClass#name` | EClass.name in any package |
| `ecore.*#name` | Any class with "name" feature in ecore |
| `*.*#name` | "name" feature on any class |

### JSON Export/Import

```java
// Export model structure to JSON
String json = calc.toJson(etlModel);
Files.writeString(Path.of("model.json"), json);

// Load from JSON (no EObjects)
ModelNode loaded = calc.fromJson(json);

// Load from JSON with EObject re-matching
ModelNode matched = calc.fromJson(json, etlResource);
```

### Save/Load Comparison Results

```java
// Save comparison for later analysis
comparator.saveJson(result, Path.of("comparison.json"));

// Load previous comparison
ComparisonResult previous = comparator.loadJson(Path.of("baseline.json"));

// Compare difference counts for regression detection
if (result.getDifferenceCount() > previous.getDifferenceCount()) {
    System.out.println("Regression detected!");
}
```

### LLM-Friendly Output Format

The `formatForLLM()` method produces structured XML for easy parsing:

```xml
<model-comparison>
  <summary>
    <status>MISMATCH</status>
    <total-differences>3</total-differences>
    <breakdown>
      <missing>1</missing>
      <attribute_mismatch>2</attribute_mismatch>
    </breakdown>
  </summary>
  <differences>
    <difference type="MISSING">
      <path>EPackage:model/EClass:Customer</path>
      <description>Element missing: EClass:Customer</description>
      <context>
        <missing-element type="EClass" id="Customer"/>
      </context>
      <suggestion>Add the missing element to the actual model</suggestion>
    </difference>
    <!-- more differences... -->
  </differences>
</model-comparison>
```

### Difference Types

| Type | Description |
|------|-------------|
| `MISSING` | Element exists in expected but not in actual |
| `EXTRA` | Element exists in actual but not in expected |
| `ATTRIBUTE_MISMATCH` | Attribute values differ |
| `REFERENCE_MISMATCH` | Non-containment reference targets differ |
| `TYPE_MISMATCH` | Element types (EClass) differ |

### Access EObjects from Differences

```java
for (Difference diff : result.getDifferences(DifferenceType.ATTRIBUTE_MISMATCH)) {
    EObject expected = diff.getExpectedObject();
    EObject actual = diff.getActualObject();

    // Debug directly on the model elements
    System.out.println("Expected: " + expected);
    System.out.println("Actual: " + actual);
}
```

### Format Options

```java
FormatOptions options = new FormatOptions()
    .includeSuggestions(false)   // Disable fix suggestions
    .includeAnalysis(false)      // Disable detailed context
    .maxDifferences(10)          // Limit output size
    .prettyPrint(true);          // Enable indentation

String output = comparator.formatForLLM(result, options);
```

---

## Performance Expectations

| Metric | Expected Value | Warning Threshold |
|--------|---------------|-------------------|
| ZETA vs ETL Speedup | ~10-45x | < 5x indicates regression |
| Element Count Match | Exact | Any mismatch is failure |
| Transformation Time | Varies by model | Monitor for regressions |

---

## Debugging Test Failures

### Model Mismatch
1. Check post-processing steps
2. Verify extension type fixup ran
3. Look for null eType attributes
4. Enable structural comparison with JSON export for detailed analysis

### Performance Regression
1. Check guard evaluation count
2. Profile with JVM profiler (see [PROFILING.md](PROFILING.md))
3. Compare against baseline metrics

### XMI ID Mismatch
1. Ensure deterministic ID generation
2. Check for parallel execution race conditions
3. Verify post-processing applies IDs correctly

### Using JSON Export for Debugging

```bash
# Run with JSON export enabled
mvn test -pl judo-tatami-asm2rdbms -Dtest=Asm2RdbmsExternalModelTest \
    -Djudo.test.comparison.structural=true \
    -Djudo.test.structural.exportJson=true \
    -Djudo.test.structural.outputDir=target/debug-comparison \
    -Pperformance

# Compare JSON files
diff target/debug-comparison/*-expected.json target/debug-comparison/*-actual.json
```
