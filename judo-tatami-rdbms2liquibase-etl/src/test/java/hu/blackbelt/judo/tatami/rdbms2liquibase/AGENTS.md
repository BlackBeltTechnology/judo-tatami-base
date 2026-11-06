# judo-tatami-rdbms2liquibase-etl/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase

RDBMS→Liquibase ETL, dual-engine and incremental tests (moved from `judo-tatami-rdbms2liquibase`); import `hu.blackbelt.judo.tatami.rdbms2liquibase.etl.*`.

| File | Purpose |
|---|---|
| `Excel2RdbmsTest.java` | Tests Excel-to-RDBMS ETL transformation execution against live database connections across HSQLDB, PostgreSQL, Oracle, and MySQL via `RdbmsDatasourceByClassExtension`. |
| `Rdbms2LiquibaseContentTest.java` | Verifies detailed schema generation (tables, junction tables, primary/foreign keys, indices) across ETL and Zeta modes using `ModelComparator`. |
| `Rdbms2LiquibaseIncrementalWorkTest.java` | Tests `Rdbms2LiquibaseIncrementalWork` task execution across dialects (`hsqldb`, `postgres`, `oracle`) within `SequentialFlow` workflows. |
| `Rdbms2LiquibaseTest.java` | End-to-end transformation test from PSM via ASM to RDBMS and Liquibase changelogs; executes changelog against in-memory HSQLDB and verifies ETL/Zeta equivalence. |
