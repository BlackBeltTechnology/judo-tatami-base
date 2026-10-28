# `Asm2Rdbms.java`

Drives the ETL engine over `asmToRdbms.etl`. Exports `executeAsm2RdbmsTransformation(Asm2RdbmsParameter)` (and a builder overload), `calculateAsm2RdbmsTransformationScriptURI()`, `calculateAsm2RdbmsModelURI()`, builder `asm2RdbmsParameter()`, constants `SCRIPT_ROOT_TATAMI_ASM_2_RDBMS`, `MODEL_ROOT_TATAMI_ASM_2_RDBMS`, `ASM_2_RDBMS_URI_POSTFIX = "asm2rdbms"`, nested `MD5Utils` with `md5(String)`.

Loads the pre-built `mapping-<dialect>-rdbms.model` and merges its contents into the target `RdbmsModel` BEFORE running the ETL, so type/rule/name mappings are present. Derives length budgets from dialect — 16/60/62/58 by default, 6/28/30/30 for `oracle` — and only a positive explicit parameter overrides them.

Injects `AbbreviateUtils`, `MD5Utils`, `asmUtils`, `rdbmsUtils`, the four `*Size` values, `createSimpleName` and the five prefixes (`T_`, `C_`, `FK_`, `FK_INV_`, `J_`) into the Epsilon context under exactly those names; the EOL operation library reads those keys. `asmModel`, `rdbmsModel`, `dialect` are `@NonNull`.