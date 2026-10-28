# `class.etl`

Maps `ASM!EClass` onto `RDBMS!RdbmsTable` plus nine mandatory system columns. `EClassToRdbmsTable` registers the table on the root package's `rootPackegeToModel` equivalent, sets `sqlName` from `tableSqlName()`, `name` from the classifier FQName, and links `parents` to the `EClassToRdbmsTable` equivalents of entity supertypes.

Sibling rules add fixed columns: `ID` (`RdbmsIdentifierField`, `java.util.UUID`, also set as `table.primaryKey`), `TYPE` (mandatory String), `VERSION` (Integer), `CREATE_USERNAME`, `CREATE_USER_ID`, `CREATE_TIMESTAMP`, `UPDATE_USERNAME`, `UPDATE_USER_ID`, `UPDATE_TIMESTAMP`.

Every rule guards on `s.isEntityType()`, so a non-entity class produces no table. The system-column SQL names are reserved — `attribute.eol` maps user attributes named `id`/`type`/`version` to `DOMAIN_*` to avoid the clash.