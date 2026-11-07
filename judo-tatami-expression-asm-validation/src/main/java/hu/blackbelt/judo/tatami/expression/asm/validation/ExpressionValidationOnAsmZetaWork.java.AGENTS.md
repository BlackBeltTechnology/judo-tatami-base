# `ExpressionValidationOnAsmZetaWork.java`

Same validation as `ExpressionValidationOnAsmWork` but on the Zeta engine, no Epsilon. `execute()` requires the same three models in `TransformationContext`, builds an `AsmModelAdapter` from the ASM and measure `ResourceSet`s, then calls `ExpressionZetaValidator.validateExpression`.
Failure surfaces as `ExpressionValidationException`, not as accumulated EVL log text — callers switching engines must change their failure handling.