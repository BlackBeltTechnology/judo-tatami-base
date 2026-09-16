# `ExpressionValidationOnAsmWork.java`

Validates expressions against ASM via Epsilon EVL. Extends `AbstractTransformationWork`; `execute()` pulls `ExpressionModel`, `MeasureModel`, `AsmModel` out of `TransformationContext` and calls `ExpressionValidatorOnAsm.validateExpressionOnAsm(logger, asm, measure, expression)`.
All three models must already be in the context — each missing one throws `IllegalArgumentException`. Wraps EVL output in a `StringBuilderLogger` bound to the class logger, closed via try-with-resources.