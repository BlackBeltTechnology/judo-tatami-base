# Tasks for fix-psm2asm-zeta-annotation-differences

## Implementation Tasks

- [x] **1. Implement AddDefaultAnnotationToTransferAttribute rule**
  - Add rule in `TransferObjectRules.java` that transforms `TransferAttribute` with `defaultValue` to `EAnnotation`
  - Guard: `s.getDefaultValue() != null`
  - Set annotation source to `getAnnotationUri("default")`
  - Add detail with key "value" and value `s.getDefaultValue().getName()`
  - Attach annotation to the equivalent EAttribute
  - Use rule name constant `ADD_DEFAULT_ANNOTATION_TO_TRANSFER_ATTRIBUTE`

- [x] **2. Implement AddDefaultAnnotationToTransferObjectRelation rule**
  - Add rule in `TransferObjectRules.java` that transforms `TransferObjectRelation` with `defaultValue` to `EAnnotation`
  - Guard: `s.getDefaultValue() != null`
  - Set annotation source to `getAnnotationUri("default")`
  - Add detail with key "value" and value `s.getDefaultValue().getName()`
  - Attach annotation to the equivalent EReference
  - Use rule name constant `ADD_DEFAULT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION`

## Validation Tasks

- [x] **3. Run Psm2AsmDualTransformationTest with STRICT mode**
  - Execute: `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest -Djudo.test.comparison.mode=STRICT`
  - Result: **PASSED** - ETL and Zeta transformations produced equivalent models
  - The `exposedBy` differences are also resolved (cascade effect confirmed)

- [x] **4. Run RackInspectPerformanceTest with STRICT mode**
  - Execute: `mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance -Djudo.test.comparison.mode=STRICT`
  - Result: The original `default` annotation issues are fixed
  - Note: Additional pre-existing differences were discovered (claim, metadata, inputRange, queryCustomizer annotations) - these are outside the scope of this change and require separate proposals

- [x] **5. Run full Psm2Asm test suite**
  - Execute: `mvn test -pl judo-tatami-psm2asm`
  - Result: **PASSED** - Tests run: 52, Failures: 0, Errors: 0, Skipped: 1

## Notes

- The **extra `exposedBy` annotations** are a cascade effect of missing `default` annotations
- `AsmUtils.enrichWithAnnotations()` excludes attributes from `exposedBy` if their name matches another attribute's `default` annotation value
- Once `default` annotations are added, `exposedBy` filtering works correctly
- The RackInspect model reveals additional pre-existing differences that need separate changes

## Implementation Summary

Added to `TransferObjectRules.java`:
1. Guard method `hasDefaultValue()` for TransferAttribute
2. Guard method `hasDefaultValueRelation()` for TransferObjectRelation
3. Rule `addDefaultAnnotationToTransferAttribute()` - lines 762-779
4. Rule `addDefaultAnnotationToTransferObjectRelation()` - lines 1025-1042
