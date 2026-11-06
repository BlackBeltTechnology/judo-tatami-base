# judo-tatami-expression-asm-validation-etl/src/test/java/hu/blackbelt/judo/tatami/expression/asm/validation

EVL test of expression-on-ASM validation, moved from core `judo-tatami-expression-asm-validation` (same package). Runs against the add-on via `ServiceLoader` discovery.

| File | Purpose |
|---|---|
| `ExpressionValidationOnAsmWorkTest.java` | Tests EVL-based `ExpressionValidationOnAsmWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. Puts `ExpressionValidationOnAsmWorkParameter` with `TransformationMode.ETL` into the context so `ExpressionValidationOnAsmWork` resolves `ExpressionValidationOnAsmEtlEngineImpl` via `ServiceLoader`. See change: split-etl-artifacts. |
