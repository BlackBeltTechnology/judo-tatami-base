# judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm/osgi

OSGi wiring for PSM→ASM: a tracker watching registered `PsmModel` services and a transformation service producing `AsmModel` + `TransformationTrace` registrations. Both class names carry the upstream misspelling `Serivce` — public component name, do not "fix" without a coordinated rename.

| File | Purpose |
|---|---|
| `Psm2AsmTransformationPsmModelTracker.java` | `AbstractModelTracker<PsmModel>` DS component publishing derived `AsmModel` from `psmModel.getName()`-keyed OSGi registrations → see `Psm2AsmTransformationPsmModelTracker.java.AGENTS.md` |
| `Psm2AsmTransformationSerivce.java` | Runs ETL PSM→ASM via `Psm2Asm.executePsm2AsmTransformation`, registers resulting `Psm2AsmTransformationTrace` as `TransformationTrace` service → see `Psm2AsmTransformationSerivce.java.AGENTS.md` |
