# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/resources`

Test resources including incremental test spreadsheets, ETL model generation scripts, XML sheet mappings, and external model test configurations.

| File | Purpose |
| --- | --- |
| `createExcelModel.etl` | Epsilon Transformation Language script transforming Excel worksheets (`CasesOriginal`, `CasesNew`, `TablesOriginal`, `TablesNew`) into original and updated `RdbmsModel` instances. |
| `external-model-tests.properties` | External model test runner configuration defining model locations and dialect parameters, targeting `<model-name>-rdbms_<dialect>.model`. |
| `mapping.xml` | Declares Epsilon Excel model worksheet mapping rules for `CasesOriginal`, `CasesNew`, `TablesOriginal`, `TablesNew`, `Differences`, and `Result`. |
| `model-search-directories.properties` | Lists comma-separated relative search directories for locating external test models; missing directories are ignored. |
