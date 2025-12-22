# Tasks

## Phase 1: Remove V1 and Rename V2

- [x] Delete `Psm2AsmZetaTransformation.java` (the V1 implementation)
- [x] Rename `Psm2AsmZetaTransformationV2.java` to `Psm2AsmZetaTransformation.java`
- [x] Update class name inside the file from `Psm2AsmZetaTransformationV2` to `Psm2AsmZetaTransformation`
- [x] Remove "V2" from log messages in the renamed class
- [x] Update builder method name if it contains V2

## Phase 2: Update References in Main Code

- [x] Update `Psm2AsmHelper.java` - remove V2 references in comments
- [x] Update `TransferObjectRules.java` - update class references
- [x] Update `DataRules.java` - update class references

## Phase 3: Update Test Classes

- [x] Update all test classes - update imports and references to use `Psm2AsmZetaTransformation`

## Phase 4: Verify and Test

- [x] Run `mvn compile` to verify no compilation errors
- [x] Run `mvn test -pl judo-tatami-psm2asm` to verify tests pass
