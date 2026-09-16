# AGENTS.md — asm2rdbms/epsilon/transformations

ETL entry points: the ASM→RDBMS lowering plus the three Excel-sourced configuration loaders it depends on.

| File | Purpose |
|---|---|
| `asmToRdbms.etl` | Root ETL of ASM→RDBMS lowering; imports `../operations/_importAll.eol` + module ETLs; `post` block rewrites `sqlName` on `RDBMS!RdbmsElement` by `uuid` via `RDBMS!NameMapping` → see `asmToRdbms.etl.AGENTS.md` |
| `excelToNameMapping.etl` | Loads SQL-name override sheet: rule `Excel2NameMapping` `NAMEMAPPING!NameMapping` → `RDBMS!NameMapping` (`FullyQualifiedName`→`fullyQualifiedName`, `SqlName`→`rdbmsName`, fresh `RDBMS!NameMappings` in `pre`); consumed by `asmToRdbms.etl` `post` → see `excelToNameMapping.etl.AGENTS.md` |
| `excelToRules.etl` | Loads relation-shape decision table: rule `Excel2Rule` `RULEMAPPING!TableMappingRule` → `RDBMS!Rule` into fresh `RDBMS!Rules` root; `ruleMapping()` resolves via `RDBMS!Rules.all.first` → see `excelToRules.etl.AGENTS.md` |
| `excelToTypeMapping.etl` | Loads ASM-type→SQL-type table: rule `Excel2TypeMapping` `TYPEMAPPING!Mapping` → `RDBMS!TypeMapping` (`Judo`→`asmType`, `Type`→`rdbmsType`, `JdbcType`→`rdbmsJdbcType`, `"-1"` for blank size) into fresh `RDBMS!TypeMappings` → see `excelToTypeMapping.etl.AGENTS.md` |
