# judo-tatami-psm2asm/src/main/epsilon/transformations/asm/modules

ETL rule modules imported by `../psmToAsm.etl`. Every module reads `JUDOPSM!` elements and emits `ASM!` Ecore elements. Shared query operations come from `../utils/_importUtils.eol`; `namespace.etl` supplies the `NamespaceToPackage` container lookup and the `@abstract CreateDocumentationAnnotation` base rule that most documentation rules extend.

Cross-module invariants: every emitted element gets a synthetic id `"(psm/" + s.getId() + ")/<RuleSuffix>"` — ids must stay unique or the ASM resource fails to load. Annotation `source` URIs come from `asmUtils.getAnnotationUri(<name>)`, never literal strings. Qualified names come from `psmUtils.namespaceElementToString(...)`, with `::` replaced by `.` where ASM expects dotted names.

| File | Purpose |
|---|---|
| `actor.etl` | Maps actor-flavoured `JUDOPSM!TransferObjectType` to ASM annotations via `CreateActorAnnotation`, `CreateActorTypeAnnotation`, `CreateRealmTypeAnnotation`, `CreateDocumentationAnnotationForActorType` → see `actor.etl.AGENTS.md` |
| `data.etl` | Maps the PSM entity layer to ASM `EClass`/`EAttribute`/`EReference` via `CreateEntityClass`, `CreateAttribute`, `CreateRelation` + annotation/constraint hierarchies → see `data.etl.AGENTS.md` |
| `derived.etl` | Maps PSM derived accessors (`PrimitiveAccessor`, `ReferenceAccessor`) to ASM members with JQL annotations via `CreateDataProperty`, `CreateNavigationProperty` + constraint hierarchy → see `derived.etl.AGENTS.md` |
| `namespace.etl` | Maps PSM namespace tree to ASM packages (`NamespaceToPackage` → `ModelToPackage`, `PackageToPackage`) and hosts `@abstract CreateDocumentationAnnotation` → see `namespace.etl.AGENTS.md` |
| `operation.etl` | Maps PSM operations to ASM `EOperation`/`EParameter` plus behaviour, stateful and permission annotations (528 lines). → see `operation.etl.AGENTS.md` |
| `static.etl` | Maps `JUDOPSM!StaticData`/`StaticNavigation` to synthetic unmapped transfer-object `EClass`es via `CreateUnmappedTransferObjectForStaticData`/`CreateStaticQueryAttribute`/`CreateUnmappedTransferObjectForStaticNavigation`/`CreateStaticQueryNavigation` → see `static.etl.AGENTS.md` |
| `transferObject.etl` | Maps the PSM transfer-object layer to ASM classes, attributes, references and binding annotations (695 lines). → see `transferObject.etl.AGENTS.md` |
| `type.etl` | Maps PSM primitive and enumeration types to ASM `EDataType`/`EEnum` via `CreateEnumeration` + per-primitive rules incl. `CreateCustomType` → see `type.etl.AGENTS.md` |
