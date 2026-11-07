# ZETA Annotations Reference

[← Back to ZETA Index](../ZETA.md)

---

## Context Annotations

| Annotation | Target | Description |
|------------|--------|-------------|
| `@TransformationContext` | Class | Marks a class as containing transformation rules |
| `@TransformRule` | Method | Defines a transformation rule |
| `@Transform` | Method | Declares source type for rule |

---

## Rule Modifiers

### @Transform

Declares the source type for type-based filtering:

```java
@Transform(type = BoundTransferObjectType.class)
public TransformFunction<BoundTransferObjectType, EClass> createBTO() {
    // Only evaluates against BoundTransferObjectType elements
}
```

### @Lazy

Rule executes on-demand via `equivalent()` calls, not during initial pass:

```java
@Lazy
@Transform(type = Element.class)
public TransformFunction<Element, EAnnotation> createAnnotation() {
    // Only triggered when ctx.equivalent(source, "createAnnotation") is called
}
```

**Note:** Must combine with `@Greedy` for `equivalent()` to find this rule automatically.

### @Greedy

Matches source type AND all subtypes (kind-of semantics):

```java
@Greedy
@Transform(type = TransferObjectType.class)  // Matches all subclasses too
public TransformFunction<TransferObjectType, EClass> createTO() {
    // Executes for BoundTransferObjectType, UnboundTransferObjectType, etc.
}
```

### @Lazy @Greedy (Combined)

Equivalent to ETL `@lazy` - rule registered for all sources but only executes on demand:

```java
@Lazy
@Greedy
@Transform(type = Element.class)
public TransformFunction<Element, EAnnotation> createAnnotation() {
    // Registered for all Element instances
    // Only executes when ctx.equivalent() is called
}
```

### @Abstract

Rule only executes via `executeParentRule()`, not directly:

```java
@Abstract
@Transform(type = NamedElement.class)
public TransformFunction<NamedElement, ENamedElement> createNamed() {
    return (source, ctx) -> {
        ENamedElement target = ctx.createTarget(ENamedElement.class);
        target.setName(source.getName());
        return target;
    };
}
```

### @Primary

Rule's result takes precedence in `equivalent()` lookups:

```java
@Primary
@Transform(type = EntityType.class)
public TransformFunction<EntityType, EClass> createEntity() {
    // This result is returned by ctx.equivalent(source, EClass.class)
}
```

### @Extends

Inherits from parent rules - parent executes first:

```java
@Extends("createNamed")
@Transform(type = EntityType.class)
public TransformFunction<EntityType, EClass> createEntity() {
    return (source, ctx) -> {
        EClass target = ctx.executeParentRule("createNamed", source);
        // target already has name set from parent
        target.setAbstract(source.isAbstract());
        return target;
    };
}
```

### @Guard

Conditional execution based on guard method:

```java
@Guard(method = "hasOutput")
@Transform(type = TransferOperation.class)
public TransformFunction<TransferOperation, EOperation> createOperation() {
    // Only executes if hasOutput() returns true
}

public boolean hasOutput(EObject eObject, TransformationContext ctx) {
    return ((TransferOperation) eObject).getOutput() != null;
}
```

### @Detached

Output NOT added to Resource.contents - caller adds to container:

```java
@Lazy
@Detached
@Transform(type = Attribute.class)
public TransformFunction<Attribute, EAttribute> createAttribute() {
    return (source, ctx) -> {
        EAttribute attr = ctx.createTarget(EAttribute.class);
        // NOT added to Resource.contents
        // Caller must add: eClass.getEStructuralFeatures().add(attr)
        return attr;
    };
}
```

---

## Lifecycle Hooks

### @PreExecution

Method runs before transformation starts:

```java
@PreExecution
public void setup(TransformationContext ctx) {
    ctx.put("config", loadConfiguration());
}
```

### @PostExecution

Method runs after transformation completes:

```java
@PostExecution
public void cleanup(TransformationContext ctx) {
    fixCrossReferences(ctx);
}
```

---

## Annotation Combinations

| Combination | Use Case |
|-------------|----------|
| `@Lazy @Greedy` | On-demand rules findable by `equivalent()` |
| `@Abstract @Extends` | Inheritance hierarchy |
| `@Lazy @Detached` | Contained elements created on demand |
| `@Primary @Greedy` | Default transformation for type hierarchy |
| `@Guard @Greedy` | Conditional transformation for subtypes |
