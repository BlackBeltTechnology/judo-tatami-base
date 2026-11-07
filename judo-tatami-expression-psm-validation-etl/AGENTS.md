# judo-tatami-expression-psm-validation-etl — module agent doctrine

EVL add-on for expression-on-PSM validation. Epsilon reaches a classpath only through this artifact; core `judo-tatami-expression-psm-validation` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../expression/psm/validation/etl/ExpressionValidationOnPsmEtlEngineImpl.java` — implements core `ExpressionValidationOnPsmEtlEngine`; former EVL body of `ExpressionValidationOnPsmWork` (`StringBuilderLogger` + `ExpressionValidatorOnPsm.validateExpressionOnPsm`).
- `src/main/java/.../expression/psm/validation/etl/osgi/ExpressionValidationOnPsmEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.expression.psm.validation.ExpressionValidationOnPsmEtlEngine` — `ServiceLoader` registration.
- `src/test/java/.../expression/psm/validation/ExpressionValidationOnPsmWorkTest.java` — EVL test, moved from core; sets `TransformationMode.ETL` in the context.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `ExpressionValidationOnPsmWork` resolves the engine only in ETL mode via `EtlEngineRegistry.require(ExpressionValidationOnPsmEtlEngine.class, ExpressionValidationOnPsmEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-expression-psm-validation-etl`. ZETA/DUAL run `ExpressionValidationOnPsmZetaWork` in core.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
