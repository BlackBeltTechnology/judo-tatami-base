# Dual-Engine Testing Framework

This document describes the testing framework for verifying ETL and Zeta transformation equivalence.

## Overview

The dual-engine testing framework enables:
- Running tests with both ETL and Zeta engines
- Comparing output models for structural equivalence
- Performance benchmarking between engines

## Components

### TransformationMode Enum

The `TransformationMode` enum from `judo-tatami-core` is used for dual-engine testing:

```java
import hu.blackbelt.judo.tatami.core.TransformationMode;

public enum TransformationMode {
    /** Use Epsilon ETL transformation engine. */
    ETL,

    /** Use Zeta Java transformation engine. */
    ZETA;

    public boolean isZeta() {
        return this == ZETA;
    }
}
```

Location: `judo-tatami-core/src/main/java/hu/blackbelt/judo/tatami/core/TransformationMode.java`

### ModelComparator

Order-independent EMF model comparison utility.

Location: `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/ModelComparator.java`

### RealisticModelGenerator

Generates realistic test models for performance testing.

Location: `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/`

## Parameterized Test Pattern

### Basic Setup

```java
@ParameterizedTest
@EnumSource(TransformationMode.class)
void testTransformation(TransformationMode transformationMode) throws Exception {
    // Setup source model
    PsmModel psmModel = createTestModel();

    // Execute transformation based on mode
    AsmModel result;
    if (transformationMode.isZeta()) {
        result = runZetaTransformation(psmModel);
    } else {
        result = runEtlTransformation(psmModel);
    }

    // Verify results
    assertNotNull(result);
    verifyExpectedOutput(result);
}
```

### Using Work Classes

```java
@ParameterizedTest
@EnumSource(TransformationMode.class)
void testWithWorkClass(TransformationMode transformationMode) throws Exception {
    TransformationContext context = new TransformationContext("TestModel");
    context.put(psmModel);
    context.put(Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter()
            .transformationMode(transformationMode)
            .createTrace(true)
            .build());

    Psm2AsmWork work = new Psm2AsmWork(context);
    work.execute();

    AsmModel result = context.getByClass(AsmModel.class)
            .orElseThrow(() -> new IllegalStateException("ASM Model not found"));

    // Verify
    verifyResult(result);
}
```

## ModelComparator Usage

### Basic Comparison

```java
// Assert two resources are equivalent
ModelComparator.assertEquivalent(
    etlResult.getResource(),
    zetaResult.getResource()
);
```

### With Comparison Mode

```java
ModelComparator.assertEquivalent(
    etlResult.getResource(),
    zetaResult.getResource(),
    ModelComparator.ComparisonMode.STRUCTURAL
);
```

### Detailed Comparison

```java
ComparisonResult result = ModelComparator.compare(
    etlRoot,
    zetaRoot,
    ModelComparator.ComparisonMode.STRUCTURAL
);

if (!result.isEquivalent()) {
    log.warn("Differences found: {}", result.getDifferenceCount());
    for (Difference diff : result.getDifferenceList()) {
        log.warn("  - {}", diff.describe());
    }
}
```

## Comparison Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `STRICT` | All attributes and references must match exactly | Final validation |
| `STRUCTURAL` | Element structure must match, annotation differences tolerated | Development |
| `LENIENT` | Major structural elements must match, minor differences allowed | Initial migration |

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `judo.test.comparison.enabled` | `true` | Enable/disable comparison |
| `judo.test.comparison.mode` | `STRUCTURAL` | Comparison strictness |
| `judo.test.comparison.maxDifferences` | `50` | Max differences to report |
| `judo.test.comparison.reportFile` | `null` | Output file for diff report |

### Setting Properties

```bash
# Via command line
mvn test -Djudo.test.comparison.mode=STRICT

# Via surefire plugin
<configuration>
    <systemPropertyVariables>
        <judo.test.comparison.mode>STRUCTURAL</judo.test.comparison.mode>
    </systemPropertyVariables>
</configuration>
```

## Performance Testing

### RealisticPerformanceTest Pattern

```java
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    private final RealisticPsmModelGenerator generator = new RealisticPsmModelGenerator();

    @Test
    void testRealisticModelPerformance() throws Exception {
        runPerformanceTest(70, "RackInspect-like");
    }

    private void runPerformanceTest(int entityCount, String testName) throws Exception {
        // Generate model
        PsmModel psmModel = generator.generate(entityCount);

        // Warmup
        executeTransformation(psmModel, TransformationMode.ETL);
        executeTransformation(psmModel, TransformationMode.ZETA);

        // ETL measurement
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = executeTransformation(psmModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;

        // Zeta measurement
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = executeTransformation(psmModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;

        // Compare results
        compareModels(etlResult, zetaResult);

        // Report
        log.info("ETL: {}ms, Zeta: {}ms", etlTime, zetaTime);
    }
}
```

### Running Performance Tests

```bash
# Run with performance profile
mvn test -pl judo-tatami-psm2asm -Dtest=RealisticPerformanceTest -Pperformance

# Run all performance tests
mvn test -Pperformance
```

## Test Organization

```
src/test/java/hu/blackbelt/judo/tatami/<module>/
├── <Module>Test.java                 # Main transformation tests (uses TransformationMode from judo-tatami-core)
├── <Module>WorkTest.java             # Work class tests
├── <SpecificFeature>Test.java        # Feature-specific tests
└── perf/
    ├── RealisticPerformanceTest.java # Realistic model performance
    └── <Module>PerformanceTest.java  # Module-specific performance
```

Note: Tests use `TransformationMode` from `hu.blackbelt.judo.tatami.core.TransformationMode` instead of module-local enums.

## Equivalence Test Pattern

Test that both engines produce identical output:

```java
@Test
void testEtlZetaEquivalence() throws Exception {
    // Setup
    PsmModel psmModel = createTestModel();

    // Run ETL
    AsmModel etlResult = runEtlTransformation(psmModel);

    // Reset and run Zeta
    PsmModel psmModel2 = createTestModel();  // Fresh model
    AsmModel zetaResult = runZetaTransformation(psmModel2);

    // Compare
    if (!etlResult.getResourceSet().getResources().isEmpty() &&
        !zetaResult.getResourceSet().getResources().isEmpty()) {

        EObject etlRoot = etlResult.getResource().getContents().get(0);
        EObject zetaRoot = zetaResult.getResource().getContents().get(0);

        ComparisonResult result = ModelComparator.compare(
            etlRoot, zetaRoot, ComparisonMode.STRUCTURAL);

        assertTrue(result.isEquivalent(),
            "ETL and Zeta outputs differ:\n" + result.getDetailedReport());
    }
}
```

## Difference Types

The ModelComparator reports these difference types:

| Type | Description | Example |
|------|-------------|---------|
| `MissingElement` | Element in expected but not actual | `path: missing element 'EClass:Customer'` |
| `ExtraElement` | Element in actual but not expected | `path: unexpected element 'EClass:Extra'` |
| `ValueMismatch` | Attribute values differ | `name: 'Customer' vs 'customer'` |
| `TypeMismatch` | Element types differ | `type mismatch - expected EClass but was EDataType` |

## Best Practices

### 1. Use Fresh Models

Always create fresh source models for each engine to avoid state contamination:

```java
PsmModel psmModelForEtl = createTestModel();
AsmModel etlResult = runEtl(psmModelForEtl);

PsmModel psmModelForZeta = createTestModel();
AsmModel zetaResult = runZeta(psmModelForZeta);
```

### 2. Clear Caches Between Runs

```java
@BeforeEach
void setup() {
    Psm2AsmHelper.clearCaches();
}
```

### 3. Use Meaningful Test Names

```java
@ParameterizedTest(name = "{0}: Entity with inheritance")
@EnumSource(TransformationMode.class)
void testEntityInheritance(TransformationMode mode) { ... }
```

### 4. Report Differences Clearly

```java
if (!result.isEquivalent()) {
    StringBuilder sb = new StringBuilder();
    sb.append("Found ").append(result.getDifferenceCount()).append(" differences:\n");
    for (Difference diff : result.getDifferenceList()) {
        sb.append("  - ").append(diff.describe()).append("\n");
    }
    fail(sb.toString());
}
```

## Troubleshooting

### Tests Pass for ETL but Fail for Zeta

1. Check rule registration order
2. Verify post-processing handles all cross-references
3. Compare generated XMI IDs

### Comparison Reports False Positives

1. Check comparison mode (use STRUCTURAL for development)
2. Verify element ordering doesn't affect logic
3. Check for annotation differences (tolerated in STRUCTURAL mode)

### Performance Tests Show Large Variance

1. Increase warmup iterations
2. Run in isolation (no other tests)
3. Use consistent model sizes
