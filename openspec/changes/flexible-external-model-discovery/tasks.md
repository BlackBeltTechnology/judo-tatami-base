## 1. Core Implementation (judo-tatami-test-utils)

- [ ] 1.1 Add `loadSearchDirectories()` method to `AbstractExternalModelTest`
  - Load `model-search-directories.properties` from classpath
  - Parse comma/newline-separated paths
  - Resolve relative paths from module root
  - Return `List<Path>` of existing directories only

- [ ] 1.2 Add `scanDirectory(Path)` method
  - Recursively walk directory tree
  - For each subdirectory: check if it contains `*.model` files
  - Return `Stream<ExternalModelConfig>` for detected models
  - Use subdirectory name as model name

- [ ] 1.3 Add `isModelDirectory(Path)` helper method
  - Check if directory contains at least one `*.model` file
  - Use `Files.list()` with glob pattern match

- [ ] 1.4 Update `loadModelConfigs()` to merge three sources
  - Load explicit models from `external-model-tests.properties` (existing)
  - Load search directories from `model-search-directories.properties` (new)
  - Load from `-Djudo.test.discovery.basedir` system property (existing)
  - Merge with priority: properties > search-directories > basedir

## 2. Configuration Files (all 5 modules)

- [ ] 2.1 Create `model-search-directories.properties` in `judo-tatami-psm2asm/src/test/resources/`
  - Add rackinspect path: `../../../../rackinspect/application/model/target/generated-resources/model`
  - Add tatami-tests path: `../../../../tatami-tests/models`

- [ ] 2.2 Create `model-search-directories.properties` in `judo-tatami-asm2rdbms/src/test/resources/`
  - Same paths as 2.1

- [ ] 2.3 Create `model-search-directories.properties` in `judo-tatami-rdbms2liquibase/src/test/resources/`
  - Same paths as 2.1

- [ ] 2.4 Create `model-search-directories.properties` in `judo-tatami-psm2measure/src/test/resources/`
  - Same paths as 2.1

- [ ] 2.5 Create `model-search-directories.properties` in `judo-tatami-asm2keycloak/src/test/resources/`
  - Same paths as 2.1

## 3. Migration (optional cleanup)

- [ ] 3.1 Remove hardcoded `rackinspect=...` from `external-model-tests.properties` files
  - Only remove if no custom parameters (warmup, iterations)
  - Keep entries that need custom configuration

## 4. Verification

- [ ] 4.1 Run tests with rackinspect available
  - `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDiscoveryComparisonTest -Pperformance`
  - Verify rackinspect tests run and pass

- [ ] 4.2 Run tests with rackinspect unavailable (simulate missing directory)
  - Temporarily rename rackinspect directory
  - Run tests, verify they pass with 0 models discovered from search directories
  - Restore directory

- [ ] 4.3 Run tests with both sources
  - Verify models from properties + search directories are merged
  - Verify properties overrides search directory for same model name

- [ ] 4.4 Run full test suite
  - `mvn test -pl judo-tatami-psm2asm`
  - Verify no regressions

## 5. Documentation

- [ ] 5.1 Update CLAUDE.md in test-utils
  - Document new `model-search-directories.properties` file
  - Explain priority merge order
  - Add examples of usage

- [ ] 5.2 Add comments to new property files
  - Explain format (comma/newline separated)
  - Document that missing directories are silently skipped
  - Note path resolution (relative to module root)
