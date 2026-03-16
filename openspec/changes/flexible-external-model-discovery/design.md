## Context

### Current State

`AbstractExternalModelTest` (in `judo-tatami-test-utils`) provides infrastructure for external model testing. It currently supports:

1. **Explicit model configuration** via `external-model-tests.properties`:
   ```properties
   rackinspect=../../../../rackinspect/application/model/target/generated-resources/model
   ```

2. **Single directory discovery** via `-Djudo.test.discovery.basedir` system property

3. **Merge behavior**: Properties file overrides discovered models with same name

### Constraints

- **Backward compatibility**: Existing `external-model-tests.properties` must continue to work
- **Cross-module**: All 5 transformation modules use this infrastructure
- **No test failures on missing models**: CI/CD environments may not have external models available
- **Module-relative paths**: Properties files use paths relative to module root

### Stakeholders

- **CI/CD pipelines**: Need tests to pass even without external models
- **Development teams**: Want to add new models (tatami-tests) without touching properties
- **QA teams**: Use external models for validation against real-world schemas

## Goals / Non-Goals

**Goals:**
- Enable automatic discovery of models from multiple configured directories
- Support recursive scanning for subdirectories containing `*.model` files
- Gracefully skip missing search directories (no test failures)
- Maintain full backward compatibility with existing configuration

**Non-Goals:**
- Changing the test execution logic or performance measurement
- Modifying the `ExternalModelConfig` record structure
- Changing how model comparison works
- Supporting non-standard model file layouts

## Decisions

### Decision 1: Additive Property File

**Choice:** Create `model-search-directories.properties` as a new file alongside `external-model-tests.properties`

**Rationale:**
- Clear separation of concerns: explicit vs. discovered models
- No breaking changes to existing configurations
- Easy to opt-in (add new file) or opt-out (don't add it)
- Each module can customize its search paths independently

**Alternatives considered:**
- **Single unified properties file**: Would require parsing two different formats in one file, more complex
- **System property only**: Requires CI configuration changes, less discoverable
- **Maven/Gradle configuration**: Couples test logic to build system, harder to use in IDEs

### Decision 2: Recursive Directory Scanning

**Choice:** Scan search directories recursively, using subdirectory name as model name

**Rationale:**
- Supports common layouts like `/external-models/rackinspect/models/` → model name "rackinspect"
- Handles deep nesting without configuration
- Simple heuristic: "any subdirectory with *.model files is a model"

**Alternatives considered:**
- **Flat scan only**: Would require all models to be immediate children, limiting layout options
- **Configurable depth**: Adds complexity; recursive with depth limit covers most cases
- **Fixed directory structure**: Would require all external projects to adopt a specific layout

### Decision 3: Silent Skip for Missing Directories

**Choice:** Missing search directories are silently ignored, no errors or warnings logged

**Rationale:**
- CI environments may not have external models available
- Tests should run with available models only
- Warning noise would be misleading (not actually an error)

**Alternatives considered:**
- **Fail on missing directories**: Would break CI in environments without external models
- **Warn on missing**: Could be useful but adds noise; users can check test summary for what ran
- **Debug logging**: Useful for development, can be enabled via log level

### Decision 4: Priority Merge Order

**Choice:** Properties (highest) → Maven system property → Search directories file → Basedir (lowest)

**Rationale:**
- Properties file has always been the explicit override mechanism
- Maven system property allows CI/CD to inject locations without file changes
- Search directories file is a convenient default for commonly-used models
- Basedir is legacy system property, kept for compatibility

**Alternatives considered:**
- **First-wins**: Would make it impossible to override discovered models
- **Last-wins**: Would require removing properties entries to use discovered versions
- **Configurable priority**: Adds complexity with little benefit

### Decision 5: Maven System Property Override

**Choice:** Support `-Djudo.test.model.search.directories` system property for CI/CD injection

**Rationale:**
- CI/CD pipelines can inject model locations without modifying properties files
- Useful for dynamic model locations that vary per build
- Allows different configurations per environment (dev, CI, staging)
- System property takes priority over properties file (explicit override)

**Format:**
```bash
mvn test -Pperformance \
  -Djudo.test.model.search.directories=/path/to/models,/another/path
```

**Alternatives considered:**
- **Environment variables**: Less portable across shells, harder to use in IDE
- **Maven profiles**: Requires profile configuration in pom.xml, less flexible
- **Separate properties file per environment**: More files to maintain

## Risks / Trade-offs

### Risk: Accidental Discovery of Unintended Models

**Scenario:** A search directory contains subdirectories with `*.model` files that aren't meant to be tested (e.g., archived models, work-in-progress)

**Mitigation:**
- Use explicit properties file for models that need specific configuration
- Document that search directories should be organized intentionally
- Consider adding a file-based exclusion mechanism in future (e.g., `.skip-test` file)

### Risk: Performance Impact of Recursive Scanning

**Scenario:** Deep directory structures with many subdirectories slow down test initialization

**Mitigation:**
- Scanning happens once at test setup, not per-test
- File existence checks are fast (local filesystem)
- Consider depth limit (e.g., max 5 levels) if performance issues arise

### Risk: Path Resolution Confusion

**Scenario:** Developers unclear whether paths are relative to module root, project root, or working directory

**Mitigation:**
- Document clearly in properties file template
- Use existing `resolveModuleBaseDir()` logic for consistency
- Log resolved absolute paths in debug mode

### Trade-off: Flexibility vs. Simplicity

Adding three model sources increases complexity but provides significant flexibility in model management. The additive approach (new file, not changes to existing) minimizes breaking changes.

## Implementation Overview

### AbstractExternalModelTest Changes

```
loadModelConfigs()
    │
    ├── 1. Load explicit models from external-model-tests.properties (existing)
    │
    ├── 2. Load search directories from model-search-directories.properties (NEW)
    │    │
    │    └── For each search directory:
    │         └── scanDirectory(directory) → Stream<ExternalModelConfig>
    │              └── For each subdirectory (recursive):
    │                   └── If contains *.model files:
    │                       → Create ExternalModelConfig(name=dirname, exists=true)
    │
    ├── 3. Load from -Djudo.test.discovery.basedir system property (existing)
    │
    └── 4. Merge with priority: properties > search-directories > basedir
```

### New Methods

```java
// Load search directories from properties file
private List<Path> loadSearchDirectories()

// Scan directory recursively for model subdirectories
private Stream<ExternalModelConfig> scanDirectory(Path searchDir)

// Check if directory contains model files
private boolean isModelDirectory(Path dir)

// Merge config maps with priority
private void mergeConfigs(Map<String, ExternalModelConfig> destination,
                         Map<String, ExternalModelConfig> source)
```

### Property File Template

```properties
# model-search-directories.properties
# Directories to search for models (comma or newline separated)
# Missing directories are silently skipped
# Relative paths are resolved from module root

model.search.directories=\
  ../../../../rackinspect/application/model/target/generated-resources/model,\
  ../../../../tatami-tests/models
```

## Migration Plan

1. **Phase 1: Implement new functionality**
   - Add `loadSearchDirectories()` method to `AbstractExternalModelTest`
   - Add `scanDirectory()` with recursive detection
   - Update `loadModelConfigs()` to merge three sources

2. **Phase 2: Add property files to modules**
   - Create `model-search-directories.properties` in each module
   - Add common search directories (rackinspect, tatami-tests when available)

3. **Phase 3: Migrate existing properties**
   - Remove hardcoded `rackinspect=...` entries from `external-model-tests.properties`
   - Add `rackinspect` to search directories instead
   - Keep entries that need custom configuration (warmup, iterations)

4. **Phase 4: Verification**
   - Run tests with rackinspect available
   - Run tests with rackinspect unavailable (verify graceful skip)
   - Run tests with tatami-tests added

### Rollback Strategy

- All changes are additive; old properties file format still works
- Remove `model-search-directories.properties` files to revert to old behavior
- No code changes to `*DiscoveryComparisonTest` classes required

## Open Questions

1. **Depth limit for recursive scanning?**
   - Current design: unlimited recursion
   - Consider: Max depth of 5-10 levels to prevent pathological cases

2. **Should we log debug info for skipped directories?**
   - Current design: silent skip
   - Consider: Debug-level logging for troubleshooting

3. **How to handle tatami-tests when it doesn't exist yet?**
   - Current design: silently skipped
   - Consider: Document in README, add when available
