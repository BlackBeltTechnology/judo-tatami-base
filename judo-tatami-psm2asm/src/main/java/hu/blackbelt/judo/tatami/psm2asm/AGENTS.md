# judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm

Java entry points of the PSM→ASM lowering. Epsilon-free core: Zeta (`zeta/`) runs here; the Epsilon ETL entry point, scripts and engine live in `judo-tatami-psm2asm-etl` and are reached via `Psm2AsmEtlEngine`. Trace shape is shared.

| File | Purpose |
|---|---|
| `Psm2AsmEtlEngine.java` | Typed ETL engine contract. `execute(PsmModel, AsmModel, Logger, createTrace, useCache, parallel)` → `Psm2AsmTransformationTrace`; `ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-psm2asm-etl"`. Implemented by `-etl` module, resolved by `EtlEngineRegistry`. See change: split-etl-artifacts. |
| `Psm2AsmTransformationTrace.java` | Carries and (de)serialises the PSM→ASM trace; implements `TransformationTrace`, holds `trace` vs `zetaTrace` fields exclusively → see `Psm2AsmTransformationTrace.java.AGENTS.md` |
| `Psm2AsmWork.java` | Workflow work driving PSM→ASM; extends `AbstractTransformationWork`, `Psm2AsmWorkParameter`. Zeta inline; ETL/DUAL via `EtlEngineRegistry.require(Psm2AsmEtlEngine.class, ...)`; ctor `(ctx)` only → see `Psm2AsmWork.java.AGENTS.md`. See change: split-etl-artifacts. |
