# judo-tatami-psm2measure-etl/src/main/java/hu/blackbelt/judo/tatami/psm2measure/etl/osgi
OSGi wiring of the ETL add-on.
| File | Purpose |
|---|---|
| `Psm2MeasureEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Psm2MeasureEtlEngine.class, new Psm2MeasureEtlEngineImpl())`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
