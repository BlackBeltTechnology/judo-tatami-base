# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/zeta`

Zeta-based declarative transformations, incremental pipelines, rule constants, and helper utilities converting RDBMS models to Liquibase changelogs.

| File | Purpose |
| --- | --- |
| `CLAUDE.md` | Persistent Claude memory scratchpad for recent activity tracking in this package. |
| `Rdbms2LiquibaseHelper.java` | Shared utility methods formatting RDBMS fields into Liquibase column type strings (`toFieldDefinition`) and truncating string lengths (`abbreviate`). |
| `Rdbms2LiquibaseIncrementalZetaTransformation.java` | Monolithic Zeta incremental transformation declaring transformation rules and guards for table, column, constraint, index, and backup migrations. Exports `execute()`. |
| `Rdbms2LiquibaseIncrementalZetaTransformationV2.java` | Multi-phase orchestrator for incremental migration across sub-transformation phases (DB Checkup, DB Backup, Before/After Incremental, and Data Updates). Exports `execute()`, `getMissingReviewScripts()`, `getBackupDataSqlFiles()`, `getTableNameMaxSize()`. Builder takes `reviewResolver` (required unless `sqlScriptPath`; then `FileSystemReviewResolver`; else `IllegalArgumentException` `One of ReviewResolver or scriptPath have to be set`), `backupTableNamePrefix` (default `BACKUP`), `tableNameMaxSize` (default 30 oracle / 62). Backup-data SQL generated after the phases by `BackupDataSqlGenerator`. Output equals the ETL (change set order aside). See change: rdbms-incremental-without-epsilon. |
| `Rdbms2LiquibaseRuleNames.java` | String constants identifying transformation rule names matching ETL definitions for full and incremental RDBMS to Liquibase migrations. |
| `Rdbms2LiquibaseZetaTransformation.java` | Executes full-schema Zeta transformation from `RdbmsModel` to `LiquibaseModel` using registered `TableRules` and `FieldRules`. Exports `execute()`. |
