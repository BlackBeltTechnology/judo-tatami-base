# judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm

Java entry points of the PSM→ASM lowering. Two engines behind one work: Epsilon ETL (scripts in `../../../../../../epsilon/transformations/asm/`) and Zeta (`zeta/`). Trace shape is shared.

| File | Purpose |
|---|---|
| `Psm2Asm.java` | Runs the ETL PSM→ASM transformation; exports `Psm2AsmParameter`, `executePsm2AsmTransformation(...)`, `calculatePsm2AsmTransformationScriptURI()`; registers `JUDOPSM`/`ASM` resources, injects `asmUtils`/`psmUtils` → see `Psm2Asm.java.AGENTS.md` |
| `Psm2AsmTransformationTrace.java` | Carries and (de)serialises the PSM→ASM trace; implements `TransformationTrace`, holds `trace` vs `zetaTrace` fields exclusively → see `Psm2AsmTransformationTrace.java.AGENTS.md` |
| `Psm2AsmWork.java` | Workflow work driving PSM→ASM with engine selection; extends `AbstractTransformationWork`, exports `Psm2AsmWorkParameter`, branches on `transformationMode.isZeta()` → see `Psm2AsmWork.java.AGENTS.md` |
