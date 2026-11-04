# judo-tatami-asm2rdbms/model
CSV mapping tables (converted from the `.xlsx` workbooks, now in `judo-tatami-asm2rdbms-etl/model/`). Loaded by `ExcelMappingModels2Rdbms` to generate `mapping-<dialect>-rdbms.model`.
| File | Purpose |
|---|---|
| `RDBMS_Data_Types_Hsqldb.csv` | Type mapping (ASM type → SQL type) for hsqldb; CSV form of the former `.xlsx`. Read by `ExcelMappingModels2Rdbms.typeMappings`. See change: split-etl-artifacts. |
| `RDBMS_Data_Types_Oracle.csv` | Type mapping (ASM type → SQL type) for oracle; CSV form of the former `.xlsx`. Numeric untyped cells render as doubles (parity). See change: split-etl-artifacts. |
| `RDBMS_Data_Types_Postgresql.csv` | Type mapping (ASM type → SQL type) for postgresql; CSV form of the former `.xlsx`. Numeric untyped cells render as doubles (parity). See change: split-etl-artifacts. |
| `RDBMS_Sql_Name_Mapping.csv` | SQL name mapping rows (`NameMapping`); read by `ExcelMappingModels2Rdbms.nameMappings`. See change: split-etl-artifacts. |
| `RDBMS_Table_Mapping_Rules.csv` | Table mapping rules (`Rule`); `cascade` is never copied (ETL parity, always false). See change: split-etl-artifacts. |
