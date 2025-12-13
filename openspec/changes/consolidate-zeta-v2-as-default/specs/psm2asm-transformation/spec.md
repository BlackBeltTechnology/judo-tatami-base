# PSM2ASM Transformation

## REMOVED Requirements

- `Psm2AsmZetaTransformation` (V1) - Original Zeta implementation removed
- `Psm2AsmZetaTransformationV2` class name - Renamed to `Psm2AsmZetaTransformation`

## MODIFIED Requirements

### Requirement: Zeta Transformation Class

The Zeta transformation MUST use the TransformationRegistry-based implementation as the default. The implementation SHALL NOT contain any "V2" naming in class names, logs, or comments.

#### Scenario: Single Zeta implementation
Given the psm2asm module
When using Zeta transformation
Then `Psm2AsmZetaTransformation` is the only implementation
And it uses TransformationRegistry for rule execution
And no "V2" naming appears in class names, logs, or comments
