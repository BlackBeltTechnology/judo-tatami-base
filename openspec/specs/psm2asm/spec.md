# PSM to ASM Transformation Specification

## Purpose
Transforms a Platform-Specific Model (PsmModel) into an Architecture Specific Model (AsmModel) using Epsilon ETL scripts, producing a fully populated ASM representation of the domain along with an optional transformation trace.

## Architecture
The module is structured around three main classes and an OSGi integration layer:

- **Psm2Asm** -- Static entry point that executes the ETL transformation. Accepts a `Psm2AsmParameter` (built via Lombok `@Builder`) containing source `PsmModel`, target `AsmModel`, script URI, and execution flags (`createTrace`, `parallel`, `useCache`). Injects `AsmUtils` and `PsmUtils` into the Epsilon execution context and runs `psmToAsm.etl`.
- **Psm2AsmTransformationTrace** -- Implements `TransformationTrace`. Stores the source-to-target `EObject` mapping (`Map<EObject, List<EObject>>`). Provides serialization/deserialization of trace via `save(OutputStream)` and `fromModelsAndTrace(...)`.
- **Psm2AsmWork** -- Extends `AbstractTransformationWork` to integrate with the Tatami workflow engine. Retrieves `PsmModel` from `TransformationContext`, creates or reuses an `AsmModel`, delegates to `Psm2Asm.executePsm2AsmTransformation(...)`, and stores the trace back into the context.
- **osgi/Psm2AsmTransformationSerivce** -- OSGi `@Component` that, when a `PsmModel` is installed, runs the transformation and registers the resulting `TransformationTrace` as an OSGi service.
- **osgi/Psm2AsmTransformationPsmModelTracker** -- Extends `AbstractModelTracker<PsmModel>`. Tracks `PsmModel` OSGi services, triggers `install`/`uninstall` on the transformation service, and registers/unregisters the resulting `AsmModel` as an OSGi service.

ETL scripts reside under `src/main/epsilon/transformations/asm/` with modules: `namespace.etl`, `type.etl`, `data.etl`, `derived.etl`, `operation.etl`, `actor.etl`, `transferObject.etl`, `static.etl`.

## Requirements

### Requirement: Execute PSM to ASM Transformation
The system SHALL transform a PsmModel into an AsmModel by executing the `psmToAsm.etl` Epsilon ETL script with injected `AsmUtils` and `PsmUtils` context objects.

#### Scenario: Successful transformation with default parameters
- **GIVEN** a valid `PsmModel` and an empty `AsmModel` are provided via `Psm2AsmParameter`
- **WHEN** `Psm2Asm.executePsm2AsmTransformation(parameter)` is invoked
- **THEN** the `AsmModel` resource is populated with the transformed ASM elements and a `Psm2AsmTransformationTrace` is returned

#### Scenario: Transformation with custom script URI
- **GIVEN** a `Psm2AsmParameter` is built with a custom `scriptUri` pointing to an alternative ETL script location
- **WHEN** `executePsm2AsmTransformation` is called
- **THEN** the ETL engine resolves `psmToAsm.etl` relative to the custom script URI

### Requirement: Parameterize Transformation Execution
The system SHALL support configuring parallel execution, caching, and trace creation via `Psm2AsmParameter` builder defaults (`parallel=true`, `useCache=true`, `createTrace=false`).

#### Scenario: Parallel execution enabled
- **GIVEN** a `Psm2AsmParameter` with `parallel` set to `true`
- **WHEN** the transformation is executed
- **THEN** the Epsilon `EtlExecutionContext` and `WrappedEmfModelContext` are configured with `parallel=true`

#### Scenario: Trace creation disabled
- **GIVEN** a `Psm2AsmParameter` with `createTrace` set to `false`
- **WHEN** the transformation completes
- **THEN** the returned `Psm2AsmTransformationTrace` contains an empty trace map

### Requirement: Pass Model Metadata as ETL Parameters
The system SHALL pass `modelName`, `nsURI`, `nsPrefix`, and `extendedMetadataURI` as program parameters to the ETL execution context, derived from the `PsmModel` name.

#### Scenario: Namespace URI derivation
- **GIVEN** a `PsmModel` with name `"MyDomain"`
- **WHEN** the transformation is executed
- **THEN** the ETL parameter `nsURI` equals `"http://blackbelt.hu/judo/MyDomain"` and `nsPrefix` equals `"runtimeMyDomain"`

### Requirement: Produce Transformation Trace
The system SHALL, when `createTrace` is `true`, extract the ETL transformation trace and resolve it into a `Map<EObject, List<EObject>>` mapping PSM source elements to ASM target elements.

#### Scenario: Trace creation enabled
- **GIVEN** a `Psm2AsmParameter` with `createTrace` set to `true`
- **WHEN** the transformation completes
- **THEN** the returned `Psm2AsmTransformationTrace.getTransformationTrace()` contains non-empty mappings from PSM `EObject` instances to their corresponding ASM `EObject` instances

### Requirement: Serialize and Deserialize Trace
The system SHALL support saving the transformation trace to an `OutputStream` via `Psm2AsmTransformationTrace.save(OutputStream)` and loading it back via `Psm2AsmTransformationTrace.fromModelsAndTrace(...)`.

#### Scenario: Round-trip trace serialization
- **GIVEN** a `Psm2AsmTransformationTrace` with a non-empty trace map
- **WHEN** `save(outputStream)` is called followed by `fromModelsAndTrace(modelName, psmModel, asmModel, inputStream)`
- **THEN** the deserialized trace contains equivalent source-to-target mappings

#### Scenario: Model name mismatch on load
- **GIVEN** a `PsmModel` with name `"A"` and an `AsmModel` with name `"B"`
- **WHEN** `fromModelsAndTrace` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Model name does not match"`

### Requirement: Calculate Transformation Script URI
The system SHALL resolve the ETL script root URI from the classpath via `Psm2Asm.calculatePsm2AsmTransformationScriptURI()`, handling JAR, OSGi bundle, and directory deployments.

#### Scenario: Script URI from JAR deployment
- **GIVEN** the code source location ends with `.jar`
- **WHEN** `calculatePsm2AsmTransformationScriptURI()` is called
- **THEN** the returned URI has the format `jar:<jarUri>!/tatami/psm2asm/transformations/asm/`

### Requirement: Workflow Integration via Psm2AsmWork
The system SHALL provide `Psm2AsmWork` extending `AbstractTransformationWork` that retrieves `PsmModel` from `TransformationContext`, creates or reuses `AsmModel`, executes the transformation, and stores both the `AsmModel` and `Psm2AsmTransformationTrace` in the context.

#### Scenario: PsmModel missing from context
- **GIVEN** a `TransformationContext` without a `PsmModel`
- **WHEN** `Psm2AsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"PSM Model does not found in transformation context"`

#### Scenario: AsmModel auto-creation
- **GIVEN** a `TransformationContext` with a `PsmModel` but no `AsmModel`
- **WHEN** `Psm2AsmWork.execute()` completes
- **THEN** a new `AsmModel` is built via `AsmModel.buildAsmModel()` and placed into the `TransformationContext`

### Requirement: OSGi Service Registration
The system SHALL register `Psm2AsmTransformationSerivce` as an OSGi `@Component` that, upon `install(PsmModel)`, executes the transformation and registers the resulting `TransformationTrace` as an OSGi service.

#### Scenario: Install PsmModel via OSGi
- **GIVEN** the `Psm2AsmTransformationSerivce` component is activated with a valid `BundleContext`
- **WHEN** `install(psmModel)` is called
- **THEN** the resulting `AsmModel` is returned and a `TransformationTrace` service registration is stored

#### Scenario: Uninstall PsmModel via OSGi
- **GIVEN** a previously installed `PsmModel`
- **WHEN** `uninstall(psmModel)` is called
- **THEN** the associated `TransformationTrace` service registration is unregistered

### Requirement: OSGi Model Tracking
The system SHALL provide `Psm2AsmTransformationPsmModelTracker` that extends `AbstractModelTracker<PsmModel>`, automatically detecting new `PsmModel` OSGi services, triggering the transformation, and registering the resulting `AsmModel` as an OSGi service.

#### Scenario: Duplicate model installation
- **GIVEN** a `PsmModel` with name `"X"` is already installed
- **WHEN** `install(psmModel)` is called again with the same name
- **THEN** the tracker logs an error `"Model already loaded: X"` and does not re-register

### Requirement: Logger Lifecycle Management
The system SHALL close the internally created `BufferedSlf4jLogger` in a `finally` block when no external logger is provided, ensuring no resource leaks.

#### Scenario: Internal logger cleanup on success
- **GIVEN** no external `Logger` is set in `Psm2AsmParameter`
- **WHEN** the transformation completes successfully
- **THEN** the internally created `BufferedSlf4jLogger` is closed

#### Scenario: Internal logger cleanup on failure
- **GIVEN** no external `Logger` is set and the transformation throws an exception
- **WHEN** the exception propagates
- **THEN** the internally created `BufferedSlf4jLogger` is still closed in the finally block
