# `namedElement.eol`

`ASM!ENamedElement` extensions producing the two base SQL name forms. Exports `sqlName()` and `sqlLongName()`.
Both honour an explicit `shortName` extension annotation first, converting it via `toUpperSnakeCase()`; absent that, `sqlLongName()` always abbreviates to `sqlNameSize()` while `sqlName()` abbreviates to `sqlNameSize()` only when `createSimpleName` is set, else to the tighter `shortNameSize()`.
Every table/column name in the RDBMS model bottoms out here, so changing the abbreviation strategy renames existing schemas.
`@cached`.