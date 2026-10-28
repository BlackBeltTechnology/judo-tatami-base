# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/epsilon/transformations`

Top-level ETL orchestration scripts converting full or incremental RDBMS metamodels into Liquibase database changelog models.

| File | Purpose |
| --- | --- |
| `rdbmsIncrementalToLiquibase.etl` | Orchestrates multi-phase incremental database migration into eight distinct Liquibase changelog models (DB Checkup, DB Backup, Before Incremental, Data Update Before/After, Incremental, After Incremental, and Drop Backup) based on `RdbmsOperationMeta`. |
| `rdbmsToLiquibase.etl` | Orchestrates initial full-schema RDBMS to Liquibase transformation, initializing target `databaseChangeLog` with schema version context and importing table and field transformation rules. |
