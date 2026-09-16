# AGENTS.md — `judo-tatami-psm2measure/src/main/java/hu/blackbelt/judo/tatami/psm2measure`

Entry points and transformation orchestration converting PSM models to MEASURES models via ETL or Zeta engines.

| File | Purpose |
| --- | --- |
| `Psm2Measure.java` | Executes Epsilon ETL transformation from `PsmModel` to `MeasureModel`. Exports `executePsm2MeasureTransformation(Psm2MeasureParameter)` and `calculatePsm2MeasureTransformationScriptURI()`. Wraps models into `WrappedEmfModelContext` and constructs an `EtlExecutionContext` with buffered SLF4J logging. |
| `Psm2MeasureTransformationTrace.java` | Implements `TransformationTrace` for PSM to Measure mappings, supporting dual ETL and Zeta trace models. Exports `psm2MeasureTransformationTraceBuilder()`, serialization via `save()`, `resolvePsm2MeasureTrace(ResourceSet, URI)`, and URI prefixes `psm2measureTrace:`. |
| `Psm2MeasureWork.java` | Workflow task extending `AbstractTransformationWork` that integrates PSM to Measure transformation into workflow contexts. Checks `TransformationMode` (defaults to ZETA via system property or falls back to ETL), extracts `PsmModel` from context, creates target `MeasureModel`, and registers generated trace when requested. |
