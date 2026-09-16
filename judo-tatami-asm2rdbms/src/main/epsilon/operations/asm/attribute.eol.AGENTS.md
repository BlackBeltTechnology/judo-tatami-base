# `attribute.eol`

`ASM!EAttribute` extensions. Exports `isPrimaryKey()` (true when the attribute equals `eContainingClass.primaryKeyAttribute()`), `fieldSqlName()`, `getAttributeFQName()` (delegates to injected `asmUtils`).
`fieldSqlName()` reserves `DOMAIN_ID`/`DOMAIN_TYPE`/`DOMAIN_VERSION` for attributes named `id`/`type`/`version` ONLY while `columnPrefix` is blank; otherwise it emits `columnPrefix + abbreviate(sqlLongName(), columnNameMaxSize() - columnPrefix.length())` upper-cased.
All operations `@cached` — results freeze after the first call, so mutating names mid-transformation yields stale SQL names.