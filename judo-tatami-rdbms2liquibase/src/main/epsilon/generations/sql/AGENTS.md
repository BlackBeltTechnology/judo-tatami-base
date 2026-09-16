# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/epsilon/generations/sql`

EGL code generation templates producing table backup DDL SQL scripts prior to destructive schema operations.

| File | Purpose |
| --- | --- |
| `BackupTableData.egl` | Generates `CREATE TABLE ... AS (SELECT * FROM ...)` DDL statement with optional `WITH DATA` clause for HSQLDB dialect compatibility. |
| `main.egl` | Iterates over `RDBMS!RdbmsDeleteTableOperation` and `RDBMS!RdbmsModifyTableOperation` models, computes abbreviated backup table names, populates `BackupTableData.egl`, and writes named `.sql` files into `backupDataSqlFiles`. |
