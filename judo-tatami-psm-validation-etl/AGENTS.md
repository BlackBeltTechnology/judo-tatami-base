# judo-tatami-psm-validation-etl — module agent doctrine

EVL add-on for PSM validation. Epsilon reaches a classpath only through this artifact; core `judo-tatami-psm-validation` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../psm/validation/etl/PsmValidationEtlEngineImpl.java` — implements core `PsmValidationEtlEngine`; former EVL body of `PsmValidationWork` (`StringBuilderLogger` + `PsmEpsilonValidator.validatePsm`).
- `src/main/java/.../psm/validation/etl/osgi/PsmValidationEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.psm.validation.PsmValidationEtlEngine` — `ServiceLoader` registration.
- `src/test/java/.../psm/validation/PsmValidationWorkTest.java` — EVL test, moved from core; sets `TransformationMode.ETL` in the context.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `PsmValidationWork` resolves the engine only in ETL mode via `EtlEngineRegistry.require(PsmValidationEtlEngine.class, PsmValidationEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-psm-validation-etl`. ZETA/DUAL run `PsmValidationZetaWork` in core.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
