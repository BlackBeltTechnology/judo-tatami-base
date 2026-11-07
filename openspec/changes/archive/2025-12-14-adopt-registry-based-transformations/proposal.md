# Adopt Registry-Based Transformations

## Summary

Update all Zeta transformations to use the `TransformationRegistry` and `TransformationExecutor` pattern from the Zeta framework, following the implementation established in `Psm2AsmZetaTransformation`. This provides a consistent, declarative rule-based transformation approach across all modules.

## Motivation

Currently, only `Psm2AsmZetaTransformation` uses the Zeta framework's `TransformationRegistry` and `TransformationExecutor`. Other transformations use manual orchestration with explicit phase ordering:

| Module | Current Pattern |
|--------|----------------|
| `judo-tatami-psm2asm` | TransformationRegistry + TransformationExecutor |
| `judo-tatami-psm2measure` | Manual orchestration |
| `judo-tatami-asm2rdbms` | Manual orchestration |
| `judo-tatami-asm2keycloak` | Manual orchestration with delegated rules |
| `judo-tatami-rdbms2liquibase` | Manual orchestration (both full and incremental) |

Standardizing on the registry pattern provides:
- **Consistency**: Same transformation approach across all modules
- **Maintainability**: Rules are self-documenting via `@TransformRule` annotations
- **Extensibility**: Easy to add/modify rules without changing execution logic
- **Testability**: Individual rules can be tested in isolation

## Design Decisions

1. **Parallel execution**: All transformations MUST use `parallel(true)` to enable parallel rule execution for better performance.

2. **Rule ordering**: Rule execution order is determined by registration order in `createRegistry()` method, not by `@TransformRule(order=N)` attribute.

3. **Existing rule classes**: Rule classes in `asm2keycloak` (`RealmRules.java`, `ClientRules.java`) must be refactored to match the `@TransformRule` pattern exactly as used in psm2asm.

4. **Incremental transformation split**: `Rdbms2LiquibaseIncrementalZetaTransformation` will be split into multiple smaller transformation classes, following the ETL module structure.

5. **Performance thresholds**: Keep existing performance test thresholds when moving to 1 iteration.

## Approach

### Phase 1: Update Psm2MeasureZetaTransformation

1. Create rule classes in `zeta/rules/` package:
   - `MeasureRules.java` - measure transformation rules
   - `UnitRules.java` - unit transformation rules
2. Refactor `Psm2MeasureZetaTransformation` to use `TransformationRegistry` with `parallel(true)`
3. Update performance test to use 1 iteration (no warmup needed)

### Phase 2: Update Asm2RdbmsZetaTransformation

1. Create rule classes in `zeta/rules/` package:
   - `PackageRules.java` - package to model/configuration
   - `TableRules.java` - class to table transformation
   - `FieldRules.java` - attribute to field transformation
   - `ReferenceRules.java` - reference to FK/junction table
2. Refactor `Asm2RdbmsZetaTransformation` to use `TransformationRegistry` with `parallel(true)`
3. Update performance test to use 1 iteration

### Phase 3: Update Asm2KeycloakZetaTransformation

1. Refactor existing rule classes to match `@TransformRule` pattern:
   - `RealmRules.java` - refactor to use proper annotations
   - `ClientRules.java` - refactor to use proper annotations
2. Refactor `Asm2KeycloakZetaTransformation` to use `TransformationRegistry` with `parallel(true)`
3. Add performance test with 1 iteration

### Phase 4: Update Rdbms2LiquibaseZetaTransformation

1. Create rule classes in `zeta/rules/` package:
   - `TableChangeSetRules.java` - table to changeset
   - `FieldRules.java` - field to column
   - `ConstraintRules.java` - index, unique, FK constraints
2. Refactor `Rdbms2LiquibaseZetaTransformation` to use `TransformationRegistry` with `parallel(true)`
3. Update performance test to use 1 iteration

### Phase 5: Split and Update Rdbms2LiquibaseIncrementalZetaTransformation

Split into multiple transformation classes following ETL structure:

1. **DbCheckupZetaTransformation** - DB checkup preconditions
   - `CheckupRules.java` - table/field/constraint existence checks
   
2. **DbBackupZetaTransformation** - Backup operations
   - `BackupRules.java` - backup deleted/modified tables
   
3. **BeforeIncrementalZetaTransformation** - Before incremental changes
   - `DropConstraintRules.java` - drop indexes, unique, FK, not null
   
4. **IncrementalZetaTransformation** - Core incremental changes
   - `IncrementalTableRules.java` - create/drop/rename tables
   - `IncrementalFieldRules.java` - add/drop/rename/modify columns
   
5. **AfterIncrementalZetaTransformation** - After incremental changes
   - `AddConstraintRules.java` - add indexes, unique, FK, not null
   
6. **DataUpdateZetaTransformation** - Data update SQL files
   - `DataUpdateRules.java` - SQL file generation for data migration

7. **DbDropBackupZetaTransformation** - Drop backup tables
   - `DropBackupRules.java` - cleanup backup tables

Each transformation uses `TransformationRegistry` with `parallel(true)`.

## Performance Testing

All performance tests will use:
- **1 iteration only** - no warmup phase needed
- Tests have shown minimal variance between iterations
- Reduces test execution time significantly
- **Keep existing thresholds** - do not adjust time limits

```java
@RepeatedTest(1)
void testPerformance() {
    // Single iteration performance test
}
```

## Impact

- **Modules affected**: 
  - `judo-tatami-psm2measure`
  - `judo-tatami-asm2rdbms`
  - `judo-tatami-asm2keycloak`
  - `judo-tatami-rdbms2liquibase`
- **Breaking changes**: None - public API remains the same
- **Dependencies**: `judo-zeta-core` (already a dependency)

## Risks

1. **Rule ordering complexity**: Some transformations have complex phase dependencies
   - Mitigation: Careful registration order in `createRegistry()` method

2. **Large refactoring effort**: Significant changes to each module
   - Mitigation: Implement one module at a time, verify tests pass before moving on

3. **Performance regression**: Registry pattern adds overhead
   - Mitigation: Single iteration performance tests will catch any significant regressions

4. **Parallel execution issues**: Some rules may have hidden dependencies
   - Mitigation: Thorough testing, can fall back to `parallel(false)` if issues found
