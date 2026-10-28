# judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm/zeta/rules

Zeta rule classes for PSM→ASM, one per ETL module in `src/main/epsilon/transformations/asm/modules/`. Each class carries `@TransformationContext(source = ..., target = ...)`, declares a public no-arg constructor for `TransformationRegistry`, and exposes `TransformGuard` / `TransformFunction<S,T>` factory methods. Rule names come from static `Psm2AsmRuleNames` constants; ASM tree mutation goes through `Psm2AsmHelper`. Registration order is fixed in `Psm2AsmZetaTransformation.createRegistry()`: Namespace → Type → Data → TransferObject → Derived → Static → Operation → BoundTransferOperationAnnotation → Actor.

| File | Purpose |
|---|---|
| `ActorRules.java` | Port of `accesspoint.etl`/`actor.etl` actor rules: `createActorTypeClass`/`createMappedActorTypeClass` + EAnnotations, phase 8 → see `ActorRules.java.AGENTS.md` |
| `BoundTransferOperationAnnotationRules.java` | `BoundTransferOperation` → `EOperation` context placeholder; annotation covered by `CreateBoundAnnotationForTransferOperation` in `OperationRules` → see `BoundTransferOperationAnnotationRules.java.AGENTS.md` |
| `DataRules.java` | Port of `data.etl`: `EntityType` → `EClass`, `createAttribute`/`createAssociationEndRelation`/`createContainmentRelation`, sequence + unmapped-default-only markers → see `DataRules.java.AGENTS.md` |
| `DerivedRules.java` | Port of `derived.etl`: derived `EAttribute`/`EReference` emission + JQL/constraint annotations `addPrimitiveAccessorExpressionAnnotation`/`addStringPrimitiveAccessorConstraints` → see `DerivedRules.java.AGENTS.md` |
| `NamespaceRules.java` | Port of `namespace.etl`, ASM package tree builder, registry phase 1: `namespaceToPackage`/`modelToPackage`/`packageToPackage`/`modelToPackageVersion` + guard `hasVersion` → see `NamespaceRules.java.AGENTS.md` |
| `OperationRules.java` | Port of `operation.etl`: bound/bound-transfer/unbound `EOperation`s, input parameters, script-body, behaviour, stateful, initializer and custom-implementation annotations (1970 lines). → see `OperationRules.java.AGENTS.md` |
| `StaticRules.java` | Port of `static.etl`: `@Greedy` `createUnmappedTransferObjectForStaticData`/`createUnmappedTransferObjectForStaticNavigation` → see `StaticRules.java.AGENTS.md` |
| `TransferObjectRules.java` | Port of `transferObject.etl`: mapped/unmapped transfer-object classes, reference classes for entities, transfer attributes and relations, plus their binding, constraint, permission and metadata annotations (1431 lines). → see `TransferObjectRules.java.AGENTS.md` |
| `TypeRules.java` | Port of `type.etl`: PSM type → `EDataType`/`EEnum` via `createEnumeration`/`createStringType`/`createIntegerType`/`createDecimalType` + integer/decimal/custom guards → see `TypeRules.java.AGENTS.md` |
