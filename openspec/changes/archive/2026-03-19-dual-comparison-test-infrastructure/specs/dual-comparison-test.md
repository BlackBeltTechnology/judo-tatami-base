# Spec: AbstractDualComparisonTest and PerformanceMeasurement

## PerformanceMeasurement

**Package:** `hu.blackbelt.judo.tatami.test.util`
**File:** `PerformanceMeasurement.java`

### Requirements

- `TimedResult<T>` record with `result()` and `durationMs()` fields
- `measure(Callable<T>)` — executes once, returns TimedResult with wall-clock duration
- `measureAvg(Callable<T>, int iterations)` — executes N times, returns last result with average duration
- Uses `System.currentTimeMillis()` for timing (consistent with existing codebase)
- No dependencies beyond `java.util.concurrent.Callable`

## AbstractDualComparisonTest<S, T>

**Package:** `hu.blackbelt.judo.tatami.test.util`
**File:** `AbstractDualComparisonTest.java`
**Extends:** `AbstractExternalModelTest`

### Abstract methods (subclass must implement)

| Method | Returns | Description |
|---|---|---|
| `parseSource(ExternalModelConfig)` | `S` | Load source model from config |
| `executeEtl(S)` | `T` | Run ETL transformation |
| `executeZeta(S)` | `T` | Run ZETA transformation |
| `getResource(T)` | `Resource` | Extract EMF Resource from target model |
| `getModuleName()` | `String` | Module name for reporting (e.g., "jsl2psm") |

### Optional overrides

| Method | Default | Description |
|---|---|---|
| `getOutputLabel()` | `"elements"` | Label for element count in reports |
| `getPropertiesFile()` | `"external-model-tests.properties"` | Properties file for model discovery |
| `getComparisonMode()` | from `judo.test.comparison.mode` sysprop, else `STRICT` | ModelComparator mode |
| `shouldFailOnDiff()` | `false` | Whether to fail test on comparison mismatch |

### Provided methods

**`@TestFactory Collection<DynamicTest> compareExternalModels()`**

Orchestration:
1. Load models via `loadModelConfigs(getPropertiesFile(), getClass())`
2. Skip if no models found (`Assumptions.assumeTrue`)
3. For each `ExternalModelConfig`:
   a. Check model exists (`checkModelExists`)
   b. Warmup if `config.isWarmupEnabled()`: parse + ETL + parse + ZETA (discarded)
   c. ETL: `parseSource(config)` → `PerformanceMeasurement.measure(executeEtl)` → `TimedResult<T>`
   d. ZETA: `parseSource(config)` → `PerformanceMeasurement.measureAvg(executeZeta, config.getIterations())` → `TimedResult<T>`
   e. Count elements via `countElements(getResource(etl).getResourceSet())` and same for zeta
   f. Compare: `ModelComparator.compare(getResource(etl), getResource(zeta), getComparisonMode())`
   g. Record result via `recordResult(...)`
   h. If `shouldFailOnDiff()` and not equivalent → `fail()` with difference report
4. Final "== Summary ==" dynamic test: `printSummary(getModuleName())` + `writeJsonResults(getModuleName(), Path.of("target"))`

**`assertDualEquivalent(S source, String testName)`**

For inline @Test methods:
1. `executeEtl(source)` → `T etlResult`
2. `executeZeta(source)` → `T zetaResult`
3. Compare: `ModelComparator.compare(getResource(etlResult), getResource(zetaResult), getComparisonMode())`
4. If not equivalent → `fail()` with detailed report

**`assertDualEquivalent(Supplier<S> sourceFactory, String testName)`**

Safe variant for mutable sources:
1. `executeEtl(sourceFactory.get())` → `T etlResult`
2. `executeZeta(sourceFactory.get())` → `T zetaResult`
3. Same comparison and failure as above

### Error handling

- `parseSource()` exceptions: wrapped in `Assumptions.assumeTrue(false, msg)` — test skipped, not failed
- `executeEtl()`/`executeZeta()` exceptions: propagated to JUnit (test fails)
- Empty models (both ETL and ZETA produce 0 elements): recorded as EQUIVALENT
