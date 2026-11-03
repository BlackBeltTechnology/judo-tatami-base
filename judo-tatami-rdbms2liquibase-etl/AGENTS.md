# judo-tatami-rdbms2liquibase-etl — module agent doctrine

ETL add-on for the RDBMS→Liquibase stage. Epsilon reaches a classpath only through this artifact; core `judo-tatami-rdbms2liquibase` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../rdbms2liquibase/etl/Rdbms2Liquibase.java` — ETL entry point, moved from core; package `hu.blackbelt.judo.tatami.rdbms2liquibase.etl`.
- `src/main/java/.../rdbms2liquibase/etl/Rdbms2LiquibaseEtlEngineImpl.java` — implements core `Rdbms2LiquibaseEtlEngine` by delegating to `Rdbms2Liquibase`.
- `src/main/java/.../rdbms2liquibase/etl/osgi/Rdbms2LiquibaseEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseEtlEngine` — `ServiceLoader` registration.
- `src/main/epsilon/**` — ETL/EOL scripts, moved from core.
- `src/test/java/**` — ETL, dual-engine (ETL vs Zeta) and perf tests, moved from core.
- `etl/Rdbms2LiquibaseIncremental.java`, `etl/Rdbms2LiquibaseIncrementalWork.java` — ETL/EGL-only incremental changelog pipeline, moved from core; only main-code caller is `judo-rdbms-schema-incremental`.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `Rdbms2LiquibaseWork` resolves the engine only in `ETL`/`DUAL` mode via `EtlEngineRegistry.require(Rdbms2LiquibaseEtlEngine.class, Rdbms2LiquibaseEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl`.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
