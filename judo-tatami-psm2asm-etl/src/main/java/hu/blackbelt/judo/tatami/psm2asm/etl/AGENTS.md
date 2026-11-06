# judo-tatami-psm2asm-etl/src/main/java/hu/blackbelt/judo/tatami/psm2asm/etl
ETL entry point of the PSM→ASM stage plus its engine adapter. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `Psm2Asm.java` | Runs the ETL PSM→ASM transformation; exports `Psm2AsmParameter`, `executePsm2AsmTransformation(...)`, `calculatePsm2AsmTransformationScriptURI()`; registers `JUDOPSM`/`ASM` resources, injects `asmUtils`/`psmUtils`. Package `.etl` (moved from core) → see `Psm2Asm.java.AGENTS.md`. See change: split-etl-artifacts. |
| `Psm2AsmEtlEngineImpl.java` | Implements core `Psm2AsmEtlEngine`; `execute(...)` builds `Psm2AsmParameter` and calls `Psm2Asm.executePsm2AsmTransformation`; `log` may be `null` (buffered SLF4J logger). Loaded by `ServiceLoader` or `Psm2AsmEtlEngineComponent`. See change: split-etl-artifacts. |
