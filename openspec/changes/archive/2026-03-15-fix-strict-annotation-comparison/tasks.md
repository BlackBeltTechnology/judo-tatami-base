## 1. ModelComparator — enum changes

- [x] 1.1 Remove `ComparisonMode.LENIENT` from `ModelComparator.java` and delete all LENIENT-specific branches
- [x] 1.2 Rename `ComparisonMode.STRUCTURAL` → `ComparisonMode.SKELETON` in `ModelComparator.java`
- [x] 1.3 Change `DEFAULT_MODE` from `STRUCTURAL` to `STRICT` in `ModelComparator.java`
- [x] 1.4 Update Javadoc on `getConfiguredMode()` to reflect new default and rename

## 2. ModelComparator — fix STRICT annotation comparison

- [x] 2.1 Fix `getAnnotationSignatureFromEObject()` to sort `details` entries by key before building the signature string, making signatures order-insensitive

## 3. Update callers — rename STRUCTURAL → SKELETON

- [x] 3.1 Search entire codebase for `ComparisonMode.STRUCTURAL` and replace with `ComparisonMode.SKELETON`
- [x] 3.2 Update `AGENTS.md` / `CLAUDE.md` documentation that references mode names

## 4. Re-enable STRICT test

- [x] 4.1 Remove `@Disabled` from `testEtlAndZetaProduceEquivalentModelsStrict` in `Psm2AsmDualTransformationTest`

## 5. Verify

- [x] 5.1 Run `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest` — all tests must pass
- [x] 5.2 Run `mvn test -pl judo-tatami-test-utils` — ModelComparator unit tests must pass
- [x] 5.3 Run `mvn test -pl judo-tatami-psm2asm -Pperformance -Dtest=Psm2AsmDiscoveryComparisonTest` — strict external model comparison must pass
