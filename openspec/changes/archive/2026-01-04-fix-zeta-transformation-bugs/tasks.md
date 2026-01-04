# Tasks

## Prerequisites

- [x] **0.1** Update `judo-zeta` dependency to latest snapshot version in parent pom.xml
  - Updated to version 1.0.0.20260104_150012_6d24b690
- [x] **0.2** Verify build succeeds with updated dependency
- [x] **0.3** Run all existing tests to establish baseline

## Bug 1: PSM2ASM - Missing Model Prefix

**Resolution**: The issue was in the Zeta framework's cache optimization affecting cross-element dependencies in parallel execution. Fixed in Zeta version 150012.

- [x] **1.1** Analyze ETL `asmUtils.getClassifierFQName()` implementation
- [x] **1.2** Root cause identified: Zeta framework parallel execution issue
- [x] **1.3** Fixed by Zeta version 150012 with improved cache handling
- [x] **1.4** Run `Psm2AsmExternalModelTest` with STRICT comparison - PASSED
- [x] **1.5** Added ETL compatibility mode for additional stability

## Bug 2: PSM2Measure - Wrong Ordering

**Resolution**: Fixed in separate change `fix-parallel-transformation-issues`. Root cause was HashSet in ModelComparator causing non-deterministic iteration order.

- [x] **2.1** Analyze ETL measure iteration order
- [x] **2.2** Root cause: HashSet in ModelComparator.java (lines 551, 574)
- [x] **2.3** Fixed by replacing HashSet with LinkedHashSet
- [x] **2.4** Run `Psm2MeasureExternalModelTest` - PASSED
- [x] **2.5** Run `Psm2MeasureTest` - PASSED

## Bug 3: RDBMS2Liquibase - Flaky Test

**Resolution**: Fixed in separate change `fix-parallel-transformation-issues`. Disabled parallel execution due to race conditions.

- [x] **3.1** Ran test multiple times - confirmed flakiness (231, 232, 233 changeSets)
- [x] **3.2** Root cause: Parallel execution causes race conditions in changeSet generation
- [x] **3.3** Fixed by disabling parallel execution
- [x] **3.4** Test now stable with consistent 231 changeSets

## Enable Parallel Execution

- [x] **4.1** Evaluated ASM2RDBMS for parallel execution
  - **Finding**: Cannot enable parallel due to EMF thread safety issues in postProcess
- [x] **4.2** PSM2ASM parallel execution verified working with Zeta 150012
- [x] **4.3** All tests pass

## Final Verification

- [x] **5.1** Run all external model tests with STRICT comparison:
  - `Psm2AsmExternalModelTest` - PASS (3.57x faster, EQUIVALENT)
  - `Asm2RdbmsExternalModelTest` - PASS
  - `Psm2MeasureExternalModelTest` - PASS
  - `Rdbms2LiquibaseExternalModelTest` - PASS
  - `Asm2KeycloakExternalModelTest` - PASS
- [x] **5.2** Run all dual transformation tests - PASSED
- [x] **5.3** Verify all 5 transformations produce equivalent output - CONFIRMED
- [x] **5.4** Run full test suite - No regressions

## Summary

All bugs fixed through combination of:
1. Zeta framework version 150012 (fixed parallel execution race conditions)
2. ETL compatibility mode enabled for PSM2ASM
3. ModelComparator HashSet → LinkedHashSet fix
4. RDBMS2Liquibase parallel execution disabled
