# `Psm2AsmDiscoveryComparisonTest.java`

Compares `TransformationMode.ETL` vs `.ZETA` PSM2ASM outputs over models discovered on disk (`judo.test.discovery.basedir` auto-discovery + `external-model-tests.properties`), not built in code. `@Tag("comparison") @Tag("performance")`, extends `AbstractDualComparisonTest<PsmModel, AsmModel>`.

Overrides `getModuleName()`→`psm2asm`, `getOutputLabel()`→`Classifiers`, `parseSource(ExternalModelConfig)` (loads via `PsmModel.loadPsmModel` + `psmLoadArgumentsBuilder().uri("source-psm.model").inputStream(new FileInputStream(...))`), `executeEtl`, `executeZeta`, `getResource(AsmModel)`→`getResourceSet().getResources().get(0)`.

`executeTransformation(PsmModel, TransformationMode)` runs `Psm2AsmWork` on `TransformationContext("DiscoveryComparisonTest")` with `createTrace(false)`, `parallel(false)`, `useCache(true)`; returns `context.getByClass(AsmModel.class)` or throws `IllegalStateException("ASM Model not found after transformation")`.

`getResource` indexes resource 0 — empty `ResourceSet` hits `IndexOutOfBoundsException`; no models discovered means nothing to compare, test passes silently.