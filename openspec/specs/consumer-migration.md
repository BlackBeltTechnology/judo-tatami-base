# Spec: Consumer Module Migration to AbstractDualComparisonTest

This spec is designed to be self-contained so that an LLM agent working in any consumer repository can independently apply the migration without needing to read other files.

## Background

`judo-tatami-test-utils` (in `judo-tatami-base`) now provides:

- **`AbstractDualComparisonTest<S, T>`** — generic base class extending `AbstractExternalModelTest` that provides `@TestFactory compareExternalModels()` orchestration (warmup, timed ETL, timed ZETA with N iterations, ModelComparator comparison, result recording, summary table, JSON export) and `assertDualEquivalent(S, String)` / `assertDualEquivalent(Supplier<S>, String)` for inline `@Test` methods.

- **`PerformanceMeasurement`** — standalone timing utility with `TimedResult<T>` record, `measure(Callable<T>)`, and `measureAvg(Callable<T>, int)`.

### What AbstractDualComparisonTest provides (inherited from AbstractExternalModelTest)

```
loadModelConfigs(propertiesFile, class) — 3-tier model discovery (properties + search dirs + basedir)
ExternalModelConfig — model config record with parameters map (warmup, iterations, dialect, behaviours, companions)
TestResult — 9-field record (modelName, etlTimeMs, zetaTimeMs, speedup, etlOutputCount, zetaOutputCount, outputLabel, comparisonResult, differenceCount)
recordResult(...) / clearResults() / getResults()
printSummary(moduleName) — formatted table via SLF4J
writeJsonResults(moduleName, targetDir) — hand-rolled JSON output
countElements(ResourceSet) — recursive element counter
logTestHeader(testName, config) / checkModelExists(config)
TestTransformationMode.DUAL / shouldRunEtl() / shouldRunZeta() / shouldCompareResults()
compareModelsStructural(Resource, Resource) — StructuralModelComparator integration
isStructuralComparisonEnabled() / isJsonExportEnabled()
```

### What AbstractDualComparisonTest adds

```
@TestFactory compareExternalModels() — full orchestration per ExternalModelConfig
assertDualEquivalent(S source, String testName) — for inline @Test methods
assertDualEquivalent(Supplier<S> factory, String testName) — for mutable sources
```

### Abstract methods subclass must implement

```java
protected abstract S parseSource(ExternalModelConfig config) throws Exception;
protected abstract T executeEtl(S source) throws Exception;
protected abstract T executeZeta(S source) throws Exception;
protected abstract Resource getResource(T model);
protected abstract String getModuleName();
```

### Optional overrides

```java
protected String getOutputLabel()                     // default: "elements"
protected String getPropertiesFile()                  // default: "external-model-tests.properties"
protected ModelComparator.ComparisonMode getComparisonMode()  // default: from sysprop or STRICT
protected boolean shouldFailOnDiff()                  // default: false (log only)
```

---

## Module: judo-tatami-base

### Repository: `judo-tatami-base`

### Candidates for migration

| Test Class | Module | LOC | Currently Extends | Migration Type |
|---|---|---|---|---|
| `Psm2AsmDiscoveryComparisonTest` | psm2asm/perf | 241 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |
| `Psm2MeasureDiscoveryComparisonTest` | psm2measure/perf | 212 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |
| `Asm2RdbmsDiscoveryComparisonTest` | asm2rdbms/perf | 257 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |
| `Asm2KeycloakDiscoveryComparisonTest` | asm2keycloak/perf | 255 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |
| `Rdbms2LiquibaseDiscoveryComparisonTest` | rdbms2liquibase/perf | 259 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |

### NOT migrated (leave as-is)

| Test Class | Module | Reason |
|---|---|---|
| `Psm2AsmDualTransformationTest` | psm2asm | Inline tests with extensive debug logging; not worth the churn |
| `AbstractDualTransformationTest` | psm2asm | PSM2ASM-specific base with `@ParameterizedTest` + `TransformationMode` enum — different pattern |
| `Asm2RdbmsInheritanceTest` | asm2rdbms | Extends `Asm2RdbmsMappingTestBase`; uses `@ParameterizedTest` with `TransformationMode` — different pattern |

### Migration pattern: Psm2AsmDiscoveryComparisonTest

**Before** (241 LOC):
- Extends `AbstractExternalModelTest`
- Own `@TestFactory` with `@ParameterizedTest` + `@MethodSource`
- Own `executeTransformation(PsmModel, TransformationMode)` with switch
- Own model loading via `loadModelConfigs`
- Own element counting via custom classifier counter

**After** (~60 LOC):

```java
@Tag("comparison")
@Tag("performance")
public class Psm2AsmDiscoveryComparisonTest
    extends AbstractDualComparisonTest<PsmModel, AsmModel> {

    @Override protected String getModuleName() { return "psm2asm"; }
    @Override protected String getOutputLabel() { return "classifiers"; }

    @Override protected PsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("psm");
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
            .uri(URI.createURI(config.modelName() + "-psm.model"))
            .inputStream(new FileInputStream(modelFile.toFile()))
            .build());
    }

    @Override protected AsmModel executeEtl(PsmModel source) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        TransformationContext ctx = new TransformationContext("test");
        ctx.put(source);
        ctx.put(asmModel);
        new Psm2AsmWork(ctx, Psm2AsmWorkParameter.psm2AsmWorkParameter()
            .transformationMode(TransformationMode.ETL)
            .createTrace(false).parallel(false).useCache(true)).execute();
        return ctx.getByClass(AsmModel.class).orElseThrow();
    }

    @Override protected AsmModel executeZeta(PsmModel source) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        TransformationContext ctx = new TransformationContext("test");
        ctx.put(source);
        ctx.put(asmModel);
        new Psm2AsmWork(ctx, Psm2AsmWorkParameter.psm2AsmWorkParameter()
            .transformationMode(TransformationMode.ZETA)
            .createTrace(false).parallel(false).useCache(true)).execute();
        return ctx.getByClass(AsmModel.class).orElseThrow();
    }

    @Override protected Resource getResource(AsmModel model) {
        return model.getResourceSet().getResources().get(0);
    }
}
```

### Migration pattern: Asm2RdbmsDiscoveryComparisonTest

**Unique:** Uses `config.getDialect()` and loads RDBMS mapping models.

```java
@Tag("comparison")
@Tag("performance")
public class Asm2RdbmsDiscoveryComparisonTest
    extends AbstractDualComparisonTest<AsmModel, RdbmsModel> {

    @Override protected String getModuleName() { return "asm2rdbms"; }
    @Override protected String getOutputLabel() { return "tables"; }

    @Override protected AsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("asm");
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
            .uri(URI.createURI(config.modelName() + "-asm.model"))
            .inputStream(new FileInputStream(modelFile.toFile()))
            .build());
    }

    @Override protected RdbmsModel executeEtl(AsmModel source) throws Exception {
        // Note: dialect comes from ExternalModelConfig.parameters
        // The @TestFactory calls parseSource per model, so we need dialect from config
        // This requires storing config in a field — see "dialect pattern" below
        RdbmsModel rdbmsModel = buildRdbmsModel().build();
        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
            .asmModel(source).rdbmsModel(rdbmsModel)
            .dialect(currentDialect).createTrace(false));
        return rdbmsModel;
    }

    @Override protected RdbmsModel executeZeta(AsmModel source) throws Exception {
        RdbmsModel rdbmsModel = buildRdbmsModel().build();
        Asm2RdbmsZetaTransformation.builder()
            .asmModel(source).rdbmsModel(rdbmsModel)
            .dialect(currentDialect)
            .build().execute();
        return rdbmsModel;
    }

    @Override protected Resource getResource(RdbmsModel model) {
        return model.getResourceSet().getResources().get(0);
    }

    // Dialect pattern: store current config's dialect for use in executeEtl/executeZeta
    private String currentDialect = "hsqldb";

    // Override to capture dialect before each test
    // Alternatively, subclass can override testModel() or use a ThreadLocal
}
```

**Note on dialect pattern:** The `AbstractDualComparisonTest` calls `parseSource(config)` then `executeEtl(S)` / `executeZeta(S)` — but `executeEtl/executeZeta` don't receive the `ExternalModelConfig`. For tests that need config parameters (like dialect), the subclass should store the current config in a field. This can be achieved by overriding `parseSource()` to also capture the config:

```java
private ExternalModelConfig currentConfig;

@Override protected AsmModel parseSource(ExternalModelConfig config) throws Exception {
    this.currentConfig = config;
    // ... load model
}
```

Then `executeEtl`/`executeZeta` can access `currentConfig.getDialect()`.

### Migration pattern: Rdbms2LiquibaseDiscoveryComparisonTest

Same dialect pattern as Asm2Rdbms. RDBMS model files use dialect suffix: `{name}-rdbms_{dialect}.model`.

### Verification per module

```bash
mvn test -pl judo-tatami-psm2asm -Pperformance -Dtest=Psm2AsmDiscoveryComparisonTest
mvn test -pl judo-tatami-psm2measure -Pperformance -Dtest=Psm2MeasureDiscoveryComparisonTest
mvn test -pl judo-tatami-asm2rdbms -Pperformance -Dtest=Asm2RdbmsDiscoveryComparisonTest
mvn test -pl judo-tatami-asm2keycloak -Pperformance -Dtest=Asm2KeycloakDiscoveryComparisonTest
mvn test -pl judo-tatami-rdbms2liquibase -Pperformance -Dtest=Rdbms2LiquibaseDiscoveryComparisonTest
```

---

## Module: judo-tatami-jsl

### Repository: `judo-tatami-jsl`

### Candidates for migration

| Test Class | Module | LOC | Currently Extends | Migration Type |
|---|---|---|---|---|
| `Jsl2PsmDiscoveryComparisonTest` | jsl2psm/perf | 462 | Nothing | Migrate to `AbstractDualComparisonTest` |
| `Jsl2UiDiscoveryComparisonTest` | jsl2ui/perf | 458 | Nothing | Migrate to `AbstractDualComparisonTest` |
| `Jsl2UiExternalDualComparisonTest` | jsl2ui/dual | 290 | Nothing | Migrate to `AbstractDualComparisonTest` |

### NOT migrated (leave as-is)

| Test Class | Module | Reason |
|---|---|---|
| `Jsl2PsmDualTransformationTest` | jsl2psm/dual | 1330 LOC, 90% inline test methods with domain-specific JSL strings. Can adopt `assertDualEquivalent` but not worth full migration. |
| `Jsl2UiDualTransformationTest` | jsl2ui/dual | 625 LOC, 80% inline test methods with TYPES constant and JSL strings. Same reasoning. |

### JSL-specific: companion file resolution

JSL discovery tests have a `resolveJslFiles(ModelConfig)` method that handles multi-file JSL models with companion files specified via `companions` property parameter. This stays in the subclass (~15 LOC):

```java
private List<File> resolveJslFiles(ExternalModelConfig config) {
    List<File> files = new ArrayList<>();
    // Primary file: look for *.jsl in model directory
    Path modelDir = config.modelDirectory();
    try (var stream = Files.list(modelDir)) {
        stream.filter(p -> p.toString().endsWith(".jsl"))
              .forEach(p -> files.add(p.toFile()));
    }
    // Companion files from parameters
    String companions = config.parameters().get("companions");
    if (companions != null) {
        for (String companion : companions.split(",")) {
            File compFile = modelDir.resolve(companion.trim()).toFile();
            if (compFile.exists()) files.add(compFile);
        }
    }
    return files;
}
```

### Migration pattern: Jsl2PsmDiscoveryComparisonTest

**Before** (462 LOC): Own `ModelConfig`, `TestResult`, `printSummary`, `writeJsonResults`, `loadModelConfigs`, `discoverJslFiles`, `resolveJslFiles`, `countElements`, `getZetaIterations`, `isWarmupEnabled`, `getComparisonMode`, `truncate`, `escapeJson`.

**After** (~55 LOC):

```java
@Tag("performance")
public class Jsl2PsmDiscoveryComparisonTest
    extends AbstractDualComparisonTest<JslDslModel, PsmModel> {

    @Override protected String getModuleName() { return "jsl2psm"; }
    @Override protected String getOutputLabel() { return "PSM elements"; }

    @Override protected JslDslModel parseSource(ExternalModelConfig config) throws Exception {
        List<File> files = resolveJslFiles(config);
        JslDslModel model = JslParser.getModelFromFiles(files);
        assertTrue(model.isValid(), "JSL model is not valid: " + config.modelName());
        return model;
    }

    @Override protected PsmModel executeEtl(JslDslModel m) throws Exception {
        PsmModel psm = buildPsmModel().build();
        Jsl2Psm.executeJsl2PsmTransformation(Jsl2Psm.Jsl2PsmParameter.jsl2PsmParameter()
            .jslModel(m).psmModel(psm).createTrace(false)
            .parallel(true).useCache(true)
            .generateBehaviours(hasBehaviours()));
        return psm;
    }

    @Override protected PsmModel executeZeta(JslDslModel m) {
        PsmModel psm = buildPsmModel().build();
        Jsl2PsmZetaTransformation.builder()
            .jslModel(m).psmModel(psm).defaultModelName(m.getName())
            .generateBehaviours(hasBehaviours())
            .build().execute();
        return psm;
    }

    @Override protected Resource getResource(PsmModel m) {
        return m.getResourceSet().getResources().get(0);
    }

    private ExternalModelConfig currentConfig;

    @Override protected JslDslModel parseSource(ExternalModelConfig config) throws Exception {
        this.currentConfig = config;
        // ... as above
    }

    private boolean hasBehaviours() {
        return currentConfig != null
            && Boolean.parseBoolean(currentConfig.parameters().getOrDefault("behaviours", "false"));
    }

    private List<File> resolveJslFiles(ExternalModelConfig config) {
        // ~15 LOC as shown above
    }
}
```

**Note:** `parseSource` shown twice above for clarity — in actual code, merge the `this.currentConfig = config` line into the single `parseSource` method.

### Migration pattern: Jsl2UiDiscoveryComparisonTest / Jsl2UiExternalDualComparisonTest

Same pattern as Jsl2Psm but with `JslDslModel → UiModel`:

```java
@Override protected UiModel executeEtl(JslDslModel m) throws Exception {
    UiModel ui = buildUiModel().name(m.getName()).build();
    Jsl2Ui.executeJsl2UiTransformation(Jsl2Ui.Jsl2UiParameter.jsl2UiParameter()
        .jslModel(m).uiModel(ui).createTrace(false));
    return ui;
}

@Override protected UiModel executeZeta(JslDslModel m) {
    UiModel ui = buildUiModel().name(m.getName()).build();
    Jsl2UiZetaTransformation.builder()
        .jslModel(m).uiModel(ui).defaultModelName(m.getName())
        .build().execute();
    return ui;
}

@Override protected Resource getResource(UiModel m) {
    return m.getResourceSet().getResources().get(0);
}
```

### Optional: adopt assertDualEquivalent in inline tests

`Jsl2PsmDualTransformationTest` and `Jsl2UiDualTransformationTest` can optionally adopt `assertDualEquivalent` for their inline `@Test` methods by extending `AbstractDualComparisonTest` and replacing their `assertModelEquivalence` helper:

```java
// Before (in each @Test method):
assertModelEquivalence("testFoo", "TestModel", """model Test; ...""");

// After (extend AbstractDualComparisonTest, add):
@Test void testFoo() throws Exception {
    JslDslModel model = JslParser.getModelFromStrings("TestModel", List.of("""model Test; ..."""));
    assertDualEquivalent(model, "testFoo");
}
```

This is optional — the `assertModelEquivalence` helper is test-local and works fine.

### Properties file format

Existing `external-model-tests.properties` files use this format:
```properties
# Simple
modelname=../relative/path/to/model

# Extended with parameters
modelname=../path;behaviours=true;companions=extra.jsl,helper.jsl;warmup=true;iterations=3
```

`ExternalModelConfig` parses this natively — no changes to properties files needed.

### Verification

```bash
cd judo-tatami-jsl
mvn test -pl judo-tatami-jsl-jsl2psm -Pperformance -Dtest=Jsl2PsmDiscoveryComparisonTest
mvn test -pl judo-tatami-jsl-jsl2ui -Pperformance -Dtest=Jsl2UiDiscoveryComparisonTest
mvn test -pl judo-tatami-jsl-jsl2ui -Dtest=Jsl2UiExternalDualComparisonTest
```

---

## Module: judo-tatami-client

### Repository: `judo-tatami-client`

### Candidates for migration

| Test Class | Module | LOC | Currently Extends | Migration Type |
|---|---|---|---|---|
| `Esm2UiExternalModelTest` | esm2ui/perf | 248 | `AbstractExternalModelTest` | Migrate to `AbstractDualComparisonTest` |

### NOT migrated (leave as-is)

| Test Class | Module | Reason |
|---|---|---|
| `AbstractDualTest` + 4 diff test classes | esm2ui/dual | Custom base with `DualResult` record, XMI ID comparison, `assertDualDiffers()`. Different pattern — tests expected differences, not equivalence. |
| `Esm2UiAnnotationDiffTest` | esm2ui/dual | Extends `AbstractDualTest` — testing expected annotation differences |
| `Esm2UiRuntimeDiffTest` | esm2ui/dual | Extends `AbstractDualTest` — testing expected runtime differences |
| `Esm2UiBodyLogicDiffTest` | esm2ui/dual | Extends `AbstractDualTest` — testing expected body logic differences |
| `Esm2UiExternalDiffTest` | esm2ui/dual | Extends `AbstractDualTest` — regression tests for known diffs |
| `Esm2UiConcurrencyTest` | esm2ui/dual | Standalone — tests Zeta determinism, not ETL vs ZETA comparison |
| `Esm2UiCorrectnessTest` | esm2ui | Already extends `AbstractExternalModelTest` with orphan detection; complex enough to keep custom |
| `AbstractModelTest` + `Esm2UiBenchmarkTest` | esm2ui | Custom base with parameterized `TransformationMode`; different pattern |
| `TestModelBuilders` | esm2ui/dual | Utility class, not a test |

### Why fewer migrations in tatami-client

The `esm2ui` module uses `TransformationMode` enum (ETL/ZETA) passed to a single `executeEsm2UiTransformation()` method rather than separate `executeEtl`/`executeZeta` methods. The `AbstractDualTest` base class also provides `DualResult` with XMI ID comparison — a feature `AbstractDualComparisonTest` does not provide.

Only `Esm2UiExternalModelTest` follows the standard discovery + comparison pattern and benefits from the migration.

### Migration pattern: Esm2UiExternalModelTest

**Before** (248 LOC): Extends `AbstractExternalModelTest`, own `@ParameterizedTest` + `@MethodSource`, own `executeTransformation(EsmModel, TransformationMode, String)`, own timing and comparison.

**After** (~70 LOC):

```java
@Tag("performance")
public class Esm2UiExternalModelTest
    extends AbstractDualComparisonTest<EsmModel, UiModel> {

    @Override protected String getModuleName() { return "esm2ui"; }
    @Override protected String getOutputLabel() { return "UI elements"; }

    @Override protected EsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("esm");
        return EsmModel.loadEsmModel(esmLoadArgumentsBuilder()
            .uri(URI.createURI(config.modelName() + "-esm.model"))
            .inputStream(new FileInputStream(modelFile.toFile()))
            .validateModel(false));
    }

    @Override protected UiModel executeEtl(EsmModel source) throws Exception {
        UiModel uiModel = buildUiModel()
            .uri(URI.createURI("urn:etl-ui"))
            .name("ExternalModelTest").build();
        executeEsm2UiTransformation(Esm2Ui.Esm2UiParameter.esm2UiParameter()
            .esmModel(source).uiModel(uiModel)
            .transformationMode(TransformationMode.ETL)
            .parallel(false).build());
        return uiModel;
    }

    @Override protected UiModel executeZeta(EsmModel source) throws Exception {
        UiModel uiModel = buildUiModel()
            .uri(URI.createURI("urn:zeta-ui"))
            .name("ExternalModelTest").build();
        executeEsm2UiTransformation(Esm2Ui.Esm2UiParameter.esm2UiParameter()
            .esmModel(source).uiModel(uiModel)
            .transformationMode(TransformationMode.ZETA)
            .parallel(false).build());
        return uiModel;
    }

    @Override protected Resource getResource(UiModel model) {
        return model.getResourceSet().getResources().get(0);
    }
}
```

### Verification

```bash
cd judo-tatami-client
mvn test -pl judo-tatami-esm2ui -Pperformance -Dtest=Esm2UiExternalModelTest
```

---

## Summary across all modules

| Repository | Tests to Migrate | Tests to Leave | LOC Eliminated (est) |
|---|---|---|---|
| `judo-tatami-base` | 5 discovery tests | 3 (different patterns) | ~800 |
| `judo-tatami-jsl` | 3 discovery tests | 2 inline tests (optional) | ~900 |
| `judo-tatami-client` | 1 external model test | 9 (different patterns) | ~150 |
| **Total** | **9** | **14** | **~1850** |

## Dependency requirement

All consumer modules must depend on `judo-tatami-test-utils` version that includes `AbstractDualComparisonTest` and `PerformanceMeasurement`. Verify the test-utils dependency version in each module's `pom.xml` before migrating.

## Migration checklist (per test class)

1. Change `extends AbstractExternalModelTest` (or nothing) to `extends AbstractDualComparisonTest<S, T>`
2. Implement 5 abstract methods: `parseSource`, `executeEtl`, `executeZeta`, `getResource`, `getModuleName`
3. Override `getOutputLabel()` if not "elements"
4. Override `shouldFailOnDiff()` → `true` if test should fail on mismatch (not just log)
5. Delete: `ModelConfig` inner class, `TestResult` record, `printSummary()`, `writeJsonResults()`, `countElements()`, `loadModelConfigs()`, `discoverJslFiles()`, `resolveJslFiles()` (if generic), `getZetaIterations()`, `isWarmupEnabled()`, `getComparisonMode()`, `truncate()`, `escapeJson()`, `@TestFactory` method
6. Keep: any domain-specific logic (JSL companion resolution, RDBMS dialect handling, behaviours flag)
7. Ensure `external-model-tests.properties` uses `ExternalModelConfig` format (semicolon-separated params)
8. Run the test and verify equivalent behavior
