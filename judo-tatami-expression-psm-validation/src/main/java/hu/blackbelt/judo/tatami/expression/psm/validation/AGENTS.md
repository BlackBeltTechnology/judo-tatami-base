# judo-tatami-expression-psm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/psm/validation

Workflow works validating an `ExpressionModel` against a `PsmModel`. Core is Epsilon-free: `ExpressionValidationOnPsmWork` picks Zeta (default) or delegates EVL to the optional `-etl` add-on via `ExpressionValidationOnPsmEtlEngine`.

| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmEtlEngine.java` | Core contract of the EVL add-on; `ARTIFACT_COORDINATES` = `hu.blackbelt.judo.tatami:judo-tatami-expression-psm-validation-etl`; `void validate(ExpressionModel, PsmModel, Logger log) throws Exception`; null `log` = impl logger. Impl in `judo-tatami-expression-psm-validation-etl`. See change: split-etl-artifacts. |
| `ExpressionValidationOnPsmWork.java` | Mode-aware validation work; ZETA/DUAL runs `ExpressionValidationOnPsmZetaWork`, ETL calls the `ExpressionValidationOnPsmEtlEngine` add-on via `EtlEngineRegistry`; nested `ExpressionValidationOnPsmWorkParameter` (`expressionValidationOnPsmWorkParameter()`, `transformationMode`) → see `ExpressionValidationOnPsmWork.java.AGENTS.md`. See change: split-etl-artifacts. |
| `ExpressionValidationOnPsmZetaWork.java` | Same validation on the Zeta engine — builds a `PsmModelAdapter`, calls `ExpressionZetaValidator.validateExpression`; failure surfaces as `ExpressionValidationException` → see `ExpressionValidationOnPsmZetaWork.java.AGENTS.md` |
