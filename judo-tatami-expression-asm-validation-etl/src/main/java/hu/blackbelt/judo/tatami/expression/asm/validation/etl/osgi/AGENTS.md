# judo-tatami-expression-asm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/expression/asm/validation/etl/osgi
OSGi wiring of the EVL add-on.
| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` registers `new ExpressionValidationOnAsmEtlEngineImpl()` as `ExpressionValidationOnAsmEtlEngine` in `EtlEngineRegistry`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
