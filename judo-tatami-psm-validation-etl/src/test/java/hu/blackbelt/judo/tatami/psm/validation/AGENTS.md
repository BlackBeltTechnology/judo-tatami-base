# judo-tatami-psm-validation-etl/src/test/java/hu/blackbelt/judo/tatami/psm/validation

EVL test of PSM validation, moved from core `judo-tatami-psm-validation` (same package). Runs against the add-on via `ServiceLoader` discovery.

| File | Purpose |
|---|---|
| `PsmValidationWorkTest.java` | Tests EVL-based `PsmValidationWork` execution within a sequential workflow pipeline using Northwind models. Asserts `WorkStatus.COMPLETED`. Puts `PsmValidationWorkParameter` with `TransformationMode.ETL` into the context so `PsmValidationWork` resolves `PsmValidationEtlEngineImpl` via `ServiceLoader`. See change: split-etl-artifacts. |
