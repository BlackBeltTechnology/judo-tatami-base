# ZETA Parallel Execution

[← Back to ZETA Index](../ZETA.md)

---

## Overview

ZETA supports thread-safe parallel execution for large models using a two-phase staging approach. This provides significant performance improvements over sequential execution.

---

## How It Works

### Phase 1: Parallel Transformation

- Elements are created and transformed in parallel threads
- Created elements are staged in a thread-safe queue
- Element ordering is tracked via atomic sequence numbers
- XMI IDs are deferred until commit phase

### Phase 2: Sequential Commit

- Staged elements are committed to the target Resource
- Elements are sorted by creation sequence for deterministic ordering
- XMI IDs are applied after elements are added to the Resource
- Single-threaded to ensure EMF thread-safety

---

## Thread-Safety Guidelines

### Safe Operations (DO)

| Operation | Why Safe |
|-----------|----------|
| `ctx.createTarget()` | Creates new element in thread-local staging |
| Set properties on your elements | You own the element you created |
| `ctx.equivalent()` | Thread-safe cache lookup/creation |
| Read from source elements | Source model is read-only |
| `ctx.call()` | Extension registry is thread-safe |
| `ctx.put()` / `ctx.get()` | Cache is thread-safe |

### Unsafe Operations (DON'T)

| Operation | Why Unsafe |
|-----------|------------|
| Modify source elements | Source should be read-only |
| Modify elements from other rules | Race condition |
| Shared mutable state | Race condition |
| Non-thread-safe collections | Concurrent modification |
| Direct EMF list operations | EMF ELists not thread-safe |

---

## Thread-Safe Collection Operations

When running in parallel mode, EMF ELists are not thread-safe. Use synchronized helpers:

### Synchronized Add Helper

```java
public class TransformationHelper {

    public static void addToPackage(EPackage pkg, EClassifier classifier) {
        if (pkg != null && classifier != null) {
            synchronized (pkg.getEClassifiers()) {
                pkg.getEClassifiers().add(classifier);
            }
        }
    }

    public static void addToClass(EClass eClass, EStructuralFeature feature) {
        if (eClass != null && feature != null) {
            synchronized (eClass.getEStructuralFeatures()) {
                eClass.getEStructuralFeatures().add(feature);
            }
        }
    }

    public static void addAnnotation(EModelElement element, EAnnotation annotation) {
        if (element != null && annotation != null) {
            synchronized (element.getEAnnotations()) {
                element.getEAnnotations().add(annotation);
            }
        }
    }
}
```

### Usage in Rules

```java
@Transform(type = Attribute.class)
public TransformFunction<Attribute, EAttribute> createAttribute() {
    return (source, ctx) -> {
        EAttribute attr = ctx.createTarget(EAttribute.class);
        attr.setName(source.getName());

        // Get containing class
        EClass container = ctx.equivalent(source.eContainer(), EClass.class);

        // Thread-safe add
        TransformationHelper.addToClass(container, attr);

        return attr;
    };
}
```

---

## Example: Thread-Safe Rule

```java
@TransformRule(name = "CreateTransferObject")
@Transform(type = TransferObjectType.class)
public TransformFunction<TransferObjectType, EClass> createTransferObject() {
    return (source, ctx) -> {
        // Safe: create new target element
        EClass target = ctx.createTarget(EClass.class);

        // Safe: set properties on our created element
        target.setName(source.getName());
        target.setAbstract(source.isAbstract());

        // Safe: get equivalent (thread-safe lazy execution)
        EPackage pkg = ctx.equivalent(source.eContainer(), EPackage.class);

        // Thread-safe: synchronized add
        TransformationHelper.addToPackage(pkg, target);

        // Safe: read from source and transform children
        for (Attribute attr : source.getAttributes()) {
            EAttribute eAttr = ctx.equivalent(attr, EAttribute.class);
            TransformationHelper.addToClass(target, eAttr);
        }

        return target;
    };
}
```

---

## Post-Processing for Cross-References

Some cross-references cannot be set during parallel execution because both elements must exist first. Use post-processing:

```java
@PostExecution
public void setEOpposite(TransformationContext ctx) {
    // After all elements exist, set bidirectional references
    for (EReference ref : getAllReferences(ctx)) {
        if (ref.getEOpposite() == null) {
            EReference opposite = findOpposite(ref, ctx);
            if (opposite != null) {
                ref.setEOpposite(opposite);
                opposite.setEOpposite(ref);
            }
        }
    }
}
```

---

## Deterministic Ordering

ZETA maintains deterministic ordering even in parallel mode:

1. **Creation sequence numbers** - Each element gets an atomic sequence number
2. **Sorted commit** - Elements are committed in sequence order
3. **Reproducible results** - Same input always produces same output order

This ensures XMI files are identical across runs.

---

## Performance Characteristics

| Metric | Value |
|--------|-------|
| Default parallel threshold | 1000 elements |
| Default chunk size | 100 elements |
| Thread pool | ForkJoinPool (work-stealing) |
| Expected speedup | 2-4x on 8-core CPU |
| PSM2ASM speedup | ~7.5x over ETL |

---

## When NOT to Use Parallel

Parallel execution may not help or may cause issues when:

1. **Small models** (< 1000 elements) - Overhead exceeds benefit
2. **Heavy cross-references** - Too many synchronized operations
3. **Complex circular dependencies** - May hit caching limitations
4. **Debugging** - Sequential is easier to debug

Use sequential mode for debugging:
```java
TransformationExecutor executor = TransformationExecutor.builder()
    .registry(registry)
    .context(context)
    .parallel(false)  // Disable parallel for debugging
    .build();
```
