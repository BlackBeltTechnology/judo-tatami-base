# AGENTS.md — asm2rdbms/epsilon/transformations/modules

ETL rule modules of `asmToRdbms.etl`, one per ASM source metaclass.

| File | Purpose |
|---|---|
| `attribute.etl` | Maps `ASM!EAttribute` onto RDBMS columns via `EAttributeToRdbmsField` → `EAttributeToTableValueField`/`EAttributeToIndex`; IDs/`uuid` key trace resolution. → see `attribute.etl.AGENTS.md` |
| `class.etl` | Maps `ASM!EClass` onto `RDBMS!RdbmsTable` via `EClassToRdbmsTable` plus nine fixed system columns (`ID`/`TYPE`/`VERSION`/`*_USERNAME`/`*_USER_ID`/`*_TIMESTAMP`). → see `class.etl.AGENTS.md` |
| `package.etl` | Maps ROOT `ASM!EPackage` via `rootPackegeToModel`/`rootPackegeToConfiguration` onto `RdbmsModel`/`RdbmsConfiguration`; parameters `modelVersion` and `dialect`. → see `package.etl.AGENTS.md` |
| `reference.etl` | Maps `ASM!EReference` onto FKs and junction tables via `EReferenceToRdbmsTableForeignKey`/`...InverseForeignKey`/`@lazy ...JunctionTable`, dispatched on `s.ruleMapping()` flags. → see `reference.etl.AGENTS.md` |
