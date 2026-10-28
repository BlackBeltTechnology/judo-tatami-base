# judo-tatami-psm2asm/src/test/java/hu/blackbelt/judo/tatami/psm2asm/perf

PSM→ASM performance and large-model ETL-vs-Zeta comparison tests. Every test
class here carries `@Tag("performance")`, so the default surefire run skips them;
they are driven by `-Pperformance`.

| File | Purpose |
|---|---|
| `CLAUDE.md` | `claude-mem` scratch context wrapped in a `<claude-mem-context>` element; the "Recent Activity" block is auto-generated and currently reads `*No recent activity*`. Carries no test or build knowledge. Hand edits belong outside the tags — anything inside is overwritten by the generator. |
| `Psm2AsmDiscoveryComparisonTest.java` | Runs the dual comparison against models discovered on disk rather than built in code. Extends `AbstractDualComparisonTest<PsmModel, AsmModel>`; `@Tag("comparison")` + `@Tag("performance")`. Overrides `getModuleName()`→`psm2asm`, `getOutputLabel()`→`Classifiers`, `parseSource`, `executeEtl`, `executeZeta`, `getResource`. → see `Psm2AsmDiscoveryComparisonTest.java.AGENTS.md` |
| `Psm2AsmPerformanceTest.java` | Synthetic-scale ETL-vs-Zeta timing over generated PSM models. `testSmallModelComparison`, `testMediumModelComparison`, and `@ParameterizedTest @ValueSource(ints = {100, 500, 1000}) testVariousModelSizesComparison(int)`. → see `Psm2AsmPerformanceTest.java.AGENTS.md` |
| `RealisticPerformanceTest.java` | Times both engines on RackInspect-shaped synthetic models from `RealisticPsmModelGenerator`; `testSmallRealisticModel` (20), `testRealisticModelPerformance` (70), `testLargeRealisticModel` (100) each call `runPerformanceTest(entityCount, name)`, count via `RealisticPsmModelGenerator.countElements`, compare with `ModelComparator`. → see `RealisticPerformanceTest.java.AGENTS.md` |
