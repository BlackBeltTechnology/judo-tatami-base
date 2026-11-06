# judo-tatami-expression-psm-validation-etl/src/test/java/hu/blackbelt/judo/tatami/expression/psm/validation

EVL test of expression-on-PSM validation, moved from core `judo-tatami-expression-psm-validation` (same package). Runs against the add-on via `ServiceLoader` discovery.

| File | Purpose |
|---|---|
| `ExpressionValidationOnPsmWorkTest.java` | Tests EVL-based `ExpressionValidationOnPsmWork` execution within a sequential workflow pipeline using Northwind models. Annotated with `@Disabled`. Puts `ExpressionValidationOnPsmWorkParameter` with `TransformationMode.ETL` into the context so `ExpressionValidationOnPsmWork` resolves `ExpressionValidationOnPsmEtlEngineImpl` via `ServiceLoader`. See change: split-etl-artifacts. |
