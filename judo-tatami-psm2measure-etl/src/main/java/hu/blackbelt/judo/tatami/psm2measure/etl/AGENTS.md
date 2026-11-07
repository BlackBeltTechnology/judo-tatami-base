# judo-tatami-psm2measure-etl/src/main/java/hu/blackbelt/judo/tatami/psm2measure/etl
ETL entry point of the PSM→Measure stage plus its engine adapter. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `Psm2Measure.java` | Executes Epsilon ETL PSM→Measure. Exports `executePsm2MeasureTransformation(Psm2MeasureParameter)`, `calculatePsm2MeasureTransformationScriptURI()`; wraps models as `WrappedEmfModelContext`, runs `EtlExecutionContext` with buffered SLF4J logging. Package `.etl` (moved from core). See change: split-etl-artifacts. |
| `Psm2MeasureEtlEngineImpl.java` | Implements core `Psm2MeasureEtlEngine`; `execute(...)` builds `Psm2MeasureParameter` and calls `Psm2Measure.executePsm2MeasureTransformation`; `log` may be `null` (buffered SLF4J logger). Loaded by `ServiceLoader` or `Psm2MeasureEtlEngineComponent`. See change: split-etl-artifacts. |
