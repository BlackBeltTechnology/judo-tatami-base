# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase`

Core (Epsilon-free, Zeta) tests: `Rdbms2LiquibaseWork` per dialect, ETL-missing failure and incremental entry point validation. ETL, dual-engine, incremental and Excel-to-RDBMS tests live in `judo-tatami-rdbms2liquibase-etl`.

| File | Purpose |
| --- | --- |
| `Rdbms2LiquibaseIncrementalTransformationTest.java` | Incremental entry point without `-etl`: Zeta without `ReviewResolver`/`sqlScriptPath` → `IllegalArgumentException` `One of ReviewResolver or scriptPath have to be set` (TP-18, also default mode); `ETL` mode → `EtlEngineMissingException` naming the add-on coordinates, `getEngineType()` = `Rdbms2LiquibaseIncrementalEtlEngine`, phase models untouched (TP-19). See change: rdbms-incremental-without-epsilon. |
| `Rdbms2LiquibaseWorkEtlMissingTest.java` | ETL mode without add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl`; asserts `getEngineType()` = `Rdbms2LiquibaseEtlEngine`. See change: split-etl-artifacts. |
| `Rdbms2LiquibaseWorkTest.java` | Verifies `Rdbms2LiquibaseWork` execution per dialect (`hsqldb`, `oracle`) within `TransformationContext` workflows managed by `WorkFlowEngine`. |
