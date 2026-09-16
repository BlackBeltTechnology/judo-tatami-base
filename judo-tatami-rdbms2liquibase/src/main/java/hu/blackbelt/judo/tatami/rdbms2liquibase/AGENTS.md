# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase`

Transformation runners, string abbreviation utilities, and workflow tasks converting RDBMS models to Liquibase changelogs.

| File | Purpose |
| --- | --- |
| `AbbreviateUtils.java` | Abbreviates camelCase and underscored identifiers to target lengths by removing vowels and consonants according to English letter frequency tables. Exports `abbreviate(String, Integer, String)`. Requires `maxLength >= 1`. |
| `FileSystemReviewResolver.java` | Implements `ReviewResolver` over a directory root on disk. Exports `exists(String)` checking file presence and `resolve(String)` reading contents as UTF-8 string. |
| `Rdbms2Liquibase.java` | Executes full-schema Epsilon ETL transformations converting `RdbmsModel` to `LiquibaseModel`. Exports `executeRdbms2LiquibaseTransformation(Rdbms2LiquibaseParameter)` and `calculateRdbms2LiquibaseTransformationScriptURI()`. |
| `Rdbms2LiquibaseIncremental.java` | Executes incremental migration pipeline transforming `incrementalRdbmsModel` into multiple phased Liquibase changelog models and generating backup SQL scripts via EGL. Exports `executeRdbms2LiquibaseIncrementalTransformation(Rdbms2LiquibaseIncrementalParameter)`. |
| `Rdbms2LiquibaseIncrementalWork.java` | Workflow task extending `AbstractTransformationWork` to run incremental RDBMS to Liquibase migration for a given database dialect. Retrieves `rdbms-incremental:<dialect>` from context and records output models and script directories. |
| `Rdbms2LiquibaseWork.java` | Workflow task extending `AbstractTransformationWork` for full RDBMS to Liquibase transformation. Checks `TransformationMode` (defaults to ZETA via system property or falls back to ETL), manages dialect-specific models, and puts generated `LiquibaseModel` into context. |
| `ReviewResolver.java` | Interface for resolving external review SQL scripts by filename. Declares `exists(String name)` and `resolve(String name)`. |
