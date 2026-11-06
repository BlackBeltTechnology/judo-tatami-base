# judo-tatami-asm-validation-etl — module agent doctrine

EVL add-on for ASM validation. Epsilon reaches a classpath only through this artifact; core `judo-tatami-asm-validation` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../asm/validation/etl/AsmValidationEtlEngineImpl.java` — implements core `AsmValidationEtlEngine`; former EVL body of `AsmValidationWork` (`StringBuilderLogger` + `AsmEpsilonValidator.validateAsm`).
- `src/main/java/.../asm/validation/etl/osgi/AsmValidationEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.asm.validation.AsmValidationEtlEngine` — `ServiceLoader` registration.
- `src/test/java/.../asm/validation/AsmValidationWorkTest.java` — EVL test, moved from core; sets `TransformationMode.ETL` in the context.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `AsmValidationWork` resolves the engine only in ETL mode via `EtlEngineRegistry.require(AsmValidationEtlEngine.class, AsmValidationEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm-validation-etl`. ZETA/DUAL run `AsmValidationZetaWork` in core.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
