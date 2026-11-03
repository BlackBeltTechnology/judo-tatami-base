# judo-tatami-asm2rdbms-etl/model
Excel workbooks and spreadsheet configs moved from core. Parity reference only: `ExcelMappingParityTest` proves the CSV loader in core equals the ETL output over these. Not read at runtime.
| File | Purpose |
|---|---|
| `RDBMS_Data_Types_Hsqldb.xlsx` | Type-mapping workbook, hsqldb; CSV twin in core `model/`. Moved from core. See change: split-etl-artifacts. |
| `RDBMS_Data_Types_Oracle.xlsx` | Type-mapping workbook, oracle; CSV twin in core `model/`. Moved from core. See change: split-etl-artifacts. |
| `RDBMS_Data_Types_Postgresql.xlsx` | Type-mapping workbook, postgresql; CSV twin in core `model/`. Moved from core. See change: split-etl-artifacts. |
| `RDBMS_Sql_Name_Mapping.xlsx` | SQL name-mapping workbook; CSV twin in core `model/`. Moved from core. See change: split-etl-artifacts. |
| `RDBMS_Table_Mapping_Rules.xlsx` | Table-mapping rules workbook; CSV twin in core `model/`. Moved from core. See change: split-etl-artifacts. |
| `namemapping.xml` | Epsilon spreadsheet-model config for the NAMEMAPPING workbook. Used by `ExcelMappingEtlReference` only. Moved from core. See change: split-etl-artifacts. |
| `rulemapping.xml` | Epsilon spreadsheet-model config for the RULEMAPPING workbook. Used by `ExcelMappingEtlReference` only. Moved from core. See change: split-etl-artifacts. |
| `typemapping.xml` | Epsilon spreadsheet-model config for the TYPEMAPPING workbook. Used by `ExcelMappingEtlReference` only. Moved from core. See change: split-etl-artifacts. |
