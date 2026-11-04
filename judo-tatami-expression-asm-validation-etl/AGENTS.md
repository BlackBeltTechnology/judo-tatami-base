# judo-tatami-expression-asm-validation-etl — module agent doctrine

EVL add-on for expression-on-ASM validation. Epsilon reaches a classpath only through this artifact; core `judo-tatami-expression-asm-validation` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../expression/asm/validation/etl/ExpressionValidationOnAsmEtlEngineImpl.java` — implements core `ExpressionValidationOnAsmEtlEngine`; former EVL body of `ExpressionValidationOnAsmWork` (`StringBuilderLogger` + `ExpressionValidatorOnAsm.validateExpressionOnAsm`).
- `src/main/java/.../expression/asm/validation/etl/osgi/ExpressionValidationOnAsmEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.expression.asm.validation.ExpressionValidationOnAsmEtlEngine` — `ServiceLoader` registration.
- `src/test/java/.../expression/asm/validation/ExpressionValidationOnAsmWorkTest.java` — EVL test, moved from core; sets `TransformationMode.ETL` in the context.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `ExpressionValidationOnAsmWork` resolves the engine only in ETL mode via `EtlEngineRegistry.require(ExpressionValidationOnAsmEtlEngine.class, ExpressionValidationOnAsmEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-expression-asm-validation-etl`. ZETA/DUAL run `ExpressionValidationOnAsmZetaWork` in core.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
