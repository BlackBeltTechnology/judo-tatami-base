# `OperationRules.java`

1970 lines — largest file in the module. `hu.blackbelt.judo.tatami.psm2asm.zeta.rules`. Zeta port of `operation.etl`. Annotated `@TransformationContext(source = BoundOperation.class, target = EOperation.class)`.

## Operation-producing rules

`createBoundOperation` (`BoundOperation` → `EOperation`), `createBoundTransferOperation` (`BoundTransferOperation` → `EOperation`), `createUnboundOperation` (`UnboundOperation` → `EOperation`).

## Parameter rules

`createInputParameter`, `createBoundOperationInputParameter` → `EParameter`; `createOutputParameterNameForBoundOperation` → `EAnnotation` (output is carried as an annotation, not an `EParameter`, matching ETL).

## Annotation rules

`createBoundOperationAnnotation`, `createInstanceRepresentationOfBoundOperation`, `createAbstractBoundOperationAnnotation`, `createCustomImplementationAnnotationOnBoundOperation`, `createCustomImplementationForBTO`, `createCustomImplementationForUO`, `createScriptBodyAnnotationForBoundOperation`, `createScriptBodyAnnotationForUnboundOperation`, `createInitializerAnnotation`, `createBehaviourAnnotationForBoundTransferOp`, `createBehaviourAnnotationForUnboundOp`, `createStatefulAnnotationOnBoundTransferOp`, `createStatefulAnnotationOnUnboundOp`, `createStatefulAnnotationDefaultForBTO`, `createStatefulAnnotationDefaultForUO`.

## Guards

`hasInput`, `hasOutput`, `hasTransferOutput`, `isInputParameter`, `isAbstractOperation`, `hasBoundOperationImplementation`, `hasUnboundOperationImplementation`, `hasBoundTransferImplementation`, `hasImplementation`, `hasBehaviour`, `hasBoundTransferBehaviour`, `hasUnboundBehaviour`, `hasNoImplementationAndNoBehaviour`, `hasBTONoImplementationAndNoBehaviour`, `hasUONoImplementationAndNoBehaviour`, `hasBoundOperationScriptBody`, `hasUnboundOperationScriptBody`, `isInitializer`.

## Contracts

- The ETL rule `CreateStatefulAnnotationOnOperation*` trio is split here per concrete source type: `…OnBoundTransferOp` / `…OnUnboundOp` for the implementation-or-behaviour case, `…DefaultForBTO` / `…DefaultForUO` for the neither case. Guards `hasBTONoImplementationAndNoBehaviour` / `hasUONoImplementationAndNoBehaviour` must stay the exact complement of `hasBoundTransferImplementation ∨ hasBoundTransferBehaviour` (and the unbound pair) — overlap yields two `stateful` annotations on one `EOperation`, a gap yields none.
- Same split applies to custom-implementation and behaviour rules (`…ForBTO` / `…ForUO`); the split is Zeta-only and its rule names have no ETL counterpart.
- `BoundTransferOperationAnnotationRules` must be registered *after* `OperationRules` (`Psm2AsmZetaTransformation` phase 7b) so its annotations attach to operations created here.
- All mutations must go through `Psm2AsmHelper` (`addOperation`, `addParameter`, `addAnnotation`) — raw EMF list mutation bypasses id propagation.
- Rule names come from static `Psm2AsmRuleNames` constants; never inline a rule-name string.
