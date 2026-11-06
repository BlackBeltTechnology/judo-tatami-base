# psm2asm-xmi-id-correctness Specification

## Purpose
Ensures that psm2asm Zeta transformation produces deterministic XMI IDs matching ETL output, and that dual transformation tests verify XMI ID equivalence.

## Requirements

### Requirement: psm2asm Zeta target elements have deterministic XMI IDs matching ETL output
Every target element created by the psm2asm Zeta transformation SHALL have a deterministic XMI ID registered in the XMLResource ID map, matching the ID produced by the corresponding ETL rule's `t.setId(...)` call. The ID format is `(psm/<sourceElementId>)/<RuleSuffix>` for top-level elements and `<parentId>/<ChildSuffix>` for child/inline elements.

#### Scenario: Top-level annotation element gets correct XMI ID
- **WHEN** a psm2asm Zeta rule creates a top-level `EAnnotation` target
- **THEN** the element is created via `ctx.createTarget(EAnnotation.class, id)` where `id` matches the ETL `t.setId(...)` value for the same rule

#### Scenario: Child annotation element gets hierarchical XMI ID
- **WHEN** a psm2asm Zeta rule creates a child annotation element (e.g., `Getter`, `GetterDialect`, `parameterized`) within a parent annotation rule
- **THEN** the element's XMI ID is derived from the parent annotation's ID using the same suffix as the corresponding ETL rule (e.g., `parentId + "/Getter"`)

#### Scenario: XMI ID equivalence assertion passes
- **WHEN** both ETL and Zeta transformations are run on the same PSM model
- **THEN** `ModelComparator.assertXmiIdsEquivalent(etlResource, zetaResource)` passes without any missing or mismatched IDs

### Requirement: psm2asm dual transformation test verifies XMI ID equivalence
The `Psm2AsmDualTransformationTest` SHALL include a call to `ModelComparator.assertXmiIdsEquivalent` after the structural equivalence comparison passes.

#### Scenario: XMI ID check added to dual test
- **WHEN** both ETL and Zeta structural comparison passes in the dual test
- **THEN** `assertXmiIdsEquivalent` is called on the same resources and does not throw
