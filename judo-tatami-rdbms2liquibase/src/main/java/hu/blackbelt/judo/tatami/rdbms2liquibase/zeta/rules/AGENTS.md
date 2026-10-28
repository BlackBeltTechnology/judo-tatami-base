# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/zeta/rules`

Zeta transformation rule declarations mapping RDBMS metamodel elements into Liquibase change sets and schema operations.

| File | Purpose |
| --- | --- |
| `FieldRules.java` | Zeta transformation rules mapping `RdbmsField` instances into Liquibase `Column` elements, primary keys, foreign keys, not-null constraints, and indices. Exports `fieldToColumn()`, `identifierFieldToPkConstraint()`, `valueFieldToColumn()`, `indexToCreateIndex()`, `isMandatoryField()`, more in sidecar. Bounds `source = RdbmsField.class, target = Column.class`. → see `FieldRules.java.AGENTS.md` |
| `TableRules.java` | Zeta transformation rules mapping `RdbmsTable` instances into Liquibase `CreateTable` operations and change sets. Exports `tableToCreateTable()`, `tableToCreateTableChangeSet()`, `tableToCreateForeignKeysChangeSet()`, `tableToAddNotNullChangeSet()`, `hasForeignKeys()`, `hasMandatoryFields()`. Context bounds `source = RdbmsTable.class, target = CreateTable.class`. |
