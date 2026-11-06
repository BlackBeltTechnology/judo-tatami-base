# judo-tatami-psm2asm-etl/src/main/resources/META-INF/services
`ServiceLoader` registration for the ETL engine.
| File | Purpose |
|---|---|
| `hu.blackbelt.judo.tatami.psm2asm.Psm2AsmEtlEngine` | Lists `hu.blackbelt.judo.tatami.psm2asm.etl.Psm2AsmEtlEngineImpl`; plain-Java/Spring discovery of the engine by `EtlEngineRegistry`. See change: split-etl-artifacts. |
