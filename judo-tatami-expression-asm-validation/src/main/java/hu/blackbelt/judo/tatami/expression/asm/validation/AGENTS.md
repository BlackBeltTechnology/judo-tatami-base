# judo-tatami-expression-asm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/asm/validation

Workflow works validating an `ExpressionModel` against an `AsmModel` + `MeasureModel`. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmWork.java` | Validates expressions against ASM via Epsilon EVL; `execute()` calls `ExpressionValidatorOnAsm.validateExpressionOnAsm(logger, asm, measure, expression)` on the three context models → see `ExpressionValidationOnAsmWork.java.AGENTS.md` |
| `ExpressionValidationOnAsmZetaWork.java` | Same validation as `ExpressionValidationOnAsmWork` but on the Zeta engine, no Epsilon; `execute()` builds an `AsmModelAdapter`, calls `ExpressionZetaValidator.validateExpression`, failure surfaces as `ExpressionValidationException` → see `ExpressionValidationOnAsmZetaWork.java.AGENTS.md` |
