# AGENTS.md — asm2rdbms

ASM→RDBMS transformation entry points: CSV mapping loader, workflow work unit, dual-engine trace, SQL-name abbreviator, ETL engine contract. Epsilon-free core; ETL driver lives in `judo-tatami-asm2rdbms-etl`.

| File | Purpose |
|---|---|
| `AbbreviateUtils.java` | `abbreviate(String, Integer, String)` shrinks a SQL identifier to a dialect length budget via letter-frequency deletion; injected into the Epsilon context under key `AbbreviateUtils`. → see `AbbreviateUtils.java.AGENTS.md` |
| `Asm2RdbmsEtlEngine.java` | Typed ETL engine contract. `execute(AsmModel, RdbmsModel, dialect, Logger, Asm2RdbmsWorkParameter)` → `Asm2RdbmsTransformationTrace`; `ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-asm2rdbms-etl"`. Implemented by `-etl` module, resolved by `EtlEngineRegistry`. See change: split-etl-artifacts. |
| `Asm2RdbmsTransformationTrace.java` | `TransformationTrace` impl carrying ETL (`Map<EObject, List<EObject>>`) or Zeta trace; exports `fromModelsAndTrace(...)`, `resolveAsm2RdbmsTrace(...)`, `createAsm2RdbmsTraceResource(...)`, `save(...)`. → see `Asm2RdbmsTransformationTrace.java.AGENTS.md` |
| `Asm2RdbmsWork.java` | `AbstractTransformationWork` per dialect; dispatches `transformationMode`. Zeta inline; ETL/DUAL via `EtlEngineRegistry.require(Asm2RdbmsEtlEngine.class, ...)`. Ctor `(ctx, URI modelRoot, dialect)` / `(ctx, dialect)`; `Asm2RdbmsWorkParameter` has `@Getter`. → see `Asm2RdbmsWork.java.AGENTS.md`. See change: split-etl-artifacts. |
| `ExcelMappingModels2Rdbms.java` | Plain-Java (Epsilon-free) loader of `model/*.csv` into `mapping-<dialect>-rdbms.model`; `injectExcelMappings(...)`, `calculateExcelMappingModelURI()`, `main(...)` build-time generator (exec-maven-plugin). Value-parity with old `excelTo*.etl`. → see `ExcelMappingModels2Rdbms.java.AGENTS.md`. See change: split-etl-artifacts. |
