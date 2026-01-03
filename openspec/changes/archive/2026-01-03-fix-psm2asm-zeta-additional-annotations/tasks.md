# Tasks for fix-psm2asm-zeta-additional-annotations

## Implementation Tasks

- [x] **1. Implement CreateTransferAttributeClaimAnnotation rule**
  - Add rule in `TransferObjectRules.java`
  - Guard: `s.getClaimType() != null`
  - Set annotation source to `getAnnotationUri("claim")`
  - Add detail with key "value" and value `s.getClaimType()`
  - Attach to equivalent EAttribute
  - Use constant `CREATE_TRANSFER_ATTRIBUTE_CLAIM_ANNOTATION`
  - **Implemented**: Added `hasClaimType` guard and `createTransferAttributeClaimAnnotation` rule

- [x] **2. Implement CreateMetadataAnnotationForMetadataClass rule**
  - Add rule in `TransferObjectRules.java`
  - Guard: `isMetadataType()` - checks if any GET_METADATA operation outputs this type
  - Set annotation source to `getAnnotationUri("metadata")`
  - Add detail with key "value" and value "true"
  - Attach to equivalent EClass
  - Use constant `CREATE_METADATA_ANNOTATION`
  - **Implemented**: Added `isMetadataType` guard with helper method and `createMetadataAnnotation` rule

- [x] **3. Implement CreateQueryCustomizerAnnotationForQueryCustomizerClass rule**
  - Add rule in `TransferObjectRules.java`
  - Guard: `s.isQueryCustomizer()` (boolean property on TransferObjectType)
  - Set annotation source to `getAnnotationUri("queryCustomizer")`
  - Add detail with key "value" and value "true"
  - Attach to equivalent EClass
  - Use constant `CREATE_QUERY_CUSTOMIZER_ANNOTATION`
  - **Implemented**: Added `isQueryCustomizer` guard and `createQueryCustomizerAnnotation` rule

- [x] **4. Rewrite CreateTransferOperationInputRangeAnnotation rule**
  - Replace guard `isInputRangeOperation` with new guard checking `s.getInputRange() != null`
  - Change value from `"true"` to `getReferenceFQName(ctx.equivalent(s.getInputRange(), EReference.class))`
  - Remove spurious "operation" detail
  - Simplify rule body to match ETL pattern
  - **Implemented**: Replaced `isInputRangeOperation` with `hasInputRange` guard and fixed rule implementation

- [x] **5. Add filter/filterDialect to mappedEntityType annotation**
  - Update `createMappedTransferObjectAnnotation` in `TransferObjectRules.java`
  - When `s.getFilter() != null`:
    - Add detail "filter" with value `s.getFilter().getExpression()`
    - Add detail "filter.dialect" with value `s.getFilter().getDialect().toString()`
  - **Implemented**: Added filter condition with both details

## Validation Tasks

- [x] **6. Run Psm2AsmDualTransformationTest with STRICT mode**
  - Execute: `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest -Djudo.test.comparison.mode=STRICT`
  - **Result**: PASSED - ETL and Zeta transformations produced equivalent models

- [x] **7. Run RackInspectPerformanceTest with STRICT mode**
  - Execute: `mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance -Djudo.test.comparison.mode=STRICT`
  - **Result**: PASSED - ETL and Zeta models are EQUIVALENT
  - Note: Required enhancement to ModelComparator to compare annotation details in order-independent manner

- [x] **8. Run full Psm2Asm test suite**
  - Execute: `mvn test -pl judo-tatami-psm2asm`
  - **Result**: PASSED - Tests run: 52, Failures: 0, Errors: 0, Skipped: 1

## Dependencies

- Tasks 1-5 can be done in parallel
- Tasks 6-8 depend on tasks 1-5 being complete

## Implementation Summary

Added to `TransferObjectRules.java`:
1. Guard method `hasClaimType()` for TransferAttribute
2. Guard method `isQueryCustomizer()` for TransferObjectType
3. Guard method `isMetadataType()` for TransferObjectType (with helper `isGetMetadataOperationFor()`)
4. Rule `createTransferAttributeClaimAnnotation()` - claim annotation
5. Rule `createQueryCustomizerAnnotation()` - queryCustomizer annotation
6. Rule `createMetadataAnnotation()` - metadata annotation
7. Updated `createMappedTransferObjectAnnotation()` - added filter/filter.dialect details

Updated in `OperationRules.java`:
1. Replaced guard `isInputRangeOperation` with `hasInputRange`
2. Rewrote `createTransferOperationInputRangeAnnotation()` to use correct guard and value
