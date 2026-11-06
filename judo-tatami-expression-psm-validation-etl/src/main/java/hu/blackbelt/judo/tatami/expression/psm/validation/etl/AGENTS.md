# judo-tatami-expression-psm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/expression/psm/validation/etl
EVL engine adapter of expression-on-PSM validation. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmEtlEngineImpl.java` | Implements core `ExpressionValidationOnPsmEtlEngine`; `validate(ExpressionModel, PsmModel, Logger log)` runs `ExpressionValidatorOnPsm.validateExpressionOnPsm` inside a `StringBuilderLogger`; null `log` falls back to the impl logger. Former EVL body of `ExpressionValidationOnPsmWork`. Loaded by `ServiceLoader` or `ExpressionValidationOnPsmEtlEngineComponent`. See change: split-etl-artifacts. |
