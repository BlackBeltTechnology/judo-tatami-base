# AGENTS.md — `judo-tatami-expression-asm-validation/src/test/java/hu/blackbelt/judo/tatami/expression/asm/validation`

Unit tests verifying EVL and Zeta workflow tasks for expression validation against ASM models.

| File | Purpose |
| --- | --- |
| `ExpressionValidationOnAsmWorkTest.java` | Tests EVL-based `ExpressionValidationOnAsmWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. |
| `ExpressionValidationOnAsmZetaWorkTest.java` | Tests Zeta-based `ExpressionValidationOnAsmZetaWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. |
