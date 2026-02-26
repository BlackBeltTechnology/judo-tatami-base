# ASM to Expression Transformation Specification

## Purpose
Extracts JQL (JUDO Query Language) expressions from an Application-Specific Model (AsmModel) and populates an ExpressionModel using the `AsmJqlExtractor`, with optional MeasureModel integration for unit-aware expressions.

## Architecture
The module is structured around:

- **Asm2Expression** -- Static entry point. Accepts `Asm2ExpressionParameter` containing a required `AsmModel`, a required `ExpressionModel`, an optional `MeasureModel`, and an `Asm2ExpressionConfiguration`. Creates an `AsmJqlExtractor` with the ASM, measure, and expression resource sets, then calls `jqlExtractor.extractExpressions()`.
- **Asm2ExpressionConfiguration** -- Configuration POJO with a single boolean property `resolveOnlyCurrentLambdaScope` (default `true`), which controls whether lambda scope resolution is restricted to the current scope.
- **Asm2ExpressionWork** -- Extends `AbstractTransformationWork`. Retrieves `AsmModel` and optional `MeasureModel` from `TransformationContext`, creates or reuses an `ExpressionModel` (inheriting name and version from ASM), and delegates to `Asm2Expression.executeAsm2Expression(...)`.
- **osgi/Asm2ExpressionTranformationSerivce** -- OSGi `@Component` for service-based execution.
- **osgi/Asm2ExpressionTransformationModelServiceTracker** -- Tracks `AsmModel` OSGi services and triggers expression extraction.

Note: Unlike other transformation modules, this module does NOT use Epsilon ETL scripts. It uses the `AsmJqlExtractor` from the `judo-meta-expression` library directly.

## Requirements

### Requirement: Extract JQL Expressions from ASM Model
The system SHALL extract JQL expressions from the AsmModel and populate the ExpressionModel by invoking `AsmJqlExtractor.extractExpressions()` with the ASM, measure, and expression resource sets.

#### Scenario: Successful expression extraction
- **GIVEN** a valid `AsmModel` containing JQL annotations, an empty `ExpressionModel`, and an optional `MeasureModel`
- **WHEN** `Asm2Expression.executeAsm2Expression(parameter)` is invoked
- **THEN** the `ExpressionModel` resource set is populated with the extracted expression elements

#### Scenario: Expression extraction without MeasureModel
- **GIVEN** a valid `AsmModel` and `ExpressionModel` but `measureModel` is `null`
- **WHEN** `executeAsm2Expression` is called
- **THEN** a fresh measure resource set is created via `MeasureModelResourceSupport.createMeasureResourceSet()` and used for extraction

### Requirement: Configure Lambda Scope Resolution
The system SHALL pass `Asm2ExpressionConfiguration.resolveOnlyCurrentLambdaScope` to the `JqlExpressionBuilderConfig`, controlling whether expression resolution is constrained to the current lambda scope.

#### Scenario: Default lambda scope configuration
- **GIVEN** an `Asm2ExpressionConfiguration` with default settings
- **WHEN** the expression extraction runs
- **THEN** `JqlExpressionBuilderConfig.resolveOnlyCurrentLambdaScope` is set to `true`

#### Scenario: Custom lambda scope configuration
- **GIVEN** an `Asm2ExpressionConfiguration` with `resolveOnlyCurrentLambdaScope` set to `false`
- **WHEN** the expression extraction runs
- **THEN** `JqlExpressionBuilderConfig.resolveOnlyCurrentLambdaScope` is set to `false`

### Requirement: Workflow Integration via Asm2ExpressionWork
The system SHALL provide `Asm2ExpressionWork` extending `AbstractTransformationWork` that retrieves `AsmModel` (required), `MeasureModel` (optional), and `Asm2ExpressionConfiguration` (optional, defaults to new instance) from `TransformationContext`, creates or reuses an `ExpressionModel`, and stores it back in the context.

#### Scenario: AsmModel missing from context
- **GIVEN** a `TransformationContext` without an `AsmModel`
- **WHEN** `Asm2ExpressionWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"ASM Model does not found in transformation context"`

#### Scenario: ExpressionModel auto-creation
- **GIVEN** a `TransformationContext` with an `AsmModel` named `"Orders"` version `"2.0"` but no `ExpressionModel`
- **WHEN** `Asm2ExpressionWork.execute()` completes
- **THEN** a new `ExpressionModel` is built with name `"Orders"` and version `"2.0"` and stored in the context

#### Scenario: Configuration from context
- **GIVEN** a `TransformationContext` containing an `Asm2ExpressionConfiguration` with `resolveOnlyCurrentLambdaScope=false`
- **WHEN** `Asm2ExpressionWork.execute()` runs
- **THEN** the custom configuration is used instead of the default

### Requirement: OSGi Service Integration
The system SHALL provide `Asm2ExpressionTranformationSerivce` and `Asm2ExpressionTransformationModelServiceTracker` as OSGi components to automatically trigger expression extraction when an `AsmModel` service appears in the OSGi registry.

#### Scenario: AsmModel detected in OSGi registry
- **GIVEN** the tracker is activated and listening for `AsmModel` services
- **WHEN** a new `AsmModel` service is registered
- **THEN** expression extraction is triggered automatically
