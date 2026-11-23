## 1. judo-tatami-base (rank 7)

- [x] 1.1 Core: `Rdbms2LiquibaseIncrementalEtlEngine`, parameter / result types, `Rdbms2LiquibaseIncrementalTransformation`.
- [x] 1.2 Zeta V2 and `DataUpdate{Before,After}ZetaTransformation`: review resolution, `backupPrefix`,
      `tableNameMaxSize`, backup-data SQL, previous / new model guards.
- [x] 1.3 `-etl`: `Rdbms2LiquibaseIncrementalEtlEngineImpl`, `META-INF/services`, OSGi component.
- [x] 1.4 Tests: ETL-vs-Zeta equivalence (8 phases, review decision table, backup SQL, table name limits),
      determinism, ETL SPI delegation, argument validation, missing add-on.
- [x] 1.5 Spec delta (`rdbms2liquibase`, `zeta-parity`) and `AGENTS.md` rows.
