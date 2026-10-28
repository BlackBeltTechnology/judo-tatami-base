# judo-tatami-expression-psm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/psm/validation

Workflow works validating an `ExpressionModel` against a `PsmModel`. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmWork.java` | Validates expressions against PSM via Epsilon EVL; `execute()` pulls `ExpressionModel` + `PsmModel` from `TransformationContext` → see `ExpressionValidationOnPsmWork.java.AGENTS.md` |
| `ExpressionValidationOnPsmZetaWork.java` | Same validation on the Zeta engine — builds a `PsmModelAdapter`, calls `ExpressionZetaValidator.validateExpression`; failure surfaces as `ExpressionValidationException` → see `ExpressionValidationOnPsmZetaWork.java.AGENTS.md` |
