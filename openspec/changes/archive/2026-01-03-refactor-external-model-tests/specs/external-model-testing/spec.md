# External Model Testing Specification

## Purpose

Enable parametrized testing of ETL and ZETA transformations against external model files configured via properties files, making the test framework reusable across modules and external projects.

## ADDED Requirements

### Requirement: Properties-Based Model Configuration

The test framework MUST support loading model configurations from a properties file with optional parameters.

#### Scenario: Valid properties file with single model (simple format)
Given a file `external-model-tests.properties` exists in `src/test/resources`
And it contains the entry `rackinspect=../path/to/models`
When the test framework loads configurations
Then one `ExternalModelConfig` is created with modelName "rackinspect"
And the path is resolved relative to the module root
And default parameters are used (warmup=false, iterations=1, dialect=hsqldb)

#### Scenario: Valid properties file with extended format
Given a file `external-model-tests.properties` exists in `src/test/resources`
And it contains `mymodel=/path/to/models;dialect=postgresql;warmup=true;iterations=3`
When the test framework loads configurations
Then one `ExternalModelConfig` is created with modelName "mymodel"
And dialect parameter is "postgresql"
And warmup parameter is true
And iterations parameter is 3

#### Scenario: Valid properties file with multiple models
Given a file `external-model-tests.properties` exists in `src/test/resources`
And it contains entries for "modelA" and "modelB"
When the test framework loads configurations
Then two `ExternalModelConfig` instances are created
And each model is tested independently

#### Scenario: Properties file with absolute path
Given a file `external-model-tests.properties` contains `mymodel=/absolute/path/to/models`
When the test framework loads configurations
Then the path is used as-is without relative resolution

#### Scenario: Missing properties file
Given no `external-model-tests.properties` file exists
When the test class is executed
Then zero test cases are generated
And no errors are thrown

### Requirement: Graceful Handling of Missing Models

The test framework MUST gracefully handle missing model directories or files.

#### Scenario: Model directory does not exist
Given `external-model-tests.properties` contains `missing=/nonexistent/path`
When the test framework loads configurations
Then the configuration is created with `exists=false`
And that test case is skipped

#### Scenario: Model file does not exist in existing directory
Given the model directory exists but `rackinspect-psm.model` is missing
When the PSM2ASM test executes for that model
Then the test fails with a clear error message indicating the expected file name

### Requirement: Parametrized Test Execution

The test framework MUST execute each configured model as a separate parametrized test case.

#### Scenario: Multiple models configured
Given three models are configured in properties file
And all three model directories exist with valid model files
When the test class executes
Then three test cases are executed
And each test case shows the model name in the test report

#### Scenario: Default execution behavior (no warmup, single iteration)
Given a model is configured with simple format `rackinspect=../path`
When the test executes
Then no warmup run is performed
And ETL transformation is executed once
And ZETA transformation is executed once

#### Scenario: Custom execution behavior with warmup and iterations
Given a model is configured with `mymodel=../path;warmup=true;iterations=3`
When the test executes
Then warmup runs are performed for both ETL and ZETA
And ETL transformation is executed 3 times
And ZETA transformation is executed 3 times
And timing metrics are averaged across iterations

#### Scenario: Performance profiling per model
Given a model is configured in properties file
When the test executes
Then both ETL and ZETA transformations are executed
And execution times are logged for each
And throughput metrics are calculated

### Requirement: Model Comparison

The test framework MUST compare ETL and ZETA transformation outputs.

#### Scenario: Successful model comparison
Given ETL and ZETA transformations complete successfully
When the results are compared using `ModelComparator`
Then the comparison uses the configured comparison mode
And success is logged if models are equivalent
And failure details are provided if models differ

### Requirement: Abstract Base Class for Reusability

The framework MUST provide an abstract base class in `judo-tatami-test-utils` for reuse.

#### Scenario: External project uses the base class
Given an external project depends on `judo-tatami-test-utils`
And creates a class extending `AbstractExternalModelTest`
And provides `external-model-tests.properties` in test resources
When the test class is executed
Then the external models are tested using the framework

### Requirement: README Documentation

The framework MUST be documented in the project README.

#### Scenario: User reads documentation
Given a user wants to configure external model testing
When they read the README documentation
Then they understand the properties file format
And they understand how to run the tests
And they understand the model file naming convention

## Related Capabilities

- [model-comparison](../../../specs/model-comparison/spec.md) - ModelComparator for ETL/ZETA comparison
- [zeta-transformations](../../../specs/zeta-transformations/spec.md) - ZETA transformation framework
