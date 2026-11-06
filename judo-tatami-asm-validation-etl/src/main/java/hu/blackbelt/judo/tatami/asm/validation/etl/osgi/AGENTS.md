# judo-tatami-asm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/asm/validation/etl/osgi
OSGi wiring of the EVL add-on.
| File | Purpose |
|---|---|
| `AsmValidationEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` registers `new AsmValidationEtlEngineImpl()` as `AsmValidationEtlEngine` in `EtlEngineRegistry`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
