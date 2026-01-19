# ZETA API Reference

[← Back to ZETA Index](../ZETA.md)

---

## TransformContext Methods

### ctx.equivalent(source, ruleName)

**Purpose:** Get or create target element via named rule.

**Signature:**
```java
<T> T equivalent(EObject source, String ruleName);
<T> T equivalent(EObject source, Class<T> targetClass);
```

**Behavior:**
1. Check cache for existing target (key: source + ruleName)
2. If not found and rule is `@Lazy @Greedy`, trigger rule
3. Cache result and return

**Requirements:**
- Rule must have `@Lazy @Greedy` for automatic registration and triggering
- `@Lazy` alone without `@Greedy` = rule NOT registered for all sources
- Works from both rule bodies and utility methods

**Example:**
```java
// By rule name
EClass target = ctx.equivalent(source, "CreateTransferObject");

// By target type (finds matching rule)
EClass target = ctx.equivalent(source, EClass.class);
```

---

### ctx.equivalentDiscriminated(source, targetClass, ruleName, discriminator)

**Purpose:** Get or create discriminated target element via named rule.

**Signature:**
```java
<T> T equivalentDiscriminated(EObject source, Class<T> targetClass,
                               String ruleName, String discriminator);
```

**Behavior:**
1. Check cache for existing target (key: source + ruleName + discriminator)
2. If not found and rule is `@Lazy`, trigger rule
3. Cache result at END of rule execution
4. Return cached result

**Circular Dependency Handling:**
```
Rule A calls equivalentDiscriminated() → triggers Rule B
Rule B calls something → triggers Rule A again
Rule A lookup fails (not cached yet - caching at END)
Returns NULL
```

**Recommended Pattern:**
```java
// Try equivalentDiscriminated first
EAnnotation ann = ctx.equivalentDiscriminated(
    source, EAnnotation.class, RULE_NAME, discriminator);

if (ann == null) {
    // Fallback for circular dependencies - create inline
    ann = EcoreFactory.eINSTANCE.createEAnnotation();
    ann.setSource(annotationSource);
}
return ann;
```

---

### ctx.createTarget(targetClass)

**Purpose:** Create new target element.

**Signature:**
```java
<T extends EObject> T createTarget(Class<T> targetClass);
```

**Behavior:**
- Creates EMF object of specified type
- Adds to Resource.contents (unless `@Detached` rule)
- Generates XMI ID based on current context

**Example:**
```java
EClass target = ctx.createTarget(EClass.class);
target.setName(source.getName());
```

---

### ctx.getElementId(element) / ctx.setElementId(element, id)

**Purpose:** Get/set XMI ID handling deferred assignment.

**Signatures:**
```java
String getElementId(EObject element);
void setElementId(EObject element, String id);
```

**Why use ctx.getElementId()?**

ZETA uses **deferred ID assignment** for target elements:
1. `ctx.setElementId(element, id)` stores ID in `pendingXmiIds` map
2. IDs are applied to XMI resource later (during commit phase)
3. `XMIResource.getID()` returns `null` until commit

**Which method to use:**

| Element Type | Method | Example |
|--------------|--------|---------|
| Source (PSM) | `IdExtensions.getId(element)` | `getId(transferObject)` |
| Target (ASM) | `ctx.getElementId(element)` | `ctx.getElementId(eClass)` |

**Example:**
```java
// Setting ID on target
ctx.setElementId(target, "(psm/" + getId(source) + ")/TransferObject");

// Getting ID from target (handles deferred)
String id = ctx.getElementId(target);
```

---

### ctx.put(key, value) / ctx.get(key)

**Purpose:** Store/retrieve values in transformation cache.

**Signatures:**
```java
void put(String key, Object value);
<T> T get(String key);
```

**Example:**
```java
// Store for later use
ctx.put("rootPackage", rootPkg);

// Retrieve in another rule
EPackage root = ctx.get("rootPackage");
```

---

### ctx.executeParentRule(ruleName, source)

**Purpose:** Execute parent rule in inheritance chain.

**Signature:**
```java
<T> T executeParentRule(String ruleName, EObject source);
```

**Example:**
```java
@Extends("createNamedElement")
public TransformFunction<EntityType, EClass> createEntity() {
    return (source, ctx) -> {
        // Execute parent first
        EClass target = ctx.executeParentRule("createNamedElement", source);
        // Add entity-specific properties
        target.setAbstract(source.isAbstract());
        return target;
    };
}
```

---

### ctx.call(source, methodName, returnType)

**Purpose:** Invoke extension method through registry (enables @Cached).

**Signature:**
```java
<T> T call(EObject source, String methodName, Class<T> returnType);
```

**Why use ctx.call()?**

Direct static method calls bypass `@Cached` annotation:

```java
// WRONG: @Cached NOT applied!
String name = PsmExtensions.getFullyQualifiedName(source);

// CORRECT: @Cached IS applied!
String name = ctx.call(source, "getFullyQualifiedName", String.class);
```

---

## Common Patterns

### Create and Configure

```java
return (source, ctx) -> {
    EClass target = ctx.createTarget(EClass.class);
    target.setName(source.getName());
    ctx.setElementId(target, "(psm/" + getId(source) + ")/EClass");
    return target;
};
```

### Resolve Reference

```java
return (source, ctx) -> {
    EClass target = ctx.createTarget(EClass.class);

    // Resolve supertype
    EClass superType = ctx.equivalent(source.getSuperType(), EClass.class);
    if (superType != null) {
        target.getESuperTypes().add(superType);
    }

    return target;
};
```

### Discriminated Creation

```java
// Create different annotations for same source
EAnnotation typeAnn = ctx.equivalentDiscriminated(
    source, EAnnotation.class, "CreateAnnotation", "type");
EAnnotation docAnn = ctx.equivalentDiscriminated(
    source, EAnnotation.class, "CreateAnnotation", "documentation");
```
