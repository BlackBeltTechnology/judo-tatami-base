# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/epsilon/transformations/liquibase/modules`

Modular ETL transformation rules generating specific Liquibase changelog elements for full and incremental database migrations.

| File | Purpose |
| --- | --- |
| `afterIncremental.etl` | Generates post-migration Liquibase constraints (`AddForeignKeyConstraints`, `AddNotNullConstraints`, and `AddUniqueConstraints`) targeting `afterIncrementalModel`. |
| `beforeIncremental.etl` | Generates pre-migration Liquibase drop operations (`DropIndexes`, `DropUniqueConstraints`, `DropNotNullConstraints`, and `DropForeignKeyConstraints`) targeting `beforeIncrementalModel`. |
| `dataUpdateAfterIncremental.etl` | Generates `SqlFile` change sets in `dataUpdateAfterIncrementalModel` for mandatory reviews, field creations, and type transitions to foreign keys. |
| `dataUpdateBeforeIncremental.etl` | Generates `SqlFile` change sets in `dataUpdateBeforeIncrementalModel` for column conversions, size reductions, and table merge/split data migrations. |
| `dbBackup.etl` | Generates `SqlFile` backup change sets in `dbBackupModel` for deleted and modified tables using configured SQL dialects and table prefixes. |
| `dbCheckup.etl` | Generates `preConditions` checks (`TableExists`, `ColumnExists`, and negated backup table checks) in `dbCheckupModel` to verify schema state before running incremental changes. |
| `dbDropBackup.etl` | Generates `DropTable` operations and post-verification preconditions in `dbDropBackupModel` to clean up temporary backup tables. |
| `field.etl` | Maps `RDBMS!RdbmsField` instances to Liquibase `Column`, `AddForeignKeyConstraint`, and `AddNotNullConstraint` objects for full schema generation. |
| `incremental.etl` | Maps structural schema operations (`RdbmsCreateTableOperation`, `RdbmsDeleteTableOperation`, `RdbmsModifyTableOperation`, and field operations) into Liquibase DDL changes. |
| `table.etl` | Maps `RDBMS!RdbmsTable` instances into Liquibase `CreateTable` definitions and associated `ChangeSet` containers for table creation, foreign keys, and not-null constraints. |
