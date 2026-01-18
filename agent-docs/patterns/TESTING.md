# Testing Guide - PSM2ASM ZETA Transformation

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
mvn compile -pl judo-tatami-psm2asm -q
```

### Phase 2: Run Benchmark
```bash
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance
```

### Phase 3: Strict Comparison
```bash
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true \
    -Pperformance
```

---

## Comparison Modes

The `ModelComparator` class supports three comparison modes:

| Mode | Description | Use Case |
|------|-------------|----------|
| STRICT | All attributes and references must match exactly. Annotations compared order-independently. | Final validation |
| STRUCTURAL | Element structure must match. Annotation differences tolerated. | Default mode |
| LENIENT | Most permissive. Basic structural comparison only. | Debugging |

### Configuring Comparison Mode

```bash
# System property
-Djudo.test.comparison.mode=STRICT

# With XMI ID comparison (recommended for STRICT)
-Djudo.test.comparison.xmiIds=true
```

---

## Key Test Class

**Psm2AsmExternalModelTest.java**
- Location: `judo-tatami-psm2asm/src/test/java/.../perf/`
- Purpose: Benchmark ETL vs ZETA transformation
- Model: rackinspect (22,370 elements)

### Test Configuration

```java
// Parallel execution (recommended)
@Test
void compareWithZetaParallel() {
    // Uses parallel rule execution
    // Should produce identical results to sequential
}

// Sequential execution (for debugging)
@Test
void compareWithZetaSequential() {
    // Uses single-threaded execution
    // Useful when parallel execution has issues
}
```

---

## Performance Expectations

| Metric | Expected Value | Warning Threshold |
|--------|---------------|-------------------|
| ZETA vs ETL Speedup | ~7.5x | < 5x indicates regression |
| Guard Evaluations | ~80,695 | > 100k indicates problem |
| Transformation Time | ~5000ms | > 7000ms indicates regression |
| Post-Processing | ~479ms | > 700ms indicates problem |

---

## Debugging Test Failures

### Model Mismatch
1. Check post-processing steps (see [POST_PROCESSING.md](POST_PROCESSING.md))
2. Verify extension type fixup ran
3. Look for null eType attributes

### Performance Regression
1. Check guard evaluation count
2. Profile with JVM profiler (see [PROFILING.md](PROFILING.md))
3. Compare against baseline metrics

### XMI ID Mismatch
1. Ensure deterministic ID generation
2. Check for parallel execution race conditions
3. Verify post-processing applies IDs correctly

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
