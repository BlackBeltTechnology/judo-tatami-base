# `ExcelMappingModels2Rdbms.java`

Builds the `mapping-<dialect>-rdbms.model` that `Asm2Rdbms` later consumes, by running the three Excel loader ETLs. Exports `injectExcelMappings(RdbmsModel, Logger, URI scriptUri, URI excelModelUri, String dialect)`, `calculateExcelMapping2RdbmsTransformationScriptURI()`, `calculateExcelMappingModelURI()`, `calculateURI(String)`, `main(String[])`.

Registers Epsilon model contexts `RDBMS`, `TYPEMAPPING` (`RDBMS_Data_Types_<Dialect>.xlsx` + `typemapping.xml`), `RULEMAPPING` (`RDBMS_Table_Mapping_Rules.xlsx` + `rulemapping.xml`), `NAMEMAPPING` (`RDBMS_Sql_Name_Mapping.xlsx` + `namemapping.xml`) — the ETL scripts address those exact names.

Executes `excelToNameMapping.etl`, `excelToTypeMapping.etl`, `excelToRules.etl` in that order, then commits.

`main(rdbmsModelFile, dialect)` is the build-time generator; it registers the three mapping metamodels first, else the model will not serialise. `calculateURI` handles plain directory, `.jar` and `jar:bundle:` code sources — do not simplify it, OSGi depends on the bundle branch. The type-mapping workbook name capitalises only the first letter of the dialect.