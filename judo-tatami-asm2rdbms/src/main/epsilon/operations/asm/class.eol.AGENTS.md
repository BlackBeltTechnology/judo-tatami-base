# `class.eol`

`ASM!EClass` extensions driving table/view naming and inheritance walks. Exports `classSqlName()`, `tableSqlName()`, `viewSqlName()`, `primaryKeyAttribute()`, `allSupertypesAttributes()`, `allDescandences()` (sic), `root()`, `getClassifierFQName()`, `isEntityType()`.
`classSqlName()` prefixes the package SQL name unless program parameter `createSimpleName` is true; `tableSqlName()` applies `tablePrefix` and abbreviates to `tableNameMaxSize()`; `viewSqlName()` prefixes `V_` and reserves 2 chars.
`primaryKeyAttribute()` picks the single attribute annotated `id=true` — a class with two such annotations resolves non-deterministically.
`allDescandences()` recurses over all `ASM!EClass.all`, so it is O(n²) on the whole model.
All `@cached`.