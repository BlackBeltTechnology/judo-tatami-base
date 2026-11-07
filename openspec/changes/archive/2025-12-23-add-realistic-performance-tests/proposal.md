# Proposal: Add Realistic Performance Tests for All Transformations

## Summary

Replace RackInspect-based performance tests with synthetic RealisticPerformanceTests across all transformation modules. This removes the dependency on external RackInspect model files while maintaining equivalent test coverage with models that have the same characteristics.

## Motivation

Currently, performance tests use the RackInspect real-world model stored in `rackinspect/` directory. This approach has drawbacks:

1. **External dependency**: Tests depend on pre-generated model files that must be kept in sync
2. **Opaque model**: Developers cannot easily understand or modify the test model characteristics
3. **Single configuration**: Only one model size/complexity is tested
4. **Maintenance burden**: Model files must be regenerated when metamodels change

The existing `RealisticPerformanceTest` in `judo-tatami-psm2asm` demonstrates a better approach:
- Generates synthetic models programmatically
- Matches RackInspect characteristics (71 entities, ~837 transfer objects, etc.)
- Tests multiple sizes (small/medium/large)
- Self-contained with no external file dependencies

## Approach

1. **Create shared test utilities module** for model generation:
   - Shared generators for PSM, ASM, RDBMS models
   - Common test base classes and utilities
   - Future candidate for migration to tatami-core

2. **Update existing Psm2Asm RealisticPerformanceTest** to use shared utilities

3. **Create RealisticPerformanceTest** classes for each transformation module:
   - `judo-tatami-psm2measure` - generates PSM with measures/units directly
   - `judo-tatami-asm2rdbms` - generates ASM models directly
   - `judo-tatami-rdbms2liquibase` - generates RDBMS models directly
   - `judo-tatami-asm2keycloak` - generates ASM models with actors directly

4. Each test will:
   - Generate synthetic input models with RackInspect-like characteristics
   - Run both ETL and Zeta transformations
   - Compare outputs for equivalence
   - Report performance metrics

5. **Remove RackInspect files** after verification:
   - Delete `rackinspect/` directory
   - Remove RackInspectPerformanceTest classes from all modules

## Design Decisions

1. **Direct model generation** (not transformation chains): Each test generates its input model directly rather than running upstream transformations. This is faster and isolates test concerns.

2. **Shared utilities module**: Model generators will be shared across modules to reduce duplication. May be migrated to tatami-core in the future.

3. **RackInspect removal in same change**: After RealisticPerformanceTests are verified, RackInspect tests and model files will be removed as part of this change.

## RackInspect Model Characteristics (to replicate)

Based on analysis of the RackInspect PSM model:
- **71 Entities** (all with default representation)
- **837 Transfer Objects** (168 mapped, 668 unmapped)
- **~12 Transfer Objects per Entity**
- **315 Attributes** (~4.4 per entity)
- **91 AssociationEnds** (~1.3 per entity)
- **45 Containments**
- **357 DataProperties** (~5 per entity)
- **194 NavigationProperties** (~2.7 per entity)
- **2397 TO Attributes** (~2.9 per TO)
- **2138 TO Relations** (~2.6 per TO)

## Scope

### In Scope
- Create shared test model generator utilities
- Update Psm2Asm RealisticPerformanceTest to use shared utilities
- Create RealisticPerformanceTest for psm2measure, asm2rdbms, rdbms2liquibase, asm2keycloak
- Support multiple test sizes (small=20, medium=70, large=100 entities)
- Maintain ETL vs Zeta comparison and equivalence checking
- Remove RackInspect tests and model files after verification

### Out of Scope
- Performance optimization work
- Migration of shared utilities to tatami-core (future work)

## Impact

- **Test coverage**: Equivalent or better (multiple sizes vs single model)
- **Dependencies**: Removes need for external model files
- **Maintainability**: Self-documenting, programmatic model generation
- **CI/CD**: Faster setup, no file synchronization needed

## Verification

1. Run all RealisticPerformanceTests with `-Pperformance`
2. Verify ETL and Zeta produce equivalent outputs
3. Confirm performance characteristics match RackInspect tests
4. All tests should pass with 0 differences between ETL and Zeta
5. Verify build succeeds after RackInspect removal
