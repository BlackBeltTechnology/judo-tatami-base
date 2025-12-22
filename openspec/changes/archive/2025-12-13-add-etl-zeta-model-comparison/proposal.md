# Add ETL-Zeta Model Comparison for Transformation Tests

## Summary

Enhance the dual transformation test infrastructure to automatically compare ETL and Zeta transformation outputs, verifying that both engines produce semantically equivalent models. The comparison should be order-independent for collections and recursively validate all elements.

## Motivation

Currently, transformation tests run ETL and Zeta transformations independently, verifying that each produces a valid model. However, there is no automated verification that both transformation engines produce **equivalent** output. This gap means:

1. Subtle differences between ETL and Zeta outputs may go undetected
2. Regression detection relies on manual inspection
3. No confidence that Zeta transformations are complete implementations of ETL rules

Adding automatic model comparison will:
- Ensure functional equivalence between ETL and Zeta transformations
- Catch regressions early in the development cycle
- Provide clear diagnostics when differences are detected
- Build confidence in the Zeta transformation migration

## Approach

### 1. Enhanced ModelComparator

Extend the existing `ModelComparator` class with:
- **Order-independent collection comparison**: Compare collections as sets rather than ordered lists
- **Recursive element validation**: Traverse entire model graph checking all elements
- **Bidirectional completeness check**: Verify no missing elements in either model
- **Detailed difference reporting**: Clear path-based reporting of discrepancies

### 2. Dual Test Enhancement

Modify parameterized tests to:
- Store both ETL and Zeta transformation results
- Run comparison after both transformations complete
- Provide clear failure messages identifying specific differences

### 3. Comparison Modes

Support different comparison strictness:
- **Strict**: All attributes and references must match exactly
- **Structural**: Element structure must match, annotation differences tolerated
- **Lenient**: Major structural elements must match, minor differences allowed

## Impact

### Modules Affected
- `judo-tatami-psm2asm` - Primary implementation and tests
- `judo-tatami-psm2measure` - Test updates
- `judo-tatami-asm2rdbms` - Test updates
- `judo-tatami-rdbms2liquibase` - Test updates
- `judo-tatami-asm2keycloak` - Test updates

### Breaking Changes
None - this is an additive enhancement to test infrastructure.

### Dependencies
- Existing `ModelComparator` class
- Existing `AbstractDualTransformationTest` base class
- EMF Ecore model traversal utilities

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Performance overhead | Medium | Low | Run comparison only in CI, optional locally |
| False positives from ordering | Low | Medium | Robust order-independent comparison |
| Large diff output | Medium | Low | Summarize with option for full details |

## Open Questions

1. Should comparison be enabled by default or opt-in via system property?
2. What level of difference detail should be shown in test output?
3. Should we support saving diff reports to files for analysis?
