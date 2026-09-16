# `attribute.etl`

Maps `ASM!EAttribute` onto RDBMS columns. `@abstract rule EAttributeToRdbmsField` fills type and name — enums and untyped attributes both degrade to `java.lang.Integer`, other types go through `fillType(s.eType.instanceClassName, s)`, `sqlName` from `fieldSqlName()`, `mandatory` hard-wired false (source `required` is commented out).

`EAttributeToTableValueField` extends it, produces `RDBMS!RdbmsValueField` and appends to the containing class's `EClassToRdbmsTable` equivalent. `EAttributeToIndex` produces an `RDBMS!RdbmsIndex` named `IDX_<md5(uuid)>` for attributes `asmUtils.isIdentifier(...)` reports, referencing the attribute's `EAttributeToTableValueField` equivalent.

All guards require `eContainingClass.isEntityType()` and skip derived attributes. IDs/`uuid` follow `(asm/<sourceId>)/<Suffix>` — trace resolution keys off them. Commented-out `EAttributeToUniqueKey` shows unique constraints were replaced by indexes.