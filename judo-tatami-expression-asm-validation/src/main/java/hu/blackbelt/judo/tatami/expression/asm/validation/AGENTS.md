# judo-tatami-expression-asm-validation/src/main/java/hu/blackbelt/judo/tatami/expression/asm/validation

Workflow works validating an `ExpressionModel` against an `AsmModel` + `MeasureModel`. Core is Epsilon-free: `ExpressionValidationOnAsmWork` picks Zeta (default) or delegates EVL to the optional `-etl` add-on via `ExpressionValidationOnAsmEtlEngine`.

| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmEtlEngine.java` | Core contract of the EVL add-on; `ARTIFACT_COORDINATES` = `hu.blackbelt.judo.tatami:judo-tatami-expression-asm-validation-etl`; `void validate(ExpressionModel, MeasureModel, AsmModel, Logger log) throws Exception`; null `log` = impl logger. Impl in `judo-tatami-expression-asm-validation-etl`. See change: split-etl-artifacts. |
| `ExpressionValidationOnAsmWork.java` | Mode-aware validation work; ZETA/DUAL runs `ExpressionValidationOnAsmZetaWork`, ETL calls the `ExpressionValidationOnAsmEtlEngine` add-on via `EtlEngineRegistry`; nested `ExpressionValidationOnAsmWorkParameter` (`expressionValidationOnAsmWorkParameter()`, `transformationMode`) → see `ExpressionValidationOnAsmWork.java.AGENTS.md`. See change: split-etl-artifacts. |
| `ExpressionValidationOnAsmZetaWork.java` | Same validation as `ExpressionValidationOnAsmWork` but on the Zeta engine, no Epsilon; `execute()` builds an `AsmModelAdapter`, calls `ExpressionZetaValidator.validateExpression`, failure surfaces as `ExpressionValidationException` → see `ExpressionValidationOnAsmZetaWork.java.AGENTS.md` |
