# AGENTS.md — asm2rdbms/epsilon/operations/asm

EOL operations on the ASM (Ecore) source side: SQL naming, entity classification, reference shape.

| File | Purpose |
|---|---|
| `_importAsm.eol` | Include point for the ASM-side operation library — imports `attribute.eol`, `class.eol`, `namedElement.eol`, `package.eol`, `reference.eol`. Pulled in by `operations/_importAll.eol`; a new ASM operation file must be listed here to be visible to the ETL rules. |
| `attribute.eol` | `ASM!EAttribute` extensions: `isPrimaryKey()`, `fieldSqlName()`, `getAttributeFQName()` — `fieldSqlName()` reserves `DOMAIN_ID`/`DOMAIN_TYPE`/`DOMAIN_VERSION` while `columnPrefix` is blank → see `attribute.eol.AGENTS.md` |
| `class.eol` | `ASM!EClass` extensions: `classSqlName()`, `tableSqlName()`, `viewSqlName()`, `primaryKeyAttribute()`, `allDescandences()` (sic), `root()`, `getClassifierFQName()`, `isEntityType()` → see `class.eol.AGENTS.md` |
| `namedElement.eol` | `ASM!ENamedElement` extensions: `sqlName()`, `sqlLongName()` — base SQL name forms honouring `shortName` annotation, abbreviate to `sqlNameSize()`/`shortNameSize()` → see `namedElement.eol.AGENTS.md` |
| `package.eol` | `ASM!EPackage` extensions. Exports `packageSqlName()` (delegates to `sqlName()`) and `root()`, which walks `eSuperPackage` to the topmost package. `root()` assumes an acyclic package containment — a cycle loops forever. `@cached`. |
| `reference.eol` | `ASM!EReference` extensions: FK/inverse-FK/junction-table naming — `referenceFkSqlName()`, `referenceManyToManyTableSqlName()`, `ruleMapping()`, `isForeign()`, `isFirst()`, `isMandatory()`, `toString()` → see `reference.eol.AGENTS.md` |
