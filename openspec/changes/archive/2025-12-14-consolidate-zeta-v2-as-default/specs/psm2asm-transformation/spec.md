# PSM2ASM Transformation

## ADDED Requirements

### Requirement: Single Zeta Transformation Implementation

The Zeta transformation MUST use the TransformationRegistry-based implementation as the default. The implementation SHALL NOT contain any "V2" naming in class names, logs, or comments.

#### Scenario: Single Zeta implementation
Given the psm2asm module
When using Zeta transformation
Then `Psm2AsmZetaTransformation` is the only implementation
And it uses TransformationRegistry for rule execution
And no "V2" naming appears in class names, logs, or comments
