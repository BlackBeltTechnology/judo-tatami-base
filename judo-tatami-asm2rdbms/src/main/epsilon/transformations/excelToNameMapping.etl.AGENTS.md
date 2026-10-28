# `excelToNameMapping.etl`

Loads the SQL-name override sheet: rule `Excel2NameMapping` transforms `NAMEMAPPING!NameMapping` to `RDBMS!NameMapping`, copying `FullyQualifiedName`→`fullyQualifiedName` and `SqlName`→`rdbmsName`, appending to a fresh `RDBMS!NameMappings` root built in `pre`.
Rows without `FullyQualifiedName` are skipped by the guard.
Output is consumed by `asmToRdbms.etl`'s `post` block, matched on element `uuid`, so an FQ name that no element carries is silently ignored.