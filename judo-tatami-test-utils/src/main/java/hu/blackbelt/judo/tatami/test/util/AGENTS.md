# AGENTS.md — `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util`

Testing framework utilities for external model discovery, dual transformation comparison (ETL vs Zeta), performance timing, and EMF model equivalence verification.

| File | Purpose |
| --- | --- |
| `AbstractDualComparisonTest.java` | Base test class orchestrating ETL vs Zeta comparison runs with warmups, timing, result reporting, and `assertDualEquivalent()` assertions. |
| `AbstractExternalModelTest.java` | Abstract base for parametrized ETL-vs-ZETA external-model tests: subclasses must expose an `externalModels()` `@MethodSource` calling `loadModelConfigs()` to supply `ExternalModelConfig` streams; provides `TestTransformationMode` (`ZETA`/`ETL`/`DUAL`), `recordResult()` timings, and `exportModelStructure()` JSON export gated by `PROP_STRUCTURAL_EXPORT_JSON`. |
| `CLAUDE.md` | Claude memory context tracker file for session history within the test util package. |
| `ExternalModelConfig.java` | Configuration record capturing model name, directory path, existence state, and execution parameters (`dialect`, `warmup`, `iterations`). |
| `ModelComparator.java` | Deep EMF model and XMI ID equivalence comparator detecting missing/extra elements, value mismatches, and container discrepancies; exports `assertEquivalent()`. |
| `PerformanceMeasurement.java` | Utility measuring single or multi-iteration execution wall-clock time; exports `measure()` and `measureAvg()`. |
