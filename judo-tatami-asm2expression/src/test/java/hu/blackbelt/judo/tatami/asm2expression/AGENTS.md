# AGENTS.md — `judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression`

Unit tests verifying ASM to Expression metamodel transformation direct API execution and workflow engine integration.

| File | Purpose |
| --- | --- |
| `Asm2ExpressionTest.java` | Tests standalone execution of `Asm2Expression.executeAsm2ExpressionGeneration()` against Northwind Demo PSM, ASM, and Measure models. Verifies generation and disk serialization of `northwind-expression.model`. Test imports `Psm2Asm`/`Psm2Measure` ETL entry points from `-etl` artifacts (test scope, package `.etl`). See change: split-etl-artifacts. Also asserts the default `Asm2ExpressionConfiguration` lambda scope is `false` (TP-10). See change: persisted-query-model. |
| `Asm2ExpressionWorkTest.java` | Tests `Asm2ExpressionWork` step execution within a `WorkFlowEngine` sequential workflow pipeline. Sets up Northwind PSM2ASM and PSM2Measure prerequisites in a shared `TransformationContext` and asserts `WorkStatus.COMPLETED`. Unused Epsilon `EmfUtils` import removed. Test imports `Psm2Asm`/`Psm2Measure` ETL entry points from `-etl` artifacts (test scope, package `.etl`). See change: split-etl-artifacts. Also asserts `ExpressionModelOrigin`: created in this run, preloaded empty model, and a saved `-expression.model` preloaded before the work (TP-62, asm2expression part). See change: persisted-query-model. `failedExtractionRecordsNoOrigin`: armed failing measure `ResourceSet` → work FAILED, no `ExpressionModelOrigin`. |
