# judo-tatami-asm-validation-etl/src/main/java/hu/blackbelt/judo/tatami/asm/validation/etl
EVL engine adapter of ASM validation. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `AsmValidationEtlEngineImpl.java` | Implements core `AsmValidationEtlEngine`; `validate(AsmModel asmModel, Logger log)` runs `AsmEpsilonValidator.validateAsm` inside a `StringBuilderLogger`; null `log` falls back to the impl logger. Former EVL body of `AsmValidationWork`. Loaded by `ServiceLoader` or `AsmValidationEtlEngineComponent`. See change: split-etl-artifacts. |
