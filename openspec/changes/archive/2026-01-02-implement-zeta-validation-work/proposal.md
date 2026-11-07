# Implement Zeta Validation Work Classes

## Summary

Add Zeta-based validation Work classes to judo-tatami-base validation modules, enabling consuming projects to use native Java validation instead of Epsilon EVL.

## Motivation

The validation modules in judo-tatami-base currently provide only Epsilon EVL-based validation via Work classes:
- `PsmValidationWork` - uses `PsmEpsilonValidator`
- `AsmValidationWork` - uses `AsmEpsilonValidator`
- `ExpressionValidationOnPsmWork` - uses `ExpressionValidatorOnPsm`
- `ExpressionValidationOnAsmWork` - uses `ExpressionValidatorOnAsm`

The underlying metamodel projects (judo-meta-psm, judo-meta-asm, judo-meta-expression) already have Zeta validators implemented:
- `PsmValidator` (38 rule classes, fully functional)
- `AsmValidator` (skeleton, ASM EVL is empty)
- `ExpressionZetaValidator` (fully functional with 18 rule classes)

However, there are no corresponding Zeta Work classes in judo-tatami-base to expose these validators to consuming projects. The judo-tatami project (parent) has placeholder implementations (`EsmValidationZeta`, `ExpressionValidationOnEsmZeta`) that throw `UnsupportedOperationException`.

## Current State Analysis

| Module | EVL Work Class | Zeta Validator Available | Zeta Work Class |
|--------|---------------|-------------------------|-----------------|
| judo-tatami-psm-validation | PsmValidationWork | PsmValidator (38 rules) | **Missing** |
| judo-tatami-asm-validation | AsmValidationWork | AsmValidator (skeleton) | **Missing** |
| judo-tatami-expression-psm-validation | ExpressionValidationOnPsmWork | ExpressionZetaValidator | **Missing** |
| judo-tatami-expression-asm-validation | ExpressionValidationOnAsmWork | ExpressionZetaValidator | **Missing** |

## Approach

1. Create Zeta Work classes in each validation module that call the corresponding Zeta validators
2. Follow the existing Work class pattern (`AbstractTransformationWork`)
3. Use the Zeta validators from the metamodel projects (judo-meta-psm, judo-meta-asm, judo-meta-expression)
4. Add tests to verify Zeta validation works correctly

## Impact

- **Modules affected**:
  - judo-tatami-psm-validation
  - judo-tatami-asm-validation
  - judo-tatami-expression-psm-validation
  - judo-tatami-expression-asm-validation
- **Breaking changes**: None (additive only)
- **Dependencies**:
  - judo-meta-psm (PsmValidator)
  - judo-meta-asm (AsmValidator)
  - judo-meta-expression (ExpressionZetaValidator)
  - judo-zeta-validation-core

## Design Decisions

1. **Naming Convention**: Use suffix pattern - `*ValidationZetaWork` (e.g., `PsmValidationZetaWork`)

2. **ASM Validation**: Create `AsmValidationZetaWork` even though `AsmValidator` has no rules (asm.evl is empty). This provides infrastructure for future rules.

3. **Testing Strategy**: Tests simply verify Zeta validation runs without errors (no EVL/Zeta comparison).

4. **Expression ModelAdapter**: Follow the same pattern as EVL Work classes - internally create the appropriate adapter (`PsmModelAdapter` or `AsmModelAdapter`).

## Risks

1. **ASM validation is empty**: The `AsmValidator` has no validation rules because `asm.evl` is empty. The Zeta Work class will be a pass-through.
   - **Mitigation**: Document this clearly; the Work class provides infrastructure for future rules.

2. **Expression adapter dependencies**: `ExpressionZetaValidator` requires adapters from judo-meta-expression-psm/asm.
   - **Mitigation**: Add necessary dependencies to pom.xml files.
