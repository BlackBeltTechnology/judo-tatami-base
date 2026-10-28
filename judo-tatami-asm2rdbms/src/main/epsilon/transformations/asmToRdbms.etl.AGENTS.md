# `asmToRdbms.etl`

Root ETL of the ASM→RDBMS lowering. Imports `../operations/_importAll.eol` then `modules/class.etl`, `modules/attribute.etl`, `modules/reference.etl`, `modules/package.etl` — all rules live in the modules.
Its own `post` block rewrites `sqlName` on every `RDBMS!RdbmsElement` whose `uuid` matches a `RDBMS!NameMapping.fullyQualifiedName`, so `excelToNameMapping.etl` must have populated `NameMappings` into the same RDBMS model first; without it names stay as generated.
Commented-out block would have added inheritance-based `RdbmsForeignKey` per parent table — superseded by the module rules.