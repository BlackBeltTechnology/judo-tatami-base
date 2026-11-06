# judo-tatami-psm2asm-etl/src/main/java/hu/blackbelt/judo/tatami/psm2asm/etl/osgi
OSGi wiring of the ETL add-on.
| File | Purpose |
|---|---|
| `Psm2AsmEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Psm2AsmEtlEngine.class, new Psm2AsmEtlEngineImpl())`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
