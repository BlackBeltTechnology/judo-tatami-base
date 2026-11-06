# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase`

Workflow task, review resolvers, abbreviation utilities and ETL engine contract for RDBMS→Liquibase. Epsilon-free core; ETL entry points (full and incremental) live in `judo-tatami-rdbms2liquibase-etl`.

| File | Purpose |
| --- | --- |
| `AbbreviateUtils.java` | Abbreviates camelCase and underscored identifiers to target lengths by removing vowels and consonants according to English letter frequency tables. Exports `abbreviate(String, Integer, String)`. Requires `maxLength >= 1`. |
| `FileSystemReviewResolver.java` | Implements `ReviewResolver` over a directory root on disk. Exports `exists(String)` checking file presence and `resolve(String)` reading contents as UTF-8 string. |
| `Rdbms2LiquibaseEtlEngine.java` | Typed ETL engine contract. `execute(RdbmsModel, LiquibaseModel, dialect, Logger, createTrace, useCache, parallel)` → `void`; `ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl"`. Implemented by `-etl` module, resolved by `EtlEngineRegistry`. See change: split-etl-artifacts. |
| `Rdbms2LiquibaseWork.java` | Workflow task for full RDBMS→Liquibase; per-dialect models; mode ZETA by default. Zeta inline; ETL/DUAL via `EtlEngineRegistry.require(Rdbms2LiquibaseEtlEngine.class, ...)`. Ctor `(ctx, dialect)` only (script-root ctor removed). See change: split-etl-artifacts. |
| `ReviewResolver.java` | Interface for resolving external review SQL scripts by filename. Declares `exists(String name)` and `resolve(String name)`. |
