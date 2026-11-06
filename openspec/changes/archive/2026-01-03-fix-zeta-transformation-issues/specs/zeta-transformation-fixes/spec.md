# Zeta Transformation Fixes

## MODIFIED Requirements

### Requirement: PSM2Measure Unit-Measure Association

The PSM2Measure Zeta transformation MUST associate units with their parent measures correctly using direct EMF containment.

#### Scenario: Unit correctly associated with parent measure
- **Given** a PSM model with a Measure containing Units
- **When** the Zeta transformation processes the Units
- **Then** each Unit is associated with its containing Measure (via `unit.eContainer()`)
- **And** the output matches ETL transformation output

### Requirement: RDBMS2Liquibase Thread Safety

The RDBMS2Liquibase Zeta transformation MUST use thread-safe operations when modifying shared ELists.

#### Scenario: Concurrent changeset additions do not cause errors
- **Given** a transformation running with parallel execution
- **When** multiple threads add ChangeSet elements to the databaseChangeLog
- **Then** no ArrayIndexOutOfBoundsException occurs
- **And** all changesets are successfully added

### Requirement: PSM2ASM Behavior Annotation Ordering

The PSM2ASM Zeta transformation MUST produce behavior annotations in deterministic order.

#### Scenario: Multiple behavior annotations for same operation are ordered consistently
- **Given** multiple TransferOperations bound to the same entity operation
- **When** behavior annotations are added to the entity operation
- **Then** the annotations are ordered deterministically (e.g., alphabetically by owner FQ name)
- **And** the order matches ETL transformation output

### Requirement: PSM2ASM Extension Package Metadata

The PSM2ASM Zeta transformation MUST set nsURI and nsPrefix on extension packages.

#### Scenario: Extension package has correct namespace metadata
- **Given** an extension package created during transformation
- **When** the package is added to the model
- **Then** the package has nsURI derived from root model nsURI
- **And** the package has nsPrefix derived from root model nsPrefix
- **And** the values match ETL transformation output

### Requirement: PSM2ASM Complete Operation Generation

The PSM2ASM Zeta transformation MUST generate all operations that ETL generates.

#### Scenario: List operations are generated for all applicable relations
- **Given** a PSM model with entity relations
- **When** the Zeta transformation processes the model
- **Then** all `_list*` operations are generated
- **And** the operation set matches ETL transformation output
