# ASM Validation Specification

## Purpose
Validates an Architecture Specific Model (AsmModel) against structural and semantic constraints defined in Epsilon EVL scripts, ensuring the ASM model conforms to the JUDO ASM metamodel rules before downstream transformations to RDBMS or expression models.

## Architecture
The module contains a single class:

- **AsmValidationWork** -- Extends `AbstractTransformationWork`. Retrieves `AsmModel` from `TransformationContext` and delegates validation to `AsmEpsilonValidator.validateAsm(logger, asmModel, scriptURI)` using the script URI calculated by `AsmEpsilonValidator.calculateAsmValidationScriptURI()`. Uses `StringBuilderLogger` for collecting validation output.

The actual validation logic and EVL scripts are defined in the `hu.blackbelt.judo.meta.asm.runtime` dependency (`hu.blackbelt.judo.meta.asm.model`). This module serves as the Tatami workflow integration point.

## Requirements

### Requirement: Validate ASM Model via Epsilon EVL
The system SHALL validate an AsmModel by executing Epsilon EVL validation scripts via `AsmEpsilonValidator.validateAsm()`, reporting all constraint violations.

#### Scenario: Valid ASM model
- **GIVEN** a structurally and semantically correct `AsmModel` in the `TransformationContext`
- **WHEN** `AsmValidationWork.execute()` is called
- **THEN** validation completes without throwing an exception

#### Scenario: Invalid ASM model
- **GIVEN** an `AsmModel` that violates one or more EVL constraints
- **WHEN** `AsmValidationWork.execute()` is called
- **THEN** the validation throws an exception with details of the violated constraints

### Requirement: AsmModel Presence in TransformationContext
The system SHALL require an `AsmModel` to be present in the `TransformationContext` before validation can proceed.

#### Scenario: AsmModel missing from context
- **GIVEN** a `TransformationContext` without an `AsmModel`
- **WHEN** `AsmValidationWork.execute()` is called
- **THEN** an `IllegalArgumentException` is thrown with message `"ASM Model does not found in transformation context"`

### Requirement: Validation Script URI Resolution
The system SHALL resolve the validation script URI via `AsmEpsilonValidator.calculateAsmValidationScriptURI()`, supporting JAR, OSGi bundle, and directory deployments.

#### Scenario: Script URI resolution
- **GIVEN** the ASM validation module is deployed as a JAR
- **WHEN** `calculateAsmValidationScriptURI()` is called
- **THEN** the returned URI points to the packaged EVL scripts within the JAR

### Requirement: Logging Validation Output
The system SHALL capture all validation output using a `StringBuilderLogger` that is properly closed after validation completes.

#### Scenario: Logger lifecycle
- **GIVEN** an `AsmValidationWork` is executed
- **WHEN** validation completes (success or failure)
- **THEN** the `StringBuilderLogger` is closed via try-with-resources
