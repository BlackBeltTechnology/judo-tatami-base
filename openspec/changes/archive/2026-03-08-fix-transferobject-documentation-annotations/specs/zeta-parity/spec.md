## ADDED Requirements

### Requirement: Zeta produces documentation annotations for TransferObject types
The Zeta PSM2ASM transformation SHALL produce documentation annotations for TransferObjectType, TransferAttribute, and TransferObjectRelation elements, matching the ETL output.

#### Scenario: TransferObjectType with documentation
- **WHEN** a PSM TransferObjectType has a non-empty `documentation` field
- **THEN** the Zeta transformation creates an EAnnotation with URI `documentation` and detail key `value` containing the documentation text, attached to the equivalent EClass

#### Scenario: TransferAttribute with documentation
- **WHEN** a PSM TransferAttribute has a non-empty `documentation` field
- **THEN** the Zeta transformation creates an EAnnotation with URI `documentation` and detail key `value` containing the documentation text, attached to the equivalent EAttribute

#### Scenario: TransferObjectRelation with documentation
- **WHEN** a PSM TransferObjectRelation has a non-empty `documentation` field
- **THEN** the Zeta transformation creates an EAnnotation with URI `documentation` and detail key `value` containing the documentation text, attached to the equivalent EReference
