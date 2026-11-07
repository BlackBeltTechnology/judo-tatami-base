# `BoundTransferOperationAnnotationRules.java`

Declares `BoundTransferOperation` → `EOperation` transformation context and nothing else — class body holds only registry constructor, no guards, no rules.
Header states why: `CreateBoundAnnotationForTransferOperation` in `OperationRules` already covers `BoundTransferOperation` because it extends `TransferObjectType`'s `TransferOperation`, so ETL rule `CreateBoundOperationAnnotation` needs no Zeta twin here.
Registering it (phase 7b) is currently a no-op placeholder; removing it changes nothing in the output but loses the documented intent.