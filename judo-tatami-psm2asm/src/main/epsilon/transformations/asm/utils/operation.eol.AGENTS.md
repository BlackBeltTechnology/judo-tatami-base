# `operation.eol`

Resolves ASM equivalents and parameter roles for PSM operations. Exports `@cached asmEquivalent()` on `BoundOperation` (`CreateBoundOperation`), `BoundTransferOperation` (`CreateBoundTransferOperation`), `UnboundOperation` (`CreateUnboundOperation`), all → `EOperation`; plus `@cached` role predicates on `Parameter`: `isInput()`, `isOutput()`, `isFault()`, each comparing against the containing `OperationDeclaration`'s `input`/`output`/`faults`.
Predicates read `self.eContainer` directly — a detached `Parameter` yields false, not an error.