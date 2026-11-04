# judo-tatami-asm2rdbms-etl — module agent doctrine

ETL add-on for the ASM→RDBMS stage. Epsilon reaches a classpath only through this artifact; core `judo-tatami-asm2rdbms` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../asm2rdbms/etl/Asm2Rdbms.java` — ETL entry point, moved from core; package `hu.blackbelt.judo.tatami.asm2rdbms.etl`.
- `src/main/java/.../asm2rdbms/etl/Asm2RdbmsEtlEngineImpl.java` — implements core `Asm2RdbmsEtlEngine` by delegating to `Asm2Rdbms`.
- `src/main/java/.../asm2rdbms/etl/osgi/Asm2RdbmsEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsEtlEngine` — `ServiceLoader` registration.
- `src/main/epsilon/**` — ETL/EOL scripts, moved from core.
- `src/test/java/**` — ETL, dual-engine (ETL vs Zeta) and perf tests, moved from core.
- `model/*.xlsx`, `model/*.xml` — Excel workbooks + spreadsheet configs, moved from core; parity reference only (`ExcelMappingParityTest`). Runtime mapping data are the CSV files in core `judo-tatami-asm2rdbms/model/`.
- `src/test/java/.../asm2rdbms/ExcelMappingEtlReference.java`, `ExcelMappingParityTest.java` — prove the core CSV loader equals the original `excelTo*.etl` output (hsqldb, postgresql, oracle).

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `Asm2RdbmsWork` resolves the engine only in `ETL`/`DUAL` mode via `EtlEngineRegistry.require(Asm2RdbmsEtlEngine.class, Asm2RdbmsEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm2rdbms-etl`.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
