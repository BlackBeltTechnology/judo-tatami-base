# AGENTS.md — asm2rdbms/osgi

OSGi Declarative Services wrapper around the ASM→RDBMS transformation.

| File | Purpose |
|---|---|
| `Asm2RdbmsTransformationAsmModelTracker.java` | `AbstractModelTracker<AsmModel>` DS component; installs each model via `Asm2RdbmsTransformationSerivce.install(asmModel, "hsqldb")` — dialect HARD-CODED to `hsqldb`. → see `Asm2RdbmsTransformationAsmModelTracker.java.AGENTS.md` |
| `Asm2RdbmsTransformationSerivce.java` | DS `@Component`; `install(AsmModel, String dialect)` builds `RdbmsModel` at `rdbms:<asmName>.model` via the ETL path and registers `TransformationTrace` keyed by `AsmModel`. → see `Asm2RdbmsTransformationSerivce.java.AGENTS.md` |
