# judo-tatami-psm2asm-etl — module agent doctrine

ETL add-on for the PSM→ASM stage. Epsilon reaches a classpath only through this artifact; core `judo-tatami-psm2asm` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../psm2asm/etl/Psm2Asm.java` — ETL entry point, moved from core; package `hu.blackbelt.judo.tatami.psm2asm.etl`.
- `src/main/java/.../psm2asm/etl/Psm2AsmEtlEngineImpl.java` — implements core `Psm2AsmEtlEngine` by delegating to `Psm2Asm`.
- `src/main/java/.../psm2asm/etl/osgi/Psm2AsmEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.psm2asm.Psm2AsmEtlEngine` — `ServiceLoader` registration.
- `src/main/epsilon/**` — ETL/EOL scripts, moved from core.
- `src/test/java/**` — ETL, dual-engine (ETL vs Zeta) and perf tests, moved from core.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `Psm2AsmWork` resolves the engine only in `ETL`/`DUAL` mode via `EtlEngineRegistry.require(Psm2AsmEtlEngine.class, Psm2AsmEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-psm2asm-etl`.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
