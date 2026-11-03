# AGENTS.md — `judo-tatami-expression-psm-validation/src/test/java/hu/blackbelt/judo/tatami/expression/psm/validation`

Unit tests verifying Zeta workflow tasks and the missing-add-on guard (EVL test moved to `-etl`) for expression validation against PSM models.

| File | Purpose |
| --- | --- |
| `ExpressionValidationOnPsmWorkEtlMissingTest.java` | ETL-mode guard without add-on. `etlModeWithoutAddOnFailsNamingTheArtifact` expects `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-expression-psm-validation-etl`; `zetaModeRunsWithoutAddOn` runs the ZETA branch with no add-on on the classpath. See change: split-etl-artifacts. |
| `ExpressionValidationOnPsmZetaWorkTest.java` | Tests Zeta-based `ExpressionValidationOnPsmZetaWork` execution within a sequential workflow pipeline using Northwind models. Annotated with `@Disabled`. Test imports `Psm2Asm`/`Psm2Measure` ETL entry points from `-etl` artifacts (test scope, package `.etl`). See change: split-etl-artifacts. |
