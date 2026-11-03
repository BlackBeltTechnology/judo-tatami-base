# judo-tatami-expression-asm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/expression/asm/validation/etl
EVL engine adapter of expression-on-ASM validation. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmEtlEngineImpl.java` | Implements core `ExpressionValidationOnAsmEtlEngine`; `validate(ExpressionModel, MeasureModel, AsmModel, Logger log)` runs `ExpressionValidatorOnAsm.validateExpressionOnAsm` inside a `StringBuilderLogger`; null `log` falls back to the impl logger. Former EVL body of `ExpressionValidationOnAsmWork`. Loaded by `ServiceLoader` or `ExpressionValidationOnAsmEtlEngineComponent`. See change: split-etl-artifacts. |
