# zeta-validation-work Specification

## Purpose
TBD - created by archiving change implement-zeta-validation-work. Update Purpose after archive.
## Requirements
### Requirement: PsmValidationZetaWork

The PSM validation module SHALL provide a Zeta-based Work class that validates PSM models using native Java validation rules.

#### Scenario: Validate PSM model using Zeta
Given a valid PSM model in the transformation context
When PsmValidationZetaWork.execute() is called
Then the PsmValidator validates the model using Zeta validation rules
And validation errors are thrown as exceptions

#### Scenario: Missing PSM model
Given no PSM model in the transformation context
When PsmValidationZetaWork.execute() is called
Then an IllegalArgumentException is thrown

### Requirement: AsmValidationZetaWork

The ASM validation module SHALL provide a Zeta-based Work class that validates ASM models using native Java validation rules.

#### Scenario: Validate ASM model using Zeta
Given a valid ASM model in the transformation context
When AsmValidationZetaWork.execute() is called
Then the AsmValidator validates the model using Zeta validation rules
And validation errors are thrown as exceptions

#### Scenario: Missing ASM model
Given no ASM model in the transformation context
When AsmValidationZetaWork.execute() is called
Then an IllegalArgumentException is thrown

### Requirement: ExpressionValidationOnPsmZetaWork

The Expression PSM validation module SHALL provide a Zeta-based Work class that validates Expression models against PSM models.

#### Scenario: Validate Expression on PSM using Zeta
Given a valid Expression model in the transformation context
And a valid PSM model in the transformation context
When ExpressionValidationOnPsmZetaWork.execute() is called
Then the ExpressionZetaValidator validates the Expression model using Zeta rules
And the PsmModelAdapter is used for type resolution

#### Scenario: Missing Expression model
Given no Expression model in the transformation context
When ExpressionValidationOnPsmZetaWork.execute() is called
Then an IllegalArgumentException is thrown

### Requirement: ExpressionValidationOnAsmZetaWork

The Expression ASM validation module SHALL provide a Zeta-based Work class that validates Expression models against ASM models.

#### Scenario: Validate Expression on ASM using Zeta
Given a valid Expression model in the transformation context
And a valid ASM model in the transformation context
And a valid Measure model in the transformation context
When ExpressionValidationOnAsmZetaWork.execute() is called
Then the ExpressionZetaValidator validates the Expression model using Zeta rules
And the AsmModelAdapter is used for type resolution

#### Scenario: Missing models
Given missing Expression, ASM, or Measure model in the transformation context
When ExpressionValidationOnAsmZetaWork.execute() is called
Then an IllegalArgumentException is thrown

