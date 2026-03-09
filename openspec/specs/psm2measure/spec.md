# PSM to Measure Transformation Specification

## Purpose
Transforms measure and unit definitions from a Platform-Specific Model (PsmModel) into a dedicated Measure Model (MeasureModel) using Epsilon ETL scripts, with optional transformation traceability.

## Architecture
The module mirrors the psm2asm structure with measure-specific models:

- **Psm2Measure** -- Static entry point executing `psmToMeasure.etl`. Accepts `Psm2MeasureParameter` containing source `PsmModel`, target `MeasureModel`, and execution flags. Injects `PsmUtils` and `MeasureUtils` into the Epsilon execution context.
- **Psm2MeasureTransformationTrace** -- Implements `TransformationTrace`. Stores the `Map<EObject, List<EObject>>` mapping from PSM measure elements to MeasureModel elements. Supports serialization via `save()` and deserialization via `fromModelsAndTrace()`.
- **Psm2MeasureWork** -- Extends `AbstractTransformationWork`. Retrieves `PsmModel` from `TransformationContext`, creates or reuses a `MeasureModel` (inheriting name and version from the PSM model), and stores the trace.
- **osgi/Psm2MeasureTransformationService** -- OSGi `@Component` that runs the transformation on `install(PsmModel)` and registers `TransformationTrace` as an OSGi service.
- **osgi/Psm2MeasureTransformationPsmModelTracker** -- Tracks `PsmModel` OSGi services and triggers transformation/registration.

ETL scripts: `src/main/epsilon/transformations/measure/psmToMeasure.etl` with modules `measure.etl` and `unit.etl`.

## Requirements

### Requirement: Execute PSM to Measure Transformation
The system SHALL transform measure-related elements from a PsmModel into a MeasureModel by executing the `psmToMeasure.etl` Epsilon ETL script with injected `PsmUtils` and `MeasureUtils`.

#### Scenario: Successful measure transformation
- **GIVEN** a valid `PsmModel` containing measure and unit definitions and an empty `MeasureModel`
- **WHEN** `Psm2Measure.executePsm2MeasureTransformation(parameter)` is invoked
- **THEN** the `MeasureModel` resource is populated with transformed measure and unit elements

### Requirement: Parameterize Measure Transformation
The system SHALL support configuring parallel execution (`parallel=true`), caching (`useCache=true`), and trace creation (`createTrace=false`) via `Psm2MeasureParameter` builder defaults.

#### Scenario: Cache disabled
- **GIVEN** a `Psm2MeasureParameter` with `useCache` set to `false`
- **WHEN** the transformation is executed
- **THEN** the Epsilon model contexts are configured without caching

### Requirement: Produce Measure Transformation Trace
The system SHALL, when `createTrace` is `true`, extract the ETL trace and resolve it into a `Map<EObject, List<EObject>>` mapping PSM source elements to MeasureModel target elements using `resolvePsm2MeasureTrace()`.

#### Scenario: Trace creation for measures
- **GIVEN** `createTrace` is `true` in the parameter
- **WHEN** transformation completes
- **THEN** `Psm2MeasureTransformationTrace.getTransformationTrace()` contains mappings from PSM measure EObjects to their MeasureModel counterparts

### Requirement: Serialize and Deserialize Measure Trace
The system SHALL support round-trip persistence of the measure trace via `save(OutputStream)` and `fromModelsAndTrace(...)`, with model name consistency validation.

#### Scenario: Model name mismatch on trace load
- **GIVEN** a `PsmModel` with name `"A"` and a `MeasureModel` with name `"B"`
- **WHEN** `fromModelsAndTrace(...)` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Model name does not match"`

### Requirement: Workflow Integration via Psm2MeasureWork
The system SHALL provide `Psm2MeasureWork` extending `AbstractTransformationWork` that retrieves `PsmModel` from `TransformationContext`, creates a `MeasureModel` with the same name and version as the PSM model if not already present, and stores both the model and trace in the context.

#### Scenario: PsmModel missing from context
- **GIVEN** a `TransformationContext` without a `PsmModel`
- **WHEN** `Psm2MeasureWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"PSM Model does not found in transformation context"`

#### Scenario: MeasureModel auto-creation with inherited metadata
- **GIVEN** a `PsmModel` with name `"Sales"` and version `"1.0"`
- **WHEN** `Psm2MeasureWork.execute()` runs and no `MeasureModel` exists in context
- **THEN** a new `MeasureModel` is built with name `"Sales"` and version `"1.0"`

### Requirement: OSGi Service Registration for Measure Transformation
The system SHALL register `Psm2MeasureTransformationService` as an OSGi `@Component` that creates a `MeasureModel` with URI `"measure:<name>.measure"`, runs the transformation, and registers the `TransformationTrace`.

#### Scenario: Install and produce MeasureModel via OSGi
- **GIVEN** the service is activated with a valid `BundleContext`
- **WHEN** `install(psmModel)` is called
- **THEN** a `MeasureModel` is returned and a `TransformationTrace` service is registered

#### Scenario: Uninstall non-existent model
- **GIVEN** a `PsmModel` that was never installed
- **WHEN** `uninstall(psmModel)` is called
- **THEN** an error is logged stating the PSM model is not installed

### Requirement: Calculate Measure Transformation Script URI
The system SHALL resolve the ETL script root from classpath via `calculatePsm2MeasureTransformationScriptURI()`, supporting JAR, OSGi bundle, and directory deployments at path `tatami/psm2measure/transformations/measure/`.

#### Scenario: Script URI from directory deployment
- **GIVEN** the code source location is a directory path
- **WHEN** `calculatePsm2MeasureTransformationScriptURI()` is called
- **THEN** the returned URI appends `/tatami/psm2measure/transformations/measure/` to the directory path
