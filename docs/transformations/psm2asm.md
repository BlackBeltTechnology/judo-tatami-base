# PSM to ASM Transformation

## Overview

The PSM to ASM transformation converts Platform Specific Model (PSM) elements to Abstract Semantic Model (ASM) elements. This is the primary transformation that maps the domain model to the runtime execution model.

**Module:** `judo-tatami-psm2asm`  
**Source Model:** PSM (hu.blackbelt.judo.meta.psm)  
**Target Model:** ASM (hu.blackbelt.judo.meta.asm / Ecore)

## ETL Files

| File | Purpose |
|------|---------|
| `psmToAsm.etl` | Main orchestrator, imports all modules |
| `modules/namespace.etl` | Namespace and package transformations |
| `modules/type.etl` | Type transformations (primitives, enums) |
| `modules/data.etl` | Entity and attribute transformations |
| `modules/derived.etl` | Derived attribute transformations |
| `modules/operation.etl` | Operation transformations |
| `modules/transferObject.etl` | Transfer object transformations |
| `modules/actor.etl` | Actor and access point transformations |
| `modules/static.etl` | Static query and data transformations |

## Transformation Rules

### Namespace Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `NamespaceToPackage` | Namespace | EPackage | Abstract rule for namespace to package |
| `ModelToPackage` | Model | EPackage | Transform root model to root package |
| `ModelToPackageVersion` | Model | EAnnotation | Create version annotation |
| `PackageToPackage` | Package | EPackage | Transform sub-packages |
| `CreateDocumentationAnnotation` | NamedElement | EAnnotation | Add documentation annotations |

### Type Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateEnumeration` | EnumerationType | EEnum | Transform enumerations |
| `CreateStringType` | StringType | EDataType | Transform string types |
| `CreateIntegerType` | NumericType (integer) | EDataType | Transform integer types |
| `CreateDecimalType` | NumericType (decimal) | EDataType | Transform decimal types |
| `CreateMeasuredAnnotationOfIntegerType` | MeasuredType | EAnnotation | Add measure annotations |
| `CreateBooleanType` | BooleanType | EDataType | Transform boolean types |
| `CreateBinaryType` | BinaryType | EDataType | Transform binary types |
| `CreateDateType` | DateType | EDataType | Transform date types |
| `CreateTimestampType` | TimestampType | EDataType | Transform timestamp types |
| `CreateTimeType` | TimeType | EDataType | Transform time types |
| `CreateCustomType` | CustomType | EDataType | Transform custom types |

### Data Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateEntityClass` | EntityType | EClass | Transform entities to classes |
| `CreateEntityAnnotationClass` | EntityType | EAnnotation | Add entity annotation |
| `CreateAttribute` | Attribute | EAttribute | Transform attributes |
| `CreateAssociationEndRelation` | AssociationEnd | EReference | Transform associations |
| `CreateContainmentRelation` | Containment | EReference | Transform containments |
| `AddStringAttributeConstraints` | Attribute | EAnnotation | Add string constraints |
| `AddNumericAttributeConstraints` | Attribute | EAnnotation | Add numeric constraints |
| `AddMeasuredAttributeConstraints` | Attribute | EAnnotation | Add measure constraints |
| `CreateIdentifierAnnotationForAttribute` | Attribute | EAnnotation | Mark identifier attributes |
| `CreateNamespaceSequence` | NamespaceSequence | EAnnotation | Create namespace sequences |
| `CreateEntitySequence` | EntitySequence | EAnnotation | Create entity sequences |

### Derived Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateDerivedAttribute` | DerivedAttribute | EAttribute | Transform derived attributes |
| `CreateDataPropertyForDerivedAttribute` | DerivedAttribute | EAnnotation | Add data property |
| `CreateStaticNavigationForDerivedAttribute` | DerivedAttribute | EAnnotation | Add static navigation |

### Operation Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateBoundOperation` | BoundOperation | EOperation | Transform bound operations |
| `CreateUnboundOperation` | UnboundOperation | EOperation | Transform unbound operations |

### Transfer Object Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateTransferObjectTypeClass` | TransferObjectType | EClass | Transform transfer objects |
| `CreateMappedTransferObjectTypeClass` | MappedTransferObjectType | EClass | Transform mapped TOs |
| `CreateUnmappedTransferObjectTypeClass` | UnmappedTransferObjectType | EClass | Transform unmapped TOs |
| `CreateTransferAttribute` | TransferAttribute | EAttribute | Transform TO attributes |
| `CreateTransferRelation` | TransferObjectRelation | EReference | Transform TO relations |

### Actor Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateActorTypeClass` | ActorType | EClass | Transform actor types |
| `CreateAccessPointAnnotation` | ActorType | EAnnotation | Add access point annotation |
| `CreatePrincipalAnnotation` | ActorType | EAnnotation | Add principal annotation |

### Static Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateStaticQuery` | StaticQuery | EOperation | Transform static queries |
| `CreateStaticData` | StaticData | EClass | Transform static data |

## Type Mapping

### Primitive Type Mapping

| PSM Type | ASM Type | Java Instance Class |
|----------|----------|---------------------|
| StringType | EDataType | java.lang.String |
| BooleanType | EDataType | java.lang.Boolean |
| NumericType (precision ≤ 9) | EDataType | java.lang.Integer |
| NumericType (precision 10-19) | EDataType | java.lang.Long |
| NumericType (precision > 19) | EDataType | java.math.BigDecimal |
| DecimalType (precision ≤ 7) | EDataType | java.lang.Float |
| DecimalType (precision 8-15) | EDataType | java.lang.Double |
| DecimalType (precision > 15) | EDataType | java.math.BigDecimal |
| DateType | EDataType | java.time.LocalDate |
| TimestampType | EDataType | java.time.LocalDateTime |
| TimeType | EDataType | java.time.LocalTime |
| BinaryType | EDataType | byte[] |
| EnumerationType | EEnum | - |

## Annotations

The transformation creates various annotations on ASM elements:

| Annotation URI | Purpose |
|----------------|---------|
| `entity` | Marks a class as an entity |
| `constraints` | Stores attribute constraints |
| `documentation` | Stores documentation text |
| `identifier` | Marks identifier attributes |
| `measured` | Stores measurement unit info |
| `sequence` | Defines sequence parameters |
| `defaultRepresentation` | Links to default transfer object |
| `reverseCascadeDelete` | Enables reverse cascade delete |
| `unmappedDefaultOnly` | Marks unmapped default values |
| `ModelVersion` | Stores model version |

## Usage Example

```java
// Create source PSM model
PsmModel psmModel = new Demo().fullDemo();

// Create target ASM model
AsmModel asmModel = AsmModel.buildAsmModel().build();

// Execute transformation
Psm2AsmTransformationTrace trace = executePsm2AsmTransformation(
    psm2AsmParameter()
        .psmModel(psmModel)
        .asmModel(asmModel)
);

// Access transformation trace
Map<EObject, List<EObject>> resolvedTrace = trace.getTransformationTrace();
```

## Zeta Implementation

The Zeta implementation uses Java classes with `@TransformRule` annotations:

```java
@TransformationContext(
    sourceModel = PsmModel.class,
    targetModel = AsmModel.class
)
public class Psm2AsmTransformation {
    
    @TransformRule(name = Psm2AsmRuleNames.CREATE_ENTITY_CLASS)
    @Primary
    public EClass transformEntityType(EntityType entity, TransformationContext ctx) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName(entity.getName());
        eClass.setAbstract(entity.isAbstract());
        // ... transformation logic
        return eClass;
    }
}
```

Rule name constants are defined in `Psm2AsmRuleNames`:

```java
public final class Psm2AsmRuleNames {
    public static final String CREATE_ENTITY_CLASS = "CreateEntityClass";
    public static final String CREATE_ATTRIBUTE = "CreateAttribute";
    // ...
}
```
