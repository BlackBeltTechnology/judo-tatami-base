# Fix Zeta Transformation Bugs

## Summary

Fix bugs in Zeta transformations discovered during external model benchmarking with the RackInspect model (22,134 elements). Update Zeta dependency, fix transformation bugs, enable parallel execution, and investigate test flakiness.

## Motivation

The Zeta transformations provide significant performance improvements over ETL (5-34x faster), but two transformations produce output that differs from ETL:

| Transformation  | Speedup | Status | Issue |
|-----------------|---------|--------|-------|
| PSM2ASM         | 5.67x   | FAIL   | Missing model prefix in owner path |
| PSM2Measure     | 1.85x   | FAIL   | Wrong measure ordering |
| ASM2RDBMS       | 8.79x   | PASS   | Equivalent (not parallel) |
| RDBMS2Liquibase | 31.42x  | PASS   | Equivalent (flaky?) |
| ASM2Keycloak    | 14.29x  | PASS   | Equivalent |

## Prerequisites

### Update Zeta Framework Dependency

Before fixing bugs, update the `judo-zeta` dependency to the latest snapshot version to ensure we're working with the most recent framework fixes.

## Bugs to Fix

### Bug 1: PSM2ASM - Missing Model Prefix in Owner Path

**Location**: `Psm2AsmHelper.getClassifierFQName()` and related FQName methods

**Symptom**: The `owner` annotation value is missing the root model name:
- ETL: `owner=rackinspect.services.partner_service.Partner#warehouses`
- Zeta: `owner=services.partner_service.Partner#warehouses`

**Root Cause**: The `getClassifierFQName()` method in `Psm2AsmHelper.java` traverses the package hierarchy using `getESuperPackage()`, but the root EPackage (which represents the model) is being excluded or handled differently than in ETL.

**Affected Operations**:
- `_listWarehousesForRackinspect_services_partner_service_Partner`
- `_listErrorQualificationForRackinspect_entities_ErrorQualificationInstance`
- Multiple similar operations with `behaviour` annotations

### Bug 2: PSM2Measure - Wrong Measure Ordering

**Location**: `Psm2MeasureZetaTransformation` or `MeasureRules`

**Symptom**: The first measure in the output is wrong:
- ETL: `AmountOfSubstance` with unit `mol`
- Zeta: `Time` with units `day, week, second, minute, hour, millisecond`

**Root Cause**: The measures are being iterated in a different order than ETL, causing the comparison to fail. This could be due to:
1. Non-deterministic iteration order (HashMap vs LinkedHashMap)
2. Different source element collection order
3. Missing stable sort before transformation

### Bug 3: RDBMS2Liquibase - Flaky Test

**Location**: `Rdbms2LiquibaseExternalModelTest`

**Symptom**: Test initially failed with `231 vs 232 changeSets` but passed on re-run.

**Investigation Needed**:
- Determine if this is a Zeta transformation issue or test infrastructure issue
- Check for non-deterministic behavior in changeSet generation
- Ensure stable, reproducible test results

## Additional Work

### Enable Parallel Execution for ASM2RDBMS

Current parallel settings:
- PSM2ASM: `parallel(true)` - already parallel
- ASM2RDBMS: `parallel(false)` - **needs to be enabled**
- RDBMS2Liquibase: `parallel(true)` - already parallel
- ASM2Keycloak: `parallel(false)` - small model, keep sequential
- PSM2Measure: `parallel(false)` - small model, keep sequential

Enable parallel execution for **ASM2RDBMS** to improve performance for large models.

## Scope

- Update Zeta framework dependency to latest snapshot
- Fix PSM2ASM `getClassifierFQName()` to include root model package
- Fix PSM2Measure ordering to match ETL output
- Investigate and fix RDBMS2Liquibase flakiness
- Enable parallel execution for ASM2RDBMS
- Verify fixes with external model tests

## Out of Scope

- ASM2Keycloak (already passing, small model - keep sequential)
- PSM2Measure parallel mode (small model - keep sequential)
- New features beyond bug fixes

## Success Criteria

1. All external model tests pass with STRICT comparison mode
2. PSM2ASM external test passes with XMI ID comparison enabled
3. PSM2Measure external test passes
4. RDBMS2Liquibase test is stable (no flakiness)
5. ASM2RDBMS runs in parallel mode
6. No regression in passing transformations
