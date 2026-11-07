# AGENTS.md — hu.blackbelt.judo.tatami.asm.validation (test)

| File | Purpose |
|---|---|
| `AsmValidationWorkEtlMissingTest.java` | ETL-mode guard without add-on. `etlModeWithoutAddOnFailsNamingTheArtifact` expects `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm-validation-etl`; `zetaModeRunsWithoutAddOn` runs the ZETA branch with no add-on on the classpath. See change: split-etl-artifacts. |
| `AsmValidationZetaWorkTest.java` | Same northwind PSM→ASM fixture as `AsmValidationWorkTest`, but exercises `AsmValidationZetaWork` (native Java rules). Asserts sequential flow `WorkReport` reaches `WorkStatus.COMPLETED`, pinning Zeta/EVL parity for the empty ASM rule set. Test imports `Psm2Asm` ETL entry point from `-etl` artifacts (test scope, package `.etl`). See change: split-etl-artifacts. |
