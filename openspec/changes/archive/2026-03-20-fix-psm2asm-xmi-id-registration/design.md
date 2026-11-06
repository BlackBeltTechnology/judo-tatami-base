## Context

The `asm2rdbms` Zeta transformation was recently fixed (change `fix-zeta-xmi-id-registration`) to register XMI IDs correctly via `ctx.createTarget(Type.class, id)` instead of the no-op `setUuid()` pattern. The same problem exists in `psm2asm`.

In `psm2asm` Zeta, 73 calls to `ctx.createTarget(Type.class)` omit the ID parameter. These produce auto-generated IDs using the rule method name as suffix (e.g., `(psm/.../CreateMeasuredAnnotationOfIntegerType)`) rather than the ETL-derived suffix (e.g., `(psm/.../MeasuredAnnotationOfIntegerType)`). The mismatch makes `assertXmiIdsEquivalent` fail and is currently untested.

Key finding from ETL analysis: ETL sets IDs on **all** targets including `EAnnotation` child elements (e.g., `/Getter`, `/GetterDialect`, `/parameterized`). These form hierarchical IDs rooted at the parent element's ID. The 73 Zeta sites are entirely `EAnnotation` and child elements within annotation rules.

## Goals / Non-Goals

**Goals:**
- Fix all `createTarget(Type.class)` calls in 6 psm2asm rule files to pass the correct ETL-matching ID
- Enable `assertXmiIdsEquivalent` in `Psm2AsmDualTransformationTest`
- All existing tests continue to pass (no behavior change)

**Non-Goals:**
- Fixing psm2measure, rdbms2liquibase, or asm2keycloak Zeta transformations
- Changing any structural transformation behavior
- Adding new transformation rules

## Decisions

### D1: Use `ctx.createTarget(Type.class, id)` as the primary fix pattern

Same pattern established by `fix-zeta-xmi-id-registration`. Passes the ID at creation time, routed through `setElementIdInternal()` → `pendingXmiIds` → `XMLResource.setID()` at commit.

For annotation child elements that are created inline (not via `ctx.createTarget`) and directly added to parent annotations, use `ctx.setElementId(element, id)` after creation.

### D2: Hierarchical IDs for annotation child elements

ETL builds IDs hierarchically: `parentAnnotationId + "/ChildSuffix"`. For example:
- Parent: `"(psm/" + s.getId() + ")/PrimitiveAccessorExpressionAnnotation"`
- Child getter: `parentId + "/Getter"`
- Child getter dialect: `parentId + "/GetterDialect"`

Zeta rules must follow the same scheme — compute the parent ID first, then derive child IDs from it.

### D3: Scope by rule file

Fix by file in order of complexity (simplest first):
1. `NamespaceRules.java` (1 site) — `EAnnotation` for package annotations
2. `DataRules.java` (1 site) — `EAnnotation` for data annotations
3. `ActorRules.java` (3 sites) — `EAnnotation` for actor annotations
4. `DerivedRules.java` (6 sites) — `EAnnotation` for derived accessor annotations
5. `TransferObjectRules.java` (24 sites) — `EAnnotation` for transfer object annotations
6. `OperationRules.java` (38 sites) — `EAnnotation` for operation annotations

### D4: Test gate — add `assertXmiIdsEquivalent` only after all rules are fixed

Add the assertion to `Psm2AsmDualTransformationTest` as the final task, after verifying all 6 rule files are fixed and tests pass.

## Risks / Trade-offs

- **Annotation ID derivation complexity**: ETL creates multi-level ID hierarchies (e.g., `annotation/Getter/GetterDialect`). Each level must be derived from the parent ID, requiring careful reading of the corresponding ETL rule.
  → Mitigation: Read the ETL file for each rule before implementing the Zeta fix.

- **Large OperationRules.java (38 sites, ~1900 lines)**: High volume of annotation rules with complex child hierarchies.
  → Mitigation: Tackle rule-by-rule systematically; use grep to find each ETL counterpart.

## Open Questions

- None. The approach is established from the `asm2rdbms` fix.
