# judo-tatami-asm-validation-etl/src/test/java/hu/blackbelt/judo/tatami/asm/validation

EVL test of ASM validation, moved from core `judo-tatami-asm-validation` (same package). Runs against the add-on via `ServiceLoader` discovery.

| File | Purpose |
|---|---|
| `AsmValidationWorkTest.java` | Covers `AsmValidationWork` end-to-end. Builds northwind `PsmModel` from `Demo.fullDemo()`, runs `executePsm2AsmTransformation` into empty `AsmModel`, puts model in `TransformationContext("northwind")`, then runs work in `aNewSequentialFlow()` on `aNewWorkFlowEngine()`. Asserts `WorkReport` status equals `WorkStatus.COMPLETED`. Depends on `judo-tatami-psm2asm` and northwind demo model on test classpath. Puts `AsmValidationWorkParameter` with `TransformationMode.ETL` into the context so `AsmValidationWork` resolves `AsmValidationEtlEngineImpl` via `ServiceLoader`. See change: split-etl-artifacts. |
