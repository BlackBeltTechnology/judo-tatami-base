# judo-tatami-psm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/psm/validation/etl/osgi
OSGi wiring of the EVL add-on.
| File | Purpose |
|---|---|
| `PsmValidationEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` registers `new PsmValidationEtlEngineImpl()` as `PsmValidationEtlEngine` in `EtlEngineRegistry`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
