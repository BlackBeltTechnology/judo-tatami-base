# judo-tatami-expression-psm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/expression/psm/validation/etl/osgi
OSGi wiring of the EVL add-on.
| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` registers `new ExpressionValidationOnPsmEtlEngineImpl()` as `ExpressionValidationOnPsmEtlEngine` in `EtlEngineRegistry`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
