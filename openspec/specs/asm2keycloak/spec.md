# ASM to Keycloak Transformation Specification

## Purpose
Transforms an Architecture Specific Model (AsmModel) into a Keycloak Model (KeycloakModel) using Epsilon ETL scripts, generating Keycloak realm and client configurations from ASM actor and security definitions.

## Architecture
The module follows the standard Tatami transformation pattern:

- **Asm2Keycloak** -- Static entry point executing `asmToKeycloak.etl`. Accepts `Asm2KeycloakParameter` with required `AsmModel`, required `KeycloakModel`, and execution flags (`createTrace`, `parallel`, `useCache`). Injects `AsmUtils` into the Epsilon execution context. Passes `modelVersion` as an ETL program parameter.
- **Asm2KeycloakTransformationTrace** -- Implements `TransformationTrace` for ASM-to-Keycloak mappings. Provides serialization/deserialization via `save()` and `fromModelsAndTrace()`.
- **Asm2KeycloakWork** -- Extends `AbstractTransformationWork`. Retrieves `AsmModel` from context, creates or reuses a `KeycloakModel` (inheriting name and version from ASM), executes the transformation, and stores the trace.
- **osgi/Asm2KeycloakTransformationSerivce** -- OSGi `@Component` for on-demand transformation.
- **osgi/Asm2KeycloakTransformationAsmModelTracker** -- Tracks `AsmModel` OSGi services.

ETL scripts: `src/main/epsilon/transformations/asmToKeycloak.etl` with modules `keycloak/modules/realm.etl` and `keycloak/modules/client.etl`.

## Requirements

### Requirement: Execute ASM to Keycloak Transformation
The system SHALL transform actor and security elements from an AsmModel into Keycloak realm and client configurations by executing `asmToKeycloak.etl` with `AsmUtils` injected and `modelVersion` passed as a parameter.

#### Scenario: Successful Keycloak transformation
- **GIVEN** a valid `AsmModel` with actor definitions and an empty `KeycloakModel`
- **WHEN** `Asm2Keycloak.executeAsm2KeycloakTransformation(parameter)` is invoked
- **THEN** the `KeycloakModel` resource is populated with realm and client elements

### Requirement: Parameterize Keycloak Transformation
The system SHALL support configuring parallel execution (`parallel=true`), caching (`useCache=true`), and trace creation (`createTrace=false`) via `Asm2KeycloakParameter` builder defaults.

#### Scenario: Parallel execution with caching
- **GIVEN** an `Asm2KeycloakParameter` with `parallel=true` and `useCache=true`
- **WHEN** the transformation is executed
- **THEN** the Epsilon model contexts and ETL execution context are configured with parallel and cache enabled

### Requirement: Produce Keycloak Transformation Trace
The system SHALL, when `createTrace` is `true`, extract the ETL trace and resolve it via `resolveAsm2KeycloakTrace()` into a mapping from ASM `EObject` elements to Keycloak `EObject` elements.

#### Scenario: Trace creation enabled
- **GIVEN** `createTrace` is `true`
- **WHEN** transformation completes
- **THEN** the returned `Asm2KeycloakTransformationTrace` contains non-empty source-to-target mappings

### Requirement: Serialize and Deserialize Keycloak Trace
The system SHALL support saving and loading the trace via `save(OutputStream)` and `fromModelsAndTrace(...)`, validating that `asmModel.getName()` equals `keycloakModel.getName()`.

#### Scenario: Model name mismatch
- **GIVEN** an `AsmModel` with name `"A"` and a `KeycloakModel` with name `"B"`
- **WHEN** `fromModelsAndTrace(...)` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Model name does not match"`

#### Scenario: Round-trip trace persistence
- **GIVEN** a `Asm2KeycloakTransformationTrace` with trace data
- **WHEN** `save(outputStream)` followed by `fromModelsAndTrace(name, asmModel, keycloakModel, inputStream)` is called
- **THEN** the deserialized trace contains equivalent mappings

### Requirement: Workflow Integration via Asm2KeycloakWork
The system SHALL provide `Asm2KeycloakWork` extending `AbstractTransformationWork` that retrieves `AsmModel` from `TransformationContext`, creates or reuses a `KeycloakModel` (with name and version from ASM), executes the transformation, and stores the `Asm2KeycloakTransformationTrace` in the context.

#### Scenario: AsmModel missing from context
- **GIVEN** a `TransformationContext` without an `AsmModel`
- **WHEN** `Asm2KeycloakWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"ASM Model does not found in transformation context"`

#### Scenario: KeycloakModel auto-creation
- **GIVEN** a `TransformationContext` with `AsmModel` named `"Auth"` version `"1.0"` but no `KeycloakModel`
- **WHEN** `Asm2KeycloakWork.execute()` completes
- **THEN** a new `KeycloakModel` is built with name `"Auth"` and version `"1.0"` and placed into the context

### Requirement: Calculate Keycloak Transformation Script URI
The system SHALL resolve the ETL script root from the classpath via `calculateAsm2KeycloakTransformationScriptURI()` at path `tatami/asm2keycloak/transformations/`, supporting JAR, OSGi bundle, and directory deployments.

#### Scenario: Script URI from OSGi bundle
- **GIVEN** the code source starts with `"jar:bundle:"`
- **WHEN** `calculateAsm2KeycloakTransformationScriptURI()` is called
- **THEN** the bundle prefix is stripped and the path `tatami/asm2keycloak/transformations/` is appended

### Requirement: Logger Lifecycle Management
The system SHALL close the internally created `BufferedSlf4jLogger` in a `finally` block when no external logger is provided.

#### Scenario: Internal logger cleanup
- **GIVEN** no external `Logger` is set in `Asm2KeycloakParameter`
- **WHEN** the transformation completes (success or failure)
- **THEN** the internally created logger is closed
