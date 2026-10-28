# judo-tatami-psm-validation/src/main/java/hu/blackbelt/judo/tatami/psm/validation

Workflow works validating a `PsmModel` before PSM→ASM/measure lowering. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `PsmValidationWork.java` | Validates `PsmModel` via Epsilon EVL; `execute()` runs `PsmEpsilonValidator.validatePsm(logger, model, calculatePsmValidationScriptURI())` → see `PsmValidationWork.java.AGENTS.md` |
| `PsmValidationZetaWork.java` | Validates `PsmModel` on the Zeta engine; `PsmValidator.validate(log, psmModel)` → `List<ValidationResult>`, throws `PsmJavaValidationException` on any `Severity.ERROR` → see `PsmValidationZetaWork.java.AGENTS.md` |
