# PSM Validation Specification

## Purpose
Validates a Platform-Specific Model (PsmModel) against structural and semantic constraints defined in Epsilon EVL (Epsilon Validation Language) scripts, ensuring the PSM model conforms to the JUDO metamodel rules before downstream transformations.

## Architecture
The module contains a single class:

- **PsmValidationWork** -- Extends `AbstractTransformationWork`. Retrieves `PsmModel` from `TransformationContext` and delegates validation to `PsmEpsilonValidator.validatePsm(logger, psmModel, scriptURI)` using the script URI calculated by `PsmEpsilonValidator.calculatePsmValidationScriptURI()`. Uses `StringBuilderLogger` for collecting validation output.

The actual validation logic and EVL scripts are defined in the `hu.blackbelt.judo.meta.psm` dependency (`hu.blackbelt.judo.meta.psm.model`). This module serves as the Tatami workflow integration point.

## Requirements

### Requirement: Validate PSM Model via Epsilon EVL
The system SHALL validate a PsmModel by executing Epsilon EVL validation scripts via `PsmEpsilonValidator.validatePsm()`, reporting all constraint violations.

#### Scenario: Valid PSM model
- **GIVEN** a structurally and semantically correct `PsmModel` in the `TransformationContext`
- **WHEN** `PsmValidationWork.execute()` is called
- **THEN** validation completes without throwing an exception

#### Scenario: Invalid PSM model
- **GIVEN** a `PsmModel` that violates one or more EVL constraints
- **WHEN** `PsmValidationWork.execute()` is called
- **THEN** the validation throws an exception with details of the violated constraints

### Requirement: PsmModel Presence in TransformationContext
The system SHALL require a `PsmModel` to be present in the `TransformationContext` before validation can proceed.

#### Scenario: PsmModel missing from context
- **GIVEN** a `TransformationContext` without a `PsmModel`
- **WHEN** `PsmValidationWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"PSM Model does not found in transformation context"`

### Requirement: Validation Script URI Resolution
The system SHALL resolve the validation script URI via `PsmEpsilonValidator.calculatePsmValidationScriptURI()`, supporting JAR, OSGi bundle, and directory deployments.

#### Scenario: Script URI resolution
- **GIVEN** the PSM validation module is deployed as a JAR
- **WHEN** `calculatePsmValidationScriptURI()` is called
- **THEN** the returned URI points to the packaged EVL scripts within the JAR

### Requirement: Logging Validation Output
The system SHALL capture all validation output using a `StringBuilderLogger` that is properly closed after validation completes.

#### Scenario: Logger lifecycle
- **GIVEN** a `PsmValidationWork` is executed
- **WHEN** validation completes (success or failure)
- **THEN** the `StringBuilderLogger` is closed via try-with-resources
