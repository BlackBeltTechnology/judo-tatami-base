# AGENTS.md — asm2rdbms/epsilon/operations

EOL operation libraries mixed into the ASM→RDBMS ETL scripts.

| File | Purpose |
|---|---|
| `_importAll.eol` | Single include point for the whole operation library — imports `asm/_importAsm.eol`, `rdbms/_importRdbms.eol`, `util.eol`, `id.eol`. ETL modules import this file, never the leaves, so adding a new operation file means registering it in the matching `_import*.eol` or it stays invisible. |
| `id.eol` | Extends `Any` with `setId(id : String)` / `getId() : String`, delegating to `self.eResource.setId/getId`. Only works on elements already contained in a resource — an unattached `EObject` has no `eResource` and the call fails. IDs written here are the traceability keys the transformation trace resolves against. |
| `util.eol` | Naming and cloning operations shared by every RDBMS rule. Exports `generateUuid(namespace, name, separator)`, `md5(name)`, `md5short(name)` (both delegate to injected `MD5Utils.md5`), cached size accessors `shortNameSize()`, `sqlNameSize()`, `tableNameMaxSize()`, `columnNameMaxSize()` reading the ETL program parameters of the same name, `abbreviate(text, maxLength)` via `AbbreviateUtils.abbreviate(..., "_")`, `RDBMS!EObject.clone()` via `EMFTool.ecoreUtil.copy`, `String.addWithDotTo(to)`, `String.toUpperSnakeCase()`. `MD5Utils`, `AbbreviateUtils`, `EMFTool` and the `*Size` parameters must be injected into the Epsilon context by the Java driver, else every call here throws. `md5short` is currently identical to `md5` — it does not truncate. |
