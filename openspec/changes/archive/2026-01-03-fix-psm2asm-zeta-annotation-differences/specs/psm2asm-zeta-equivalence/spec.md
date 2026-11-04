# psm2asm-zeta-equivalence Specification

## Purpose

Ensure that the PSM2ASM Zeta transformation produces output that is byte-for-byte equivalent to the ETL transformation when using STRICT comparison mode.

## ADDED Requirements

### Requirement: Default Annotations on Transfer Attributes

The Zeta transformation MUST add `default` annotations to EAttribute elements when the source TransferAttribute has a defaultValue defined.

#### Scenario: TransferAttribute with defaultValue gets default annotation
Given a TransferAttribute with a defined defaultValue
When the Zeta transformation executes
Then the equivalent EAttribute has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/default`
And the annotation has a detail entry with key "value" and value equal to the defaultValue name

### Requirement: Default Annotations on Transfer Object Relations

The Zeta transformation MUST add `default` annotations to EReference elements when the source TransferObjectRelation has a defaultValue defined.

#### Scenario: TransferObjectRelation with defaultValue gets default annotation
Given a TransferObjectRelation with a defined defaultValue
When the Zeta transformation executes
Then the equivalent EReference has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/default`
And the annotation has a detail entry with key "value" and value equal to the defaultValue name

### Requirement: Correct exposedBy Filtering via Default Annotations

The Zeta transformation MUST produce `default` annotations that enable correct filtering in `AsmUtils.enrichWithAnnotations()`, ensuring derived default attributes do not receive `exposedBy` annotations.

#### Scenario: Derived default attributes excluded from exposedBy
Given a TransferAttribute with defaultValue pointing to a derived attribute (e.g., `_startDate_default_*`)
When the Zeta transformation adds the `default` annotation
And `enrichWithAnnotations()` is called
Then the derived attribute named in the `default` annotation value is excluded from receiving `exposedBy`
And the output matches ETL behavior

## Related Capabilities

- [psm2asm-transformation](../../specs/psm2asm-transformation/spec.md)
- [zeta-transformations](../../specs/zeta-transformations/spec.md)
