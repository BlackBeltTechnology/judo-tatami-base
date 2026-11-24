## MODIFIED Requirements

### Requirement: Execute Incremental RDBMS to Liquibase Transformation
The system SHALL transform a differential RdbmsModel into multiple LiquibaseModel phases through `Rdbms2LiquibaseIncrementalTransformation`, producing changelogs for: database checkup, backup, before-incremental, data update before, incremental, data update after, after-incremental, and drop-backup. The engine SHALL be selected by `TransformationMode`: `ZETA` (default) runs in the Epsilon-free core artifact; `ETL` delegates to the `Rdbms2LiquibaseIncrementalEtlEngine` found through `EtlEngineRegistry` (implemented by `judo-tatami-rdbms2liquibase-etl`, wrapping `Rdbms2LiquibaseIncremental`). Both engines SHALL return the same `Rdbms2LiquibaseIncrementalResult`.

#### Scenario: Incremental migration produces all phases
- **GIVEN** a valid incremental `RdbmsModel` and eight empty `LiquibaseModel` instances
- **WHEN** `Rdbms2LiquibaseIncrementalTransformation.executeRdbms2LiquibaseIncrementalTransformation(parameter)` is invoked in `ZETA` mode
- **THEN** each of the eight `LiquibaseModel` resources is populated with phase-specific changelog entries
- **AND** the entries equal those of the ETL engine (same change sets, same changes; the order of change sets is not significant)

#### Scenario: Incremental migration with SQL output
- **GIVEN** `sqlOutput` is set to a valid directory path
- **WHEN** the incremental transformation completes in either mode
- **THEN** backup data SQL files from `backupDataSqlFiles` are written to the specified directory

#### Scenario: ETL mode delegates to the add-on
- **GIVEN** `transformationMode` is `ETL` and `judo-tatami-rdbms2liquibase-etl` is available
- **WHEN** the incremental transformation is invoked
- **THEN** the registered `Rdbms2LiquibaseIncrementalEtlEngine` is called
- **AND** its output equals a direct `Rdbms2LiquibaseIncremental.executeRdbms2LiquibaseIncrementalTransformation` call

#### Scenario: ETL mode without the add-on
- **GIVEN** `transformationMode` is `ETL` and the add-on is not on the classpath
- **WHEN** the incremental transformation is invoked
- **THEN** it fails before any phase model is touched, with an `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl`

### Requirement: Review Script Resolution
The system SHALL resolve review scripts through a `ReviewResolver` in both engines. If no `ReviewResolver` is provided, a `FileSystemReviewResolver` is created from the `sqlScriptPath` parameter. A `SqlFile` SHALL be added to a data update change set only for an existing script with non-blank content; an existing blank script adds no `SqlFile` and is not reported missing; an unknown script adds no `SqlFile` and is reported missing. The change set of a review rule SHALL exist in every case.

#### Scenario: FileSystemReviewResolver fallback
- **GIVEN** no `ReviewResolver` is provided but `sqlScriptPath` is set
- **WHEN** incremental transformation runs
- **THEN** a `FileSystemReviewResolver` backed by the `sqlScriptPath` directory is used

#### Scenario: Missing both ReviewResolver and sqlScriptPath
- **GIVEN** neither `ReviewResolver` nor `sqlScriptPath` is set
- **WHEN** incremental transformation is attempted in `ZETA` mode
- **THEN** an `IllegalArgumentException` is thrown with message `"One of ReviewResolver or scriptPath have to be set"`

#### Scenario: Review script present, empty or absent
- **GIVEN** an operation that requires review whose script is present with content / present and blank / absent
- **WHEN** incremental transformation runs in either mode
- **THEN** a `SqlFile` is added only for the first case, and only the absent script appears in `missingReviewScripts`
- **AND** both engines produce the same `missingReviewScripts`

### Requirement: Report Missing Review Scripts
The system SHALL collect missing review script names in `Rdbms2LiquibaseIncrementalResult.missingReviewScripts` during the incremental transformation of both engines (script name to message `Sql script not found for ...`), allowing callers to identify scripts that need manual creation.

#### Scenario: Missing review scripts reported
- **GIVEN** the incremental RDBMS model references review scripts that do not exist
- **WHEN** incremental transformation completes in either mode
- **THEN** `result.getMissingReviewScripts()` contains entries for each missing script, equal in both engines
