# AGENTS.md — asm2rdbms

ASM→RDBMS transformation entry points: ETL driver, Excel mapping loader, workflow work unit, dual-engine trace, SQL-name abbreviator.

| File | Purpose |
|---|---|
| `AbbreviateUtils.java` | `abbreviate(String, Integer, String)` shrinks a SQL identifier to a dialect length budget via letter-frequency deletion; injected into the Epsilon context under key `AbbreviateUtils`. → see `AbbreviateUtils.java.AGENTS.md` |
| `Asm2Rdbms.java` | Drives the ETL over `asmToRdbms.etl` via `executeAsm2RdbmsTransformation(Asm2RdbmsParameter)`; merges `mapping-<dialect>-rdbms.model` and injects `AbbreviateUtils`/`MD5Utils`/`*Size` values/prefixes into the Epsilon context. → see `Asm2Rdbms.java.AGENTS.md` |
| `Asm2RdbmsTransformationTrace.java` | `TransformationTrace` impl carrying ETL (`Map<EObject, List<EObject>>`) or Zeta trace; exports `fromModelsAndTrace(...)`, `resolveAsm2RdbmsTrace(...)`, `createAsm2RdbmsTraceResource(...)`, `save(...)`. → see `Asm2RdbmsTransformationTrace.java.AGENTS.md` |
| `Asm2RdbmsWork.java` | `AbstractTransformationWork` unit per dialect; `execute()` dispatches on `Asm2RdbmsWorkParameter.transformationMode`, Zeta path flattens trace into legacy `Map<EObject, List<EObject>>`. → see `Asm2RdbmsWork.java.AGENTS.md` |
| `ExcelMappingModels2Rdbms.java` | Builds `mapping-<dialect>-rdbms.model` via `injectExcelMappings(...)`, running `excelToNameMapping.etl`/`excelToTypeMapping.etl`/`excelToRules.etl`; `main(...)` is the build-time generator. → see `ExcelMappingModels2Rdbms.java.AGENTS.md` |
