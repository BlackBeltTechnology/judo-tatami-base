# Consolidate Zeta V2 as Default Implementation

## Why

In the `judo-tatami-psm2asm` module, there are currently two Zeta transformation implementations:
- `Psm2AsmZetaTransformation` (V1) - Original Zeta implementation
- `Psm2AsmZetaTransformationV2` (V2) - Improved implementation using TransformationRegistry

The V2 implementation has been validated and produces equivalent output to ETL. The V1 implementation is no longer needed. The goal is to:
1. Remove the V1 Zeta implementation
2. Rename V2 to be the default (remove "V2" suffix)
3. Clean up all "V2" references in code, comments, and logs

## What Changes

### Remove V1 Implementation
- Delete `Psm2AsmZetaTransformation.java` (the V1 class)

### Rename V2 to Default
- Rename `Psm2AsmZetaTransformationV2` to `Psm2AsmZetaTransformation`
- Remove "V2" from all log messages, comments, and javadoc
- Update all imports and references

### Update References
- Update `Psm2AsmWork.java` to use renamed class
- Update all test classes that reference `Psm2AsmZetaTransformationV2`
- Update rule classes that reference V2 in comments
- Update performance tests

## Affected Files

| File | Change |
|------|--------|
| `Psm2AsmZetaTransformation.java` | Delete (V1) |
| `Psm2AsmZetaTransformationV2.java` | Rename to `Psm2AsmZetaTransformation.java` |
| `Psm2AsmWork.java` | Update import/reference |
| `Psm2AsmHelper.java` | Remove V2 comments |
| `TransferObjectRules.java` | Remove V2 comments |
| `DataRules.java` | Remove V2 comments |
| `Psm2AsmDualTransformationTest.java` | Update references |
| `Psm2AsmPerformanceTest.java` | Remove V2 comparison code |
| Multiple test files | Update imports |

## Risks

1. **Breaking change for external code**: If external projects reference `Psm2AsmZetaTransformationV2` directly
   - **Mitigation**: This is internal implementation; public API via `Psm2AsmWork` is unchanged

2. **Git history**: Renaming files loses direct git blame history
   - **Mitigation**: Use `git mv` to preserve rename tracking
