# AGENTS.md — hu.blackbelt.judo.tatami.asm.validation (main)

| File | Purpose |
|---|---|
| `AsmValidationWork.java` | Workflow work validating `AsmModel` via Epsilon EVL. Extends `AbstractTransformationWork`; exports `AsmValidationWork(TransformationContext)`, `execute()`. Runs `AsmEpsilonValidator.validateAsm` with script URI from `calculateAsmValidationScriptURI()`, logging through `StringBuilderLogger`. Requires `AsmModel` registered in `TransformationContext`; missing model throws `IllegalArgumentException`. |
| `AsmValidationZetaWork.java` | Workflow work validating `AsmModel` via native Java (Zeta) rules instead of EVL. Exports `AsmValidationZetaWork(TransformationContext)`, `execute()`. Delegates to `AsmValidator.validateAsm(log, asmModel)`, which throws `AsmValidationException` on rule failure. Zeta replacement for `AsmValidationWork`; ASM rule set currently empty (`asm.evl` has no rules). Requires `AsmModel` in context; missing model throws `IllegalArgumentException`. |
