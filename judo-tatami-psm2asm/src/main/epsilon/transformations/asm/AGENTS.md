# judo-tatami-psm2asm/src/main/epsilon/transformations/asm

Epsilon ETL entry point for the PSM→ASM lowering. Rule bodies live in `modules/`; shared query operations in `utils/`.

| File | Purpose |
|---|---|
| `psmToAsm.etl` | Root ETL module for PSM→ASM. Reads `PSM!` namespaces, entity/data types, transfer objects, actors, operations; emits the `ASM!` Ecore model (`EPackage`/`EClass`/`EAttribute`/`EReference`/`EOperation`/`EParameter`). Declares no rules itself — imports `modules/actor.etl`, `modules/data.etl`, `modules/derived.etl`, `modules/namespace.etl`, `modules/operation.etl`, `modules/static.etl`, `modules/transferObject.etl`, `modules/type.etl`. `post` block counts `eAnnotations` across all ASM element kinds, then calls `asmUtils.enrichWithAnnotations()` and re-counts; the `asmUtils` native tool must be bound by the caller or the post block fails. Annotation enrichment runs once, after every rule — rules must not rely on enriched annotations being present during transform. Count logging is debug-only instrumentation, not a contract. |
