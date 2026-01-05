# Design: Zeta to ETL Mapping Analysis

## Detailed Comparison by Transformation

### PSM2ASM (137 ETL rules → 92 Zeta rules)

#### Rule Mapping Analysis

| ETL Rule | Zeta Rule | Status | Notes |
|----------|-----------|--------|-------|
| CreateActorAnnotation | (inline) | **DIFF** | Zeta inline in CREATE_ACTOR_TYPE_CLASS |
| CreateActorTypeAnnotation | CREATE_ACTOR_TYPE_ANNOTATION | MATCH | |
| CreateRealmTypeAnnotation | CREATE_REALM_TYPE_ANNOTATION | MATCH | |
| CreateDocumentationAnnotationForActorType | CREATE_DOCUMENTATION_ANNOTATION_FOR_ACTOR_TYPE | MATCH | |
| CreateEntityAnnotationClass | (inline) | **DIFF** | Zeta inline in CREATE_ENTITY_CLASS |
| CreateEntityClass | CREATE_ENTITY_CLASS | PARTIAL | Inline annotations in Zeta |
| CreateEntityDefaultRepresentationAnnotation | (inline) | **DIFF** | Zeta inline |
| CreateDocumentationAnnotationForEntityType | CREATE_DOCUMENTATION_ANNOTATION_FOR_ENTITY_TYPE | PARTIAL | Guard differs |
| CreateAttribute | CREATE_ATTRIBUTE | MATCH | |
| AddStringAttributeConstraints | ADD_STRING_TRANSFER_ATTRIBUTE_CONSTRAINTS | MATCH | |
| AddNumericAttributeConstraints | ADD_NUMERIC_TRANSFER_ATTRIBUTE_CONSTRAINTS | MATCH | |
| AddMeasuredAttributeConstraints | ADD_MEASURED_TRANSFER_ATTRIBUTE_CONSTRAINTS | MATCH | |
| CreateIdentifierAnnotationForAttribute | (missing) | **MISSING** | |
| CreateRelation (abstract) | - | N/A | Abstract in ETL |
| CreateAssociationEndRelation | CREATE_ASSOCIATION_END_RELATION | MATCH | |
| CreateContainmentRelation | CREATE_CONTAINMENT_RELATION | MATCH | |
| AddUnmappedDefaultOnlyAttributeAnnotation | ADD_UNMAPPED_DEFAULT_ONLY_ATTRIBUTE_ANNOTATION | MATCH | |
| AddUnmappedDefaultOnlyReferenceAnnotation | ADD_UNMAPPED_DEFAULT_ONLY_REFERENCE_ANNOTATION | MATCH | |
| CreateNamespaceSequence | CREATE_NAMESPACE_SEQUENCE | MATCH | |
| CreateEntitySequence | CREATE_ENTITY_SEQUENCE | MATCH | |
| CreatePrimitiveAccessor (abstract) | - | N/A | Abstract in ETL |
| CreateDataProperty | CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE | MATCH | |
| CreateNavigationProperty | CREATE_NAVIGATION_PROPERTY | MATCH | |
| CreateBoundOperation | CREATE_BOUND_OPERATION | MATCH | |
| CreateBoundTransferOperation | CREATE_BOUND_TRANSFER_OPERATION | MATCH | |
| CreateUnboundOperation | CREATE_UNBOUND_OPERATION | MATCH | |
| CreateTransferOperationInputRangeAnnotation | CREATE_TRANSFER_OPERATION_INPUT_RANGE_ANNOTATION | PARTIAL | Guard differs |
| CreateTransferObjectTypeAnnotationClass | CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS | MATCH | |
| CreateMappedTransferObject | CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS | MATCH | |
| CreateUnmappedTransferObject | CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS | MATCH | |
| CreateReferenceClassForEntityType | CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE | MATCH | |
| CreateTransferObjectAttribute | CREATE_TRANSFER_ATTRIBUTE | MATCH | |
| CreateTransferObjectRelation | CREATE_TRANSFER_OBJECT_RELATION | MATCH | |
| CreateDataReferenceBinding | CREATE_DATA_REFERENCE_BINDING | MATCH | |
| CreateNavigationReferenceBinding | CREATE_NAVIGATION_REFERENCE_BINDING | MATCH | |
| CreateQueryCustomizerAnnotationForQueryCustomizerClass | CREATE_QUERY_CUSTOMIZER_ANNOTATION | MATCH | |
| CreateMetadataAnnotationForMetadataClass | CREATE_METADATA_ANNOTATION | MATCH | |
| CreateGetRangeInputAnnotationForGetRangeInputClass | CREATE_GET_RANGE_INPUT_ANNOTATION | MATCH | |
| CreateEnumeration | CREATE_ENUMERATION | MATCH | |
| CreateStringType | CREATE_STRING_TYPE | MATCH | |
| CreateIntegerType | CREATE_INTEGER_TYPE | MATCH | |
| CreateDecimalType | CREATE_DECIMAL_TYPE | MATCH | |
| CreateMeasuredAnnotationOfIntegerType | CREATE_MEASURED_ANNOTATION_OF_INTEGER_TYPE | MATCH | |
| CreateBooleanType | CREATE_BOOLEAN_TYPE | MATCH | |
| CreateBinaryType | CREATE_BINARY_TYPE | MATCH | |
| CreateDateType | CREATE_DATE_TYPE | MATCH | |
| CreateTimestampType | CREATE_TIMESTAMP_TYPE | MATCH | |
| CreateTimeType | CREATE_TIME_TYPE | MATCH | |
| CreateCustomType | CREATE_CUSTOM_TYPE | MATCH | |

#### Missing Rules in Zeta (45 total)
1. CreateIdentifierAnnotationForAttribute
2. CreateDocumentationAnnotationForAtrributes
3. CreateDocumentationAnnotationForAssociationEndRelation
4. CreateDocumentationAnnotationForContainmentRelation
5. CreatePrimitiveAccessorExpressionAnnotation
6. CreateReferenceAccessorExpressionAnnotation
7. CreateDocumentationAnnotationForDataProperty
8. CreateDocumentationAnnotationForNavigationProperty
9. CreateDocumentationAnnotationForBoundOperation
10. CreateDocumentationAnnotationForTransferOperation
11. CreateDocumentationAnnotationForInputParameter
12. CreateDocumentationAnnotationForOutputParameter
13. CreateDocumentationAnnotationForBoundOutputParameter
14. CreateDocumentationAnnotationForBoundInputParameter
15. CreateScriptBodyAnnotationForBoundOperation
16. CreateScriptBodyAnnotationForUnboundOperation
17. CreateCustomImplementationAnnotationOnOperation
18. CreateCustomImplementationAnnotationOnBoundOperation
19. CreateCustomImplementationAnnotationOnUnboundOperation
20. CreateStatefulAnnotationOnOperation
21. CreateStatefulAnnotationOnOperationWithoutImplementationAndBehaviour
22. CreateStatefulAnnotationOnOperationWithBehaviour
23. CreateOperationPermissions
24. CreateImmutableFlagForTransferOperation
25. CreateBoundAnnotationForTransferOperation
26. AddBehaviourAnnotation
27. CreateTransferObjectRelationEmbeddedFlags
28. CreateTransferObjectRelationRangeAnnotation
29. CreateTransferObjectRelationAccessAnnotation
30. AddTransientAnnotationToTransferObjectRelation
31. AddTransferAttributeConstraints (abstract)
32. AddStringTransferAttributeConstraints
33. AddCustomTransferAttributeConstraints
34. AddNumericTransferAttributeConstraints
35. AddMeasuredTransferAttributeConstraints
36. CreateTransferObjectAttributeBindingAnnotation
37. CreateTransferAttributeParameterizedAnnotation
38. AddDefaultAnnotationToTransferAttribute
39. CreateTransferAttributeClaimAnnotation
40. CreateDocumentationAnnotationForTransferAttribute
41. CreateDocumentationAnnotationForTransferObjectRelation
42. CreateStaticQueryAttribute
43. CreateStaticDataQueryAnnotation
44. CreateStaticNavigationQueryAnnotation
45. CreateStaticQueryNavigation

#### Topological Order for PSM2ASM Refactoring

```
Level 1 (No dependencies):
├── CREATE_ENUMERATION
├── CREATE_STRING_TYPE
├── CREATE_INTEGER_TYPE
├── CREATE_DECIMAL_TYPE
├── CREATE_BOOLEAN_TYPE
├── CREATE_BINARY_TYPE
├── CREATE_DATE_TYPE
├── CREATE_TIMESTAMP_TYPE
├── CREATE_TIME_TYPE
├── CREATE_CUSTOM_TYPE
├── CREATE_MEASURED_ANNOTATION_OF_INTEGER_TYPE

Level 2 (Depends on types):
├── MODEL_TO_PACKAGE
├── PACKAGE_TO_PACKAGE
├── NAMESPACE_TO_PACKAGE

Level 3 (Depends on namespaces):
├── CREATE_ENTITY_CLASS
├── CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE

Level 4 (Depends on entities):
├── CREATE_ATTRIBUTE
├── CREATE_ASSOCIATION_END_RELATION
├── CREATE_CONTAINMENT_RELATION
├── CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE
├── CREATE_NAVIGATION_PROPERTY

Level 5 (Depends on entities/attributes):
├── CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS
├── CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS
├── CREATE_TRANSFER_ATTRIBUTE
├── CREATE_TRANSFER_OBJECT_RELATION

Level 6 (Depends on transfer objects):
├── CREATE_BOUND_OPERATION
├── CREATE_UNBOUND_OPERATION
├── CREATE_BOUND_TRANSFER_OPERATION
├── CREATE_QUERY_CUSTOMIZER_ANNOTATION
├── CREATE_METADATA_ANNOTATION
├── CREATE_GET_RANGE_INPUT_ANNOTATION

Level 7 (Depends on operations):
├── All operation annotation rules
├── All transfer object annotation rules
```

### ASM2RDBMS (24 ETL rules → 22 Zeta rules)

#### Rule Mapping Analysis

| ETL Rule | Zeta Rule | Status | Notes |
|----------|-----------|--------|-------|
| EAttributeToRdbmsField | EATTRIBUTE_TO_RDBMS_FIELD | MATCH | |
| EAttributeToTableValueField | EATTRIBUTE_TO_TABLE_VALUE_FIELD | MATCH | |
| EAttributeToIndex | EATTRIBUTE_TO_INDEX | MATCH | |
| EClassToRdbmsTable | ECLASS_TO_RDBMS_TABLE | PARTIAL | Uses named equivalent |
| EClassToTableIdField | ECLASS_TO_TABLE_ID_FIELD | MATCH | |
| EClassToTableTypeField | ECLASS_TO_TABLE_TYPE_FIELD | MATCH | |
| EClassToTableVersionField | ECLASS_TO_TABLE_VERSION_FIELD | MATCH | |
| EClassToTableCreateUsernameField | ECLASS_TO_TABLE_CREATE_USERNAME_FIELD | MATCH | |
| EClassToTableCreateUserIdField | ECLASS_TO_TABLE_CREATE_USER_ID_FIELD | MATCH | |
| EClassToTableCreateTimestampField | ECLASS_TO_TABLE_CREATE_TIMESTAMP_FIELD | MATCH | |
| EClassToTableUpdateUsernameField | ECLASS_TO_TABLE_UPDATE_USERNAME_FIELD | MATCH | |
| EClassToTableUpdateUserIdField | ECLASS_TO_TABLE_UPDATE_USER_ID_FIELD | MATCH | |
| EClassToTableUpdateTimestampField | ECLASS_TO_TABLE_UPDATE_TIMESTAMP_FIELD | MATCH | |
| rootPackegeToModel | ROOT_PACKAGE_TO_MODEL | MATCH | |
| rootPackegeToConfiguration | ROOT_PACKAGE_TO_CONFIGURATION | MATCH | |
| EReferenceToRdbmsTableForeignKey | EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY | MATCH | |
| EReferenceToRdbmsTableInverseForeignKey | EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY | MATCH | |
| EReferenceToRdbmsJunctionTable | EREFERENCE_TO_RDBMS_JUNCTION_TABLE | PARTIAL | Uses @Lazy, not @Greedy |
| EReferenceToRdbmsJunctionTablePrimaryKey | EREFERENCE_TO_RDBMS_JUNCTION_TABLE_PRIMARY_KEY | MATCH | |
| EReferenceToRdbmsJunctionTableForeignKeyBidirectional | EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_BIDIRECTIONAL | MATCH | |
| EReferenceToRdbmsJunctionTableForeignKeyUnidirectional | EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_1 | MATCH | |

#### Missing Rules in Zeta (2 total)
1. Excel2NameMapping
2. Excel2Rule
3. Excel2TypeMapping (3 rules for Excel mappings - lower priority)

#### Topological Order for ASM2RDBMS Refactoring

```
Level 1:
├── ROOT_PACKAGE_TO_MODEL
├── ROOT_PACKAGE_TO_CONFIGURATION

Level 2:
├── ECLASS_TO_RDBMS_TABLE

Level 3:
├── ECLASS_TO_TABLE_ID_FIELD
├── ECLASS_TO_TABLE_TYPE_FIELD
├── ECLASS_TO_TABLE_VERSION_FIELD
├── ECLASS_TO_TABLE_CREATE_USERNAME_FIELD
├── ECLASS_TO_TABLE_CREATE_USER_ID_FIELD
├── ECLASS_TO_TABLE_CREATE_TIMESTAMP_FIELD
├── ECLASS_TO_TABLE_UPDATE_USERNAME_FIELD
├── ECLASS_TO_TABLE_UPDATE_USER_ID_FIELD
├── ECLASS_TO_TABLE_UPDATE_TIMESTAMP_FIELD

Level 4:
├── EATTRIBUTE_TO_RDBMS_FIELD
├── EATTRIBUTE_TO_TABLE_VALUE_FIELD
├── EATTRIBUTE_TO_INDEX

Level 5:
├── EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY
├── EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY
├── EREFERENCE_TO_RDBMS_JUNCTION_TABLE
```

### RDBMS2LIQUIBASE (62 ETL rules → 14 Zeta rules)

#### Rule Mapping Analysis

| ETL Rule | Zeta Rule | Status | Notes |
|----------|-----------|--------|-------|
| TableToCreateTable | TABLE_TO_CREATE_TABLE | MATCH | |
| TableToCreateTableChangeSet | TABLE_TO_CREATE_TABLE_CHANGESET | MATCH | |
| TableToCreateForeignKeysChangeSet | TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET | MATCH | |
| TableToAddNotNullChangeSet | TABLE_TO_ADD_NOT_NULL_CHANGESET | MATCH | |
| FieldToColumn | FIELD_TO_COLUMN | MATCH | |
| IdentifierFieldToCreateTableColumn | IDENTIFIER_FIELD_TO_COLUMN | MATCH | |
| IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint | IDENTIFIER_FIELD_TO_PK_CONSTRAINT | MATCH | |
| ValueFieldToCreateTableColumn | VALUE_FIELD_TO_COLUMN | MATCH | |
| ForeignKeyFieldToAddForeignKeyConstraint | FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT | MATCH | |
| FieldToCreateTableAddNotNullConstraint | FIELD_TO_ADD_NOT_NULL_CONSTRAINT | MATCH | |
| IndexToCreateIndex | INDEX_TO_CREATE_INDEX | MATCH | |
| AddUniqueConstraints | UNIQUE_CONSTRAINT_TO_ADD_UNIQUE | MATCH | |

#### Missing Rules in Zeta (50 total)
All incremental, backup, checkup, and SQL file generation rules are missing.

#### Topological Order for RDBMS2LIQUIBASE Refactoring

```
Level 1:
├── TABLE_TO_CREATE_TABLE

Level 2:
├── TABLE_TO_CREATE_TABLE_CHANGESET
├── TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET
├── TABLE_TO_ADD_NOT_NULL_CHANGESET

Level 3:
├── FIELD_TO_COLUMN
├── IDENTIFIER_FIELD_TO_COLUMN
├── VALUE_FIELD_TO_COLUMN
├── FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT
├── FIELD_TO_ADD_NOT_NULL_CONSTRAINT

Level 4:
├── IDENTIFIER_FIELD_TO_PK_CONSTRAINT
├── INDEX_TO_CREATE_INDEX
├── UNIQUE_CONSTRAINT_TO_ADD_UNIQUE
```

### PSM2MEASURE (5 ETL rules → 4 Zeta rules)

#### Rule Mapping Analysis

| ETL Rule | Zeta Rule | Status | Notes |
|----------|-----------|--------|-------|
| CreateBaseMeasure | BASE_MEASURE | MATCH | |
| CreateDerivedMeasure | DERIVED_MEASURE | MATCH | |
| CreateUnit | UNIT | MATCH | |
| CreateDurationUnit | DURATION_UNIT | MATCH | |

#### Missing Rules in Zeta (1 total)
1. CreateMeasure (abstract - base for both base and derived)

#### Topological Order for PSM2MEASURE Refactoring

```
Level 1:
├── BASE_MEASURE
├── DERIVED_MEASURE

Level 2:
├── UNIT
├── DURATION_UNIT
```

### ASM2KEYCLOAK (3 ETL rules → 3 Zeta rules)

#### Rule Mapping Analysis

| ETL Rule | Zeta Rule | Status | Notes |
|----------|-----------|--------|-------|
| CreateKeycloakClient | CREATE_KEYCLOAK_CLIENT | MATCH | |
| CreateKeycloakClientClaim | CREATE_KEYCLOAK_CLIENT_CLAIM | MATCH | |

#### Topological Order for ASM2KEYCLOAK Refactoring

```
Level 1:
├── CREATE_KEYCLOAK_CLIENT

Level 2:
├── CREATE_KEYCLOAK_CLIENT_CLAIM
```

## Key Differences Summary

### 1. Annotation Handling

**ETL Pattern:**
```epsilon
rule CreateEntityAnnotationClass
    transform s : JUDOPSM!EntityType
    to t : ASM!EAnnotation {
    t.setId("(psm/" + s.getId() + ")/EntityAnnotationClass");
    t.source = asmUtils.getAnnotationUri("entity");
    s.equivalent("CreateEntityClass").eAnnotations.add(t);
}
```

**Current Zeta Pattern (INCORRECT):**
```java
@TransformRule(name = CREATE_ENTITY_CLASS)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        // Inline annotation - WRONG
        EAnnotation entityAnnotation = createAnnotation(...);
        addAnnotation(t, entityAnnotation);
        return t;
    };
}
```

**Corrected Zeta Pattern:**
```java
@TransformRule(name = CREATE_ENTITY_CLASS)
@Greedy
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        // NO annotations here
        return t;
    };
}

@TransformRule(name = CREATE_ENTITY_ANNOTATION_CLASS)
@Greedy
@Transform(type = EntityType.class)
@To(type = EAnnotation.class)
public TransformFunction<EntityType, EAnnotation> createEntityAnnotationClass() {
    return (s, ctx) -> {
        EAnnotation t = ctx.createTarget(EAnnotation.class);
        t.setSource(getAnnotationUri("entity"));
        EClass entityClass = ctx.equivalent(s, EClass.class, CREATE_ENTITY_CLASS);
        addAnnotation(entityClass, t);
        return t;
    };
}
```

### 2. Named Equivalent Usage

**ETL Pattern:**
```epsilon
for (super in s.superEntityTypes) {
    t.eSuperTypes.add(super.equivalent("CreateEntityClass"));
}
```

**Current Zeta Pattern (INCORRECT):**
```java
for (EntityType superType : s.getSuperEntityTypes()) {
    EClass superClass = ctx.equivalent(superType, EClass.class);  // WRONG
    addSuperType(t, superClass);
}
```

**Corrected Zeta Pattern:**
```java
for (EntityType superType : s.getSuperEntityTypes()) {
    EClass superClass = ctx.equivalent(superType, EClass.class, CREATE_ENTITY_CLASS);  // CORRECT
    addSuperType(t, superClass);
}
```

### 3. Guard Methods

All guards should be implemented identically to ETL:

| ETL Guard | Zeta Guard | Status |
|-----------|------------|--------|
| `s.dataType.isKindOf(JUDOPSM!StringType)` | `isStringType(s.getDataType())` | Implement |
| `s.isPrimitive()` | `isPrimitiveAttribute(s)` | Implement |
| `s.isKindOf(JUDOPSM!NumericType) and not s.dataType.isKindOf(JUDOPSM!MeasuredType)` | `isNumericNotMeasured(s.getDataType())` | Implement |

## Impact Analysis

### Files to Modify

1. **psm2asm** (8 files):
   - TypeRules.java
   - NamespaceRules.java
   - DataRules.java
   - TransferObjectRules.java
   - OperationRules.java
   - ActorRules.java
   - DerivedRules.java
   - StaticRules.java

2. **asm2rdbms** (4 files):
   - PackageRules.java
   - ClassRules.java
   - AttributeRules.java
   - ReferenceRules.java

3. **rdbms2liquibase** (2 files):
   - TableRules.java
   - FieldRules.java

4. **psm2measure** (2 files):
   - MeasureRules.java
   - UnitRules.java

5. **asm2keycloak** (2 files):
   - RealmRules.java
   - ClientRules.java

### Test Files to Validate

1. `**/Psm2AsmDualTransformationTest.java`
2. `**/Asm2RdbmsDualTransformationTest.java`
3. `**/Rdbms2LiquibaseDualTransformationTest.java`
4. `**/Psm2MeasureDualTransformationTest.java`
5. `**/Asm2KeycloakDualTransformationTest.java`
6. `**/Psm2AsmExternalModelTest.java`
7. `**/Asm2RdbmsExternalModelTest.java`
