# ZETA Known Limitations

[← Back to ZETA Index](../ZETA.md)

---

## Limitation 1: Circular Dependency Caching

### Description

ZETA caches elements at **END** of rule execution, not START. This differs from ETL which caches at START.

### Impact

Circular calls to `equivalentDiscriminated()` return `null`:

```
Rule A calls equivalentDiscriminated() → triggers Rule B
Rule B calls something → triggers Rule A again
Rule A lookup fails (not cached yet - caching at END)
Returns NULL
```

### ETL vs ZETA Comparison

| Framework | Caching Point | Circular Call Result |
|-----------|--------------|---------------------|
| ETL | START of rule | Partially-constructed element |
| ZETA | END of rule | NULL |

### Workaround

Use fallback pattern with inline creation:

```java
@Transform(type = Element.class)
public TransformFunction<Element, EAnnotation> createAnnotation() {
    return (source, ctx) -> {
        // Try equivalentDiscriminated first
        EAnnotation ann = ctx.equivalentDiscriminated(
            source, EAnnotation.class, RULE_NAME, discriminator);

        if (ann == null) {
            // Fallback for circular dependencies - create inline
            ann = EcoreFactory.eINSTANCE.createEAnnotation();
            ann.setSource(annotationSource);
            // Note: XMI ID will be different from equivalentDiscriminated path
        }

        return ann;
    };
}
```

### Framework Fix (Not Yet Implemented)

```java
// Pseudocode for potential fix
public <T> T equivalentDiscriminated(...) {
    // 1. Check cache
    T cached = getFromCache(source, ruleName, discriminator);
    if (cached != null) return cached;

    // 2. Create placeholder and cache IMMEDIATELY (like ETL)
    T placeholder = createEmptyTarget(targetClass);
    addToCache(source, ruleName, discriminator, placeholder);  // Cache at START

    // 3. Execute rule to populate placeholder
    executeRule(rule, source, placeholder);

    return placeholder;
}
```

---

## Limitation 2: @Lazy vs @Lazy @Greedy Semantics

### Description

ETL's `@lazy` annotation has different semantics than ZETA's `@Lazy`.

### ETL Behavior

`@lazy` = Rule is **registered** for all sources AND only **executes** when called via `equivalent()`

### ZETA Behavior

- `@Lazy` alone = Rule uses cached evaluation, may **NOT** be registered for all sources
- `@Greedy` = Rule **registered** for all matching sources
- `@Lazy @Greedy` = Equivalent to ETL `@lazy`

### Impact

If you port ETL `@lazy` to ZETA `@Lazy` (without `@Greedy`), `ctx.equivalent()` may not find the rule.

### Solution

Always use both annotations when porting ETL `@lazy`:

```java
// ETL
@lazy
rule CreateAnnotation transform s: PSM!Element to t: ASM!EAnnotation { ... }

// ZETA - Must use BOTH annotations
@Lazy
@Greedy  // Required for equivalent() to find this rule!
@Transform(type = Element.class)
public TransformFunction<Element, EAnnotation> createAnnotation() { ... }
```

---

## Limitation 3: @Cached Bypassed by Direct Calls

### Description

The `@Cached` annotation only works when methods are invoked through `ExtensionMethodRegistry`. Direct static method calls bypass caching.

### Impact

```java
// WRONG: @Cached NOT applied - no caching!
String name = PsmExtensions.getFullyQualifiedName(source);

// CORRECT: @Cached IS applied - results cached!
String name = ctx.call(source, "getFullyQualifiedName", String.class);
```

### Workaround

1. Always use `ctx.call()` for cached extension methods
2. Or implement manual caching in the extension method

See [Extensions Documentation](EXTENSIONS.md) for details.

---

## Limitation 4: Deferred ID Assignment

### Description

ZETA uses deferred ID assignment for target elements. IDs are stored in a map and applied during commit phase.

### Impact

`XMIResource.getID(element)` returns `null` for target elements until commit.

### Solution

Use `ctx.getElementId()` instead:

```java
// WRONG: May return null for target elements
String id = ((XMIResource) target.eResource()).getID(target);

// CORRECT: Handles deferred IDs
String id = ctx.getElementId(target);
```

### When to Use Which

| Element Type | Method |
|--------------|--------|
| Source (PSM) | `IdExtensions.getId(element)` |
| Target (ASM) | `ctx.getElementId(element)` |

---

## Limitation 5: EMF Thread Safety

### Description

EMF ELists are not thread-safe. Concurrent modifications during parallel execution can cause `ConcurrentModificationException` or data corruption.

### Impact

Direct list operations may fail in parallel mode:

```java
// UNSAFE in parallel mode
container.getEClassifiers().add(classifier);
```

### Solution

Use synchronized helpers:

```java
// SAFE in parallel mode
synchronized (container.getEClassifiers()) {
    container.getEClassifiers().add(classifier);
}
```

See [Parallel Execution](PARALLEL.md) for helper patterns.

---

## Limitation 6: Cross-Reference Timing

### Description

During parallel execution, elements created by different rules cannot reference each other until all rules complete.

### Impact

Setting cross-references during transformation may fail if the target element doesn't exist yet.

### Solution

Use post-processing for cross-references:

```java
@PostExecution
public void setCrossReferences(TransformationContext ctx) {
    // All elements exist now - safe to set cross-references
    for (EReference ref : getAllReferences(ctx)) {
        EClass targetType = findTargetType(ref, ctx);
        ref.setEType(targetType);
    }
}
```

See [Post-Processing](../patterns/POST_PROCESSING.md) for the 6-step pattern.

---

## Summary Table

| Limitation | Impact | Workaround |
|------------|--------|------------|
| Circular caching | `equivalentDiscriminated()` returns null | Fallback pattern |
| @Lazy semantics | `equivalent()` can't find rule | Use `@Lazy @Greedy` |
| @Cached bypass | No caching benefit | Use `ctx.call()` |
| Deferred IDs | `getID()` returns null | Use `ctx.getElementId()` |
| EMF thread safety | Concurrent modification | Synchronized helpers |
| Cross-reference timing | References may fail | Post-processing |
