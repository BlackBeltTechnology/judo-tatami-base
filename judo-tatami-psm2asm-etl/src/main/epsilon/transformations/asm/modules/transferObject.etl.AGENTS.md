# `transferObject.etl`

695 lines. Maps the PSM transfer-object layer to ASM classes, attributes, references and binding annotations. Imports `../utils/_importUtils.eol` and `namespace.etl`.

## Structural rules

| Rule | Source → Target | Note |
|---|---|---|
| `CreateTransferObject` (`@abstract`) | `TransferObjectType` → `EClass` | base; never fires alone |
| `CreateMappedTransferObject` (`@greedy`) | `MappedTransferObjectType` → `EClass` | extends `CreateTransferObject` |
| `CreateUnmappedTransferObject` (`@greedy`) | `UnmappedTransferObjectType` → `EClass` | extends `CreateTransferObject` |
| `CreateReferenceClassForEntityType` | `EntityType` → `EClass` | synthetic reference class per entity |
| `CreateTransferObjectAttribute` | `TransferAttribute` → `EAttribute` | |
| `CreateTransferObjectRelation` | `TransferObjectRelation` → `EReference` | |

## Binding / expression annotations

`CreateTransferObjectAttributeBindingAnnotation`, `CreateTransferObjectRelationBindingAnnotation`, `CreateDataReferenceBinding` (`TransferAttribute`), `CreateNavigationReferenceBinding` (`TransferObjectRelation`). Binding rules carry the JQL getter/setter expressions onto the ASM member.

## Metadata annotations

`CreateTransferObjectTypeAnnotationClass`, `CreateTransferObjectTypeAnnotationClassForReferenceClass`, `CreateMappedEntityTypeAnnotationOnMappedTransferObject`, `CreateMappedEntityTypeAnnotationOnReferenceClassForEntityType`, `CreateAnnotationOnReferenceClassForEntityType`, `AddTransientAnnotationToTransferAttribute`, `AddTransientAnnotationToTransferObjectRelation`, `AddDefaultAnnotationToTransferAttribute`, `AddDefaultAnnotationToTransferObjectRelation`, `CreateTransferAttributeParameterizedAnnotation`, `CreateTransferObjectRelationParameterizedAnnotation`, `CreateTransferObjectRelationRangeAnnotation`, `CreateTransferAttributeClaimAnnotation`, `CreateTransferObjectRelationAccessAnnotation`, `CreateTransferObjectRelationEmbeddedFlags`, `CreateTransferObjectRelationPermissions`, `CreateQueryCustomizerAnnotationForQueryCustomizerClass`, `CreateMetadataAnnotationForMetadataClass`, `CreateGetRangeInputAnnotationForGetRangeInputClass`.

## Constraint hierarchy

`AddTransferAttributeConstraints` (`@abstract`) → `AddStringTransferAttributeConstraints` (`maxLength`, optional `pattern`), `AddCustomTransferAttributeConstraints` (`customType`), `AddAbstractNumericTransferAttributeConstraints` (`@abstract`) → `AddNumericTransferAttributeConstraints` / `AddMeasuredTransferAttributeConstraints`. Mirrors the attribute constraint hierarchy in `data.etl` — the two must be changed together.

## Documentation

`CreateDocumentationAnnotationForTransferObjectType`, `CreateDocumentationAnnotationForTransferAttribute`, `CreateDocumentationAnnotationForTransferObjectRelation` — all extend `CreateDocumentationAnnotation` from `namespace.etl`.

## Contracts

- Element ids follow `"(psm/" + s.getId() + ")/<RuleSuffix>"`; duplicates break ASM resource loading.
- Annotation `source` values must come from `asmUtils.getAnnotationUri(...)`, never literals.
- `actor.etl` attaches its annotations via `s.asmEquivalent()` to classes produced here — this module must transform first.
- `@abstract` rules never run standalone; only through an `extends` child.
