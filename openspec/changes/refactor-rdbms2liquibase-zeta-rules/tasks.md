# Tasks

## Phase 1: Create TableRules

- [ ] Create `rules/TableRules.java`
  - Add `@TransformationContext` annotation
  - Implement `tableToCreateTable()` with `@TransformRule`, `@Lazy`
  - Implement `tableToCreateTableChangeSet()` with `@TransformRule`
  - Implement `tableToCreateForeignKeysChangeSet()` with `@TransformRule`, `@Guard`
  - Implement `tableToAddNotNullChangeSet()` with `@TransformRule`, `@Guard`
  - Add guard methods `hasForeignKeys()`, `hasMandatoryFields()`
  - Use `ctx.equivalent(s, TABLE_TO_CREATE_TABLE)` for lazy lookup

## Phase 2: Create FieldRules

- [ ] Create `rules/FieldRules.java`
  - Implement `fieldToColumn()` with `@TransformRule`, `@Abstract`
  - Implement `identifierFieldToColumn()` with `@TransformRule`, `@Extends`
  - Implement `identifierFieldToPkConstraint()` with `@TransformRule`
  - Implement `valueFieldToColumn()` with `@TransformRule`, `@Extends`
  - Implement `foreignKeyFieldToAddFkConstraint()` with `@TransformRule`, `@Abstract`
  - Implement `foreignKeyFieldToCreateTableAddFkConstraint()` with `@TransformRule`, `@Extends`
  - Implement `fieldToAddNotNullConstraint()` with `@TransformRule`, `@Abstract`
  - Implement `fieldToCreateTableAddNotNullConstraint()` with `@TransformRule`, `@Extends`, `@Guard`
  - Implement `indexToCreateIndex()` with `@TransformRule`
  - Implement `addUniqueConstraints()` with `@TransformRule`
  - All rules use `ctx.equivalent()` for named lookups matching ETL
  - Use `ctx.executeParentRule()` for `@Extends` inheritance

## Phase 3: Refactor Orchestrator

- [ ] Simplify `Rdbms2LiquibaseZetaTransformation.java`
  - Remove inline procedural transformation methods (`transformTables()`, `transformFields()`, etc.)
  - Remove inline annotated rules (duplicates of rule class methods)
  - Remove manual `addTrace()`/`getEquivalent()` methods
  - Add `createRegistry()` method registering rule classes
  - Add `createContext()` method with configuration attributes
  - Keep or extract `getOrCreateChangeSet()` for index/unique constraint rules
  - Use `TransformationExecutor` for execution
  - Add `postProcess()` for adding root databaseChangeLog to resource

## Phase 4: Verification

- [ ] Run existing tests to verify transformation works
- [ ] Run comparison tests (ETL vs Zeta) if available
- [ ] Verify all rule names match ETL exactly
- [ ] Run full module test suite

## Dependencies

- Phase 2 can start after Phase 1 table rules are done (fields use table equivalents)
- Phase 3 depends on Phase 1 and Phase 2
- Phase 4 depends on Phase 3
