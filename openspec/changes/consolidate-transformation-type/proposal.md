# Consolidate TransformationType to Use TransformationMode

## Summary

Replace module-specific `TransformationType` enums with the centralized `TransformationMode` enum from judo-tatami-core across all transformation modules.

## Motivation

Currently, tatami-base defines a `TransformationType` enum in each transformation module's test package for parameterized testing. This is ambiguous and redundant because:

1. **Duplication**: Five identical `TransformationType` enums exist across modules:
   - `judo-tatami-psm2asm/src/test/java/.../psm2asm/TransformationType.java`
   - `judo-tatami-psm2measure/src/test/java/.../psm2measure/TransformationType.java`
   - `judo-tatami-asm2rdbms/src/test/java/.../asm2rdbms/TransformationType.java`
   - `judo-tatami-asm2keycloak/src/test/java/.../asm2keycloak/TransformationType.java`
   - `judo-tatami-rdbms2liquibase/src/test/java/.../rdbms2liquibase/TransformationType.java`

2. **Naming Conflict**: judo-tatami-core already provides `TransformationMode` which serves the same purpose:
   - Used by all `*Work` classes to determine which transformation engine to use
   - Provides system property support via `TransformationMode.fromSystemProperty()`
   - Has helper methods `isZeta()` and `isEtl()`
   - Includes comprehensive documentation

3. **Inconsistency**: Production code uses `TransformationMode` while tests use `TransformationType`, creating conceptual confusion

4. **Maintenance Burden**: Changes to transformation mode handling must be synchronized across five duplicate enums

## Approach

1. **Remove Duplicate Enums**: Delete all five `TransformationType.java` files from test packages

2. **Update Test Imports**: Replace test imports:
   ```java
   // Before
   import hu.blackbelt.judo.tatami.psm2asm.TransformationType;
   @EnumSource(TransformationType.class)

   // After
   import hu.blackbelt.judo.tatami.core.TransformationMode;
   @EnumSource(TransformationMode.class)
   ```

3. **Update Test Method Signatures**: Change parameter types:
   ```java
   // Before
   void testTransformation(TransformationType transformationType)

   // After
   void testTransformation(TransformationMode transformationMode)
   ```

4. **Update Test Logic**: Replace comparisons:
   ```java
   // Before
   if (transformationType == TransformationType.ZETA)

   // After
   if (transformationMode.isZeta())
   // or
   if (transformationMode == TransformationMode.ZETA)
   ```

5. **Update Documentation**: Revise references in:
   - `openspec/AGENTS.md`
   - `CONTRIBUTING.md`
   - `docs/migration/dual-engine-testing.md`
   - `docs/migration/etl-to-zeta-migration.md`
   - `docs/transformations/README.md`
   - OpenSpec change archives

## Impact

### Modules Affected
- `judo-tatami-psm2asm` (13 test files)
- `judo-tatami-psm2measure` (1 test file + performance tests)
- `judo-tatami-asm2rdbms` (5 test files + performance tests)
- `judo-tatami-asm2keycloak` (1 test file + performance tests)
- `judo-tatami-rdbms2liquibase` (2 test files + performance tests)
- Performance test infrastructure in all modules
- Documentation files (5 files)
- OpenSpec changes (3 archived changes)

### Breaking Changes
- None - this is test-only refactoring
- All changes are internal to test code
- Production code already uses `TransformationMode`

### Dependencies
- Requires `judo-tatami-core` (already a dependency of all modules)
- No new dependencies needed

### Benefits
- Single source of truth for transformation mode
- Consistent naming between tests and production code
- Easier to maintain and evolve
- Better alignment with the actual runtime behavior

## Risks

### Low Risk: Test Compilation Failures
**Mitigation**: All changes are compile-time checked. Any missed references will cause immediate build failures.

### Low Risk: Incomplete Migration
**Mitigation**: Use grep to verify all references are updated before validation.

### No Risk: Behavior Changes
**Impact**: Pure refactoring - no logic changes, only type renaming.

## Implementation Strategy

1. **Phase 1**: Update all test files in one module (psm2asm) and verify tests pass
2. **Phase 2**: Apply same changes to remaining modules in parallel
3. **Phase 3**: Remove duplicate `TransformationType.java` files
4. **Phase 4**: Update documentation
5. **Phase 5**: Build and validate all modules

This approach ensures incremental validation and reduces risk of breaking the build.
