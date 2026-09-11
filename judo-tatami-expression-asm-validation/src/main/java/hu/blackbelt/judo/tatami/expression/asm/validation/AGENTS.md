# judo-tatami-expression-asm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/asm/validation

Workflow works validating an `ExpressionModel` against an `AsmModel` + `MeasureModel`. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmWork.java` | Validates expressions against ASM via Epsilon EVL. Extends `AbstractTransformationWork`; `execute()` pulls `ExpressionModel`, `MeasureModel`, `AsmModel` out of `TransformationContext` and calls `ExpressionValidatorOnAsm.validateExpressionOnAsm(logger, asm, measure, expression)`. All three models must already be in the context — each missing one throws `IllegalArgumentException`. Wraps EVL output in a `StringBuilderLogger` bound to the class logger, closed via try-with-resources. |
| `ExpressionValidationOnAsmZetaWork.java` | Same validation as `ExpressionValidationOnAsmWork` but on the Zeta engine, no Epsilon. `execute()` requires the same three models in `TransformationContext`, builds an `AsmModelAdapter` from the ASM and measure `ResourceSet`s, then calls `ExpressionZetaValidator.validateExpression`. Failure surfaces as `ExpressionValidationException`, not as accumulated EVL log text — callers switching engines must change their failure handling. |
