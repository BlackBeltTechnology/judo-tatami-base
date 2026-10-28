# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/epsilon/transformations/rdbms/utils`

EOL helper operations querying and comparing RDBMS schema elements (models, tables, and fields).

| File | Purpose |
| --- | --- |
| `field.eol` | Extends `RDBMS!RdbmsField` with difference predicates (`isSqlNameChanged`, `isMandatoryChanged`, `isSizeChanged`, `isAnyChanged`), container lookup `table()`, and SQL string type formatter `toFieldDefinition()`. |
| `model.eol` | Extends `RDBMS!RdbmsModel` with table membership and existence queries (`tableExists`, `tableOf`, `contains`), and exports string utility function `abbreviate(text, maxLength)`. |
| `table.eol` | Extends `RDBMS!RdbmsTable` with existence and lookup operations for fields, indexes, and unique constraints matching by UUID, along with name mutation check `isSqlNameChanged`. |
