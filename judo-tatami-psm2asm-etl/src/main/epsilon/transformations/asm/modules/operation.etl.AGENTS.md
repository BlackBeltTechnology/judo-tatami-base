# `operation.etl`

528 lines. Maps PSM operations to ASM `EOperation`/`EParameter` plus behaviour and permission metadata. Imports `../utils/_importUtils.eol` and `namespace.etl`.

## Structural rules

| Rule | Source → Target | Note |
|---|---|---|
| `CreateOperation` (`@abstract`) | `OperationDeclaration` → `EOperation` | base |
| `CreateBoundOperation` (`@greedy`) | `BoundOperation` → `EOperation` | extends `CreateOperation` |
| `CreateUnboundOperation` (`@greedy`) | `UnboundOperation` → `EOperation` | extends `CreateOperation` |
| `CreateBoundTransferOperation` (`@greedy`) | `BoundTransferOperation` → `EOperation` | standalone, not extending |
| `CreateParameter` (`@abstract`) | `Parameter` → `ETypedElement` | base |
| `CreateInputParameter` (`@greedy`) | `Parameter` → `EParameter` | extends `CreateParameter` |
| `CreateOutputParameterName` (`@greedy`) | `OperationDeclaration` → `EAnnotation` | output carried as annotation, not `EParameter` |

## Script body

`CreateScriptBodyAnnotation` (`@abstract`) → `CreateScriptBodyAnnotationForBoundOperation`, `CreateScriptBodyAnnotationForUnboundOperation`.

## Behaviour / implementation metadata

`CreateCustomImplementationAnnotationOnOperation` (`TransferOperation`), `CreateCustomImplementationAnnotationOnBoundOperation`, `CreateCustomImplementationAnnotationOnUnboundOperation`, `AddBehaviourAnnotation` (`TransferOperation`, ~100 lines dispatching on behaviour kind), `CreateInitializerAnnotation` (`UnboundOperation`), `CreateAbstractAnnotationForBoundOperation`, `CreateInstanceRepresentationOfBoundOperation`, `CreateBoundOperationAnnotation`, `CreateImmutableFlagForTransferOperation`, `CreateTransferOperationInputRangeAnnotation`, `CreateOperationPermissions`.

## Stateful marking

`CreateStatefulAnnotationOnOperation`, `CreateStatefulAnnotationOnOperationWithoutImplementationAndBehaviour`, `CreateStatefulAnnotationOnOperationWithBehaviour` — all three transform `TransferOperation` → `EAnnotation`.

## Documentation

`CreateDocumentationAnnotationForInputParameter`, `CreateDocumentationAnnotationForOutputParameter` (`BoundOperation`), `CreateDocumentationAnnotationForBoundOperation`, `CreateDocumentationAnnotationForTransferOperation` — all extend `CreateDocumentationAnnotation` from `namespace.etl`.

## Contracts

- The three `CreateStatefulAnnotationOn*` rules are mutually exclusive by guard (has implementation / has behaviour / neither). Weakening one guard puts two `stateful` annotations on the same `EOperation`.
- Element ids follow `"(psm/" + s.getId() + ")/<RuleSuffix>"`; duplicates break ASM resource loading.
- Annotation `source` values must come from `asmUtils.getAnnotationUri(...)`.
- `@abstract` rules (`CreateOperation`, `CreateParameter`, `CreateScriptBodyAnnotation`) never run standalone.
