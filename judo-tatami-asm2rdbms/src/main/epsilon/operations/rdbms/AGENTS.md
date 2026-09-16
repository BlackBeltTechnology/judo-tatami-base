# AGENTS.md — asm2rdbms/epsilon/operations/rdbms

EOL operations on the RDBMS target side: type mapping onto `RdbmsField`, rule tracing.

| File | Purpose |
|---|---|
| `_importRdbms.eol` | Include point for the RDBMS-side operation library — imports `field.eol`, `rule.eol`, `table.eol`. Pulled in by `operations/_importAll.eol`; a new RDBMS operation file must be listed here to reach the ETL rules. |
| `field.eol` | Extends `RDBMS!RdbmsField`: `fillType()`/`fromType()` from `RDBMS!TypeMapping` by `asmType`; `#`-indirection sizes; `parseAnnotationName()`/`parseAnnotationDetailsKey()` → see `field.eol.AGENTS.md` |
| `rule.eol` | `RDBMS!Rule.toString()` dumps the full relation-shape record: `symbol`, `lower`/`upper`, `opposite`, `foreignKey`, `inverseForeignKey`, `readOnly`, `cascade`, `mandatory`, `rangeConstraint`, `joinTable` — diagnostic, logged via `ruleMapping()` → see `rule.eol.AGENTS.md` |
| `table.eol` | Inert: whole body is a commented-out `RDBMS!RdbmsTable.addSuperPrimaryKey(class : ASM!EClass)` that would have cloned the supertype's `EAttributeToTableIdField` equivalent into the table and set it as `primaryKey`. Defines no live operation; supertype primary-key propagation now happens in the ETL rules instead. Kept as the import target of `_importRdbms.eol`. |
