# AGENTS.md — hu.blackbelt.judo.tatami.asm.validation (test)

| File | Purpose |
|---|---|
| `AsmValidationWorkTest.java` | Covers `AsmValidationWork` end-to-end. Builds northwind `PsmModel` from `Demo.fullDemo()`, runs `executePsm2AsmTransformation` into empty `AsmModel`, puts model in `TransformationContext("northwind")`, then runs work in `aNewSequentialFlow()` on `aNewWorkFlowEngine()`. Asserts `WorkReport` status equals `WorkStatus.COMPLETED`. Depends on `judo-tatami-psm2asm` and northwind demo model on test classpath. |
| `AsmValidationZetaWorkTest.java` | Same northwind PSM→ASM fixture as `AsmValidationWorkTest`, but exercises `AsmValidationZetaWork` (native Java rules). Asserts sequential flow `WorkReport` reaches `WorkStatus.COMPLETED`, pinning Zeta/EVL parity for the empty ASM rule set. |
