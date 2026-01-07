# Strict Model Validation with ID Equivalence

## ADDED Requirements

### Requirement: Strict Comparison Mode Verification

External model tests MUST pass when running with STRICT comparison mode, verifying that ETL and Zeta transformations produce identical output including all attributes, references, and annotations.

#### Scenario: Run external model test in strict mode
Given an external PSM model configured in properties file
When the test runs with `-Djudo.test.comparison.mode=STRICT`
Then all structural features including annotations are compared exactly
And the test passes with no differences reported

#### Scenario: Verify all transformation modules
Given external model tests for Psm2Asm, Asm2Rdbms, Rdbms2Liquibase, Psm2Measure, and Asm2Keycloak
When each test runs with STRICT mode enabled
Then all tests pass with equivalent ETL and Zeta output

### Requirement: XMI ID Equivalence Verification

External model tests MUST pass when XMI ID comparison is enabled, verifying that element IDs are equivalent between ETL and Zeta outputs using flexible rule-name matching.

#### Scenario: Enable XMI ID comparison
Given an external model test
When the test runs with `-Djudo.test.comparison.xmiIds=true`
Then XMI IDs are compared between ETL and Zeta outputs
And flexible matching handles rule name differences between engines
And the test passes with equivalent XMI IDs

#### Scenario: Combined strict and XMI ID validation
Given an external model test
When the test runs with both STRICT mode and XMI ID comparison enabled
Then both structural equivalence and XMI ID equivalence are verified
And the test passes confirming full ETL-Zeta parity
