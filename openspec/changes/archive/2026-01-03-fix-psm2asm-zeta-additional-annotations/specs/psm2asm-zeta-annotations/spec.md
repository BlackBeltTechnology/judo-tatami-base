# psm2asm-zeta-annotations Specification

## Purpose

Ensure that the PSM2ASM Zeta transformation produces all required annotations to achieve byte-for-byte equivalence with the ETL transformation.

## ADDED Requirements

### Requirement: Claim Annotations on Transfer Attributes

The Zeta transformation MUST add `claim` annotations to EAttribute elements when the source TransferAttribute has a claimType defined.

#### Scenario: TransferAttribute with claimType gets claim annotation
Given a TransferAttribute with a defined claimType
When the Zeta transformation executes
Then the equivalent EAttribute has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/claim`
And the annotation has a detail entry with key "value" and value equal to the claimType

### Requirement: Metadata Annotations on Metadata Classes

The Zeta transformation MUST add `metadata` annotations to EClass elements when the source TransferObjectType is a metadata type.

#### Scenario: Metadata TransferObjectType gets metadata annotation
Given a TransferObjectType that is a metadata type
When the Zeta transformation executes
Then the equivalent EClass has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/metadata`
And the annotation has a detail entry with key "value" and value "true"

### Requirement: QueryCustomizer Annotations on QueryCustomizer Classes

The Zeta transformation MUST add `queryCustomizer` annotations to EClass elements when the source TransferObjectType has queryCustomizer flag set to true.

#### Scenario: QueryCustomizer TransferObjectType gets queryCustomizer annotation
Given a TransferObjectType with queryCustomizer property set to true
When the Zeta transformation executes
Then the equivalent EClass has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/queryCustomizer`
And the annotation has a detail entry with key "value" and value "true"

### Requirement: InputRange Annotations on Correct Operations

The Zeta transformation MUST add `inputRange` annotations only to TransferOperation elements that have an inputRange defined.

#### Scenario: TransferOperation with inputRange gets inputRange annotation
Given a TransferOperation with a defined inputRange
When the Zeta transformation executes
Then the equivalent EOperation has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/inputRange`
And the annotation has a detail entry with key "value" and value equal to the input range reference FQ name

#### Scenario: TransferOperation without inputRange does not get inputRange annotation
Given a TransferOperation without a defined inputRange
When the Zeta transformation executes
Then the equivalent EOperation does not have an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/inputRange`

## MODIFIED Requirements

### Requirement: MappedEntityType Annotation with Filter Details

The Zeta transformation MUST include filter and filterDialect details in the `mappedEntityType` annotation when the MappedTransferObjectType has a filter defined.

#### Scenario: MappedTransferObjectType with filter gets complete mappedEntityType annotation
Given a MappedTransferObjectType with a defined filter expression
When the Zeta transformation executes
Then the equivalent EClass has an EAnnotation with source `http://blackbelt.hu/judo/meta/ExtendedMetadata/mappedEntityType`
And the annotation has a detail entry with key "value" and value equal to the entity type FQ name
And the annotation has a detail entry with key "filter" and value equal to the filter expression
And the annotation has a detail entry with key "filterDialect" and value equal to the filter dialect

## Related Capabilities

- [psm2asm-transformation](../../specs/psm2asm-transformation/spec.md)
- [zeta-transformations](../../specs/zeta-transformations/spec.md)
