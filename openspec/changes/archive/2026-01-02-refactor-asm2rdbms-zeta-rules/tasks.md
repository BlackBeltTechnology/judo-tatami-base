# Tasks

## Phase 1: Create Helper Class

- [x] Create `Asm2RdbmsHelper.java` with utility methods
  - Extract `getId()` method
  - Extract `tableSqlName()`, `fieldSqlName()` methods
  - Extract `referenceIdentifierSqlName()`, `referenceFkSqlName()` methods
  - Extract `fillType()` method
  - Extract `md5()` method
  - Add cache management like `Psm2AsmHelper`

## Phase 2: Create PackageRules

- [x] Create `rules/PackageRules.java`
  - Add `@TransformationContext` annotation
  - Implement `rootPackegeToModel()` with `@TransformRule`
  - Implement `rootPackegeToConfiguration()` with `@TransformRule`
  - Add guard method `isRootPackage()`
  - Use `ctx.equivalent(s, ROOT_PACKAGE_TO_MODEL)` for named lookup

## Phase 3: Create ClassRules

- [x] Create `rules/ClassRules.java`
  - Implement `eClassToRdbmsTable()` with `@TransformRule`, `@Primary`
  - Implement `eClassToTableIdField()` with `@TransformRule`
  - Implement `eClassToTableTypeField()` with `@TransformRule`
  - Implement `eClassToTableVersionField()` with `@TransformRule`
  - Implement `eClassToTableCreateUsernameField()` with `@TransformRule`
  - Implement `eClassToTableCreateUserIdField()` with `@TransformRule`
  - Implement `eClassToTableCreateTimestampField()` with `@TransformRule`
  - Implement `eClassToTableUpdateUsernameField()` with `@TransformRule`
  - Implement `eClassToTableUpdateUserIdField()` with `@TransformRule`
  - Implement `eClassToTableUpdateTimestampField()` with `@TransformRule`
  - Add guard method `isEntityType()`
  - All field rules use `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` to get table

## Phase 4: Create AttributeRules

- [x] Create `rules/AttributeRules.java`
  - Implement `eAttributeToRdbmsField()` with `@TransformRule`, `@Abstract`
  - Implement `eAttributeToTableValueField()` with `@TransformRule`, `@Extends`
  - Implement `eAttributeToIndex()` with `@TransformRule`
  - Add guard methods for entity type and derived checks
  - Use `ctx.executeParentRule()` for inheritance
  - Use `ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE)` for table lookup
  - Use `ctx.equivalent(s, EATTRIBUTE_TO_TABLE_VALUE_FIELD)` for index field reference

## Phase 5: Create ReferenceRules

- [x] Create `rules/ReferenceRules.java`
  - Implement `eReferenceToRdbmsTableForeignKey()` with `@TransformRule`
  - Implement `eReferenceToRdbmsTableInverseForeignKey()` with `@TransformRule`
  - Implement `eReferenceToRdbmsJunctionTable()` with `@TransformRule`, `@Lazy`
  - Implement `eReferenceToRdbmsJunctionTablePrimaryKey()` with `@TransformRule`
  - Implement `eReferenceToRdbmsJunctionTableForeignKeyBidirectional()` with `@TransformRule`
  - Implement `eReferenceToRdbmsJunctionTableForeignKeyUnidirectional()` with `@TransformRule`
  - Add guard methods for rule mapping conditions
  - Use `ctx.equivalent(s, EREFERENCE_TO_RDBMS_JUNCTION_TABLE)` to trigger lazy creation
  - Handle bidirectional junction table field1/field2 assignment

## Phase 6: Refactor Orchestrator

- [x] Simplify `Asm2RdbmsZetaTransformation.java`
  - Remove inline transformation methods
  - Add `createRegistry()` method registering rule classes
  - Add `createContext()` method with configuration attributes
  - Keep `postProcess()` for name mappings
  - Use `TransformationExecutor` for execution

## Phase 7: Verification

- [x] Run existing tests to verify transformation works
- [x] Run dual-engine comparison tests (ETL vs Zeta)
- [x] Verify all rule names match ETL exactly
- [x] Run full module test suite (132 tests passed)

## Dependencies

- Phase 2-5 depend on Phase 1 (helper class)
- Phase 3-5 can be done in parallel after Phase 2
- Phase 6 depends on Phase 2-5
- Phase 7 depends on Phase 6
