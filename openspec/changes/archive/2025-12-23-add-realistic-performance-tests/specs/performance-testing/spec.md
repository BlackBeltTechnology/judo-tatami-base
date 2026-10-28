# Performance Testing

## ADDED Requirements

### Requirement: Synthetic Model Generation for Performance Tests

Each transformation module MUST have a RealisticPerformanceTest that generates synthetic input models programmatically, matching the characteristics of the RackInspect real-world model.

#### Scenario: Generate PSM model for Psm2Measure test
Given a target entity count of 70
When the test generates a synthetic PSM model
Then the model contains approximately 70 entities with measures and units
And the model characteristics match RackInspect ratios (4 attributes per entity, etc.)

#### Scenario: Generate ASM model for Asm2Rdbms test
Given a target entity count of 70
When the test generates a synthetic ASM model
Then the model contains approximately 1245 EClassifiers
And each EClass has appropriate EAttributes and EReferences

#### Scenario: Generate RDBMS model for Rdbms2Liquibase test
Given a target table count matching RackInspect
When the test generates a synthetic RDBMS model
Then the model contains tables with fields, indexes, and foreign keys
And the structure matches typical entity-to-table mappings

### Requirement: ETL and Zeta Transformation Comparison

Performance tests MUST execute both ETL and Zeta transformations and compare their outputs for equivalence.

#### Scenario: Compare ETL and Zeta outputs
Given a generated synthetic model
When both ETL and Zeta transformations are executed
Then the output models are structurally equivalent
And any differences are reported with detailed paths

#### Scenario: Measure transformation performance
Given a generated synthetic model
When transformations are executed after warmup
Then execution time is measured in milliseconds
And throughput (elements/second) is calculated
And speedup factor (ETL time / Zeta time) is reported

### Requirement: Multiple Test Sizes

Performance tests SHALL support multiple model sizes to test scalability.

#### Scenario: Small model test (20 entities)
Given a small model with 20 entities
When the performance test runs
Then both transformations complete successfully
And results are compared for equivalence

#### Scenario: Medium model test (70 entities - RackInspect-like)
Given a medium model with 70 entities matching RackInspect
When the performance test runs
Then both transformations complete successfully
And performance matches RackInspect baseline expectations

#### Scenario: Large model test (100 entities)
Given a large model with 100 entities
When the performance test runs
Then both transformations complete successfully
And scalability characteristics are observable

### Requirement: Self-Contained Tests Without External Dependencies

Performance tests MUST NOT depend on external model files (like RackInspect models).

#### Scenario: Run test without external files
Given no external model files are present
When the RealisticPerformanceTest is executed
Then the test generates all required models programmatically
And the test completes successfully

#### Scenario: Test portability
Given a fresh checkout of the repository
When performance tests are run with -Pperformance
Then all tests pass without requiring additional setup
And no file-not-found errors occur
