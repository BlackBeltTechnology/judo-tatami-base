# `excelToTypeMapping.etl`

Loads the ASM-type→SQL-type table: rule `Excel2TypeMapping` transforms `TYPEMAPPING!Mapping` to `RDBMS!TypeMapping` into a fresh `RDBMS!TypeMappings` root.
Maps `Judo`→`asmType`, `Type`→`rdbmsType`, `JdbcType`→`rdbmsJdbcType`, parses `SqlType` as integer, and substitutes the string `"-1"` for blank `Size`/`Precision`/`Scale`.
Rows without `Judo` are skipped. `field.eol`'s `fillType` looks mappings up by `asmType` and throws when absent, and treats a `#annotation:key` size/precision/scale value as an indirection — that convention must be preserved by the spreadsheet.