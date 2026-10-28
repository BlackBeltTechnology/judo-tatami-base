# Zeta Transformations - Bug Fixes

## MODIFIED Requirements

### Requirement: Zeta transformations MUST produce equivalent output to ETL

Zeta transformations MUST produce output that is equivalent to ETL transformations when compared using STRICT mode. This includes:
- Same element structure
- Same annotation values
- Same ordering of collections
- Deterministic output across multiple runs

#### Scenario: PSM2ASM produces correct owner annotation paths

**Given** a PSM model with transfer operations that have behaviour annotations
**When** the PSM2ASM Zeta transformation is executed
**Then** the `owner` annotation value includes the full qualified name with model prefix
**And** the output matches ETL output exactly

Example:
- Expected: `owner=rackinspect.services.partner_service.Partner#warehouses`
- Not: `owner=services.partner_service.Partner#warehouses`

#### Scenario: PSM2Measure produces measures in correct order

**Given** a PSM model with multiple measures and units
**When** the PSM2Measure Zeta transformation is executed
**Then** the measures are produced in the same order as ETL
**And** each measure has the correct units assigned

Example:
- First measure should be `AmountOfSubstance` with unit `mol`
- Not `Time` with duration units

#### Scenario: RDBMS2Liquibase produces stable output

**Given** an RDBMS model with tables and columns
**When** the RDBMS2Liquibase Zeta transformation is executed multiple times
**Then** the same number of changeSets are produced each time
**And** the changeSets are in the same order each time

#### Scenario: ASM2RDBMS runs in parallel mode

**Given** an ASM model with entities and relationships
**When** the ASM2RDBMS Zeta transformation is executed
**Then** the transformation runs in parallel mode for improved performance
**And** the output is equivalent to ETL output
