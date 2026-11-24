# judo-tatami-rdbms2liquibase-etl/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase

RDBMS→Liquibase ETL, dual-engine and incremental tests (moved from `judo-tatami-rdbms2liquibase`); import `hu.blackbelt.judo.tatami.rdbms2liquibase.etl.*`.

| File | Purpose |
|---|---|
| `Excel2RdbmsTest.java` | Tests Excel-to-RDBMS ETL transformation execution against live database connections across HSQLDB, PostgreSQL, Oracle, and MySQL via `RdbmsDatasourceByClassExtension`. |
| `IncrementalEquivalenceFixture.java` | Glue of the incremental ETL-vs-Zeta tests (harness copied from `Excel2RdbmsTest`): `excelPair(dialect[, longTableNames])`, `etlIncrementalModel` / `javaIncrementalModel`, `runEtl` (oracle `Rdbms2LiquibaseIncremental`, `parallel=false`), `runEntryPoint(mode, ...)`, `diffPhases` (structural comparator: `databaseChangeLog` roots only, change sets and changes as bags — the ETL order is a model-cache artefact, ETL leftover roots ignored), `serialize`. See change: rdbms-incremental-without-epsilon. |
| `Rdbms2LiquibaseContentTest.java` | Verifies detailed schema generation (tables, junction tables, primary/foreign keys, indices) across ETL and Zeta modes using `ModelComparator`. |
| `Rdbms2LiquibaseIncrementalEquivalenceTest.java` | ETL = Zeta on the Excel fixture (`hsqldb`, `postgresql`): 8 phases (TP-15, also on the Java diff of judo-meta-rdbms), review decision table absent/empty/present (TP-16), backup-data SQL with default and custom prefix (TP-17), `tableNameMaxSize` 30 / 62 / override on long table names (TP-20), Zeta determinism (TP-21), ETL mode delegation to the incremental SPI, discovered and registered (TP-22), comparator guard. See change: rdbms-incremental-without-epsilon. |
| `Rdbms2LiquibaseIncrementalWorkTest.java` | Tests `Rdbms2LiquibaseIncrementalWork` task execution across dialects (`hsqldb`, `postgres`, `oracle`) within `SequentialFlow` workflows. |
| `Rdbms2LiquibaseTest.java` | End-to-end transformation test from PSM via ASM to RDBMS and Liquibase changelogs; executes changelog against in-memory HSQLDB and verifies ETL/Zeta equivalence. |
