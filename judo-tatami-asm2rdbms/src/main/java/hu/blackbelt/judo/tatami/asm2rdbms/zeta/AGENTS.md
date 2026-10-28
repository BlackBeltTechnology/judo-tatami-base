# AGENTS.md — asm2rdbms/zeta

Zeta-engine port of the ASM→RDBMS ETL, the default engine when `TransformationMode` is ZETA.

| File | Purpose |
|---|---|
| `Asm2RdbmsHelper.java` | Static facade for ID resolution, SQL naming, type filling and cache clearing shared by every Zeta rule class; 1:1 port of `epsilon/operations/`. → see `Asm2RdbmsHelper.java.AGENTS.md` |
| `Asm2RdbmsRuleNames.java` | Final, non-instantiable holder of the rule-name constants (one per ETL rule, values byte-identical to ETL names, keep the `rootPackege` misspelling) shared by the Zeta rules and trace consumers → see `Asm2RdbmsRuleNames.java.AGENTS.md` |
| `Asm2RdbmsZetaTransformation.java` | Runs the ASM→RDBMS lowering on the Zeta `TransformationRegistry` + `TransformationExecutor`; `@Builder` on `asmModel`/`rdbmsModel`/`dialect`, `execute()` returns the native `TransformationTrace`, registers the four rule classes → see `Asm2RdbmsZetaTransformation.java.AGENTS.md` |
| `CLAUDE.md` | `!! GENERATED` — `claude-mem` auto-writes the `<claude-mem-context>` block. Currently holds no activity. Not a source file; hand edits inside the tags are overwritten. |
