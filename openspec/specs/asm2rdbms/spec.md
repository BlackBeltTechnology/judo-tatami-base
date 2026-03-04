# ASM to RDBMS Transformation Specification

## Purpose
Transforms an Application-Specific Model (AsmModel) into a Relational Database Model (RdbmsModel) using Epsilon ETL scripts, supporting multiple database dialects (HSQLDB, PostgreSQL, Oracle) with configurable naming conventions, size constraints, and dialect-specific mapping models.

## Architecture
The module contains:

- **Asm2Rdbms** -- Static entry point executing `asmToRdbms.etl`. Accepts `Asm2RdbmsParameter` with `AsmModel`, `RdbmsModel`, `dialect` (required), and extensive naming configuration (`tablePrefix`, `columnPrefix`, `foreignKeyPrefix`, `inverseForeignKeyPrefix`, `junctionTablePrefix`, `nameSize`, `shortNameSize`, `tableNameMaxSize`, `columnNameMaxSize`, `createSimpleName`). Loads dialect-specific mapping models (`mapping-<dialect>-rdbms.model`) into the RDBMS resource. Injects `AbbreviateUtils`, `MD5Utils`, `AsmUtils`, `RdbmsUtils`, and all size/prefix parameters into the Epsilon context.
- **Asm2RdbmsTransformationTrace** -- Implements `TransformationTrace` for ASM-to-RDBMS mappings. Supports serialization/deserialization of trace.
- **Asm2RdbmsWork** -- Extends `AbstractTransformationWork`. Registers RDBMS metamodel extensions (`RdbmsNameMapping`, `RdbmsDataTypes`, `RdbmsTableMappingRules`), stores the model keyed by dialect (`"rdbms:<dialect>"`), and stores trace keyed by `"asm2rdbmstrace:<dialect>"`.
- **AbbreviateUtils** -- Utility that abbreviates identifier strings to a maximum length by removing vowels and consonants in order of English frequency, keeping the string human-readable.
- **ExcelMappingModels2Rdbms** -- Loads Excel-based type mapping, rule mapping, and name mapping models into an RDBMS model via ETL scripts (`excelToNameMapping.etl`, `excelToTypeMapping.etl`, `excelToRules.etl`).
- **osgi/Asm2RdbmsTransformationSerivce** -- OSGi `@Component` for on-demand transformation.
- **osgi/Asm2RdbmsTransformationAsmModelTracker** -- Tracks `AsmModel` OSGi services.

ETL scripts: `src/main/epsilon/transformations/asmToRdbms.etl` with modules `package.etl`, `class.etl`, `attribute.etl`, `reference.etl`, plus Excel mapping ETLs.

## Requirements

### Requirement: Execute ASM to RDBMS Transformation
The system SHALL transform an AsmModel into an RdbmsModel by executing `asmToRdbms.etl` with the specified `dialect`, `modelVersion`, and `extendedMetadataURI` as ETL program parameters.

#### Scenario: Successful transformation for PostgreSQL dialect
- **GIVEN** a valid `AsmModel`, an empty `RdbmsModel`, and `dialect` set to `"postgresql"`
- **WHEN** `Asm2Rdbms.executeAsm2RdbmsTransformation(parameter)` is invoked
- **THEN** the `RdbmsModel` resource is populated with PostgreSQL-specific RDBMS elements

### Requirement: Dialect-Specific Mapping Model Loading
The system SHALL load the dialect-specific mapping model file (`mapping-<dialect>-rdbms.model`) from the `excelModelUri` location and merge its contents into the target `RdbmsModel` resource before executing the transformation.

#### Scenario: HSQLDB mapping model loaded
- **GIVEN** `dialect` is `"hsqldb"` and the model URI resolves to a valid resource
- **WHEN** transformation is executed
- **THEN** the contents of `mapping-hsqldb-rdbms.model` are added to the `RdbmsModel` resource

#### Scenario: Oracle mapping model loaded
- **GIVEN** `dialect` is `"oracle"`
- **WHEN** transformation is executed
- **THEN** the contents of `mapping-oracle-rdbms.model` are loaded and merged into the `RdbmsModel`

### Requirement: Dialect-Specific Name Size Defaults
The system SHALL apply dialect-specific defaults for `shortNameSize`, `nameSize`, `tableNameMaxSize`, and `columnNameMaxSize`. For Oracle: `shortNameSize=6`, `nameSize=28`, `tableNameMaxSize=30`, `columnNameMaxSize=30`. For other dialects: `shortNameSize=16`, `nameSize=60`, `tableNameMaxSize=62`, `columnNameMaxSize=58`.

#### Scenario: Oracle name size constraints
- **GIVEN** `dialect` is `"oracle"` and no custom size overrides are set (all `-1`)
- **WHEN** transformation is executed
- **THEN** the Epsilon context receives `shortNameSize=6`, `nameSize=28`, `tableNameMaxSize=30`, `columnNameMaxSize=30`

#### Scenario: Custom name size overrides
- **GIVEN** `dialect` is `"postgresql"` and `tableNameMaxSize` is set to `100`
- **WHEN** transformation is executed
- **THEN** the Epsilon context receives `tableNameMaxSize=100` (overriding the default of 62)

### Requirement: Configurable Table and Column Naming Prefixes
The system SHALL inject configurable naming prefixes into the Epsilon execution context: `tablePrefix` (default `"T_"`), `columnPrefix` (default `"C_"`), `foreignKeyPrefix` (default `"FK_"`), `inverseForeignKeyPrefix` (default `"FK_INV_"`), `junctionTablePrefix` (default `"J_"`).

#### Scenario: Default naming prefixes
- **GIVEN** an `Asm2RdbmsParameter` with default prefix values
- **WHEN** transformation is executed
- **THEN** generated table names start with `"T_"` and column names start with `"C_"`

#### Scenario: Custom naming prefixes
- **GIVEN** `tablePrefix` set to `"TBL_"` and `columnPrefix` set to `"COL_"`
- **WHEN** transformation is executed
- **THEN** the Epsilon context receives the custom prefix values for use in name generation

### Requirement: Identifier Abbreviation
The system SHALL provide `AbbreviateUtils.abbreviate(text, maxLength, separator)` that shortens identifiers by progressively removing vowels then consonants in English frequency order to fit within `maxLength`.

#### Scenario: Abbreviate long identifier
- **GIVEN** a text string longer than `maxLength`
- **WHEN** `AbbreviateUtils.abbreviate(text, maxLength, separator)` is called
- **THEN** the returned string length is at most `maxLength` characters

#### Scenario: Abbreviation with too short max length
- **GIVEN** `maxLength` is `0`
- **WHEN** `abbreviate` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Too short maxLength"`

### Requirement: MD5 Hashing for Name Uniqueness
The system SHALL provide `MD5Utils.md5(string)` that returns an MD5 hash of the input string, used to ensure unique RDBMS identifier generation for collision avoidance.

#### Scenario: MD5 hash generation
- **GIVEN** a string input `"myTable"`
- **WHEN** `MD5Utils.md5("myTable")` is called
- **THEN** a deterministic hexadecimal MD5 hash string is returned

### Requirement: Excel Mapping Model Injection
The system SHALL support loading RDBMS type mappings, table mapping rules, and name mappings from Excel files via `ExcelMappingModels2Rdbms.injectExcelMappings(...)`, executing `excelToNameMapping.etl`, `excelToTypeMapping.etl`, and `excelToRules.etl` in sequence.

#### Scenario: Excel mapping injection for PostgreSQL
- **GIVEN** an empty `RdbmsModel` and dialect `"postgresql"`
- **WHEN** `ExcelMappingModels2Rdbms.injectExcelMappings(...)` is called
- **THEN** the RDBMS model is populated with type mappings from `RDBMS_Data_Types_Postgresql.xlsx`, rules from `RDBMS_Table_Mapping_Rules.xlsx`, and name mappings from `RDBMS_Sql_Name_Mapping.xlsx`

### Requirement: Workflow Integration via Asm2RdbmsWork
The system SHALL provide `Asm2RdbmsWork` extending `AbstractTransformationWork` that registers RDBMS metamodel extensions, retrieves `AsmModel` from context, stores the `RdbmsModel` keyed by `"rdbms:<dialect>"`, and stores the trace keyed by `"asm2rdbmstrace:<dialect>"`.

#### Scenario: AsmModel missing from context
- **GIVEN** a `TransformationContext` without an `AsmModel`
- **WHEN** `Asm2RdbmsWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"ASM Model does not found in transformation context"`

#### Scenario: Dialect-keyed model storage
- **GIVEN** dialect is `"hsqldb"`
- **WHEN** `Asm2RdbmsWork.execute()` completes
- **THEN** the `RdbmsModel` is stored in `TransformationContext` under key `"rdbms:hsqldb"` and the trace under `"asm2rdbmstrace:hsqldb"`

### Requirement: Produce ASM to RDBMS Transformation Trace
The system SHALL, when `createTrace` is `true`, extract the ETL trace and resolve it via `resolveAsm2RdbmsTrace()` into a mapping from ASM `EObject` elements to RDBMS `EObject` elements.

#### Scenario: Trace with createTrace enabled
- **GIVEN** `createTrace` is `true`
- **WHEN** the transformation completes
- **THEN** the returned `Asm2RdbmsTransformationTrace` contains non-empty source-to-target mappings

### Requirement: Serialize and Deserialize RDBMS Trace
The system SHALL support saving and loading the ASM-to-RDBMS trace via `save(OutputStream)` and `fromModelsAndTrace(...)`, validating model name consistency.

#### Scenario: Model name mismatch on trace load
- **GIVEN** an `AsmModel` with name `"A"` and an `RdbmsModel` with name `"B"`
- **WHEN** `fromModelsAndTrace(...)` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Model name does not match"`
