# AGENTS.md — `judo-tatami-psm-validation/src/test/java/hu/blackbelt/judo/tatami/psm/validation`

Unit tests verifying Zeta workflow tasks and the missing-add-on guard (EVL test moved to `-etl`) for PSM model validation.

| File | Purpose |
| --- | --- |
| `PsmValidationWorkEtlMissingTest.java` | ETL-mode guard without add-on. `etlModeWithoutAddOnFailsNamingTheArtifact` expects `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-psm-validation-etl`; `zetaModeRunsWithoutAddOn` runs the ZETA branch with no add-on on the classpath. See change: split-etl-artifacts. |
| `PsmValidationZetaWorkTest.java` | Tests Zeta-based `PsmValidationZetaWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. |
