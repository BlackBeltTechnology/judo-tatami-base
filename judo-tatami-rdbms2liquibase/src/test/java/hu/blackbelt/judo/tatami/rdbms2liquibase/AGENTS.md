# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase`

Unit, workflow, and model content tests for RDBMS-to-Liquibase transformations and Excel-to-RDBMS model generation.

| File | Purpose |
| --- | --- |
| `Excel2RdbmsTest.java` | Tests Excel-to-RDBMS ETL transformation execution against live database connections across HSQLDB, PostgreSQL, Oracle, and MySQL via `RdbmsDatasourceByClassExtension`. |
| `Rdbms2LiquibaseContentTest.java` | Verifies detailed schema generation (tables, junction tables, primary/foreign keys, indices) across ETL and Zeta modes using `ModelComparator`. |
| `Rdbms2LiquibaseIncrementalWorkTest.java` | Tests `Rdbms2LiquibaseIncrementalWork` task execution across dialects (`hsqldb`, `postgres`, `oracle`) within `SequentialFlow` workflows. |
| `Rdbms2LiquibaseTest.java` | End-to-end transformation test from PSM via ASM to RDBMS and Liquibase changelogs; executes changelog against in-memory HSQLDB and verifies ETL/Zeta equivalence. |
| `Rdbms2LiquibaseWorkTest.java` | Verifies `Rdbms2LiquibaseWork` execution per dialect (`hsqldb`, `oracle`) within `TransformationContext` workflows managed by `WorkFlowEngine`. |
