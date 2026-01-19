# Tasks

## Phase 1: Add Helper Methods to Asm2RdbmsHelper

- [x] Add `toUpperSnakeCase(String)` method - convert CamelCase to UPPER_SNAKE_CASE
- [x] Add `sqlLongName(ENamedElement, AsmUtils)` method - get SQL long name with `shortName` annotation support
- [x] Add `sqlName(ENamedElement, int, AsmUtils)` method - get abbreviated SQL name with `shortName` annotation support
- [x] Add `packageSqlName(EPackage, AsmUtils)` method - get package SQL name
- [x] Add `classSqlName(EClass, boolean createSimpleName, AsmUtils)` method - get class SQL name (package + class, or just class when createSimpleName=true)
- [x] Add `getResourceId(EObject)` method - get XMI resource ID for UUID generation
- [x] Store `createSimpleName` configuration in transformation context

## Phase 2: Update SQL Name Generation

- [x] Update `tableSqlName()` to use `classSqlName()` pattern
- [x] Update `fieldSqlName()` to use `sqlLongName()` with UPPER_SNAKE_CASE
- [x] Update `referenceIdentifierSqlName()` to use `sqlLongName()` pattern
- [x] Update `referenceFkSqlName()` to use class+reference abbreviation (not MD5)
- [x] Update `referenceInvFkSqlName()` to use class+reference abbreviation
- [x] Update `referenceUniFkSqlName()` to use class+reference abbreviation
- [x] Update `referenceManyToManyTableSqlName()` to handle bidirectional refs correctly

## Phase 3: Update UUID Generation in Rules

### ClassRules.java
- [x] Update `EClassToRdbmsTable` UUID generation to use resource ID
- [x] Update `EClassToTableIdField` UUID generation
- [x] Update `EClassToTableTypeField` UUID generation
- [x] Update `EClassToTableVersionField` UUID generation
- [x] Update `EClassToTableCreateUsernameField` UUID generation
- [x] Update `EClassToTableCreateUserIdField` UUID generation
- [x] Update `EClassToTableCreateTimestampField` UUID generation
- [x] Update `EClassToTableUpdateUsernameField` UUID generation
- [x] Update `EClassToTableUpdateUserIdField` UUID generation
- [x] Update `EClassToTableUpdateTimestampField` UUID generation

### AttributeRules.java
- [x] Update `EAttributeToRdbmsValueField` UUID generation
- [x] Update `EAttributeToIndex` UUID generation
- [x] Fix field size handling from type mapping annotations

### ReferenceRules.java
- [x] Update `EReferenceToRdbmsTableForeignKey` UUID generation
- [x] Update `EReferenceToRdbmsTableInverseForeignKey` UUID generation
- [x] Update `EReferenceToRdbmsJunctionTable` UUID generation
- [x] Update `EReferenceToRdbmsJunctionTablePrimaryKey` UUID generation
- [x] Update `EReferenceToRdbmsJunctionTableForeignKeyBidirectional` UUID generation
- [x] Update `EReferenceToRdbmsJunctionTableForeignKeyUnidirectional` UUID generation

## Phase 4: Fix Field Size Defaults

- [x] Review type mapping annotation handling in `fillType()`
- [x] Ensure size=255 is properly set for String fields
- [x] Ensure annotation-based sizes are properly extracted

## Phase 5: Verification

- [x] Run `mvn compile -pl judo-tatami-asm2rdbms -q` to verify compilation
- [x] Run existing Asm2Rdbms unit tests (all 8 tests pass)
- [x] Run `Asm2RdbmsExternalModelTest` with structural comparison enabled

### Final Status (Complete)
- **Before**: 2,344 differences
- **Final**: 0 differences (100% parity achieved)

### Key Fixes Applied
1. **UUID generation**: Changed from `getResourceId(s)` to `ctx.getElementId(s)` which properly retrieves XMI IDs
   - Now matches ETL: `(asm/(psm/(esm/_xxx)/Attribute)/Attribute)/RdbmsField`

2. **UUID suffix in extending rules**: Removed UUID override in `eAttributeToTableValueField()`
   - ETL's `@extends` pattern keeps the abstract rule's UUID suffix (`/RdbmsField` not `/TableValueField`)

3. **Field size defaults**: Fixed `fillType()` to default to 255 for VARCHAR fields when:
   - Annotation-based size lookup returns nothing (no annotation)
   - Attribute is null (system fields like TYPE, CREATE_USERNAME, UPDATE_USERNAME)

- [x] Verify 0 differences between ETL and ZETA output
- [x] Run full test suite to check for regressions (132 tests pass)
