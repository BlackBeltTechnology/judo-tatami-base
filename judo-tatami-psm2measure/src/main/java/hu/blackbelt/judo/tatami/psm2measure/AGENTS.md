# AGENTS.md — `judo-tatami-psm2measure/src/main/java/hu/blackbelt/judo/tatami/psm2measure`

Entry points converting PSM models to MEASURES models. Epsilon-free core: Zeta (`zeta/`) runs here; the ETL entry point and scripts live in `judo-tatami-psm2measure-etl`, reached via `Psm2MeasureEtlEngine`.

| File | Purpose |
| --- | --- |
| `Psm2MeasureEtlEngine.java` | Typed ETL engine contract. `execute(PsmModel, MeasureModel, Logger, createTrace, useCache, parallel)` → `Psm2MeasureTransformationTrace`; `ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-psm2measure-etl"`. Implemented by `-etl` module, resolved by `EtlEngineRegistry`. See change: split-etl-artifacts. |
| `Psm2MeasureTransformationTrace.java` | Implements `TransformationTrace` for PSM to Measure mappings, supporting dual ETL and Zeta trace models. Exports `psm2MeasureTransformationTraceBuilder()`, serialization via `save()`, `resolvePsm2MeasureTrace(ResourceSet, URI)`, and URI prefixes `psm2measureTrace:`. Zeta→legacy map in `getTransformationTrace()` dedupes targets per source (identity). See change: split-etl-artifacts (esm-itest Zeta fixes). |
| `Psm2MeasureWork.java` | Workflow task extending `AbstractTransformationWork`; picks `TransformationMode` (ZETA default via system property), takes `PsmModel` from context, creates `MeasureModel`. Zeta inline; ETL/DUAL via `EtlEngineRegistry.require(Psm2MeasureEtlEngine.class, ...)`; ctor `(ctx)` only. See change: split-etl-artifacts. |
