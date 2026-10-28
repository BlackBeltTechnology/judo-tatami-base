## Why

The current external model discovery mechanism is inflexible:
- Each module hardcodes model paths in `external-model-tests.properties` (e.g., `rackinspect=../../../../rackinspect/...`)
- Only a single discovery directory is supported via `-Djudo.test.discovery.basedir` system property
- Adding new external models (like tatami-tests) requires modifying properties files across multiple modules
- No graceful handling of missing external models—either tests fail or models aren't discovered

Teams need a flexible way to discover multiple external models without touching properties files for each new source.

## What Changes

- **Add** `model-search-directories.properties` configuration file (additive to existing `external-model-tests.properties`)
- **Add** Maven system property injection for search directories (`-Djudo.test.model.search.directories`)
- **Add** recursive directory scanning for automatic model detection
- **Add** graceful skip behavior: missing search directories are silently ignored (no test failures)
- **Modify** `AbstractExternalModelTest.loadModelConfigs()` to merge four model sources with priority

### Model Sources (merged with priority)
1. **Explicit models** from `external-model-tests.properties` (highest priority)
2. **Discovered models** from `-Djudo.test.model.search.directories` system property (Maven injection)
3. **Discovered models** from `model-search-directories.properties` file
4. **Discovered models** from `-Djudo.test.discovery.basedir` system property (existing, lowest priority)

### Detection Strategy
- Scan search directories for subdirectories containing `*.model` files
- When `*.model` files are found at a given level, do **not** scan deeper into that subtree (short-circuit)
- Subdirectory name becomes the model name
- Search directory itself can be a model directory if it contains `*.model` files (scanning stops there)
- If no `*.model` files at current level, recurse into child directories
- Missing search directories are skipped silently (no error, no test failure)

### Properties File Format

```properties
# model-search-directories.properties (new)
model.search.directories=\
  ../../../../rackinspect/application/model/target/generated-resources/model,\
  ../../../../tatami-tests/models,\
  /opt/judo-external-models
```

Paths are relative to module root or absolute. Missing directories are silently skipped.

### Maven System Property Override

Search directories can also be injected via Maven system property (useful for CI/CD):

```bash
mvn test -Pperformance \
  -Djudo.test.model.search.directories=/path/to/models,/another/path
```

This allows CI pipelines to inject external model locations without modifying properties files.

## Capabilities

### Modified Capabilities
- `unified-external-model-test`: Extend discovery mechanism to support multiple search directories with recursive scanning, Maven system property injection, and graceful missing directory handling. The existing requirements for dual-source discovery (properties + basedir) are extended to include search directories as additional sources.

## Impact

**Affected Code:**
- `judo-tatami-test-utils`: `AbstractExternalModelTest.java` (new `loadModelConfigs()`, `scanDirectory()`, `detectModels()` methods), `ExternalModelConfig.java`
- All 5 transformation test modules: new `src/test/resources/model-search-directories.properties` files

**Affected Tests:**
- All `*DiscoveryComparisonTest` classes automatically benefit from flexible model discovery

**Behavior Changes:**
- Missing external models no longer cause build/test failures (graceful skip)
- New external models in configured search directories are automatically discovered
- CI/CD can inject model locations via Maven system properties

**Configuration:**
- New property file: `model-search-directories.properties` in each module's `src/test/resources/`
- Maven system property: `-Djudo.test.model.search.directories=/path1,/path2` (overrides file)
- Backward compatible: existing `external-model-tests.properties` still works
