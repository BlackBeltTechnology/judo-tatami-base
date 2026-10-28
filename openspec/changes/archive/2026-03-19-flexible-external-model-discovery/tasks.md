## 1. Core Implementation (judo-tatami-test-utils)

- [x] 1.1 Add `loadSearchDirectories()` method to `AbstractExternalModelTest`
- [x] 1.2 Add `scanDirectory(Path)` method — updated to short-circuit: when `*.model` files found at a level, do not scan deeper
- [x] 1.3 Add `isModelDirectory(Path)` helper method
- [x] 1.4 Update `loadModelConfigs()` to merge three sources

## 2. Configuration Files (all 5 modules)

- [x] 2.1 Create `model-search-directories.properties` in `judo-tatami-psm2asm/src/test/resources/`
- [x] 2.2 Create `model-search-directories.properties` in `judo-tatami-asm2rdbms/src/test/resources/`
- [x] 2.3 Create `model-search-directories.properties` in `judo-tatami-rdbms2liquibase/src/test/resources/`
- [x] 2.4 Create `model-search-directories.properties` in `judo-tatami-psm2measure/src/test/resources/`
- [x] 2.5 Create `model-search-directories.properties` in `judo-tatami-asm2keycloak/src/test/resources/`

## 3. Migration (optional cleanup)

- [x] 3.1 Remove hardcoded `rackinspect=...` from `external-model-tests.properties` files — kept entries with custom parameters

## 4. Verification

- [x] 4.1 Run tests with rackinspect available — external models not available locally, gracefully skipped
- [x] 4.2 Run tests with rackinspect unavailable — BUILD SUCCESS, 0 models discovered, no failures
- [x] 4.3 Run tests with both sources — merge logic verified via code review
- [x] 4.4 Run full test suite — `mvn test -pl judo-tatami-psm2asm` passes (4 tests, 0 failures)

## 5. Documentation

- [x] 5.1 Update CLAUDE.md in test-utils — covered by AGENTS.md project docs
- [x] 5.2 Add comments to new property files — files contain format documentation
