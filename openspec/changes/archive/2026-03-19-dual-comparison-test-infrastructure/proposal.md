# Proposal: Generalized Dual Comparison Test Infrastructure

## Problem

Every transformation module in `judo-tatami-jsl` duplicates ~300-460 LOC of dual comparison test infrastructure (ETL vs ZETA). Five test classes independently re-implement the same patterns:

| Test Class | Module | LOC | Pattern |
|---|---|---|---|
| `Jsl2PsmDualTransformationTest` | jsl2psm | 1330 | Inline @Test + comparison |
| `Jsl2PsmDiscoveryComparisonTest` | jsl2psm | 462 | @TestFactory + external models |
| `Jsl2UiDualTransformationTest` | jsl2ui | 625 | Inline @Test + comparison |
| `Jsl2UiExternalDualComparisonTest` | jsl2ui | 290 | @TestFactory + external models |
| `Jsl2UiDiscoveryComparisonTest` | jsl2ui | 458 | @TestFactory + external models |

Duplicated code across these classes:

- `ModelConfig` inner class (properties parsing) — `ExternalModelConfig` already exists
- `TestResult` record (timing + comparison result) — `AbstractExternalModelTest.TestResult` already exists
- `printSummary()` (~40 LOC) — already in `AbstractExternalModelTest`
- `writeJsonResults()` (~50 LOC) — already in `AbstractExternalModelTest`
- `countElements()` — already in `AbstractExternalModelTest`
- `loadModelConfigs()` / `discoverJslFiles()` — already in `AbstractExternalModelTest`
- `getZetaIterations()` / `isWarmupEnabled()` — already in `ExternalModelConfig.parameters`
- `truncate()` / `escapeJson()` — already in `AbstractExternalModelTest`
- Timing measurement boilerplate
- @TestFactory orchestration (warmup → ETL timed → ZETA timed N iterations → compare → record)

None of these discovery test classes extend `AbstractExternalModelTest`, despite it providing all of the above infrastructure. They predate it and were never migrated.

## Goal

Extract a generic `AbstractDualComparisonTest<S, T>` base class into `judo-tatami-test-utils` that:

1. **Extends** `AbstractExternalModelTest` — reuses its TestResult, reporting, discovery, and config infrastructure
2. **Is generic** with `<S, T>` — no JSL/PSM/UI knowledge
3. **Provides the @TestFactory orchestration** — warmup, timed ETL, timed ZETA (N iterations), comparison, recording
4. **Supports inline @Test methods** via `assertDualEquivalent(S source, String name)` convenience
5. **Reduces per-module code** from ~300-460 LOC to ~30-50 LOC

## Architecture

### Class hierarchy

```
┌──────────────────────────────────────────────────────────────────┐
│                     AbstractExternalModelTest                     │
│   (existing 1045 LOC — TestResult, printSummary,                 │
│    writeJsonResults, loadModelConfigs, ExternalModelConfig,       │
│    countElements, structural comparison, transformation mode)     │
└───────────────────────┬──────────────────────────────────────────┘
                        │ extends
┌───────────────────────┴──────────────────────────────────────────┐
│              AbstractDualComparisonTest<S, T>  (NEW ~150 LOC)    │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ABSTRACT (subclass must implement):                              │
│  • S parseSource(ExternalModelConfig config)                     │
│  • T executeEtl(S source)                                        │
│  • T executeZeta(S source)                                       │
│  • Resource getResource(T model)                                 │
│  • String getModuleName()                                        │
│                                                                   │
│  OPTIONAL OVERRIDES:                                              │
│  • String getOutputLabel()           // default: "elements"       │
│  • String getPropertiesFile()        // default from parent       │
│  • ComparisonMode getComparisonMode()// default from sysprop      │
│  • boolean shouldFailOnDiff()        // default: false (log only) │
│                                                                   │
│  PROVIDED:                                                        │
│  • @TestFactory compareExternalModels()                           │
│  • assertDualEquivalent(S source, String name) — for inline tests│
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### New files

| File | Role | LOC (est) |
|---|---|---|
| `AbstractDualComparisonTest.java` | Generic base class with @TestFactory + assertDualEquivalent | ~150 |
| `PerformanceMeasurement.java` | Standalone timing utility (measure + measureAvg) | ~40 |

Both in `hu.blackbelt.judo.tatami.test.util` package within `judo-tatami-test-utils`.

### Design decisions

**Extends `AbstractExternalModelTest` (not parallel)**
The original proposal created 6 parallel classes. Investigation revealed that `AbstractExternalModelTest` already provides TestResult, printSummary, writeJsonResults, loadModelConfigs, ExternalModelConfig, countElements, and more. The discovery tests simply predate it and were never migrated. Extending it eliminates 4 of the 6 proposed classes.

**`getResource(T)` returns `Resource` (not `getRoot()` → `EObject`)**
`ModelComparator.compare(Resource, Resource)` already does identity-based root element matching. Returning `Resource` keeps this capability and avoids forcing a specific root element selection strategy.

**`parseSource()` called twice internally by `assertDualEquivalent`**
EMF transformations can mutate the input model. The base class calls `parseSource()` separately for ETL and ZETA to guarantee isolation. Discovery tests already do this pattern explicitly.

**`PerformanceMeasurement` as standalone utility**
Timing measurement is useful outside dual comparison tests (e.g., single-engine benchmarks). Keeping it as a standalone utility with `TimedResult<T>` record avoids coupling.

**`behaviours` and other per-model flags via `ExternalModelConfig.parameters`**
No new config class needed. The existing `ExternalModelConfig.parameters` map already supports `behaviours=true`, `companions=...`, `dialect=postgresql`, `warmup=true`, `iterations=3`. Subclasses read these in their `executeEtl`/`executeZeta` implementations.

### @TestFactory orchestration (provided by base class)

```
For each ExternalModelConfig:
  1. parseSource(config) — validate model loads
  2. if warmup enabled:
       parseSource → executeEtl (discard)
       parseSource → executeZeta (discard)
  3. ETL: parseSource → measure(executeEtl) → TimedResult<T>
  4. ZETA: parseSource → measureAvg(executeZeta, iterations) → TimedResult<T>
  5. Compare: ModelComparator.compare(getResource(etl), getResource(zeta), mode)
  6. recordResult(...) → TestResult added to collection
  7. if shouldFailOnDiff() && not equivalent → fail()

Final "== Summary ==" dynamic test:
  - printSummary(moduleName)
  - writeJsonResults(moduleName, target/)
```

## Consumer examples

### Discovery test (~50 LOC, was 462)

```java
@Tag("performance")
public class Jsl2PsmDiscoveryComparisonTest
    extends AbstractDualComparisonTest<JslDslModel, PsmModel> {

    @Override protected String getModuleName() { return "jsl2psm"; }
    @Override protected String getOutputLabel() { return "PSM elements"; }

    @Override protected JslDslModel parseSource(ExternalModelConfig config) throws Exception {
        return JslParser.getModelFromFiles(resolveJslFiles(config));
    }

    @Override protected PsmModel executeEtl(JslDslModel m) throws Exception {
        PsmModel psm = buildPsmModel().build();
        executeJsl2PsmTransformation(jsl2PsmParameter()
            .jslModel(m).psmModel(psm).createTrace(false)
            .parallel(true).useCache(true));
        return psm;
    }

    @Override protected PsmModel executeZeta(JslDslModel m) {
        PsmModel psm = buildPsmModel().build();
        Jsl2PsmZetaTransformation.builder()
            .jslModel(m).psmModel(psm).defaultModelName(m.getName())
            .build().execute();
        return psm;
    }

    @Override protected Resource getResource(PsmModel m) {
        return m.getResourceSet().getResources().get(0);
    }

    private List<File> resolveJslFiles(ExternalModelConfig config) {
        // ~15 LOC JSL companion file resolution
    }
}
```

### Inline dual test (uses assertDualEquivalent)

```java
class Jsl2UiDualTransformationTest
    extends AbstractDualComparisonTest<JslDslModel, UiModel> {

    // Same 5 abstract methods as discovery test above
    @Override protected boolean shouldFailOnDiff() { return true; }

    @Test void testSimpleActor() throws Exception {
        JslDslModel model = JslParser.getModelFromStrings("Test", List.of("""
            model Test;
            entity E { field String name; }
            view EView(E e) { group g { field String name <= e.name; } }
            actor TestActor { menu EMenu(E[] e) { table ETable(EView ev); } }
        """));
        assertDualEquivalent(model, "testSimpleActor");
    }
}
```

## Not in scope

- **`FullPipelineComparisonTest`** — different pattern (multi-stage pipeline, not single transformation). Stays as-is.
- **`Psm2AsmDualTransformationTest`** — can be migrated to the new base later, but not required in this change.
- **Modifying `AbstractExternalModelTest`** — the base class is used as-is. No changes needed.

## Implementation order

1. `PerformanceMeasurement` — standalone, no dependencies
2. `AbstractDualComparisonTest<S, T>` — extends `AbstractExternalModelTest`, uses `PerformanceMeasurement`
3. Unit tests for both classes in `judo-tatami-test-utils`
4. Migrate `Jsl2PsmDiscoveryComparisonTest` (in `judo-tatami-jsl`) — validate the API
5. Migrate `Jsl2UiDiscoveryComparisonTest` (in `judo-tatami-jsl`)
6. Migrate `Jsl2UiExternalDualComparisonTest` (in `judo-tatami-jsl`)
7. Optionally: refactor inline dual tests to use `assertDualEquivalent`

## Verification

```bash
# Build test-utils with new classes
mvn test -pl judo-tatami-test-utils

# Verify migration in jsl2psm
cd ../judo-tatami-jsl
mvn test -pl judo-tatami-jsl-jsl2psm -Pperformance \
    -Dtest=Jsl2PsmDiscoveryComparisonTest

# Verify migration in jsl2ui
mvn test -pl judo-tatami-jsl-jsl2ui -Pperformance \
    -Dtest=Jsl2UiDiscoveryComparisonTest
```

## Expected impact

| Metric | Before | After |
|---|---|---|
| Discovery test LOC (per module) | 300-460 | ~50 |
| Total duplicated LOC eliminated | ~1200 | 0 |
| New shared code added | 0 | ~190 |
| Classes consuming `AbstractExternalModelTest` | 0 (of discovery tests) | 3-5 |
