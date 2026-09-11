# judo-tatami-expression-psm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/psm/validation

Workflow works validating an `ExpressionModel` against a `PsmModel`. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmWork.java` | Validates expressions against PSM via Epsilon EVL. Extends `AbstractTransformationWork`; `execute()` pulls `ExpressionModel` and `PsmModel` from `TransformationContext` and calls `ExpressionValidatorOnPsm.validateExpressionOnPsm(logger, psm, expression)`. Both models must already be in the context — a missing one throws `IllegalArgumentException`. No separate measure model: PSM carries its own measures. EVL output goes through a `StringBuilderLogger` closed via try-with-resources. |
| `ExpressionValidationOnPsmZetaWork.java` | Same validation as `ExpressionValidationOnPsmWork` but on the Zeta engine, no Epsilon. `execute()` requires `ExpressionModel` + `PsmModel` in `TransformationContext`, builds a `PsmModelAdapter` passing the PSM `ResourceSet` as both model and measure resource set (PSM embeds measures), then calls `ExpressionZetaValidator.validateExpression`. Failure surfaces as `ExpressionValidationException`, not as accumulated EVL log text — callers switching engines must change their failure handling. |
