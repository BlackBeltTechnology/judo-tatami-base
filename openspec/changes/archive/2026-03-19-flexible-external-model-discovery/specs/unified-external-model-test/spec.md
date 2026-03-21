## MODIFIED Requirements

### Requirement: Discovery test supports multiple model sources
The `*DiscoveryComparisonTest` SHALL collect models from four sources with priority:
1. Explicit models from `external-model-tests.properties` (highest priority)
2. Discovered models from `-Djudo.test.model.search.directories` system property (Maven injection)
3. Discovered models from `model-search-directories.properties` file
4. Discovered models from `judo.test.discovery.basedir` system property (legacy, lowest priority)

When multiple sources provide a model with the same name, higher priority sources override lower priority sources.

#### Scenario: Properties overrides search directory
- **WHEN** properties file has `custom-model=/path/to/custom` AND search directory contains `custom-model/` subdirectory
- **THEN** the properties-file version is used (higher priority)

#### Scenario: Search directory overrides basedir
- **WHEN** search directories contain `model-a/` AND basedir also contains `model-a/`
- **THEN** the search-directory version is used (higher priority than basedir)

#### Scenario: All three sources with different models
- **WHEN** properties has model P, search directories find models S1, S2, and basedir finds model B
- **THEN** all four models P, S1, S2, B are tested

#### Scenario: Search directory missing, basedir exists
- **WHEN** a configured search directory does not exist AND basedir is configured
- **THEN** tests run with basedir-discovered models (no error for missing search directory)

### Requirement: Search directories support recursive model detection
The system SHALL scan configured search directories recursively for subdirectories containing `*.model` files. Each subdirectory containing model files becomes a discovered model with the subdirectory name as the model name.

#### Scenario: Subdirectory contains model files
- **WHEN** search directory `/models/` contains subdirectory `rackinspect/` with `rackinspect-psm.model`
- **THEN** model `rackinspect` is discovered and added to test configs

#### Scenario: Search directory itself is a model directory
- **WHEN** search directory `/models/rackinspect/` itself contains `rackinspect-psm.model`
- **THEN** model `rackinspect` is discovered (directory name becomes model name)

#### Scenario: Deeply nested model directory
- **WHEN** search directory contains `external/projects/tatami-tests/models/` with model files
- **THEN** model `tatami-tests` is discovered (uses innermost directory name)

#### Scenario: Non-model subdirectory ignored
- **WHEN** search directory contains subdirectory `docs/` with only readme files (no *.model)
- **THEN** that subdirectory is ignored (not added to test configs)

### Requirement: Search directories are configured via properties file
The system SHALL load search directories from `model-search-directories.properties` in the test resources path. The file format uses comma or newline-separated paths relative to the module root or absolute paths.

#### Scenario: Multiple search directories
- **WHEN** `model-search-directories.properties` contains `model.search.directories=/opt/models-a,../../models-b`
- **THEN** both directories are scanned for models

#### Scenario: Absolute path in search directories
- **WHEN** properties file contains `/usr/local/judo-models`
- **THEN** that absolute path is used directly (not resolved from module root)

#### Scenario: Empty search directories file
- **WHEN** `model-search-directories.properties` exists but is empty
- **THEN** no models are discovered from this source (but other sources still work)

#### Scenario: Missing search directories file
- **WHEN** `model-search-directories.properties` does not exist
- **THEN** no models are discovered from this source (backward compatible, tests still run)

### Requirement: Missing search directories are silently skipped
The system SHALL gracefully ignore configured search directories that do not exist on the filesystem. No errors or warnings shall be raised for missing search directories.

#### Scenario: Search directory does not exist
- **WHEN** `model-search-directories.properties` contains `/nonexistent/path/to/models`
- **THEN** that directory is skipped (no error, tests continue)

#### Scenario: All search directories missing
- **WHEN** all configured search directories are missing from filesystem
- **THEN** tests run with models from properties file and/or basedir (no failure)

#### Scenario: Partial search directories exist
- **WHEN** three search directories are configured but only one exists
- **THEN** only the existing directory is scanned (others silently skipped)

### Requirement: Search directories can be injected via Maven system property
The system SHALL support search directory configuration via `-Djudo.test.model.search.directories` system property. This allows CI/CD pipelines to inject model locations without modifying properties files. The system property takes priority over the properties file.

#### Scenario: Maven system property overrides properties file
- **WHEN** both `-Djudo.test.model.search.directories=/ci/models` and `model-search-directories.properties` exist
- **THEN** only the system property directories are scanned (properties file ignored)

#### Scenario: Maven system property only
- **WHEN** `-Djudo.test.model.search.directories=/path1,/path2` is set and no properties file exists
- **THEN** both directories are scanned for models

#### Scenario: Empty Maven system property
- **WHEN** `-Djudo.test.model.search.directories=` (empty value) is set
- **THEN** no models are discovered from this source (but properties file and other sources still work)

#### Scenario: Maven system property with missing directories
- **WHEN** `-Djudo.test.model.search.directories=/nonexistent1,/existing2` is set
- **THEN** missing directories are silently skipped (same behavior as properties file)

## ADDED Requirements

### Requirement: Model detection requires at least one model file
A subdirectory SHALL be identified as a model directory only when it contains at least one file matching the pattern `*.model`.

#### Scenario: Directory with single model file
- **WHEN** subdirectory contains only `example-psm.model`
- **THEN** that subdirectory is detected as a model directory named `example`

#### Scenario: Directory with multiple model files
- **WHEN** subdirectory contains `example-psm.model`, `example-asm.model`, `example-asm2rdbms.model`
- **THEN** that subdirectory is detected as a model directory named `example`

#### Scenario: Directory with no model files
- **WHEN** subdirectory contains only `.project`, `.settings`, `readme.md` files
- **THEN** that subdirectory is NOT detected as a model directory

#### Scenario: Mixed content directory
- **WHEN** subdirectory contains `example-psm.model`, `docs/`, `src/` subdirectories
- **THEN** that subdirectory IS detected as a model directory (has at least one *.model file)

### Requirement: Discovered models use default configuration
Models discovered from search directories or basedir SHALL use default configuration: warmup disabled, iterations = 1, dialect = default for module.

#### Scenario: Discovered model defaults
- **WHEN** model is discovered from search directory (not in properties file)
- **THEN** `warmup=false`, `iterations=1`, and default dialect are used

#### Scenario: Override discovered model via properties
- **WHEN** model `custom` is discovered from search directory AND properties file has `custom=/other/path;warmup=true;iterations=5`
- **THEN** properties-file configuration takes precedence (warmup=true, iterations=5)
