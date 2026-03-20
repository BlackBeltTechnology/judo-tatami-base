## 1. Fix NamespaceRules.java — 1 annotation site

- [x] 1.1 Fix `createModelToPackageVersionAnnotation()` (or equivalent rule that creates the EAnnotation without an ID): use `ctx.createTarget(EAnnotation.class, "(psm/" + getId(s) + ")/ModelToPackageVersion")` and for the child `version` detail entry set `ctx.setElementId(version, annotationId + "/Version")`; verify against `namespace.etl` line 22–26

## 2. Fix DataRules.java — annotation sites

- [x] 2.1 Fix `createEntityAnnotationClass()`: use `ctx.createTarget(EAnnotation.class, "(psm/" + getId(s) + ")/EntityAnnotationClass")`; child `entity` detail → `annotationId + "/Entity"` (data.etl:7–11)
- [x] 2.2 Fix `createDocumentationAnnotationForEntityType()`: use `ctx.createTarget(EAnnotation.class, "(psm/" + getId(s) + ")/DocumentationAnnotationForEntityType")` (data.etl:23)
- [x] 2.3 Fix `createEntityDefaultRepresentationAnnotation()`: use `ctx.createTarget(EAnnotation.class, "(psm/" + getId(s) + ")/EntityDefaultRepresentationAnnotation")`; child → `+ "/DefaultRepresentation"` (data.etl:43–47)
- [x] 2.4 Fix `createAttributeConstraints()` (base): use `"(psm/" + getId(s) + ")/AttributeConstraints"` (data.etl:59)
- [x] 2.5 Fix `createDocumentationAnnotationForAttribute()`: use `"(psm/" + getId(s) + ")/DocumentationAnnotationForAtrributes"` (note typo in ETL) (data.etl:69)
- [x] 2.6 Fix `createStringAttributeConstraints()`: `"(psm/" + getId(s) + ")/StringAttributeConstraints"`; child `maxLength` → `+ "/MaxLength"`; child `pattern` → `+ "/Pattern"` (data.etl:78–88)
- [x] 2.7 Fix `createCustomAttributeConstraints()`: `"(psm/" + getId(s) + ")/CustomAttributeConstraints"`; child `customType` → `+ "/CustomType"` (data.etl:100–103)
- [x] 2.8 Fix `createAbstractNumericAttributeConstraints()`: `"(psm/" + getId(s) + ")/AbstractNumericAttributeConstraints"`; children `precision` → `+ "/Precision"`, `scale` → `+ "/Scale"` (data.etl:114–123)
- [x] 2.9 Fix `createNumericAttributeConstraints()`: `"(psm/" + getId(s) + ")/NumericAttributeConstraints"` (data.etl:134)
- [x] 2.10 Fix `createMeasuredAttributeConstraints()`: `"(psm/" + getId(s) + ")/MeasuredAttributeConstraints"`; children `measure` → `+ "/Measure"`, `unit` → `+ "/Unit"` (data.etl:142–151)
- [x] 2.11 Fix `createIdentifierAnnotationForAttribute()`: `"(psm/" + getId(s) + ")/IdentifierAnnotationForAttribute"`; child `identifier` → `+ "/Identifier"` (data.etl:177–181)
- [x] 2.12 Fix `createReverseCascadeDeleteAnnotation()` (on AssociationEndRelation): `"(psm/" + getId(s) + ")/ReverseCascadeDeleteAnnotation"`; child → `+ "/ReverseCascadeDelete"` (data.etl:214–218)
- [x] 2.13 Fix `createUnmappedDefaultOnlyAttributeAnnotation()`: `"(psm/" + getId(s) + ")/UnmappedDefaultOnlyAttributeAnnotation"`; child → `+ "/UnmappedDefaultOnly"` (data.etl:239–243)
- [x] 2.14 Fix `createUnmappedDefaultOnlyReferenceAnnotation()`: `"(psm/" + getId(s) + ")/UnmappedDefaultOnlyReferenceAnnotation"`; child → `+ "/UnmappedDefaultOnly"` (data.etl:255–259)
- [x] 2.15 Fix `createDocumentationAnnotationForAssociationEndRelation()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForAssociationEndRelation"` (data.etl:271)
- [x] 2.16 Fix `createDocumentationAnnotationForContainmentRelation()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForContainmentRelation"` (data.etl:279)
- [x] 2.17 Fix `createSequenceAnnotation()`: `"(psm/" + getId(s) + ")/Sequence"`; children `name` → `+ "/Name"`, `initialValue` → `+ "/InitialValue"`, `increment` → `+ "/Increment"`, `maximumValue` → `+ "/MaximumValue"`, `cyclic` → `+ "/Cyclic"` (data.etl:287–317)
- [x] 2.18 Fix `createNamespaceSequenceAnnotation()`: `"(psm/" + getId(s) + ")/NamespaceSequence"` (data.etl:327)
- [x] 2.19 Fix `createEntitySequenceAnnotation()`: `"(psm/" + getId(s) + ")/EntitySequence"` (data.etl:335)

## 3. Fix ActorRules.java — 3 annotation sites

- [x] 3.1 Fix `createActorAnnotation()`: `"(psm/" + getId(s) + ")/ActorAnnotation"`; children: `actorName` → `+ "/ActorName"`, `realm` → `+ "/Realm"` (actor.etl:10–21)
- [x] 3.2 Fix `createActorTypeAnnotation()`: `"(psm/" + getId(s) + ")/ActorTypeAnnotation"`; children: `actorType` → `+ "/ActorType"`, `managed` → `+ "/Managed"`, `kind` → `+ "/Kind"` (actor.etl:35–54)
- [x] 3.3 Fix `createRealmTypeAnnotation()`: `"(psm/" + getId(s) + ")/RealmTypeAnnotation"`; child `realm` → `+ "/Realm"` (actor.etl:68–72)
- [x] 3.4 Fix `createDocumentationAnnotationForActorType()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForActorType"` (actor.etl:85)

## 4. Fix DerivedRules.java — 6 annotation sites

- [x] 4.1 Fix `createPrimitiveAccessorConstraints()` (base): `"(psm/" + getId(s) + ")/PrimitiveAccessorConstraints"` (derived.etl:9)
- [x] 4.2 Fix `createStringPrimitiveAccessorConstraints()`: `"(psm/" + getId(s) + ")/StringPrimitiveAccessorConstraints"`; children `maxLength` → `+ "/MaxLength"`, `pattern` → `+ "/Pattern"` (derived.etl:21–31)
- [x] 4.3 Fix `createCustomPrimitiveAccessorConstraints()`: `"(psm/" + getId(s) + ")/CustomPrimitiveAccessorConstraints"`; child `customType` → `+ "/CustomType"` (derived.etl:44–47)
- [x] 4.4 Fix `createAbstractNumericPrimitiveAccessorConstraints()`: `"(psm/" + getId(s) + ")/AbstractNumericPrimitiveAccessorConstraints"`; children `precision` → `+ "/Precision"`, `scale` → `+ "/Scale"` (derived.etl:58–67)
- [x] 4.5 Fix `createNumericPrimitiveAccessorConstraints()`: `"(psm/" + getId(s) + ")/NumericPrimitiveAccessorConstraints"` (derived.etl:79)
- [x] 4.6 Fix `createMeasuredPrimitiveAccessorConstraints()`: `"(psm/" + getId(s) + ")/MeasuredPrimitiveAccessorConstraints"`; children `measure` → `+ "/Measure"`, `unit` → `+ "/Unit"` (derived.etl:88–97)
- [x] 4.7 Fix `createPrimitiveAccessorExpressionAnnotation()`: `"(psm/" + getId(s) + ")/PrimitiveAccessorExpressionAnnotation"`; children `getter` → `+ "/Getter"`, `getterDialect` → `+ "/GetterDialect"`, `getterParameterType` → `+ "/GetterParameterType"`, `setter` → `+ "/Setter"`, `setterDialect` → `+ "/SetterDialect"`, `setterParameterType` → `+ "/SetterParameterType"` (derived.etl:108–146)
- [x] 4.8 Fix `createReferenceAccessorExpressionAnnotation()`: `"(psm/" + getId(s) + ")/ReferenceAccessorExpressionAnnotation"`; same child hierarchy as 4.7 (derived.etl:160–198)

## 5. Fix TransferObjectRules.java — 24 annotation sites

- [x] 5.1 Fix `createDocumentationAnnotationForTransferObjectType()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForTransferObjectType"` (transferObject.etl:18)
- [x] 5.2 Fix `createTransferObjectTypeAnnotationClass()`: `"(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClass"`; child `transferObject` → `+ "/TransferObject"` (transferObject.etl:26–30)
- [x] 5.3 Fix `createTransferObjectTypeAnnotationClassForReferenceClass()`: `"(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClassForReferenceClass"`; child `transferObject` → `+ "/TransferObject"` (transferObject.etl:41–45)
- [x] 5.4 Fix `createMappedEntityTypeAnnotationOnMappedTransferObject()`: `"(psm/" + getId(s) + ")/MappedEntityTypeAnnotationOnMappedTransferObject"`; children `mappedEntityType` → `+ "/MappedEntityType"`, `filter` → `+ "/Filter"`, `filterDialect` → `+ "/FilterDialect"` (transferObject.etl:57–74)
- [x] 5.5 Fix `createMappedEntityTypeAnnotationOnReferenceClassForEntityType()`: `"(psm/" + getId(s) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType"`; child `mappedEntityType` → `+ "/MappedEntityType"` (transferObject.etl:102–106)
- [x] 5.6 Fix `createAnnotationOnReferenceClassForEntityType()`: `"(psm/" + getId(s) + ")/AnnotationOnReferenceClassForEntityType"`; child `referenceHolder` → `+ "/ReferenceHolder"` (transferObject.etl:128–132)
- [x] 5.7 Fix `createTransientAnnotationToTransferAttribute()`: `"(psm/" + getId(s) + ")/TransientAnnotationToTransferAttribute"`; child `entry` → `+ "/Entry"` (transferObject.etl:144–148)
- [x] 5.8 Fix `createTransferAttributeConstraints()` (base): `"(psm/" + getId(s) + ")/TransferAttributeConstraints"` (transferObject.etl:160)
- [x] 5.9 Fix `createStringTransferAttributeConstraints()`: `"(psm/" + getId(s) + ")/StringTransferAttributeConstraints"`; children `maxLength` → `+ "/MaxLength"`, `pattern` → `+ "/Pattern"` (transferObject.etl:170–180)
- [x] 5.10 Fix `createCustomTransferAttributeConstraints()`: `"(psm/" + getId(s) + ")/CustomTransferAttributeConstraints"`; child `customType` → `+ "/CustomType"` (transferObject.etl:192–195)
- [x] 5.11 Fix `createAbstractNumericTransferAttributeConstraints()`: `"(psm/" + getId(s) + ")/AbstractNumericTransferAttributeConstraints"`; children (transferObject.etl:206–215)
- [x] 5.12 Fix `createNumericTransferAttributeConstraints()`: `"(psm/" + getId(s) + ")/NumericTransferAttributeConstraints"` (transferObject.etl:226)
- [x] 5.13 Fix `createMeasuredTransferAttributeConstraints()`: `"(psm/" + getId(s) + ")/MeasuredTransferAttributeConstraints"`; children `measure` → `+ "/measure"`, `unit` → `+ "/unit"` (note lowercase — matches ETL) (transferObject.etl:234–243)
- [x] 5.14 Fix `createTransferObjectAttributeBindingAnnotation()`: `"(psm/" + getId(s) + ")/TransferObjectAttributeBindingAnnotation"`; child `binding` → `+ "/binding"` (transferObject.etl:253–257)
- [x] 5.15 Fix `createTransferAttributeParameterizedAnnotation()`: `"(psm/" + getId(s) + ")/TransferAttributeParameterizedAnnotation"`; children `parameterized` → `+ "/parameterized"`, `parameterizedType` → `+ "/parameterizedType"` (transferObject.etl:269–279)
- [x] 5.16 Fix `createDefaultAnnotationToTransferAttribute()`: `"(psm/" + getId(s) + ")/DefaultAnnotationToTransferAttribute"`; child `defaultValue` → `+ "/DefaultValue"` (transferObject.etl:291–295)
- [x] 5.17 Fix `createTransientAnnotationToTransferObjectRelation()`: `"(psm/" + getId(s) + ")/TransientAnnotationToTransferObjectRelation"`; child `entry` → `+ "/Entry"` (transferObject.etl:307–311)
- [x] 5.18 Fix `createTransferObjectRelationBindingAnnotation()`: `"(psm/" + getId(s) + ")/TransferObjectRelationBindingAnnotation"`; child `binding` → `+ "/binding"` (transferObject.etl:323–327)
- [x] 5.19 Fix `createTransferObjectRelationParameterizedAnnotation()`: `"(psm/" + getId(s) + ")/TransferObjectRelationParameterizedAnnotation"`; children `parameterized` → `+ "/parameterized"`, `parameterizedType` → `+ "/parameterizedType"` (transferObject.etl:339–349)
- [x] 5.20 Fix `createTransferObjectRelationRangeAnnotation()`: `"(psm/" + getId(s) + ")/TransferObjectRelationRangeAnnotation"`; child `range` → `+ "/Range"` (transferObject.etl:361–365)
- [x] 5.21 Fix `createTransferAttributeClaimAnnotation()`: `"(psm/" + getId(s) + ")/TransferAttributeClaimAnnotation"`; child `claim` → `+ "/Claim"` (transferObject.etl:377–381)

## 6. Fix OperationRules.java — 38 annotation sites

- [x] 6.1 Fix `createScriptBodyAnnotation()` (unbound op): `"(psm/" + getId(s) + ")/ScriptBodyAnnotation"`; child `script` → `+ "/Body"` (operation.etl:18–22)
- [x] 6.2 Fix `createDocumentationAnnotationForInputParameter()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForInputParameter"` (operation.etl:46)
- [x] 6.3 Fix `createOutputParameterName()`: `"(psm/" + getId(s) + ")/OutputParameterName"`; child `script` → `+ "/Name"` (operation.etl:55–59)
- [x] 6.4 Fix `createDocumentationAnnotationForOutputParameter()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForOutputParameter"` (operation.etl:73)
- [x] 6.5 Fix `createCustomImplementationAnnotationOnOperation()`: `"(psm/" + getId(s) + ")/CustomImplementationAnnotationOnOperation"`; child `customImplementation` → `+ "/CustomImplementation"` (operation.etl:84–88)
- [x] 6.6 Fix `createCustomImplementationAnnotationOnBoundOperation()`: `"(psm/" + getId(s) + ")/CustomImplementationAnnotationOnBoundOperation"`; child `customImplementation` → `+ "/CustomImplementation"` (operation.etl:101–105)
- [x] 6.7 Fix `createStatefulAnnotationOnOperation()`: `"(psm/" + getId(s) + ")/StatefulAnnotationOnOperation"`; child `stateful` → `+ "/Stateful"` (operation.etl:118–122)
- [x] 6.8 Fix `createStatefulAnnotationOnOperationWithoutImplementationAndBehaviour()`: `"(psm/" + getId(s) + ")/StatefulAnnotationOnOperationWithoutImplementationAndBehaviour"`; child `stateful` → `+ "/Stateful"` (operation.etl:135–139)
- [x] 6.9 Fix `createStatefulAnnotationOnOperationWithBehaviour()`: `"(psm/" + getId(s) + ")/StatefulAnnotationOnOperationWithBehaviour"`; child `stateful` → `+ "/Stateful"` (operation.etl:152–156)
- [x] 6.10 Fix `createDocumentationAnnotationForBoundOperation()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForBoundOperation"` (operation.etl:218)
- [x] 6.11 Fix `createInstanceRepresentationOfBoundOperation()`: `"(psm/" + getId(s) + ")/InstanceRepresentationOfBoundOperation"`; child `instanceRepresentation` → `+ "/InstanceRepresentation"` (operation.etl:226–230)
- [x] 6.12 Fix `createBoundOperationAnnotation()`: `"(psm/" + getId(s) + ")/BoundOperationAnnotation"`; child `boundEntry` → `+ "/Bound"` (operation.etl:242–246)
- [x] 6.13 Fix `createBindingAnnotationForBoundTransferOperation()`: `"(psm/" + getId(s) + ")/BoundTransferOperation"` binding annotation (not the operation itself); children `bindingAnnotation` → `t.getId() + "/BindingAnnotation"`, `bindingEntry` → `bindingAnnotation.getId() + "/Binding"` (operation.etl:271–274)
- [x] 6.14 Fix `createScriptBodyAnnotationForBoundOperation()`: `"(psm/" + getId(s) + ")/ScriptBodyAnnotationForBoundOperation"` (operation.etl:290)
- [x] 6.15 Fix `createAbstractAnnotationForBoundOperation()`: `"(psm/" + getId(s) + ")/AbstractAnnotationForBoundOperation"`; child `abstractEntry` → `+ "/AbstractEntry"` (operation.etl:299–303)
- [x] 6.16 Fix `createCustomImplementationAnnotationOnUnboundOperation()`: `"(psm/" + getId(s) + ")/CustomImplementationAnnotationOnUnboundOperation"`; child `customImplementation` → `+ "/CustomImplementation"` (operation.etl:327–331)
- [x] 6.17 Fix `createScriptBodyAnnotationForUnboundOperation()`: `"(psm/" + getId(s) + ")/ScriptBodyAnnotationForUnboundOperation"` (operation.etl:345)
- [x] 6.18 Fix `createInitializerAnnotation()`: `"(psm/" + getId(s) + ")/InitializerAnnotation"`; child `initializerEntry` → `+ "/Initializer"` (operation.etl:354–358)
- [x] 6.19 Fix `createBehaviourAnnotation()`: `"(psm/" + getId(s) + ")/BehaviourAnnotation"`; children `typeEntry` → `+ "/Type"`, `ownerEntry` → `+ "/Owner"`, `bindingAnnotation` → `+ "/BindingAnnotation"`, `bindingTypeEntry` → `bindingAnnotation.getId() + "/BindingType"`, `bindingOwnerEntry` → `bindingAnnotation.getId() + "/BindingOwner"` (operation.etl:371–457)
- [x] 6.20 Fix `createOperationPermissions()`: `"(psm/" + getId(s) + ")/OperationPermissions"`; children `updateEntry` → `+ "/Update"`, `deleteEntry` → `+ "/Delete"` (operation.etl:470–480)
- [x] 6.21 Fix `createDocumentationAnnotationForTransferOperation()`: `"(psm/" + getId(s) + ")/DocumentationAnnotationForTransferOperation"` (operation.etl:493)
- [x] 6.22 Fix `createImmutableAnnotationOnOperation()`: `"(psm/" + getId(s) + ")/ImmutableAnnotationOnOperation"`; child `immutable` → `+ "/Immutable"` (operation.etl:501–505)
- [x] 6.23 Fix `createTransferOperationRangeAnnotation()`: `"(psm/" + getId(s) + ")/TransferOperationRangeAnnotation"`; child `range` → `+ "/InputRange"` (operation.etl:518–522)

## 7. Verify

- [x] 7.1 Run `Psm2AsmDualTransformationTest` — all structural tests pass (no regressions)
- [x] 7.2 Add `ModelComparator.assertXmiIdsEquivalent(etlResource, zetaResource)` call to `testEtlAndZetaProduceEquivalentModelsStrict()` in `Psm2AsmDualTransformationTest` after the structural equivalence assertion passes
- [x] 7.3 Run `Psm2AsmDualTransformationTest` again — confirm `assertXmiIdsEquivalent` passes

## 8. Fix discovery comparison test (Psm2AsmDiscoveryComparisonTest) — 586 XMI differences

- [x] 8.1 Fix Root Cause 1: Add `InputRangeForBTO → TransferOperationRangeAnnotation` and `InputRangeForUO → TransferOperationRangeAnnotation` mappings to `ZETA_TO_ETL_RULE_MAPPINGS` in `ModelComparator.java`
- [x] 8.2 Fix Root Cause 2: Fix `parseXmiId()` in `ModelComparator.java` to handle compound `((A)/ruleA)_((B)/ruleB)` ETL source IDs by scanning from the end for the last `)` followed by `/`
- [x] 8.3 Fix Root Cause 3: Add `ctx.setElementId()` calls for all inline annotations in `StaticRules.createUnmappedTransferObjectForStaticNavigation()` (`TransferObjectTypeAnnotationClassForStaticNavigation`, `StaticNavigationQueryAnnotation`, `StaticQueryNavigation` EReference, `NavigationReferenceBindingForStaticNavigation`, `TransferObjectRelationParameterizedAnnotationForStaticNavigation`)
- [x] 8.4 Add `DocumentationForBoundTransferOp → DocumentationAnnotationForTransferOperation` mapping to `ZETA_TO_ETL_RULE_MAPPINGS` in `ModelComparator.java`
- [x] 8.5 Run `Psm2AsmDiscoveryComparisonTest` with `--xmiids` flag — confirm 0 XMI differences (down from 586)
