## MODIFIED Requirements

### Requirement: Configure Lambda Scope Resolution
The system SHALL pass `Asm2ExpressionConfiguration.resolveOnlyCurrentLambdaScope` to the `JqlExpressionBuilderConfig`, controlling whether expression resolution is constrained to the current lambda scope. The default SHALL be `false`.

#### Scenario: Default lambda scope configuration
- **GIVEN** an `Asm2ExpressionConfiguration` with default settings
- **WHEN** the expression extraction runs
- **THEN** `JqlExpressionBuilderConfig.resolveOnlyCurrentLambdaScope` is set to `false`

#### Scenario: Custom lambda scope configuration
- **GIVEN** an `Asm2ExpressionConfiguration` with `resolveOnlyCurrentLambdaScope` set to `true`
- **WHEN** the expression extraction runs
- **THEN** `JqlExpressionBuilderConfig.resolveOnlyCurrentLambdaScope` is set to `true`

## ADDED Requirements

### Requirement: Expression model origin is recorded
`Asm2ExpressionWork` SHALL put an `ExpressionModelOrigin` into the `TransformationContext`: created in this run when it builds the `ExpressionModel`, preloaded when it reuses one already in the context. Downstream stages use it to tell the two apart.

#### Scenario: Expression model created in this run
- **GIVEN** a `TransformationContext` with an `AsmModel` and no `ExpressionModel`
- **WHEN** `Asm2ExpressionWork.execute()` completes
- **THEN** the context holds an `ExpressionModelOrigin` of created in this run

#### Scenario: Expression model preloaded
- **GIVEN** a `TransformationContext` with an `AsmModel` and an `ExpressionModel` loaded from a saved `-expression.model`
- **WHEN** `Asm2ExpressionWork.execute()` completes
- **THEN** the context holds an `ExpressionModelOrigin` of preloaded and the same `ExpressionModel` instance is reused
