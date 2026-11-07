# AGENTS.md — `judo-tatami-expression-asm-validation/src/test/java/hu/blackbelt/judo/tatami/expression/asm/validation`

Unit tests verifying Zeta workflow tasks and the missing-add-on guard (EVL test moved to `-etl`) for expression validation against ASM models.

| File | Purpose |
| --- | --- |
| `ExpressionValidationOnAsmWorkEtlMissingTest.java` | ETL-mode guard without add-on. `etlModeWithoutAddOnFailsNamingTheArtifact` expects `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-expression-asm-validation-etl`; `zetaModeRunsWithoutAddOn` runs the ZETA branch with no add-on on the classpath. See change: split-etl-artifacts. |
| `ExpressionValidationOnAsmZetaWorkTest.java` | Tests Zeta-based `ExpressionValidationOnAsmZetaWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. Test imports `Psm2Asm`/`Psm2Measure` ETL entry points from `-etl` artifacts (test scope, package `.etl`). See change: split-etl-artifacts. |
