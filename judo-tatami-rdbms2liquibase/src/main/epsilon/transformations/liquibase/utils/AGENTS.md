# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/epsilon/transformations/liquibase/utils`

EOL utility operations for managing Liquibase changeSets and SQL script attachments.

| File | Purpose |
| --- | --- |
| `changeSet.eol` | Extends `LIQUIBASE!databaseChangeLog` with query and factory operations. Exports `getChangeSet(id)`, `changeSetExists(id)`, and `getOrCreateChangeSet(id, logicalFilePath, dbms, context)`. |
| `sqlFile.eol` | Extends `LIQUIBASE!SqlFile` with `addSqlFileToChangeSet(changeSet, sqlName, msg)` to check script existence via `reviewResolver`, populating matching paths or logging missing scripts into `missingReviewScripts`. |
