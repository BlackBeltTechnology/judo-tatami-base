# `Asm2ExpressionTranformationSerivce.java`

Declares `@Component(service = Asm2ExpressionTranformationSerivce.class, immediate = true)`.
Exports `install(AsmModel, MeasureModel) : ExpressionModel`. Builds empty `ExpressionModel` named/versioned after `asmModel`, URI `expression:<name>.model`, then runs `Asm2Expression.executeAsm2Expression` with `Asm2ExpressionParameter`. Callers get the transformation log via `StringBuilderLogger`; on failure the log dumps red and the exception rethrows, so the returned model is never partially transformed.
Name misspells "Transformation" — bundle wiring depends on that literal, do not rename.