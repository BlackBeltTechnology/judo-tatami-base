# Verify Strict Model Validation with ID Equivalence

## Summary

Verify that existing external model tests work correctly with STRICT comparison mode and XMI ID equivalence verification enabled, benchmarking ETL vs Zeta performance on real-world models.

## Motivation

The `ModelComparator` already supports:
- STRICT comparison mode via `-Djudo.test.comparison.mode=STRICT`
- XMI ID comparison via `-Djudo.test.comparison.xmiIds=true`

Before migrating from ETL to Zeta, we need to verify these validation mechanisms work correctly on real-world external models and document the performance characteristics.

## Approach

1. **Run external model tests with strict validation** - Execute all `*ExternalModelTest` classes with STRICT mode and XMI ID comparison enabled
2. **Document any differences found** - If tests fail, analyze and fix the underlying transformation issues
3. **Capture performance benchmarks** - Document ETL vs Zeta performance from test output

### Test Command

```bash
mvn test -Dtest=*ExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true \
    -Pperformance
```

## Impact

- **Modules affected**: None (testing existing functionality)
- **Breaking changes**: None
- **Dependencies**: External model files must be configured in properties files

## Risks

1. **Tests may fail with strict validation** - ETL and Zeta may have differences not caught by STRUCTURAL mode
   - Mitigation: Fix any differences found in Zeta transformations
