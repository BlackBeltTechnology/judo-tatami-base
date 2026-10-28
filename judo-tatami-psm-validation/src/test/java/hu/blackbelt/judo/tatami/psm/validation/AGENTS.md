# AGENTS.md — `judo-tatami-psm-validation/src/test/java/hu/blackbelt/judo/tatami/psm/validation`

Unit tests verifying EVL and Zeta workflow tasks for PSM model validation.

| File | Purpose |
| --- | --- |
| `PsmValidationWorkTest.java` | Tests EVL-based `PsmValidationWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. |
| `PsmValidationZetaWorkTest.java` | Tests Zeta-based `PsmValidationZetaWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. |
