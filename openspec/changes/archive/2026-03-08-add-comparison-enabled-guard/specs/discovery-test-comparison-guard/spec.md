## ADDED Requirements

### Requirement: Discovery tests respect comparison enabled flag
All `*DiscoveryComparisonTest` classes SHALL check `ModelComparator.isComparisonEnabled()` before performing model comparison. When comparison is disabled, the test SHALL still execute both ETL and Zeta transformations and print performance results, but SHALL skip model comparison and log that comparison was skipped.

#### Scenario: Comparison enabled (default)
- **WHEN** `judo.test.comparison.enabled` is `true` (or not set)
- **THEN** the discovery test runs ETL and Zeta transformations, prints performance results, and performs full model comparison

#### Scenario: Comparison disabled
- **WHEN** `judo.test.comparison.enabled` is `false`
- **THEN** the discovery test runs ETL and Zeta transformations, prints performance results, logs that comparison was skipped, and returns without comparing models

#### Scenario: All 5 modules support the flag
- **WHEN** `judo.test.comparison.enabled=false` is set
- **THEN** Psm2AsmDiscoveryComparisonTest, Psm2MeasureDiscoveryComparisonTest, Asm2RdbmsDiscoveryComparisonTest, Rdbms2LiquibaseDiscoveryComparisonTest, and Asm2KeycloakDiscoveryComparisonTest all skip model comparison
