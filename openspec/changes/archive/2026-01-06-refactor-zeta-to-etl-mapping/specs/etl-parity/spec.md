# etl-parity Specification

## Purpose
This specification defines requirements for ensuring Zeta transformations produce 1:1 equivalent output to ETL transformations. The goal is semantic parity between ETL and Zeta rule implementations.

## ADDED Requirements
### Requirement: Separate Annotation Rules

Zeta rules MUST create elements and annotations in separate rules to match ETL semantics.

#### Scenario: Entity type transformation
- **Given** a PSM EntityType with documentation
- **When** transforming with ETL and Zeta
- **Then** ETL creates: EntityClass, EntityAnnotation, DocumentationAnnotation
- **And** Zeta creates: EntityClass, EntityAnnotation, DocumentationAnnotation
- **And** all annotations are structurally equivalent

#### Scenario: Attribute with constraints
- **Given** a PSM Attribute with StringType data type and maxLength constraint
- **When** transforming with ETL and Zeta
- **Then** ETL creates: EAttribute, ConstraintsAnnotation
- **And** Zeta creates: EAttribute, ConstraintsAnnotation
- **And** annotation details match (maxLength value)

### Requirement: Named Equivalent Usage

When looking up equivalent elements, Zeta MUST use the rule name discriminator to match ETL's `s.equivalent("RuleName")` semantics.

#### Scenario: Entity with supertypes
- **Given** a PSM EntityType that inherits from another EntityType
- **When** creating the EClass with supertypes
- **Then** Zeta uses `ctx.equivalent(superType, EClass.class, CREATE_ENTITY_CLASS)`
- **And** the superclass reference matches ETL output

#### Scenario: Attribute referencing entity
- **Given** a PSM Attribute belonging to an EntityType
- **When** creating the EAttribute
- **Then** Zeta uses `ctx.equivalent(owner, EClass.class, CREATE_ENTITY_CLASS)`
- **And** the containing class reference matches ETL output

### Requirement: Guard Method Parity

Zeta guard methods MUST implement the same boolean logic as ETL guards.

#### Scenario: String type guard
- **Given** an Attribute with StringType data type
- **When** checking `isStringTypeGuard`
- **Then** result is true
- **And** `ADD_STRING_ATTRIBUTE_CONSTRAINTS` rule fires

#### Scenario: Primitive attribute guard
- **Given** an Attribute with primitive data type
- **When** checking `isPrimitiveAttribute` guard
- **Then** result is true
- **And** `CREATE_ATTRIBUTE` rule fires

#### Scenario: Measured type guard
- **Given** an Attribute with MeasuredType data type
- **When** checking `isMeasuredAttribute` guard
- **Then** result is true
- **And** `ADD_MEASURED_ATTRIBUTE_CONSTRAINTS` rule fires

### Requirement: Rule Ordering and @Greedy Semantics

Zeta rule registration order MUST match ETL execution order, and @Greedy MUST be used appropriately.

#### Scenario: Type rules before entity rules
- **Given** a transformation with type and entity rules
- **When** registering rules with TransformationRegistry
- **Then** TypeRules are registered before DataRules
- **And** entity annotations are created after entity classes

#### Scenario: Annotation rules after main rules
- **Given** `CREATE_ENTITY_CLASS` and `CREATE_ENTITY_ANNOTATION_CLASS`
- **When** executing transformation
- **Then** entity class exists before annotation is added
- **And** annotation rule uses @Greedy to execute eagerly

### Requirement: Missing Rule Implementation

All ETL rules MUST have corresponding Zeta implementations.

#### Scenario: psm2asm has 45 missing rules
- **Given** psm2asm transformation
- **When** comparing rule count
- **Then** ETL has 137 rules
- **And** Zeta has 137 rules after implementation
- **And** all tests pass with STRICT comparison

#### Scenario: rdbms2liquibase has 48 missing rules
- **Given** rdbms2liquibase transformation
- **When** comparing rule count
- **Then** ETL has 62 rules
- **And** Zeta has 62 rules after implementation
- **And** all tests pass with STRICT comparison

### Requirement: Parallel Mode Compatibility

Zeta transformations MUST work correctly in parallel execution mode.

#### Scenario: Parallel execution produces same output
- **Given** a large model transformation
- **When** running with parallel=true
- **Then** output matches sequential execution
- **And** no ClassCastException occurs
- **And** no MissingElementDifference exceptions occur

#### Scenario: No race conditions in annotation addition
- **Given** multiple rules adding annotations to the same element
- **When** running in parallel mode
- **Then** all annotations are present in output
- **And** annotation order is deterministic (by rule registration)

### Requirement: XMI ID Consistency

Zeta transformations MUST produce XMI IDs that match ETL for comparison purposes.

#### Scenario: XMI IDs match for comparison
- **Given** an ETL and Zeta transformed model
- **When** running ModelComparator with XMI ID comparison
- **Then** elements with matching XMI IDs are matched correctly
- **And** no MissingElementDifference for elements that should match

### Requirement: Post-Processing Parity

Zeta post-processing MUST perform the same operations as ETL post-processing.

#### Scenario: Root packages added to resource
- **Given** a transformed ASM model
- **When** checking root package contents
- **Then** all Model packages are added to resource
- **And** XMI IDs are applied to all elements

#### Scenario: EOpposite set correctly
- **Given** bidirectional association between two entities
- **When** checking EReference EOpposite
- **Then** EOpposite is set on both references
- **And** values match ETL output
