# judo-tatami-psm2asm/src/test/java/hu/blackbelt/judo/tatami/psm2asm

PSM→ASM core tests (Epsilon-free, Zeta): work in workflow engine, Zeta trace, helper parity, ETL-missing failure. ETL and dual-engine tests live in `judo-tatami-psm2asm-etl`. See change: split-etl-artifacts.

| File | Purpose |
|---|---|
| `Psm2AsmHelperTest.java` | Pins `Psm2AsmHelper.getId()` to XMI-fragment parity with ETL. Asserts the returned id equals `entityType.eResource().getURIFragment(entityType)` and is not a positional `/`-path; calls `Psm2AsmHelper.clearCaches()` first so the static cache cannot mask a regression. |
| `Psm2AsmWorkEtlMissingTest.java` | ETL mode without add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-psm2asm-etl`; asserts `getEngineType()` = `Psm2AsmEtlEngine`. See change: split-etl-artifacts. |
| `Psm2AsmWorkTest.java` | Exercises `Psm2AsmWork` in the workflow engine; builds work with `new Psm2AsmWork(ctx)` (script-root ctor removed). `testSimpleWorkflow` asserts `WorkStatus.COMPLETED`, pulls `AsmModel`/`PsmModel` from context; absent models fail with `IllegalArgumentException`. See change: split-etl-artifacts. |
| `Psm2AsmZetaTraceTest.java` | Nine assertions on the Zeta `TransformationTrace` returned by `Psm2AsmZetaTransformation.execute()`: non-null, non-empty, `CreateEntityClass`/`CreateAttribute`/`CreateTransferObjectClass` source→target pairs, presence of rule names `CreateEntityClass`, `CreateAttribute`, `ModelToPackage`, JSON export and a byte-stream round trip, and that every `TraceEntry` has valid source and target. |
