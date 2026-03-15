## 1. Write Failing Tests (TDD)

- [x] 1.1 Add `testParameterizedTransferAttributeAnnotation` test to `Psm2AsmDualTransformationTest`: construct a minimal PSM with an `UnmappedTransferObjectType` containing a `TransferAttribute` with a `PrimitiveAccessor` binding whose getter expression has a `parameterType`; run ETL vs Zeta STRICT comparison; verify it FAILS (Zeta missing the `parameterized` annotation)
- [x] 1.2 Add `testParameterizedTransferObjectRelationAnnotation` test to `Psm2AsmDualTransformationTest`: construct a minimal PSM with an `UnmappedTransferObjectType` containing a `TransferObjectRelation` with a `ReferenceAccessor` binding whose getter expression has a `parameterType`; run ETL vs Zeta STRICT comparison; verify it FAILS
- [x] 1.3 Run `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest` and confirm both new tests FAIL with annotation differences

## 2. Implement Missing Zeta Rules

- [x] 2.1 In `TransferObjectRules.java`, in the `CreateTransferAttributeExpressionAnnotation` rule body: after the existing `getter.parameter` block inside the `PrimitiveAccessor` branch, add a guarded block that creates `EAnnotation(id="(psm/<id>)/TransferAttributeParameterizedAnnotation", source=parameterized)` with details `value=true` and `type=<FQName>` when `parameterType != null`, and adds it to the EAttribute
- [x] 2.2 In `TransferObjectRules.java`, in the `CreateTransferObjectRelationExpressionAnnotation` rule body: after the existing `getter.parameter` block inside the `ReferenceAccessor` branch, add the equivalent guarded block creating `EAnnotation(id="(psm/<id>)/TransferObjectRelationParameterizedAnnotation", source=parameterized)` for the EReference

## 3. Verify

- [x] 3.1 Run `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest` and confirm all tests PASS including the two new ones
- [x] 3.2 Run `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDiscoveryComparisonTest -Pperformance` and confirm all external models remain EQUIVALENT
