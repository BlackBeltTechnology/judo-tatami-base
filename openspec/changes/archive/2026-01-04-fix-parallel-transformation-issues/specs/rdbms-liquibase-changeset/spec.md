# RDBMS2Liquibase ChangeSet Parity

## Overview

Ensure RDBMS2Liquibase Zeta transformation produces identical changeSets to ETL reference implementation.

## MODIFIED Requirements

### Requirement: ChangeSet Count Parity

Zeta transformation SHALL produce the same number of changeSets as ETL for equivalent input models.

#### Scenario: External model changeSet count

**Given** the RackInspect RDBMS model (1273 tables)
**When** RDBMS2Liquibase transformation executes with Zeta
**Then** the output contains exactly 231 changeSets
**And** each changeSet ID matches the ETL output

#### Scenario: No duplicate changeSets

**Given** any RDBMS model
**When** RDBMS2Liquibase transformation executes
**Then** no duplicate changeSet IDs are generated
**And** each table/field/constraint generates exactly one changeSet

### Requirement: Guard Condition Consistency

Transformation rules SHALL have guards that match ETL behavior exactly.

#### Scenario: Table creation guard

**Given** an RdbmsTable element
**When** evaluating CreateTableChangeSet rule
**Then** the guard excludes the same tables as ETL
**And** no extra changeSets are generated

## Related Capabilities

- rdbms2liquibase-transformation
- zeta-transformation-framework
