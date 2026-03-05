# Expression Validation on ASM Specification

## Purpose
Validates an ExpressionModel against an AsmModel and MeasureModel to ensure that all extracted JQL expressions are semantically valid in the context of the application-specific model, including type compatibility and measure unit consistency.

## Architecture
The module contains a single class:

- **ExpressionValidationOnAsmWork** -- Extends `AbstractTransformationWork`. Retrieves three required models from `TransformationContext`: `ExpressionModel`, `MeasureModel`, and `AsmModel`. Delegates validation to `ExpressionValidatorOnAsm.validateExpressionOnAsm(logger, asmModel, measureModel, expressionModel)`. Uses `StringBuilderLogger` for collecting validation output.

The actual validation logic is defined in the `hu.blackbelt.judo.meta.expression.adapters.asm` dependency (`ExpressionValidatorOnAsm`). This module serves as the Tatami workflow integration point.

## Requirements

### Requirement: Validate Expressions Against ASM Model
The system SHALL validate an ExpressionModel against an AsmModel and MeasureModel by invoking `ExpressionValidatorOnAsm.validateExpressionOnAsm()`, checking type compatibility, navigation path validity, and measure unit consistency.

#### Scenario: Valid expressions
- **GIVEN** a valid `ExpressionModel`, `AsmModel`, and `MeasureModel` in the `TransformationContext`
- **WHEN** `ExpressionValidationOnAsmWork.execute()` is called
- **THEN** validation completes without throwing an exception

#### Scenario: Invalid expressions
- **GIVEN** an `ExpressionModel` containing expressions with type mismatches or invalid navigation paths relative to the `AsmModel`
- **WHEN** `ExpressionValidationOnAsmWork.execute()` is called
- **THEN** the validation throws an exception detailing the expression errors

### Requirement: ExpressionModel Presence in TransformationContext
The system SHALL require an `ExpressionModel` to be present in the `TransformationContext`.

#### Scenario: ExpressionModel missing
- **GIVEN** a `TransformationContext` without an `ExpressionModel`
- **WHEN** `ExpressionValidationOnAsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Expression Model does not found in transformation context"`

### Requirement: MeasureModel Presence in TransformationContext
The system SHALL require a `MeasureModel` to be present in the `TransformationContext`.

#### Scenario: MeasureModel missing
- **GIVEN** a `TransformationContext` without a `MeasureModel`
- **WHEN** `ExpressionValidationOnAsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"Measure Model does not found in transformation context"`

### Requirement: AsmModel Presence in TransformationContext
The system SHALL require an `AsmModel` to be present in the `TransformationContext`.

#### Scenario: AsmModel missing
- **GIVEN** a `TransformationContext` without an `AsmModel`
- **WHEN** `ExpressionValidationOnAsmWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"ASM Model does not found in transformation context"`

### Requirement: Logging Validation Output
The system SHALL capture all validation output using a `StringBuilderLogger` that is properly closed after validation completes.

#### Scenario: Logger lifecycle
- **GIVEN** an `ExpressionValidationOnAsmWork` is executed
- **WHEN** validation completes (success or failure)
- **THEN** the `StringBuilderLogger` is closed via try-with-resources
