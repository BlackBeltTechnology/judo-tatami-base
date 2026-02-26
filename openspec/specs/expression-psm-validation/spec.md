# Expression Validation on PSM Specification

## Purpose
Validates an ExpressionModel against a PsmModel to ensure that all JQL expressions are semantically valid in the context of the platform-specific model, verifying type references, navigation paths, and structural consistency at the PSM level.

## Architecture
The module contains a single class:

- **ExpressionValidationOnPsmWork** -- Extends `AbstractTransformationWork`. Retrieves two required models from `TransformationContext`: `ExpressionModel` and `PsmModel`. Delegates validation to `ExpressionValidatorOnPsm.validateExpressionOnPsm(logger, psmModel, expressionModel)`. Uses `StringBuilderLogger` for collecting validation output.

The actual validation logic is defined in the `hu.blackbelt.judo.meta.expression.adapters.psm` dependency (`ExpressionValidatorOnPsm`). This module serves as the Tatami workflow integration point. Note that unlike the ASM variant, this validator does not require a `MeasureModel` -- measure validation is handled at the PSM level internally.

## Requirements

### Requirement: Validate Expressions Against PSM Model
The system SHALL validate an ExpressionModel against a PsmModel by invoking `ExpressionValidatorOnPsm.validateExpressionOnPsm()`, checking type references and navigation path validity against PSM structures.

#### Scenario: Valid expressions against PSM
- **GIVEN** a valid `ExpressionModel` and `PsmModel` in the `TransformationContext`
- **WHEN** `ExpressionValidationOnPsmWork.execute()` is called
- **THEN** validation completes without throwing an exception

#### Scenario: Invalid expressions against PSM
- **GIVEN** an `ExpressionModel` containing expressions referencing non-existent PSM types or invalid navigation paths
- **WHEN** `ExpressionValidationOnPsmWork.execute()` is called
- **THEN** the validation throws an exception detailing the expression errors

### Requirement: ExpressionModel Presence in TransformationContext
The system SHALL require an `ExpressionModel` to be present in the `TransformationContext`.

#### Scenario: ExpressionModel missing
- **GIVEN** a `TransformationContext` without an `ExpressionModel`
- **WHEN** `ExpressionValidationOnPsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Expression Model does not found in transformation context"`

### Requirement: PsmModel Presence in TransformationContext
The system SHALL require a `PsmModel` to be present in the `TransformationContext`.

#### Scenario: PsmModel missing
- **GIVEN** a `TransformationContext` without a `PsmModel`
- **WHEN** `ExpressionValidationOnPsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"PSM Model does not found in transformation context"`

### Requirement: Logging Validation Output
The system SHALL capture all validation output using a `StringBuilderLogger` that is properly closed after validation completes.

#### Scenario: Logger lifecycle
- **GIVEN** an `ExpressionValidationOnPsmWork` is executed
- **WHEN** validation completes (success or failure)
- **THEN** the `StringBuilderLogger` is closed via try-with-resources
