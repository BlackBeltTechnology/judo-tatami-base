## Why

Module-level slice (group 3, rank 7) of the multi-module change `openspec/changes/rdbms-incremental-without-epsilon`
in the judo-ng superproject (JNG-6446). `judo-rdbms-schema` needs the incremental RDBMS-to-Liquibase step without
Epsilon. The Zeta incremental (`Rdbms2LiquibaseIncrementalZetaTransformationV2`, 8 phases) had no review script
handling, no backup-data SQL, no previous/new model awareness and no tests, so it could not replace
`Rdbms2LiquibaseIncremental`. This slice brings it to parity with the ETL and makes the ETL an optional add-on, like
the full-schema stage (`Rdbms2LiquibaseEtlEngine`).

## What Changes

- `judo-tatami-rdbms2liquibase` (core, Epsilon-free): new SPI `Rdbms2LiquibaseIncrementalEtlEngine`
  (`ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl"`), parameter and result types
  (`Rdbms2LiquibaseIncrementalParameter`, `Rdbms2LiquibaseIncrementalResult`) and the entry point
  `Rdbms2LiquibaseIncrementalTransformation` (`TransformationMode`, default `ZETA`; `ETL` through
  `EtlEngineRegistry.require`).
- Zeta incremental parity: `ReviewResolver` handling (`sqlFile.eol` semantics), `backupPrefix`, Oracle
  `tableNameMaxSize` default 30 (62 otherwise), backup-data SQL (`BackupDataSqlGenerator`, a port of
  `generations/sql/main.egl` + `BackupTableData.egl`), previous / new model guards (`previousModel.contains(...)`,
  `newModel.contains(...)`) and exact-type rule sources.
- `judo-tatami-rdbms2liquibase-etl`: `Rdbms2LiquibaseIncrementalEtlEngineImpl` (delegates to
  `Rdbms2LiquibaseIncremental`), `META-INF/services` registration and an OSGi component.
- `Rdbms2LiquibaseIncremental` keeps its signatures.

## Impact

- Compatibility: additive. No public signature is removed. The ETL stays the oracle of the equivalence tests.
- Migration: callers that want the Zeta incremental use `Rdbms2LiquibaseIncrementalTransformation` (default mode).
  `ETL` mode needs `judo-tatami-rdbms2liquibase-etl` on the classpath (plain Java) or in Karaf.
- Rollback: revert the commit; the ETL path is untouched.
