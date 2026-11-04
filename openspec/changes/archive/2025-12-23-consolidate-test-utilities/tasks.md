# Tasks

## Phase 1: Add ModelComparator to judo-tatami-test-utils

- [x] Create `hu.blackbelt.judo.tatami.test.util` package in judo-tatami-test-utils
- [x] Copy ModelComparator from judo-tatami-psm2asm (has complete javadoc)
- [x] Merge order-independent Resource comparison from judo-tatami-psm2measure version
- [x] Merge enhanced getContentSignature from judo-tatami-psm2measure version
- [x] Update package declaration to `hu.blackbelt.judo.tatami.test.util`
- [x] Verify ModelComparator compiles: `mvn compile -pl judo-tatami-test-utils`

## Phase 2: Update judo-tatami-psm2asm

- [x] Update imports in test classes to use `hu.blackbelt.judo.tatami.test.util.ModelComparator`
- [x] Delete local `ModelComparator.java` from psm2asm
- [x] Run tests: `mvn test -pl judo-tatami-psm2asm`

## Phase 3: Update judo-tatami-psm2measure

- [x] Update pom.xml to ensure test-utils dependency is present
- [x] Update imports in test classes to use shared ModelComparator
- [x] Delete local `util/ModelComparator.java`
- [x] Run tests: `mvn test -pl judo-tatami-psm2measure`

## Phase 4: Update judo-tatami-asm2rdbms

- [x] Update pom.xml to ensure test-utils dependency is present
- [x] Update imports in test classes to use shared ModelComparator
- [x] Delete local `util/ModelComparator.java`
- [x] Run tests: `mvn test -pl judo-tatami-asm2rdbms`

## Phase 5: Update judo-tatami-rdbms2liquibase

- [x] Update pom.xml to ensure test-utils dependency is present
- [x] Update imports in test classes to use shared ModelComparator
- [x] Delete local `util/ModelComparator.java`
- [x] Run tests: `mvn test -pl judo-tatami-rdbms2liquibase`

## Phase 6: Update judo-tatami-asm2keycloak

- [x] Update pom.xml to ensure test-utils dependency is present
- [x] Update imports in test classes to use shared ModelComparator
- [x] Delete local `util/ModelComparator.java`
- [x] Run tests: `mvn test -pl judo-tatami-asm2keycloak`

## Phase 7: Final Verification

- [x] Build all modules: `mvn compile`
- [x] Run full test suite: `mvn test`
- [x] Verify no duplicate ModelComparator files: Only 1 file in test-utils
- [x] Verify code reduction: 5 duplicate files removed (~4,000 lines)

## Files Deleted

- `judo-tatami-psm2asm/src/test/java/hu/blackbelt/judo/tatami/psm2asm/ModelComparator.java`
- `judo-tatami-psm2measure/src/test/java/hu/blackbelt/judo/tatami/psm2measure/util/ModelComparator.java`
- `judo-tatami-asm2rdbms/src/test/java/hu/blackbelt/judo/tatami/asm2rdbms/util/ModelComparator.java`
- `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase/util/ModelComparator.java`
- `judo-tatami-asm2keycloak/src/test/java/hu/blackbelt/judo/tatami/asm2keycloak/util/ModelComparator.java`

## Import Updates Completed

- `judo-tatami-psm2asm`: 13 test files updated
- `judo-tatami-psm2measure`: 3 test files updated
- `judo-tatami-asm2rdbms`: 6 test files updated
- `judo-tatami-rdbms2liquibase`: 4 test files updated
- `judo-tatami-asm2keycloak`: 3 test files updated
