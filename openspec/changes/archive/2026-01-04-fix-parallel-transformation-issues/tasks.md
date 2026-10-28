# Tasks

## Phase 1: PSM2Measure Ordering Fix

- [x] 1.1 Analyze PsmUtils.all() implementation to understand iteration source
  - **Finding**: Root cause was NOT in psmUtils.all() but in ModelComparator.java
  - HashSet at lines 551, 574 caused non-deterministic iteration order during comparison
- [x] 1.2 Replace HashSet with LinkedHashSet in ModelComparator
  - Changed lines 551 and 574 to use LinkedHashSet for deterministic iteration
- [x] 1.3 Add Resource-level comparison method for order-independent matching
  - Added compare(Resource, Resource) method for matching elements by identifier
  - Updated Psm2MeasureExternalModelTest to use Resource-level comparison
- [x] 1.4 Run external model test 3 consecutive times to verify consistency
  - All runs passed with EQUIVALENT status

## Phase 2: RDBMS2Liquibase Investigation

- [x] 2.1 Investigate changeSet count variance
  - **Finding**: Tests produced varying counts (231, 232, 233) across runs
  - Root cause: Parallel execution at line 127 causes race conditions
- [x] 2.2 Analyze thread safety mechanisms
  - ConcurrentHashMap at line 79 and synchronized addChangeSetThreadSafe() insufficient
- [x] 2.3 Disable parallel execution as fix
  - Changed line 127 from `.parallel(true)` to `.parallel(false)`
  - Added comment explaining race condition issue
- [x] 2.4 Verify external model test passes consistently
  - 3 consecutive runs all passed with 231 changeSets (EQUIVALENT)

## Phase 3: ASM2RDBMS Parallel Execution

- [x] 3.1 Verify current state
  - **Finding**: Parallel execution already disabled at line 173
  - Comment explains EMF thread safety issues in postProcess
- [x] 3.2 Run external model test to confirm
  - Test passes: 13.53x faster than ETL, EQUIVALENT status

## Phase 4: Validation

- [x] 4.1 Run all external model tests
- [x] 4.2 Verify all pass with EQUIVALENT status:
  - PSM2Measure: PASS (EQUIVALENT)
  - RDBMS2Liquibase: PASS (EQUIVALENT, 231 changeSets)
  - ASM2RDBMS: PASS (EQUIVALENT, 13.53x faster)

## Summary

All three issues resolved:
1. **PSM2Measure**: Fixed ModelComparator HashSet non-determinism
2. **RDBMS2Liquibase**: Disabled parallel execution to prevent race conditions
3. **ASM2RDBMS**: Already fixed (parallel disabled)
