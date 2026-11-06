# Tasks

## Phase 1: Create TableRules

- [x] Create `rules/TableRules.java`
  - Add `@TransformationContext` annotation
  - Implement `tableToCreateTable()` with `@TransformRule`, `@Lazy`, `@Greedy`
  - Implement `tableToCreateTableChangeSet()` with `@TransformRule`, `@Greedy`
  - Implement `tableToCreateForeignKeysChangeSet()` with `@TransformRule`, `@Greedy`, `@Guard`
  - Implement `tableToAddNotNullChangeSet()` with `@TransformRule`, `@Greedy`, `@Guard`
  - Add guard methods `hasForeignKeys()`, `hasMandatoryFields()`
  - Use `ctx.equivalent(s, TABLE_TO_CREATE_TABLE)` for lazy lookup

## Phase 2: Create FieldRules

- [x] Create `rules/FieldRules.java`
  - Implement `fieldToColumn()` with `@TransformRule`, `@Abstract`, `@Greedy`
  - Implement `identifierFieldToColumn()` with `@TransformRule`, `@Extends`, `@Greedy`
  - Implement `identifierFieldToPkConstraint()` with `@TransformRule`
  - Implement `valueFieldToColumn()` with `@TransformRule`, `@Extends`
  - Implement `foreignKeyFieldToAddFkConstraint()` with `@TransformRule`, `@Abstract`
  - Implement `foreignKeyFieldToCreateTableAddFkConstraint()` with `@TransformRule`, `@Extends`
  - Implement `fieldToAddNotNullConstraint()` with `@TransformRule`, `@Abstract`, `@Greedy`
  - Implement `fieldToCreateTableAddNotNullConstraint()` with `@TransformRule`, `@Extends`, `@Greedy`, `@Guard`
  - Implement `indexToCreateIndex()` with `@TransformRule` (uses `getOrCreateChangeSet()` helper)
  - Implement `addUniqueConstraints()` with `@TransformRule` (uses `getOrCreateChangeSet()` helper)
  - All rules use `ctx.equivalent()` for named lookups matching ETL
  - Use `ctx.executeParentRule()` for `@Extends` inheritance

## Phase 3: Refactor Orchestrator

- [x] Simplify `Rdbms2LiquibaseZetaTransformation.java`
  - Remove inline procedural transformation methods (`transformTables()`, `transformFields()`, etc.)
  - Remove inline annotated rules (duplicates of rule class methods)
  - Remove manual `addTrace()`/`getEquivalent()` methods
  - Add `createRegistry()` method registering rule classes
  - Add `createContext()` method with configuration attributes
  - Keep `getOrCreateChangeSet()` helper method for index/unique constraint rules
  - Store helper in context attribute so rule classes can access it
  - Use `TransformationExecutor` for execution
  - Add `postProcess()` for adding root databaseChangeLog to resource

## Phase 4: Verification

- [x] Run existing tests to verify transformation works
- [x] Run comparison tests (ETL vs Zeta) if available
- [x] Verify all rule names match ETL exactly
- [x] Run full module test suite

## Dependencies

- Phase 2 can start after Phase 1 table rules are done (fields use table equivalents)
- Phase 3 depends on Phase 1 and Phase 2
- Phase 4 depends on Phase 3
