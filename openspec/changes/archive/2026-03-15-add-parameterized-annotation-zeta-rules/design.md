## Context

The Zeta `psm2asm` pipeline reimplements the original Epsilon ETL transformation rules in Java. The ETL `transferObject.etl` has two rules (`CreateTransferAttributeParameterizedAnnotation`, `CreateTransferObjectRelationParameterizedAnnotation`) that create a distinct `ExtendedMetadata/parameterized` EAnnotation on transfer attributes and relations whose binding has a getter expression with a `parameterType`. This annotation carries two details: `value=true` and `type=<FQName of the parameter EClass>`.

The Zeta `TransferObjectRules.java` already captures `getter.parameter` inside the expression annotation (the correct field for expression builders), but does not create the separate `parameterized` annotation. The `StaticRules.java` implements the correct pattern for `StaticData`/`StaticNavigation` — so a working reference exists in-repo.

The programmatic `Demo.fullDemo()` model used in `Psm2AsmDualTransformationTest` contains no parameterized getter expressions, meaning the STRICT comparison never exercises this path. The bug only surfaces with real DSL-compiled models.

## Goals / Non-Goals

**Goals:**
- TDD: write a failing test first that constructs a minimal PSM with parameterized getter expressions and asserts ETL ≡ Zeta (STRICT)
- Implement the two missing annotation blocks in `TransferObjectRules.java`
- Verify the test passes after the fix

**Non-Goals:**
- Changes to any other transformation module
- Changes to the `Demo.fullDemo()` model (the fix is in the Zeta rules, not in updating the test model)
- Modifying `Asm2ExpressionWork` or downstream consumers

## Decisions

### D1: Fix inline vs. new rule method

**Decision:** Add the `parameterized` annotation block inline within the existing `CreateTransferAttributeExpressionAnnotation` and `CreateTransferObjectRelationExpressionAnnotation` rule methods, immediately after the existing `getter.parameter` block. No new `@TransformRule` method.

**Rationale:** The StaticRules pattern does the same — a guarded `if` block inside the rule body. ETL separates it into a standalone rule for historical ETL reasons (each rule produces exactly one target element), but in Zeta there is no such constraint. Keeping it inline avoids adding rule names to `Psm2AsmRuleNames.java` for trivial annotation blocks, and keeps the annotation co-located with the related logic.

**Alternative considered:** New `@TransformRule` method (like `CreateTransferAttributeParameterizedAnnotation`). Rejected because it adds boilerplate without benefit — the guard condition and context are identical to the expression rule, and Zeta rules can produce side-effects inline.

### D2: Annotation ID convention

**Decision:** Use IDs matching ETL exactly:
- `"(psm/" + getId(s) + ")/TransferAttributeParameterizedAnnotation"`
- `"(psm/" + getId(s) + ")/TransferObjectRelationParameterizedAnnotation"`

**Rationale:** STRICT comparison uses EAnnotation ID as a key. The ETL produces these IDs. Zeta must match.

Note: StaticRules uses `...ForStaticData` and `...ForStaticNavigation` suffixes — those are correct for static rules because the source element type is different. Transfer object rules use the same source element types as the ETL rules, so no suffix is needed.

### D3: Test approach

**Decision:** Add the new test case to `Psm2AsmDualTransformationTest` (uses `AbstractDualTransformationTest` infrastructure already wired for ETL vs Zeta comparison). Construct a minimal `UnmappedTransferObjectType` with a `TransferAttribute` whose `binding` is a `PrimitiveAccessor` with a getter expression that has a `parameterType`.

**Rationale:** This is the fastest path to a reproducible failing test in the existing dual-comparison test suite. No new test infrastructure needed.

## Risks / Trade-offs

- **Risk: Other downstream consumers expect `getter.parameter` only, not a separate annotation** → Mitigation: The StaticRules already produce the same `parameterized` annotation for static elements with no reported issues. Adding it for transfer objects restores parity with ETL behavior.
- **Risk: ID collision if the same element somehow matches both static and transfer paths** → Not possible: static elements (`StaticData`, `StaticNavigation`) are different PSM types from `TransferAttribute`/`TransferObjectRelation`.

## Open Questions

- None — the StaticRules working implementation and the ETL source provide unambiguous specifications for both rules.
