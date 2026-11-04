# `ExcelMappingModels2Rdbms.java`

Builds the `mapping-<dialect>-rdbms.model` that both engines consume — in plain Java, no Epsilon. Reads `model/*.csv` (`RDBMS_Data_Types_<Dialect>.csv`, `RDBMS_Table_Mapping_Rules.csv`, `RDBMS_Sql_Name_Mapping.csv`; converted from the former `.xlsx` workbooks) and reproduces the old `excelTo*.etl` output value-for-value. Exports `injectExcelMappings(RdbmsModel, Logger, URI scriptUri, URI modelUri, String dialect)` (public API kept; `scriptUri` ignored), `calculateExcelMapping2RdbmsTransformationScriptURI()` (`@Deprecated`, source compat only), `calculateExcelMappingModelURI()`, `calculateURI(String)`, `main(String[])`.

Parity quirks are deliberate: numeric untyped cells render as Java doubles (e.g. `"4095.0"`); `Rule.cascade` is always `false` (`excelToRules.etl` never copied it). `ExcelMappingParityTest` in `judo-tatami-asm2rdbms-etl` compares the output with `ExcelMappingEtlReference` (verbatim original ETL) for hsqldb, postgresql, oracle — change the loader and the CSV together or that test fails.

`main(rdbmsModelFile, dialect)` is the build-time generator (exec-maven-plugin); it registers the three mapping metamodels first, else the model will not serialise. `calculateURI` handles plain directory, `.jar` and `jar:bundle:` code sources — do not simplify it, OSGi depends on the bundle branch. The type-mapping CSV name capitalises only the first letter of the dialect.

See change: split-etl-artifacts: rewritten from ETL (3 `excelTo*.etl` + `.xlsx`) to Java + CSV; `.xlsx`/xml moved to `-etl` `model/`.
