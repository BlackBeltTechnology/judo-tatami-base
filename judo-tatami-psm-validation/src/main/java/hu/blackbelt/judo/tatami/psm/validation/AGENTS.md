# judo-tatami-psm-validation/src/main/java/hu/blackbelt/judo/tatami/psm/validation

Workflow works validating a `PsmModel` before PSM→ASM/measure lowering. Core is Epsilon-free: `PsmValidationWork` picks Zeta (default) or delegates EVL to the optional `-etl` add-on via `PsmValidationEtlEngine`.

| File | Purpose |
|---|---|
| `PsmValidationEtlEngine.java` | Core contract of the EVL add-on; `ARTIFACT_COORDINATES` = `hu.blackbelt.judo.tatami:judo-tatami-psm-validation-etl`; `void validate(PsmModel psmModel, Logger log) throws Exception`; null `log` = impl logger. Impl in `judo-tatami-psm-validation-etl`. See change: split-etl-artifacts. |
| `PsmValidationWork.java` | Mode-aware validation work; ZETA/DUAL runs `PsmValidationZetaWork`, ETL calls the `PsmValidationEtlEngine` add-on via `EtlEngineRegistry`; nested `PsmValidationWorkParameter` (`psmValidationWorkParameter()`, `transformationMode`) → see `PsmValidationWork.java.AGENTS.md`. See change: split-etl-artifacts. |
| `PsmValidationZetaWork.java` | Validates `PsmModel` on the Zeta engine; `PsmValidator.validate(log, psmModel)` → `List<ValidationResult>`, throws `PsmJavaValidationException` on any `Severity.ERROR` → see `PsmValidationZetaWork.java.AGENTS.md` |
