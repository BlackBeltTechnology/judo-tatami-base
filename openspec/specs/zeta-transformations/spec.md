# zeta-transformations Specification

## Purpose
TBD - created by archiving change adopt-registry-based-transformations. Update Purpose after archive.
## Requirements
### Requirement: All Zeta Transformations Must Use TransformationRegistry

All Zeta-based transformations MUST use the `TransformationRegistry` and `TransformationExecutor` pattern from the Zeta framework.

#### Scenario: Transformation uses registry pattern
Given a Zeta transformation class
When the transformation is executed
Then it must create a TransformationRegistry
And register all rule classes with the registry in correct execution order
And create a TransformationContext with proper configuration
And use TransformationExecutor with parallel set to true

### Requirement: Rule Classes Must Use TransformRule Annotations

All transformation rules MUST be defined in separate rule classes with proper annotations.

#### Scenario: Rule class structure
Given a transformation rule class
When defining transformation rules
Then each rule method must have TransformRule annotation with name and description
And use Transform annotation to specify source type
And use To annotation to specify target type
And optionally use Guard annotation for conditional execution

### Requirement: Performance Tests Must Use Single Iteration

All Zeta transformation performance tests MUST use exactly 1 iteration.

#### Scenario: Performance test configuration
Given a Zeta transformation performance test
When configuring test iterations
Then the test must use RepeatedTest with value 1 or equivalent single iteration
And no warmup phase is required
And test must measure and log execution time

