# Fix Zeta Transformation Issues Discovered in STRICT Mode Testing

## Summary

Fix multiple issues discovered during external model tests in STRICT mode that cause differences between ETL and Zeta transformation outputs.

## Motivation

Running external model tests on the RackInspect model with STRICT comparison mode revealed:

1. **PSM2ASM**: 50 differences (28 ordering, 1 missing, 21 metadata)
2. **PSM2Measure**: 9 differences (wrong measure/unit associations)
3. **RDBMS2Liquibase**: Runtime error (ArrayIndexOutOfBoundsException)

These issues must be resolved to ensure Zeta transformations produce identical results to ETL.

## Current Behavior

### PSM2ASM - 50 Differences

**A. Behavior Annotation Ordering (28 differences)**
- The `owner` detail in behavior annotations has values in different order
- Affects operations: `_listLastDocument`, `_listAssignedTo`, `_listHistory`, `_listDocuments`
- Root cause: Non-deterministic iteration order when collecting owners from inheritance hierarchy
- Impact: Cosmetic - values are correct, just ordered differently

**B. Missing Operation (1 difference)**
- Missing: `_listCountryForRackinspect_entities_CompanyAddress`
- Root cause: Needs investigation - likely missing rule or different guard evaluation
- Impact: Functional - operation is missing from generated model

**C. Extension Package Metadata (21 differences)**
- ETL produces: `http://blackbelt.hu/judo/rackinspect/rackinspect/_extension/entities`
- Zeta produces: `null/entities`
- Root cause: Extension packages not having nsURI set
- Impact: Metadata - may affect serialization/validation

### PSM2Measure - 9 Differences

**Critical Issue: Wrong measure/unit association**
- ETL produces: `AmountOfSubstance` measure with `mol` unit
- Zeta produces: `Time` measure with 6 duration units (day, week, second, minute, hour, millisecond)
- Root cause: `findEquivalentMeasure()` in UnitRules.java uses stream iteration which may return wrong measure
- Impact: Critical - output is semantically incorrect

### RDBMS2Liquibase - Runtime Error

```
ArrayIndexOutOfBoundsException: Index 19 out of bounds for length 19
at TableRules.lambda$tableToAddNotNullChangeSet$4(TableRules.java:270)
```
- Root cause: Concurrent modification of `changeLog.getChangeSet()` EList from multiple threads
- Impact: Transformation fails completely on RackInspect model

## Proposed Behavior

1. **PSM2ASM Behavior Annotations**: Sort owner values deterministically
2. **PSM2ASM Missing Operation**: Investigate and add missing rule or fix guard
3. **PSM2ASM Extension Package**: Set nsURI/nsPrefix on extension packages
4. **PSM2Measure**: Fix `findEquivalentMeasure()` to use direct containment relationship
5. **RDBMS2Liquibase**: Use thread-safe synchronization for EList operations

## Priority

| Priority | Issue | Impact |
|----------|-------|--------|
| **P1** | PSM2Measure wrong associations | Critical - semantic errors |
| **P1** | RDBMS2Liquibase thread-safety | Critical - transformation fails |
| **P2** | PSM2ASM missing operation | Functional - missing output |
| **P2** | PSM2ASM extension nsURI | Metadata - serialization |
| **P3** | PSM2ASM annotation ordering | Cosmetic - order difference |

## Acceptance Criteria

- [ ] All external model tests pass in STRICT mode for PSM2ASM
- [ ] All external model tests pass in STRICT mode for PSM2Measure
- [ ] All external model tests pass in STRICT mode for RDBMS2Liquibase
- [ ] Existing unit tests continue to pass
- [ ] Performance is not degraded

## References

- External model test output: `judo-tatami-*/target/surefire-reports/*.txt`
- Related specs: `zeta-transformations`, `psm2asm-transformation`
