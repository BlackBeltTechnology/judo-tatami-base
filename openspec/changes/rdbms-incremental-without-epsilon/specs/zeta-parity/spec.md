## ADDED Requirements

### Requirement: Incremental Liquibase parity
The Zeta incremental RDBMS to Liquibase transformation SHALL produce output equal to the ETL `Rdbms2LiquibaseIncremental`: the eight phase models, `missingReviewScripts` and `backupDataSqlFiles`. Its `tableNameMaxSize` default SHALL be 30 for `oracle` and 62 otherwise, and its output SHALL be deterministic.

#### Scenario: All eight phases equal
- **GIVEN** an Excel-defined original / new model pair for dialects `hsqldb` and `postgresql`
- **WHEN** the ETL and the Zeta engine transform the incremental model
- **THEN** all eight phase models are structurally equal (the order of change sets is not compared)

#### Scenario: Backup data SQL equal
- **GIVEN** operations that need a backup, with the default and with a custom `backupPrefix`
- **WHEN** both engines run
- **THEN** `backupDataSqlFiles` has the same keys and contents

#### Scenario: Table name limits
- **GIVEN** `oracle` without override, `postgresql` without override and an explicit override
- **WHEN** both engines run
- **THEN** `tableNameMaxSize` is 30, 62 and the override respectively, and the phase models are equal

#### Scenario: Deterministic Zeta output
- **GIVEN** one model pair
- **WHEN** the Zeta engine runs twice
- **THEN** the serialized changelogs and the backup SQL are identical
