# Rule Architecture - PSM2ASM ZETA Transformation

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Learn ZETA framework | [../ZETA.md](../ZETA.md) |
| See post-processing | [POST_PROCESSING.md](POST_PROCESSING.md) |

---

## Rule Classes Overview

The PSM2ASM transformation is organized into rule classes by PSM metamodel concept:

| Rule Class | PSM Types | Purpose |
|------------|-----------|---------|
| TypeRules | *TransferObjectType variants | Transfer object EClasses |
| AttributeRules | *Attribute | EAttributes |
| RelationRules | *Relation | EReferences |
| OperationRules | *Operation, TransferOperation | EOperations |
| ConstraintRules | *Constraint | Validation annotations |
| ActorRules | ActorType | Actor EClasses |
| EnumRules | EnumerationType | EEnums |
| EntityRules | EntityType | Entity EClasses |
| SequenceRules | SequenceType | Sequences |
| NavigationRules | Navigation* | Navigation annotations |

---

## Rule Structure

### Basic Rule Pattern

```java
@Transform(type = SourcePsmType.class)
public TransformFunction<SourcePsmType, TargetAsmType> ruleName() {
    return (ctx, source) -> {
        // Guard condition
        if (!shouldTransform(source)) {
            return null;  // Skip this element
        }

        // Create target element
        TargetAsmType target = AsmFactory.eINSTANCE.createTargetType();

        // Set properties
        target.setName(source.getName());

        // Resolve references using ctx.equivalent()
        EClass referenced = ctx.equivalent(source.getReferencedType());
        target.setEType(referenced);

        return target;
    };
}
```

### Key Components

1. **@Transform annotation** - Declares source type for type-based filtering
2. **Guard condition** - Determines if rule applies (return null to skip)
3. **Element creation** - Create ASM element using EFactory
4. **Property mapping** - Copy/transform properties from source
5. **Reference resolution** - Use `ctx.equivalent()` for cross-references

---

## Type-Based Filtering

The `@Transform` annotation's `type` parameter enables type-based filtering:

```java
// Broad type - evaluates against all TransferObject elements
@Transform(type = TransferObject.class)

// Specific type - only evaluates against BoundTransferObjectType elements
@Transform(type = BoundTransferObjectType.class)
```

### Type Hierarchy Impact

More specific types = fewer guard evaluations:

```
TransferObject (abstract)
├── BoundTransferObjectType    <- Use this for specific rules
├── UnboundTransferObjectType  <- Use this for specific rules
├── MappedTransferObjectType
└── ActorType
```

---

## Guard Patterns

### Simple Property Check
```java
if (source.isAbstract()) {
    return null;
}
```

### Type Check
```java
if (!(source.getOutput() instanceof BoundTransferObjectType)) {
    return null;
}
```

### Combined Conditions
```java
if (source.isStateless() || source.getBehaviour() == null) {
    return null;
}
```

---

## Reference Resolution

### Direct Equivalent
```java
EClass targetType = ctx.equivalent(source.getType());
```

### With Discriminator
```java
// For rules that need different results for same source
EClass targetType = ctx.equivalentDiscriminated(
    source.getType(),
    "discriminatorKey"
);
```

### Collection Resolution
```java
List<EClass> targetTypes = source.getTypes().stream()
    .map(ctx::equivalent)
    .filter(Objects::nonNull)
    .collect(Collectors.toList());
```

---

## Rule Organization

### By File
```
rules/
├── TypeRules.java        # Transfer object types
├── AttributeRules.java   # Attributes
├── RelationRules.java    # Relations/references
├── OperationRules.java   # Operations
├── ConstraintRules.java  # Constraints
├── ActorRules.java       # Actors
├── EnumRules.java        # Enumerations
├── EntityRules.java      # Entities
├── SequenceRules.java    # Sequences
└── NavigationRules.java  # Navigation
```

### Naming Convention
```
create[Target]For[Source]
```
Examples:
- `createTransferObjectTypeForBoundTransferObjectType`
- `createEAttributeForTransferAttribute`
- `createOperationPermissions`

---

## Common Patterns

### Creating Annotations
```java
EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
annotation.setSource(AsmConstants.ANNOTATION_SOURCE);
annotation.getDetails().put("key", "value");
target.getEAnnotations().add(annotation);
```

### Container Resolution
```java
EPackage container = ctx.equivalent(source.eContainer());
if (container != null) {
    container.getEClassifiers().add(target);
}
```

### Conditional Property Setting
```java
if (source.getDefaultValue() != null) {
    target.setDefaultValueLiteral(source.getDefaultValue().toString());
}
```
