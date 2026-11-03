# judo-tatami-psm2measure-etl — module agent doctrine

ETL add-on for the PSM→Measure stage. Epsilon reaches a classpath only through this artifact; core `judo-tatami-psm2measure` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../psm2measure/etl/Psm2Measure.java` — ETL entry point, moved from core; package `hu.blackbelt.judo.tatami.psm2measure.etl`.
- `src/main/java/.../psm2measure/etl/Psm2MeasureEtlEngineImpl.java` — implements core `Psm2MeasureEtlEngine` by delegating to `Psm2Measure`.
- `src/main/java/.../psm2measure/etl/osgi/Psm2MeasureEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureEtlEngine` — `ServiceLoader` registration.
- `src/main/epsilon/**` — ETL/EOL scripts, moved from core.
- `src/test/java/**` — ETL, dual-engine (ETL vs Zeta) and perf tests, moved from core.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `Psm2MeasureWork` resolves the engine only in `ETL`/`DUAL` mode via `EtlEngineRegistry.require(Psm2MeasureEtlEngine.class, Psm2MeasureEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-psm2measure-etl`.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
