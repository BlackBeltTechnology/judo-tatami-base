# `util.eol`

Naming and cloning operations shared by every RDBMS rule.
Exports `generateUuid(namespace, name, separator)`, `md5(name)`, `md5short(name)` (both delegate to injected `MD5Utils.md5`), cached size accessors `shortNameSize()`, `sqlNameSize()`, `tableNameMaxSize()`, `columnNameMaxSize()` reading the ETL program parameters of the same name, `abbreviate(text, maxLength)` via `AbbreviateUtils.abbreviate(..., "_")`, `RDBMS!EObject.clone()` via `EMFTool.ecoreUtil.copy`, `String.addWithDotTo(to)`, `String.toUpperSnakeCase()`.
`MD5Utils`, `AbbreviateUtils`, `EMFTool` and the `*Size` parameters must be injected into the Epsilon context by the Java driver, else every call here throws.
`md5short` is currently identical to `md5` — it does not truncate.