# judo-tatami-asm2rdbms-etl/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/etl/osgi
OSGi wiring of the ETL add-on.
| File | Purpose |
|---|---|
| `Asm2RdbmsEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Asm2RdbmsEtlEngine.class, new Asm2RdbmsEtlEngineImpl())`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
