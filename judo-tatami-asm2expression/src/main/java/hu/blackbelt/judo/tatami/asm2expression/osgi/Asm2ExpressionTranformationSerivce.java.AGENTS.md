# `Asm2ExpressionTranformationSerivce.java`

Declares `@Component(service = Asm2ExpressionTranformationSerivce.class, immediate = true)`.
Exports `install(AsmModel, MeasureModel) : ExpressionModel`. Builds empty `ExpressionModel` named/versioned after `asmModel`, URI `expression:<name>.model`, then runs `Asm2Expression.executeAsm2Expression` with `Asm2ExpressionParameter`. Logs completion at debug; on failure logs error (red) and rethrows, so the returned model is never partially transformed. Epsilon-free: former Epsilon `StringBuilderLogger` was never passed to the transformation (always-empty buffer) and is removed.
Name misspells "Transformation" — bundle wiring depends on that literal, do not rename.

See change: split-etl-artifacts — dropped Epsilon `StringBuilderLogger`/`LogLevel`; task 4.8.
