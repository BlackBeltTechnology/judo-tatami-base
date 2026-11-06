# `TransferObjectRules.java`

1431 lines, `hu.blackbelt.judo.tatami.psm2asm.zeta.rules`. Zeta port of `transferObject.etl`. Annotated `@TransformationContext(source = TransferObjectType.class, target = EClass.class)`.

## Class-level rules

`createMappedTransferObjectTypeClass` (`MappedTransferObjectType` → `EClass`), `createUnmappedTransferObjectTypeClass` (`UnmappedTransferObjectType` → `EClass`), `createReferenceClassForEntityType` (`EntityType` → synthetic reference `EClass`).

## Class-level annotations

`createMappedTransferObjectAnnotation`, `createTransferObjectTypeAnnotation`, `createQueryCustomizerAnnotation`, `createMetadataAnnotation`, `createActorAnnotation`, `createGetRangeInputAnnotation`, `createDocumentationAnnotationForTransferObjectType` — all → `EAnnotation`.

## Transfer attribute rules

`createTransferAttribute` (`TransferAttribute` → `EAttribute`), plus `EAnnotation` producers `addTransferAttributeBindingAnnotation`, `addTransientAnnotationToTransferAttribute`, `addStringTransferAttributeConstraints`, `addCustomTransferAttributeConstraints`, `addNumericTransferAttributeConstraints`, `addMeasuredTransferAttributeConstraints`, `createDataReferenceBinding`, `createTransferAttributeParameterizedAnnotation`, `addDefaultAnnotationToTransferAttribute`, `createTransferAttributeClaimAnnotation`, `createDocumentationAnnotationForTransferAttribute`.

## Transfer object relation rules

`createTransferObjectRelation` (`TransferObjectRelation` → `EReference`), plus `EAnnotation` producers `createTransferObjectRelationAccessAnnotation`, `createTransferObjectRelationPermissions`, `createNavigationReferenceBinding`, `createTransferObjectRelationParameterizedAnnotation`, `addDefaultAnnotationToTransferObjectRelation`, `createDocumentationAnnotationForTransferObjectRelation`.

## Guards

`isPrimitiveTransferAttribute`, `hasEntityType`, `hasBinding`, `hasNoBinding`, `isStringTransferAttribute`, `isCustomTypeTransferAttribute`, `isNumericTransferAttribute`, `isMeasuredTransferAttribute`, `isAccessRelation`, `hasNavigationBinding`, `hasDataBinding`, `hasActorType`, `isGetRangeInputType`, `hasDefaultValue`, `hasDefaultValueRelation`, `hasClaimType`, `isQueryCustomizer`, `isMetadataType`, `hasDocumentation`, `hasPrimitiveAccessorWithParameterType`, `hasReferenceAccessorWithParameterType`.

## Contracts

- `isGetRangeInputType` reads the context attribute `getRangeInputTypes`, pre-computed by `Psm2AsmZetaTransformation.createContext()`. Running these rules through a context that does not set that attribute makes the guard return false for every type, silently dropping get-range-input annotations.
- `hasBinding` / `hasNoBinding` and the four constraint guards are intended to partition their input. Weakening one produces duplicate annotations on the same ASM element.
- `createTransferObjectRelation` leaves `EType` unset when the target class is not yet created; `Psm2AsmZetaTransformation.postProcess` step 3 fills it. Rules must not assume `EReference.getEType()` is non-null during transform.
- All mutations must go through `Psm2AsmHelper` (`addClassifier`, `addStructuralFeature`, `addAnnotation`, …) — raw EMF list mutation bypasses id propagation.
- Rule names come from static `Psm2AsmRuleNames` constants; never inline a rule-name string.
- `ActorRules` resolves the classes produced here — `TransferObjectRules` must be registered first (it is: registry phase 4 vs. phase 8).
