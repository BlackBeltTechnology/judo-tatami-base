## Context

ETL has 3 documentation annotation rules in `transferObject.etl` that extend the abstract `CreateDocumentationAnnotation` rule from `namespace.etl`. The Zeta `TransferObjectRules.java` never implemented these rules. The rule name constants already exist in `Psm2AsmRuleNames.java`.

## Goals / Non-Goals

**Goals:**
- Add 3 missing documentation rules to `TransferObjectRules.java`
- Achieve STRICT equivalence on rackinspect PSM2ASM comparison

**Non-Goals:**
- Not changing any ETL rules
- Not refactoring existing documentation rules in other Zeta rule classes

## Decisions

**Follow ActorRules pattern**: Use separate `@TransformRule` methods with `@Greedy` and a shared `hasDocumentation` guard method, rather than inlining into existing rules. This matches the pattern in `ActorRules.java` and `OperationRules.java`.

**Attach via `ctx.equivalent()`**: Each rule looks up the already-created target element (EClass, EAttribute, EReference) via `ctx.equivalent()` and adds the annotation to it.

## Risks / Trade-offs

- Minimal risk - follows established pattern used by all other documentation rules in Zeta
