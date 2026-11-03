# judo-tatami-asm2rdbms-etl/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/etl
ETL entry point of the ASM→RDBMS stage plus its engine adapter. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `Asm2Rdbms.java` | Drives the ETL over `asmToRdbms.etl` via `executeAsm2RdbmsTransformation(Asm2RdbmsParameter)`; merges `mapping-<dialect>-rdbms.model`, injects `AbbreviateUtils`/`MD5Utils`/`*Size`/prefixes. `calculateAsm2RdbmsModelURI()` returns core model root. Package `.etl` → see `Asm2Rdbms.java.AGENTS.md`. See change: split-etl-artifacts. |
| `Asm2RdbmsEtlEngineImpl.java` | Implements core `Asm2RdbmsEtlEngine`; `execute(asmModel, rdbmsModel, dialect, log, Asm2RdbmsWorkParameter)` maps the work parameter (via new `@Getter`s) onto `Asm2RdbmsParameter` and calls `Asm2Rdbms.executeAsm2RdbmsTransformation`; `log` may be `null`. Loaded by `ServiceLoader` or `Asm2RdbmsEtlEngineComponent`. See change: split-etl-artifacts. |
