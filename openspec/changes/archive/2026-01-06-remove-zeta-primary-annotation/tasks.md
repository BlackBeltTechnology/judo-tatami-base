# Tasks: Remove @Primary Annotation from Zeta Transformations

## Phase 1: Update asm2rdbms ClassRules.java

- [x] 1.1 Remove `@Primary` annotation from `eClassToRdbmsTable()` method
       File: `judo-tatami-asm2rdbms/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/zeta/rules/ClassRules.java`
       Line: 144

- [x] 1.2 Update `ctx.equivalent()` calls to use 2-argument form with named rules:
       - Line 164: `ctx.equivalent(rootPackage, ROOT_PACKAGE_TO_MODEL)` - already named, add ETL comment
       - Line 173: `ctx.equivalent(superType, ECLASS_TO_RDBMS_TABLE)` - already named, add ETL diff comment
       - Line 217: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 253: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 282: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 311: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 340: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 369: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 398: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 427: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment
       - Line 456: `ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)` - add ETL diff comment

- [x] 1.3 Add ETL difference comments to rule documentation
       - Update class-level documentation (lines 39-67)
       - Update `eClassToRdbmsTable()` documentation (lines 128-142)
       - Update `eClassToTableIdField()` documentation (lines 191-200)

## Phase 2: Update psm2asm DataRules.java

- [x] 2.1 Line 131: Replace super type lookup
       Change: `EClass superClass = ctx.equivalent(superType, EClass.class);`
       To: `EClass superClass = ctx.equivalent(superType, CREATE_ENTITY_CLASS);`

- [x] 2.2 Line 163: Refactor type lookup for polymorphic types
       Change: `EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);`
       To: Use specific type rule based on actual type (following pattern from TypeRules.java lines 243-247)

## Phase 3: Update psm2asm ActorRules.java

- [x] 3.1 Lines 170, 196, 228: Replace type-based equivalents
       - `ctx.equivalent(s, EClass.class)` → `ctx.equivalent(s, CREATE_ACTOR_TYPE_CLASS)` or similar

## Phase 4: Update psm2asm DerivedRules.java

- [x] 4.1 Lines 166, 174, 206, 246, 276, 305, 319, 344, 378, 396, 426, 440
       Replace `ctx.equivalent()` calls with appropriate named rules

## Phase 5: Update psm2asm OperationRules.java

- [ ] 5.1 ~30 `ctx.equivalent()` calls
       Replace with appropriate named rules based on source and target types

## Phase 6: Update other psm2asm rules

- [x] 6.1 StaticRules.java (lines 118, 136, 146, 225, 246, 258, 270)
- [ ] 6.2 TransferObjectRules.java (~25 calls)
- [ ] 6.3 TypeRules.java (already uses named rules, verify comments)
- [ ] 6.4 NamespaceRules.java (line 163, already uses named rule)
- [ ] 6.5 Psm2AsmHelper.java (line 424)

## Phase 7: Update rdbms2liquibase rules

- [ ] 7.1 TableRules.java (line 182, already uses named rule, add comment)
- [ ] 7.2 FieldRules.java (lines 234, 265, 296, 333, 369, already uses named rules)

## Phase 8: Validation

- [ ] 8.1 Run asm2rdbms tests
       ```bash
       mvn test -pl judo-tatami-asm2rdbms -Dtest=Asm2RdbmsExternalModelTest
       ```

- [ ] 8.2 Run psm2asm tests
       ```bash
       mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest
       ```

- [ ] 8.3 Run rdbms2liquibase tests
       ```bash
       mvn test -pl judo-tatami-rdbms2liquibase -Dtest=Rdbms2LiquibaseExternalModelTest
       ```

- [ ] 8.4 Run dual transformation tests
       ```bash
       mvn test -pl judo-tatami-asm2rdbms -Dtest=Asm2RdbmsDualTransformationTest
       mvn test -pl judo-tatami-rdbms2liquibase -Dtest=Rdbms2LiquibaseDualTransformationTest
       ```

- [ ] 8.5 Full test suite
       ```bash
       mvn test
       ```

## Dependency Notes

- Phase 1 is the primary change (remove @Primary)
- Phase 2 requires understanding the polymorphic type rules
- Phases 3-6 are similar pattern replacements
- Phase 7 is mostly comments (rules already use named equivalents)
- Phase 8 must be done after all changes

## Key Clarifications

1. **@Primary is only in ClassRules.java** - the other mention in Asm2RdbmsZetaTransformation.java is just a comment

2. **For polymorphic types (EClassifier, EDataType)** - use specific rule names based on actual type, not a generic lookup

3. **Comments are minimal** - just note ETL uses `s.equivalent("RuleName")` pattern
