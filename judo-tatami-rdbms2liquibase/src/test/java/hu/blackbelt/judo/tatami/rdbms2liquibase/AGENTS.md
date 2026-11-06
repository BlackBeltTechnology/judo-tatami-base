# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase`

Core (Epsilon-free, Zeta) tests: `Rdbms2LiquibaseWork` per dialect and ETL-missing failure. ETL, dual-engine, incremental and Excel-to-RDBMS tests live in `judo-tatami-rdbms2liquibase-etl`.

| File | Purpose |
| --- | --- |
| `Rdbms2LiquibaseWorkEtlMissingTest.java` | ETL mode without add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl`; asserts `getEngineType()` = `Rdbms2LiquibaseEtlEngine`. See change: split-etl-artifacts. |
| `Rdbms2LiquibaseWorkTest.java` | Verifies `Rdbms2LiquibaseWork` execution per dialect (`hsqldb`, `oracle`) within `TransformationContext` workflows managed by `WorkFlowEngine`. |
