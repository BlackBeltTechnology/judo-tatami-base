# `Asm2Rdbms.java`

Drives the ETL engine over `asmToRdbms.etl`. Lives in `judo-tatami-asm2rdbms-etl`, package `hu.blackbelt.judo.tatami.asm2rdbms.etl`; core `Asm2RdbmsWork` reaches it only via `Asm2RdbmsEtlEngineImpl` / `EtlEngineRegistry`. Exports `executeAsm2RdbmsTransformation(Asm2RdbmsParameter)` (and a builder overload), `calculateAsm2RdbmsTransformationScriptURI()`, `calculateAsm2RdbmsModelURI()` (now returns core's `ExcelMappingModels2Rdbms.calculateExcelMappingModelURI()` — the generated mapping models ship in the core artifact), builder `asm2RdbmsParameter()`, constants `SCRIPT_ROOT_TATAMI_ASM_2_RDBMS`, `MODEL_ROOT_TATAMI_ASM_2_RDBMS`, `ASM_2_RDBMS_URI_POSTFIX = "asm2rdbms"`, nested `MD5Utils` with `md5(String)`.

Loads the pre-built `mapping-<dialect>-rdbms.model` and merges its contents into the target `RdbmsModel` BEFORE running the ETL, so type/rule/name mappings are present. Derives length budgets from dialect — 16/60/62/58 by default, 6/28/30/30 for `oracle` — and only a positive explicit parameter overrides them.

Injects `AbbreviateUtils`, `MD5Utils`, `asmUtils`, `rdbmsUtils`, the four `*Size` values, `createSimpleName` and the five prefixes (`T_`, `C_`, `FK_`, `FK_INV_`, `J_`) into the Epsilon context under exactly those names; the EOL operation library reads those keys. `asmModel`, `rdbmsModel`, `dialect` are `@NonNull`.

See change: split-etl-artifacts: moved from core to `-etl`, package `.etl`; model root now points at the core artifact.
