## 1. Fix ClassRules.java — use createTarget with ID

- [x] 1.1 Fix `eClassToRdbmsTable()`: replace `ctx.createTarget(RdbmsTable.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsTable.class, "(asm/" + ctx.getElementId(s) + ")/Table")`; keep `t.setUuid(id)` for field parity; remove dead `// Set XMI ID` comment
- [x] 1.2 Fix `eClassToTableIdField()`: replace `ctx.createTarget(RdbmsIdentifierField.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsIdentifierField.class, "(asm/" + ctx.getElementId(s) + ")/TableIdField")`; keep `t.setUuid(id)`
- [x] 1.3 Fix `eClassToTableTypeField()`: replace `ctx.createTarget(RdbmsValueField.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsValueField.class, "(asm/" + ctx.getElementId(s) + ")/TableTypeField")`; keep `t.setUuid(id)`
- [x] 1.4 Fix `eClassToTableVersionField()`: same pattern, suffix `/TableVersionField`
- [x] 1.5 Fix `eClassToTableCreateUsernameField()`: same pattern, suffix `/TableCreateUsernameField`
- [x] 1.6 Fix `eClassToTableCreateUserIdField()`: same pattern, suffix `/TableCreateUserIdField`
- [x] 1.7 Fix `eClassToTableCreateTimestampField()`: same pattern, suffix `/TableCreateTimestampField`
- [x] 1.8 Fix `eClassToTableUpdateUsernameField()`: same pattern, suffix `/TableUpdateUsernameField`
- [x] 1.9 Fix `eClassToTableUpdateUserIdField()`: same pattern, suffix `/TableUpdateUserIdField`
- [x] 1.10 Fix `eClassToTableUpdateTimestampField()`: same pattern, suffix `/TableUpdateTimestampField`

## 2. Fix AttributeRules.java — abstract rule and concrete override

- [x] 2.1 Fix `eAttributeToRdbmsField()` (abstract): replace `ctx.createTarget(RdbmsField.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsField.class, "(asm/" + ctx.getElementId(s) + ")/RdbmsField")`; keep `t.setUuid(id)`
- [x] 2.2 Fix `eAttributeToTableValueField()` (concrete/extends): after `ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s)`, add `String id = "(asm/" + ctx.getElementId(s) + ")/TableValueField"; ctx.setElementId(t, id); t.setUuid(id);` — remove incorrect comment "UUID already set by parent rule - don't override it"
- [x] 2.3 Fix `eAttributeToIndex()`: replace `ctx.createTarget(RdbmsIndex.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsIndex.class, "(asm/" + ctx.getElementId(s) + ")/Index")`; keep `t.setUuid(id)`

## 3. Fix ReferenceRules.java — all FK and junction table rules

- [x] 3.1 Fix `eReferenceToTableForeignKey()`: replace `ctx.createTarget(RdbmsForeignKey.class)` + `fk.setUuid(...)` with `ctx.createTarget(RdbmsForeignKey.class, "(asm/" + ctx.getElementId(s) + ")/TableForeignKey")`; keep `fk.setUuid(id)`
- [x] 3.2 Fix `eReferenceToTableInverseForeignKey()`: same pattern, suffix `/TableInverseForeignKey`
- [x] 3.3 Fix `eReferenceToRdbmsJunctionTable()`: replace `ctx.createTarget(RdbmsJunctionTable.class)` + `t.setUuid(...)` with `ctx.createTarget(RdbmsJunctionTable.class, "(asm/" + ctx.getElementId(s) + ")/JunctionTable")`; keep `t.setUuid(id)`
- [x] 3.4 Fix `eReferenceToRdbmsJunctionTablePrimaryKey()`: same pattern, suffix `/JunctionTablePrimaryKey`
- [x] 3.5 Fix `eReferenceToRdbmsJunctionTableForeignKeyBidirectional()`: same pattern, suffix `/JunctionTableForeignKeyBidirectional`
- [x] 3.6 Fix `eReferenceToRdbmsJunctionTableForeignKeyUnidirectional()` (or equivalent): same pattern for both `fk1` (`/JunctionTableForeignKeyUnidirectional1`) and `fk2` (`/JunctionTableForeignKeyUnidirectional2`)

## 4. Verify

- [x] 4.1 Run `Asm2RdbmsTest` — all structural tests pass
- [x] 4.2 Run `Asm2RdbmsInheritanceTest` — all inheritance tests pass
- [x] 4.3 Run discovery comparison with `--xmiids` flag and confirm `assertXmiIdsEquivalent` passes for `asm2rdbms`
