# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/zeta/incremental`

Modular Zeta incremental transformation phases generating Liquibase migration change sets.

| File | Purpose |
| --- | --- |
| `AbstractIncrementalSubTransformation.java` | Base class for incremental sub-transformations providing EMF model iteration (`all()`), change set cache lookup, trace collection, and RDBMS field formatting. |
| `AfterIncrementalZetaTransformation.java` | Sub-transformation restoring foreign keys, unique constraints, indexes, and not-null constraints after incremental schema changes. |
| `BeforeIncrementalZetaTransformation.java` | Sub-transformation dropping indexes, unique constraints, foreign keys, and not-null constraints prior to applying schema modifications. |
| `DataUpdateAfterZetaTransformation.java` | Sub-transformation generating SQL script references for data migrations after schema updates (mandatory reviews, foreign key migrations, and new column data). |
| `DataUpdateBeforeZetaTransformation.java` | Sub-transformation generating SQL script references for data migrations before schema updates (conversions, column type changes, and size reductions). |
| `DbBackupZetaTransformation.java` | Sub-transformation creating backup SQL file definitions for modified or deleted tables prior to schema alteration. |
| `DbCheckupZetaTransformation.java` | Sub-transformation constructing Liquibase `PreConditions` checking table and column presence and verifying backup table absence. |
| `DbDropBackupZetaTransformation.java` | Sub-transformation verifying backup table existence and generating `DropTable` changes to clean up temporary backup tables post-migration. |
| `IncrementalZetaTransformation.java` | Core sub-transformation generating DDL operations for creating, dropping, and renaming tables and columns, as well as modifying column data types. |
