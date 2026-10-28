# AGENTS.md — `judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression`

Unit tests verifying ASM to Expression metamodel transformation direct API execution and workflow engine integration.

| File | Purpose |
| --- | --- |
| `Asm2ExpressionTest.java` | Tests standalone execution of `Asm2Expression.executeAsm2ExpressionGeneration()` against Northwind Demo PSM, ASM, and Measure models. Verifies generation and disk serialization of `northwind-expression.model`. |
| `Asm2ExpressionWorkTest.java` | Tests `Asm2ExpressionWork` step execution within a `WorkFlowEngine` sequential workflow pipeline. Sets up Northwind PSM2ASM and PSM2Measure prerequisites in a shared `TransformationContext` and asserts `WorkStatus.COMPLETED`. |
