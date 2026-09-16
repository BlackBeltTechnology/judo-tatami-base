# judo-tatami-psm2asm/src/main/epsilon/transformations/asm

Epsilon ETL entry point for the PSM→ASM lowering. Rule bodies live in `modules/`; shared query operations in `utils/`.

| File | Purpose |
|---|---|
| `psmToAsm.etl` | Root ETL module for PSM→ASM — imports rule modules; `post` block calls `asmUtils.enrichWithAnnotations()` on every ASM element kind → see `psmToAsm.etl.AGENTS.md` |
