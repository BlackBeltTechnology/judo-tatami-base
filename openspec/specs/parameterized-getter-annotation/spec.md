## ADDED Requirements

### Requirement: Parameterized annotation on TransferAttribute with parameterized getter
When a `TransferAttribute` has a binding of type `PrimitiveAccessor` whose getter expression defines a `parameterType`, the Zeta `psm2asm` transformation SHALL create an `EAnnotation` with source `ExtendedMetadata/parameterized` on the resulting `EAttribute`, containing two details: `value=true` and `type=<FQName of the parameter EClass>`. This annotation SHALL be identical to the one produced by the ETL rule `CreateTransferAttributeParameterizedAnnotation`.

#### Scenario: TransferAttribute with parameterized PrimitiveAccessor getter produces parameterized annotation
- **WHEN** a `TransferAttribute` has a `PrimitiveAccessor` binding with a getter expression whose `parameterType` is defined
- **THEN** the resulting `EAttribute` SHALL have an `EAnnotation` with source URI `ExtendedMetadata/parameterized`
- **THEN** the annotation SHALL contain a detail with key `value` and value `true`
- **THEN** the annotation SHALL contain a detail with key `type` and value equal to the fully-qualified name of the parameter EClass in the ASM model
- **THEN** the annotation ID SHALL be `"(psm/<sourceId>)/TransferAttributeParameterizedAnnotation"`

#### Scenario: TransferAttribute without parameterized getter produces no parameterized annotation
- **WHEN** a `TransferAttribute` has a `PrimitiveAccessor` binding with a getter expression whose `parameterType` is NOT defined
- **THEN** the resulting `EAttribute` SHALL NOT have an `EAnnotation` with source URI `ExtendedMetadata/parameterized`

#### Scenario: ETL and Zeta produce equivalent output for model with parameterized TransferAttribute
- **WHEN** a PSM model contains a `TransferAttribute` with a parameterized `PrimitiveAccessor` getter
- **THEN** STRICT comparison between ETL and Zeta ASM outputs SHALL report EQUIVALENT (zero differences)

### Requirement: Parameterized annotation on TransferObjectRelation with parameterized getter
When a `TransferObjectRelation` has a binding of type `ReferenceAccessor` whose getter expression defines a `parameterType`, the Zeta `psm2asm` transformation SHALL create an `EAnnotation` with source `ExtendedMetadata/parameterized` on the resulting `EReference`, containing two details: `value=true` and `type=<FQName of the parameter EClass>`. This annotation SHALL be identical to the one produced by the ETL rule `CreateTransferObjectRelationParameterizedAnnotation`.

#### Scenario: TransferObjectRelation with parameterized ReferenceAccessor getter produces parameterized annotation
- **WHEN** a `TransferObjectRelation` has a `ReferenceAccessor` binding with a getter expression whose `parameterType` is defined
- **THEN** the resulting `EReference` SHALL have an `EAnnotation` with source URI `ExtendedMetadata/parameterized`
- **THEN** the annotation SHALL contain a detail with key `value` and value `true`
- **THEN** the annotation SHALL contain a detail with key `type` and value equal to the fully-qualified name of the parameter EClass in the ASM model
- **THEN** the annotation ID SHALL be `"(psm/<sourceId>)/TransferObjectRelationParameterizedAnnotation"`

#### Scenario: ETL and Zeta produce equivalent output for model with parameterized TransferObjectRelation
- **WHEN** a PSM model contains a `TransferObjectRelation` with a parameterized `ReferenceAccessor` getter
- **THEN** STRICT comparison between ETL and Zeta ASM outputs SHALL report EQUIVALENT (zero differences)
