# ETL to Zeta Migration Documentation

## ADDED Requirements

### Requirement: Migration Documentation SHALL Include Index

The project SHALL provide a migration documentation index at `docs/migration/README.md` that links to all migration-related documentation.

#### Scenario: Index provides navigation to migration guides
Given a user wants to learn about ETL to Zeta migration
When they access the migration documentation index
Then they find links to:
- ETL vs Zeta comparison guide
- Step-by-step migration guide
- Testing framework documentation

### Requirement: Documentation SHALL Include ETL vs Zeta Syntax Comparison

The documentation SHALL provide a comprehensive comparison of ETL and Zeta syntax patterns at `docs/migration/etl-zeta-comparison.md`.

#### Scenario: Developer compares simple rule syntax
Given a developer is migrating a simple ETL rule
When they consult the syntax comparison guide
Then they find side-by-side examples showing:
- ETL rule structure (`@greedy rule ... transform ... to ...`)
- Zeta rule structure (`@TransformRule`, `@Transform`, `@To` annotations)
- Property access patterns (`s.name` vs `s.getName()`)
- Object creation patterns (implicit vs `ctx.createTarget()`)

#### Scenario: Developer compares guarded rule syntax
Given a developer is migrating a guarded ETL rule
When they consult the syntax comparison guide
Then they find examples showing:
- ETL inline guard (`guard: condition`)
- Zeta guard method pattern (`@Guard(method = "guardMethod")`)

#### Scenario: Developer compares multi-output rule syntax
Given a developer is migrating a rule with multiple outputs
When they consult the syntax comparison guide
Then they find examples showing:
- ETL multi-output (`to t1 : Type1, t2 : Type2`)
- Zeta `ctx.registerEquivalent()` pattern

### Requirement: Documentation SHALL Include Step-by-Step Migration Guide

The documentation SHALL provide a step-by-step migration guide at `docs/migration/etl-to-zeta-migration.md`.

#### Scenario: Developer follows migration steps
Given a developer wants to migrate a transformation module
When they follow the migration guide
Then they find:
- Directory structure template for Zeta implementation
- Rule migration order recommendations (dependencies first)
- Helper class extraction patterns
- Post-processing implementation patterns

#### Scenario: Developer avoids common pitfalls
Given a developer encounters an issue during migration
When they consult the migration guide pitfalls section
Then they find documented solutions for:
- Recursive update issues with bidirectional references
- Order-dependent transformation handling
- Container addition timing issues
- Missing equivalent handling

### Requirement: Documentation SHALL Include Testing Framework Guide

The documentation SHALL provide testing framework documentation at `docs/migration/dual-engine-testing.md`.

#### Scenario: Developer sets up dual-engine tests
Given a developer wants to test both ETL and Zeta engines
When they consult the testing documentation
Then they find:
- TransformationMode enum from judo-tatami-core
- Parameterized test setup with @EnumSource
- Model comparison assertion patterns
- Configuration options

#### Scenario: Developer configures model comparison
Given a developer needs to compare ETL and Zeta outputs
When they consult the ModelComparator documentation
Then they find:
- Available comparison modes (STRICT, STRUCTURAL, LENIENT)
- Configuration via system properties
- Difference reporting and analysis

### Requirement: Documentation SHALL Include Module-Specific ETL-to-Zeta Mapping

Each transformation module documentation SHALL include a detailed ETL rule to Zeta rule mapping table.

#### Scenario: Developer looks up specific rule mapping
Given a developer is migrating a specific ETL rule in psm2asm
When they consult the psm2asm documentation
Then they find a table mapping:
- ETL rule name to Zeta method name
- ETL file location to Zeta class location
- Key implementation differences for that rule

## MODIFIED Requirements

### Requirement: Existing Documentation Index SHALL Link to Migration Guides

The existing `docs/transformations/README.md` SHALL be updated to include links to the migration documentation.

#### Scenario: User discovers migration documentation
Given a user is reading the transformations README
When they look for migration guidance
Then they find a section linking to the migration documentation

### Requirement: Module Documentation SHALL Show Both ETL and Zeta Implementations

Each existing module documentation file (`psm2asm.md`, `asm2rdbms.md`, etc.) SHALL be updated with Zeta implementation details.

#### Scenario: Module documentation shows both implementations
Given a developer is reading module documentation
When they view the rule documentation
Then they see:
- ETL rule definition (existing)
- Zeta rule definition (added)
- Implementation notes highlighting differences
