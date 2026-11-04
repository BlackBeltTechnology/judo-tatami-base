# judo-tatami-psm2measure-etl/src/main/resources/META-INF/services
`ServiceLoader` registration for the ETL engine.
| File | Purpose |
|---|---|
| `hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureEtlEngine` | Lists `hu.blackbelt.judo.tatami.psm2measure.etl.Psm2MeasureEtlEngineImpl`; plain-Java/Spring discovery of the engine by `EtlEngineRegistry`. See change: split-etl-artifacts. |
