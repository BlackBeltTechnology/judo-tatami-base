## Why

The Zeta implementation of `psm2asm` is missing two annotation rules for parameterized getter expressions on `TransferAttribute` and `TransferObjectRelation`. When a transfer attribute or relation has a getter expression with a `parameterType`, the ETL engine creates a separate `ExtendedMetadata/parameterized` annotation that downstream consumers (e.g., `Asm2ExpressionWork`) depend on to resolve input parameter types. Without this annotation, JQL expression building fails with "Unknown symbol: input", cascading to failure across the entire transformation pipeline. The bug is currently invisible because the programmatic `Demo.fullDemo()` model used in `Psm2AsmDualTransformationTest` has no parameterized expressions — so the STRICT comparison never exercises this path.

## What Changes

- Add `parameterized` annotation output to `CreateTransferAttributeExpressionAnnotation` rule in `TransferObjectRules.java` when `PrimitiveAccessor.getterExpression.parameterType` is defined
- Add `parameterized` annotation output to `CreateTransferObjectRelationExpressionAnnotation` rule in `TransferObjectRules.java` when `ReferenceAccessor.getterExpression.parameterType` is defined
- Add a unit test in `Psm2AsmDualTransformationTest` (or sibling) that constructs a minimal PSM with parameterized getter expressions and asserts ETL and Zeta outputs are EQUIVALENT (TDD: write test first, verify it fails, then fix)

## Capabilities

### New Capabilities
- `parameterized-getter-annotation`: The `ExtendedMetadata/parameterized` annotation must be created on transfer attributes and relations whose binding has a getter expression with a `parameterType`, matching ETL behavior. Covers both `TransferAttribute` (via `PrimitiveAccessor`) and `TransferObjectRelation` (via `ReferenceAccessor`).

### Modified Capabilities
<!-- No existing spec-level requirements are changing. This is a bug fix restoring conformance with existing ETL behavior. -->

## Impact

- `judo-tatami-psm2asm/src/main/java/.../zeta/rules/TransferObjectRules.java` — add parameterized annotation block in two rules
- `judo-tatami-psm2asm/src/test/java/.../Psm2AsmDualTransformationTest.java` (or new sibling test) — new test covering parameterized getter path
- No API changes, no breaking changes
- Fixes downstream failure in `Asm2ExpressionWork` for models with parameterized queries
