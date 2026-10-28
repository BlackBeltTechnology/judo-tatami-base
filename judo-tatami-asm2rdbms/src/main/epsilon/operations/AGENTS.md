# AGENTS.md — asm2rdbms/epsilon/operations

EOL operation libraries mixed into the ASM→RDBMS ETL scripts.

| File | Purpose |
|---|---|
| `_importAll.eol` | Single include point for the whole operation library — imports `asm/_importAsm.eol`, `rdbms/_importRdbms.eol`, `util.eol`, `id.eol`. ETL modules import this file, never the leaves, so adding a new operation file means registering it in the matching `_import*.eol` or it stays invisible. |
| `id.eol` | Extends `Any` with `setId(id : String)` / `getId() : String`, delegating to `self.eResource.setId/getId`. Only works on elements already contained in a resource — an unattached `EObject` has no `eResource` and the call fails. IDs written here are the traceability keys the transformation trace resolves against. |
| `util.eol` | Naming/cloning ops via `generateUuid`, `md5`, `abbreviate`, `RDBMS!EObject.clone()` → see `util.eol.AGENTS.md` |
