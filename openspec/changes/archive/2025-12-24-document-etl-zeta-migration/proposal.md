# Document Tatami-Base Transformation Usage

## Summary

Document the tatami-base specific transformation modules and dual-engine testing patterns. Generic Zeta framework documentation belongs in the Zeta project itself.

## Motivation

The judo-tatami-base project implements dual transformation engines (ETL and Zeta) for all transformation modules. This documentation focuses on:

1. **Module-specific usage** - How to use each transformation module in tatami-base
2. **Dual-engine testing** - The testing pattern for verifying ETL/Zeta equivalence
3. **Transformation configuration** - Module-specific options and settings

## Scope

### In Scope (Tatami-Base Specific)

1. **Dual-Engine Testing Guide** (`docs/migration/dual-engine-testing.md`)
   - TransformationMode enum usage from judo-tatami-core
   - ModelComparator for equivalence verification
   - Parameterized test patterns

2. **Module Documentation** (`docs/transformations/*.md`)
   - PSM to ASM transformation
   - ASM to RDBMS transformation
   - RDBMS to Liquibase transformation
   - PSM to Measure transformation
   - ASM to Keycloak transformation

3. **Migration Reference** (`docs/migration/`)
   - ETL to Zeta comparison (tatami-base specific examples)
   - Migration patterns used in this project

### Out of Scope (Belongs in Zeta Project)

- Generic Zeta framework documentation
- Zeta annotation reference
- TransformationRegistry internals
- Rule execution patterns (generic)

## Impact

- **Modules affected**: Documentation only (docs/)
- **Breaking changes**: None

## Deliverables

1. `docs/migration/README.md` - Migration documentation index
2. `docs/migration/dual-engine-testing.md` - Testing framework documentation
3. `docs/migration/etl-zeta-comparison.md` - Tatami-base specific comparison
4. `docs/migration/etl-to-zeta-migration.md` - Migration reference
5. `docs/transformations/*.md` - Module documentation
