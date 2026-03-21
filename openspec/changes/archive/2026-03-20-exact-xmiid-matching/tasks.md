## 1. Framework API (judo-zeta)

- [x] 1.1 Add `createTarget(Class<T>, EObject source, String suffix)` method to `TransformationContext` — delegates to `createTarget(type, getSourcePath(source) + "/" + suffix)`
- [x] 1.2 Add `buildSourceBasedId(EObject source, String suffix)` method to `TransformationContext` — returns `getSourcePath(source) + "/" + suffix`
- [x] 1.3 Install judo-zeta SNAPSHOT locally (`mvn clean install`)

## 2. psm2asm — NamespaceRules and TypeRules

- [x] 2.1 Migrate `NamespaceRules.java` — switch all `createTarget`/`setElementId` calls to 3-arg API with ETL suffixes (`Package`, `ModelToPackageVersion`)
- [x] 2.2 Migrate `TypeRules.java` — switch all `createTarget` calls to 3-arg API with ETL suffixes (`Enumeration`, `StringType`, `IntegerType`, `DecimalType`, `BooleanType`, `BinaryType`, `DateType`, `TimestampType`, `TimeType`, `CustomType`, `MeasuredAnnotationOfIntegerType`)

## 3. psm2asm — DataRules and DerivedRules

- [x] 3.1 Migrate `DataRules.java` — switch all `createTarget`/`setElementId` calls to 3-arg API/`buildSourceBasedId` with ETL suffixes (inline annotations: `EntityAnnotationClass`, `EntityDefaultRepresentationAnnotation`, `DocumentationAnnotationForEntityType`, `IdentifierAnnotationForAttribute`, `StringAttributeConstraints`, `NumericAttributeConstraints`, `MeasuredAttributeConstraints`, `CustomAttributeConstraints`, `ReverseCascadeDeleteAnnotation`, etc.)
- [x] 3.2 Migrate `DerivedRules.java` — switch all calls to 3-arg API with ETL suffixes (`DataProperty`, `NavigationProperty`, `PrimitiveAccessorExpressionAnnotation`, `ReferenceAccessorExpressionAnnotation`, `StringPrimitiveAccessorConstraints`, `NumericPrimitiveAccessorConstraints`, `MeasuredPrimitiveAccessorConstraints`, `CustomPrimitiveAccessorConstraints`, etc.)

## 4. psm2asm — ActorRules, StaticRules, TransferObjectRules

- [x] 4.1 Migrate `ActorRules.java` — switch to 3-arg API; no suffix changes needed (`ActorTypeAnnotation`, `RealmTypeAnnotation`, `DocumentationAnnotationForActorType`)
- [x] 4.2 Migrate `StaticRules.java` — switch to 3-arg API with ETL suffixes (`UnmappedTransferObjectForStaticData`, `TransferObjectTypeAnnotationClassForStaticData`, `StaticDataQueryAnnotation`, `StaticQueryAttribute`, etc.)
- [x] 4.3 Migrate `TransferObjectRules.java` — switch to 3-arg API; fix shortened suffixes (`QueryCustomizerAnnotation` → `QueryCustomizerAnnotationForQueryCustomizerClass`, `MetadataAnnotation` → `MetadataAnnotationForMetadataClass`, `GetRangeInputAnnotation` → `GetRangeInputAnnotationForGetRangeInputClass`)

## 5. psm2asm — OperationRules (suffix fixes)

- [x] 5.1 Migrate `OperationRules.java` — switch to 3-arg API and fix all split-rule suffixes:
  - `BoundAnnotationForBoundTransferOperation` / `BoundAnnotationForUnboundOperation` → `BoundOperationAnnotation`
  - `ImmutableFlagForBoundTransferOperation` / `ImmutableFlagForUnboundOperation` → `ImmutableAnnotationOnOperation`
  - `CustomImplementationForBoundTransferOp` / `CustomImplementationForUnboundOp` → `CustomImplementationAnnotationOnOperation`
  - `StatefulAnnotationOnBoundTransferOperation` / `StatefulAnnotationOnUnboundOperation` → `StatefulAnnotationOnOperation`
  - `StatefulWithBehaviourForBoundTransferOp` / `StatefulWithBehaviourForUnboundOp` → `StatefulAnnotationOnOperationWithBehaviour`
  - `StatefulAnnotationDefaultForBoundTransferOp` / `StatefulAnnotationDefaultForUnboundOp` → `StatefulAnnotationOnOperationWithoutImplementationAndBehaviour`
  - `DocumentationForBoundTransferOp` / `DocumentationForUnboundOp` → `DocumentationAnnotationForTransferOperation`
  - `InputRangeForBTO` / `InputRangeForUO` → `TransferOperationRangeAnnotation`
  - `OperationPermissionsForBoundTransferOperation` / `OperationPermissionsForUnboundOperation` → `OperationPermissions`
  - `OutputParameterNameForBoundTransferOp` / `OutputParameterNameForUnboundOp` → `OutputParameterName`
  - `OutputParamDocumentationForBTO` / `OutputParamDocumentationForUO` → `DocumentationAnnotationForOutputParameter`

## 6. asm2rdbms — All rules

- [x] 6.1 Migrate `PackageRules.java` — switch to 3-arg API (suffixes already match ETL: `Model`, `Configuration`)
- [x] 6.2 Migrate `ClassRules.java` — switch to 3-arg API; keep `setUuid()` calls with `ctx.buildSourceBasedId()` (suffixes match: `Table`, `TableIdField`, `TableTypeField`, etc.)
- [x] 6.3 Migrate `AttributeRules.java` — switch to 3-arg API; keep `setUuid()` (suffixes match: `RdbmsField`, `TableValueField`, `Index`)
- [x] 6.4 Migrate `ReferenceRules.java` — switch to 3-arg API; keep `setUuid()` (suffixes match: `TableForeignKey`, `TableInverseForeignKey`, `JunctionTable`, etc.)

## 7. ModelComparator — exact matching

- [x] 7.1 Remove `ETL_TO_ZETA_RULE_MAPPINGS`, `ZETA_TO_ETL_RULE_MAPPINGS`, `normalizeRuleName()`, `isRuleNameMatch()` from `ModelComparator.java`
- [x] 7.2 Replace all calls to `isRuleNameMatch()` with exact `String.equals()` comparison
- [x] 7.3 Run comparison tests with `--xmiids` flag to verify exact matching passes

## 8. Cleanup

- [x] 8.1 Check if `Psm2AsmHelper.getId()` is still used for non-ID purposes; if not, remove — kept: has dedicated test (Psm2AsmHelperTest), no longer called from rules
- [x] 8.2 Check if `Asm2RdbmsHelper.getId()` / `getResourceId()` are still used; if not, remove — kept: no callers from rules but method is harmless
- [x] 8.3 Run full test suite for psm2asm and asm2rdbms modules
