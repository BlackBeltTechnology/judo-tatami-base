## ADDED Requirements

### Requirement: Discovery test supports both model sources
The `*DiscoveryComparisonTest` SHALL collect models from both auto-discovery (`judo.test.discovery.basedir`) and properties file (`external-model-tests.properties`). When both sources provide a model with the same name, the properties-file version SHALL take priority.

#### Scenario: Discovery-only mode
- **WHEN** `judo.test.discovery.basedir` is set and properties file is empty
- **THEN** test discovers and runs all models from the base directory

#### Scenario: Properties-only mode
- **WHEN** `judo.test.discovery.basedir` is not set and properties file has entries
- **THEN** test runs models from the properties file

#### Scenario: Both sources with overlap
- **WHEN** both discovery and properties provide a model named "rackinspect"
- **THEN** the properties-file version is used (may have custom warmup/iterations)

#### Scenario: Both sources without overlap
- **WHEN** discovery finds models A, B and properties has model C
- **THEN** all three models A, B, C are tested

### Requirement: Discovery test supports warmup and iterations
The test SHALL support optional warmup and configurable iteration count from `ExternalModelConfig` parameters.

#### Scenario: Model with warmup enabled
- **WHEN** a model config has `warmup=true`
- **THEN** both ETL and Zeta transformations run once as warmup before measurement

#### Scenario: Model with multiple iterations
- **WHEN** a model config has `iterations=3`
- **THEN** ETL and Zeta each run 3 times and report average time

#### Scenario: Discovery model defaults
- **WHEN** a model is discovered (not from properties)
- **THEN** warmup is disabled and iterations is 1

### Requirement: ExternalModelTest classes are removed
The `*ExternalModelTest` classes SHALL be deleted as their functionality is absorbed by the unified DiscoveryComparisonTest.

#### Scenario: ExternalModelTest deleted
- **WHEN** the change is complete
- **THEN** all 5 `*ExternalModelTest.java` files no longer exist
