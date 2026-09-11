# judo-tatami-psm-validation/src/main/java/hu/blackbelt/judo/tatami/psm/validation

Workflow works validating a `PsmModel` before PSM→ASM/measure lowering. Two interchangeable engines: Epsilon EVL and Zeta (native Java).

| File | Purpose |
|---|---|
| `PsmValidationWork.java` | Validates `PsmModel` via Epsilon EVL. Extends `AbstractTransformationWork`; `execute()` pulls `PsmModel` from `TransformationContext` and runs `PsmEpsilonValidator.validatePsm(logger, model, PsmEpsilonValidator.calculatePsmValidationScriptURI())`. PSM model must already sit in the context — missing one throws `IllegalArgumentException`. EVL output buffers into a `StringBuilderLogger` closed via try-with-resources. Local variable holding the PSM model is misnamed `esmModel` (ESM, not PSM) — cosmetic, does not affect behaviour. |
| `PsmValidationZetaWork.java` | Validates `PsmModel` on the Zeta engine, no Epsilon. `execute()` calls `PsmValidator.validate(log, psmModel)` and returns a `List<ValidationResult>`; filters `Severity.ERROR` entries and throws `PsmJavaValidationException` carrying the full result list plus a `constraintName|message` set when any error exists. Non-error severities (warning/info) pass through and never fail the work — callers expecting EVL's stricter failure surface must check results themselves. |
