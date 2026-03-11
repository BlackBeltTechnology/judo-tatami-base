# RDBMS to Liquibase Transformation Specification

## Purpose
Transforms a Relational Database Model (RdbmsModel) into Liquibase changelog models (LiquibaseModel) for database schema management, supporting both full (initial) migration and incremental (differential) migration with backup, checkup, and review mechanisms.

## Architecture
The module provides two distinct transformation paths:

- **Rdbms2Liquibase** -- Full migration entry point. Executes `rdbmsToLiquibase.etl` to produce a complete Liquibase changelog from the current RDBMS model. Accepts `Rdbms2LiquibaseParameter` with `RdbmsModel`, `LiquibaseModel`, `dialect`, and execution flags.
- **Rdbms2LiquibaseIncremental** -- Incremental migration entry point. Executes `rdbmsIncrementalToLiquibase.etl` followed by `main.egl` SQL generation. Manages eight separate LiquibaseModel outputs: `dbCheckup`, `dbBackup`, `beforeIncremental`, `updateDataBeforeIncremental`, `incremental`, `updateDataAfterIncremental`, `afterIncremental`, `dbDropBackup`. Returns `Rdbms2LiquibaseIncrementalResult` containing `backupDataSqlFiles` and `missingReviewScripts` maps.
- **ReviewResolver** -- Interface with `exists(String name)` and `resolve(String name)` methods for resolving migration review scripts.
- **FileSystemReviewResolver** -- Default `ReviewResolver` implementation that resolves scripts from the file system.
- **AbbreviateUtils** -- Identifier abbreviation utility (shared with asm2rdbms).
- **Rdbms2LiquibaseWork** -- Workflow adapter for full migration. Retrieves `RdbmsModel` from context keyed by `"rdbms:<dialect>"`, registers RDBMS metamodel extensions, stores `LiquibaseModel` keyed by `"liquibase:<dialect>"`.
- **Rdbms2LiquibaseIncrementalWork** -- Workflow adapter for incremental migration. Retrieves incremental RDBMS model from context keyed by `"rdbms-incremental:<dialect>"`, creates eight LiquibaseModel instances keyed by descriptive names, manages SQL output and script paths.
- **osgi/Rdbms2LiquibaseTranformationSerivce** -- OSGi `@Component` for on-demand transformation.
- **osgi/Rdbms2LiquibaseRdbmsModelTracker** -- Tracks `RdbmsModel` OSGi services.

ETL scripts: `rdbmsToLiquibase.etl` (full), `rdbmsIncrementalToLiquibase.etl` (incremental) with modules `table.etl`, `field.etl`, `incremental.etl`, `dbCheckup.etl`, `dbBackup.etl`, `dbDropBackup.etl`, `beforeIncremental.etl`, `afterIncremental.etl`, `dataUpdateBeforeIncremental.etl`, `dataUpdateAfterIncremental.etl`. EGL scripts: `main.egl`, `BackupTableData.egl`.

## Requirements

### Requirement: Execute Full RDBMS to Liquibase Transformation
The system SHALL transform an RdbmsModel into a LiquibaseModel by executing `rdbmsToLiquibase.etl` with the `dialect` passed as an ETL program parameter.

#### Scenario: Full migration for PostgreSQL
- **GIVEN** a valid `RdbmsModel` and an empty `LiquibaseModel` with `dialect` set to `"postgresql"`
- **WHEN** `Rdbms2Liquibase.executeRdbms2LiquibaseTransformation(parameter)` is invoked
- **THEN** the `LiquibaseModel` resource is populated with a complete Liquibase changelog

#### Scenario: Full migration for Oracle
- **GIVEN** `dialect` is `"oracle"`
- **WHEN** the full transformation is executed
- **THEN** the Liquibase changelog reflects Oracle-specific DDL conventions

### Requirement: Execute Incremental RDBMS to Liquibase Transformation
The system SHALL transform a differential RdbmsModel into multiple LiquibaseModel phases by executing `rdbmsIncrementalToLiquibase.etl` and `main.egl`, producing changelogs for: database checkup, backup, before-incremental, data update before, incremental, data update after, after-incremental, and drop-backup.

#### Scenario: Incremental migration produces all phases
- **GIVEN** a valid incremental `RdbmsModel` and eight empty `LiquibaseModel` instances
- **WHEN** `Rdbms2LiquibaseIncremental.executeRdbms2LiquibaseIncrementalTransformation(parameter)` is invoked
- **THEN** each of the eight `LiquibaseModel` resources is populated with phase-specific changelog entries

#### Scenario: Incremental migration with SQL output
- **GIVEN** `sqlOutput` is set to a valid directory path
- **WHEN** the incremental transformation completes
- **THEN** backup data SQL files from `backupDataSqlFiles` are written to the specified directory

### Requirement: Dialect-Specific Table Name Constraints for Incremental Migration
The system SHALL apply dialect-specific `tableNameMaxSize` defaults in incremental mode: 62 for non-Oracle dialects, 30 for Oracle, with explicit override support.

#### Scenario: Oracle table name limit in incremental mode
- **GIVEN** `dialect` is `"oracle"` and no custom `tableNameMaxSize` is set
- **WHEN** incremental transformation is executed
- **THEN** `tableNameMaxSize` is set to `30`

### Requirement: Configurable Backup Prefix
The system SHALL support a configurable `backupPrefix` (default `"BACKUP"`) that is passed to the ETL and EGL scripts as `backupTableNamePrefix` and `backupChangeSetNamePrefix` (lowercased) parameters.

#### Scenario: Default backup prefix
- **GIVEN** no custom `backupPrefix` is specified
- **WHEN** incremental transformation runs
- **THEN** `backupTableNamePrefix` is `"BACKUP"` and `backupChangeSetNamePrefix` is `"backup"`

### Requirement: Review Script Resolution
The system SHALL inject a `ReviewResolver` into the incremental transformation execution context. If no `ReviewResolver` is provided, a `FileSystemReviewResolver` is created from the `sqlScriptPath` parameter.

#### Scenario: FileSystemReviewResolver fallback
- **GIVEN** no `ReviewResolver` is provided but `sqlScriptPath` is set
- **WHEN** incremental transformation runs
- **THEN** a `FileSystemReviewResolver` backed by the `sqlScriptPath` directory is used

#### Scenario: Missing both ReviewResolver and sqlScriptPath
- **GIVEN** neither `ReviewResolver` nor `sqlScriptPath` is set
- **WHEN** incremental transformation is attempted
- **THEN** an `IllegalArgumentException` is thrown with message `"One of ReviewResolver or scriptPath have to be set"`

### Requirement: Report Missing Review Scripts
The system SHALL collect missing review script names in `Rdbms2LiquibaseIncrementalResult.missingReviewScripts` during the incremental transformation, allowing callers to identify scripts that need manual creation.

#### Scenario: Missing review scripts reported
- **GIVEN** the incremental RDBMS model references review scripts that do not exist
- **WHEN** incremental transformation completes
- **THEN** `result.getMissingReviewScripts()` contains entries for each missing script

### Requirement: Workflow Integration via Rdbms2LiquibaseWork (Full)
The system SHALL provide `Rdbms2LiquibaseWork` extending `AbstractTransformationWork` that retrieves `RdbmsModel` from context by key `"rdbms:<dialect>"`, registers RDBMS metamodel extensions, creates a `LiquibaseModel`, and stores it under key `"liquibase:<dialect>"`.

#### Scenario: RdbmsModel missing for dialect
- **GIVEN** a `TransformationContext` without an `RdbmsModel` for dialect `"postgresql"`
- **WHEN** `Rdbms2LiquibaseWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"RDBMS Model of postgresql dialect does not found in transformation context"`

#### Scenario: LiquibaseModel auto-creation
- **GIVEN** an `RdbmsModel` keyed by `"rdbms:hsqldb"` with name `"MyApp"` and version `"1.0"`
- **WHEN** `Rdbms2LiquibaseWork.execute()` completes
- **THEN** a `LiquibaseModel` with name `"MyApp"` and version `"1.0"` is stored under key `"liquibase:hsqldb"`

### Requirement: Workflow Integration via Rdbms2LiquibaseIncrementalWork
The system SHALL provide `Rdbms2LiquibaseIncrementalWork` extending `AbstractTransformationWork` that retrieves the incremental `RdbmsModel` from context by key `"rdbms-incremental:<dialect>"`, creates or retrieves eight `LiquibaseModel` instances, and manages temporary directories for SQL output and script paths.

#### Scenario: Incremental RdbmsModel missing from context
- **GIVEN** a `TransformationContext` without a `"rdbms-incremental:postgresql"` entry
- **WHEN** `Rdbms2LiquibaseIncrementalWork.execute()` is called
- **THEN** a `RuntimeException` is thrown stating `"Required rdbms-incremental:postgresql cannot be found in transformation context"`

#### Scenario: Automatic temporary directory creation
- **GIVEN** no `sqlOutput` or `sqlScriptPath` is set in the context
- **WHEN** `Rdbms2LiquibaseIncrementalWork.execute()` runs
- **THEN** temporary directories are created and their paths stored in the context under `"liquibase-incremental:<dialect>-sqlOutput"` and `"liquibase-incremental:<dialect>-sqlScriptPath"`

### Requirement: ReviewResolver Interface Contract
The system SHALL define the `ReviewResolver` interface with two methods: `boolean exists(String name)` to check if a review script exists and `String resolve(String name)` to return the script content.

#### Scenario: Review script exists
- **GIVEN** a `ReviewResolver` implementation and a review script named `"migration_v2.sql"` that exists
- **WHEN** `exists("migration_v2.sql")` is called
- **THEN** it returns `true`

#### Scenario: Resolve review script content
- **GIVEN** a `ReviewResolver` and an existing script `"migration_v2.sql"`
- **WHEN** `resolve("migration_v2.sql")` is called
- **THEN** the SQL content of the script is returned as a `String`
