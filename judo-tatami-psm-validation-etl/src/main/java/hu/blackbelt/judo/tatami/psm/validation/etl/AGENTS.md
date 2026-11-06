# judo-tatami-psm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/psm/validation/etl
EVL engine adapter of PSM validation. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `PsmValidationEtlEngineImpl.java` | Implements core `PsmValidationEtlEngine`; `validate(PsmModel psmModel, Logger log)` runs `PsmEpsilonValidator.validatePsm` inside a `StringBuilderLogger`; null `log` falls back to the impl logger. Former EVL body of `PsmValidationWork`. Loaded by `ServiceLoader` or `PsmValidationEtlEngineComponent`. See change: split-etl-artifacts. |
