# Proposal: Consolidate Test Utilities into judo-tatami-test-utils

## Summary

Move duplicated test utility classes (ModelComparator and related utilities) from individual transformation modules into the shared `judo-tatami-test-utils` module. This eliminates code duplication and prepares the utilities for potential extraction into a separate project.

## Motivation

Currently, the `ModelComparator` utility class is duplicated across 5 transformation modules:

| Module | Location | Lines |
|--------|----------|-------|
| judo-tatami-psm2asm | `src/test/java/.../ModelComparator.java` | 975 |
| judo-tatami-psm2measure | `src/test/java/.../util/ModelComparator.java` | 883 |
| judo-tatami-asm2rdbms | `src/test/java/.../util/ModelComparator.java` | 799 |
| judo-tatami-rdbms2liquibase | `src/test/java/.../util/ModelComparator.java` | 799 |
| judo-tatami-asm2keycloak | `src/test/java/.../util/ModelComparator.java` | 799 |

**Total: ~4,255 lines of duplicated code**

Problems with current state:
1. **Maintenance burden**: Bug fixes or improvements must be applied to 5 copies
2. **Drift**: The psm2asm version has more features (975 lines vs 799 lines)
3. **Inconsistency**: Different package locations (`util/` vs root)
4. **Future extraction**: Having utilities in one place simplifies extraction to separate project

## Approach

1. **Consolidate ModelComparator** into `judo-tatami-test-utils`
   - **Merge strategy**: Combine psm2asm version (complete javadoc) with psm2measure version (order-independent Resource comparison)
   - Create unified package: `hu.blackbelt.judo.tatami.test.util`
   - Ensure backward compatibility with existing tests

2. **Update dependencies** in transformation modules
   - Add/update `judo-tatami-test-utils` dependency (already present in most)
   - Update imports in test classes

3. **Remove duplicated classes** from transformation modules
   - Delete local ModelComparator copies
   - Verify tests still pass

## Design Decisions

1. **Package location**: `hu.blackbelt.judo.tatami.test.util.ModelComparator`
   - Follows existing pattern in test-utils (`hu.blackbelt.judo.tatami.test`)
   - Clear separation from model generators

2. **Merge best of both versions**:
   - From psm2asm: Full javadoc documentation, all comparison modes, system property configuration, report file generation
   - From psm2measure: Order-independent Resource content comparison (matches elements by identifier instead of position)

3. **No breaking changes**: All existing tests should work with updated imports

## Scope

### In Scope
- Move ModelComparator to judo-tatami-test-utils
- Update imports in all transformation test modules
- Remove duplicate ModelComparator files
- Verify all tests pass

### Out of Scope
- Adding new features to ModelComparator
- Extracting test-utils to separate project (future work)
- Changing ModelComparator API

## Impact

- **Modules affected**: All transformation test modules (psm2asm, psm2measure, asm2rdbms, rdbms2liquibase, asm2keycloak)
- **Breaking changes**: None (import changes only)
- **Dependencies**: No new external dependencies

## Verification

1. Build all modules: `mvn compile -pl judo-tatami-test-utils,...`
2. Run all tests: `mvn test`
3. Run performance tests: `mvn test -Pperformance`
4. Verify no duplicate ModelComparator files remain
