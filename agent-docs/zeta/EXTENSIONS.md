# ZETA Extension Methods and @Cached

[← Back to ZETA Index](../ZETA.md)

---

## Overview

Extension methods provide reusable helper functionality for transformations. The `@Cached` annotation enables automatic result caching for expensive operations.

---

## CRITICAL: @Cached Only Works Through Registry

**The `@Cached` annotation is ONLY applied when methods are invoked through `ExtensionMethodRegistry.invoke()` or `ctx.call()`.** Direct static method calls bypass caching entirely!

```java
// WRONG: Direct static call - @Cached NOT applied, no caching!
String fqName = PsmExtensions.getFullyQualifiedName(source);

// CORRECT: Through TransformationContext.call() - @Cached IS applied!
String fqName = ctx.call(source, "getFullyQualifiedName", String.class);
```

---

## Performance Impact

This is a **major performance concern**. For example, `getFullyQualifiedName()` traverses the containment hierarchy on every call:

```java
@Cached  // Only works through registry!
public static String getFullyQualifiedName(NamedElement self) {
    String fqName = self.getName();
    EObject container = self.eContainer();
    if (container instanceof NamedElement) {
        fqName = getFullyQualifiedName((NamedElement) container) + "::" + fqName;
    }
    return fqName;
}
```

**Without caching (direct calls):**
- Each call traverses O(depth) containment hierarchy
- Repeated expensive computation
- Can add seconds to transformation time

**With caching (through registry):**
- First call computes and caches
- Subsequent calls return cached result
- Significant performance improvement

---

## How to Use @Cached Methods

### Option 1: Use ctx.call() (Recommended)

```java
@TransformRule(name = "MyRule")
public TransformFunction<NamedElement, ENamedElement> myRule() {
    return (source, ctx) -> {
        // Use ctx.call() which routes through ExtensionMethodRegistry
        String fqName = ctx.call(source, "getFullyQualifiedName", String.class);

        ENamedElement target = ctx.createTarget(ENamedElement.class);
        target.setName(fqName);
        return target;
    };
}
```

### Option 2: Manual Caching in Extension Methods

When `@Cached` can't be used (e.g., called from non-rule context):

```java
public class PsmExtensions {
    // Manual cache when @Cached can't be used
    private static final Map<EObject, String> fqNameCache = new ConcurrentHashMap<>();

    public static String getFullyQualifiedName(NamedElement self) {
        return fqNameCache.computeIfAbsent(self, s -> computeFqName((NamedElement) s));
    }

    private static String computeFqName(NamedElement self) {
        // Original expensive computation
        String fqName = self.getName();
        EObject container = self.eContainer();
        if (container instanceof NamedElement) {
            fqName = computeFqName((NamedElement) container) + "::" + fqName;
        }
        return fqName;
    }

    // Clear cache between transformations
    public static void clearCache() {
        fqNameCache.clear();
    }
}
```

---

## Defining Extension Methods

### Basic Extension Method

```java
public class PsmTypeExtensions {

    public static String getTypeName(Type type) {
        if (type instanceof PrimitiveType) {
            return ((PrimitiveType) type).getName();
        } else if (type instanceof EntityType) {
            return ((EntityType) type).getName();
        }
        return "Unknown";
    }
}
```

### Cached Extension Method

```java
public class PsmTypeExtensions {

    @Cached
    public static EDataType getAsmEquivalent(PrimitiveType type, TransformationContext ctx) {
        // Expensive lookup - will be cached
        return ctx.equivalent(type, EDataType.class);
    }
}
```

### Extension Method with Context

```java
public class PsmEntityExtensions {

    @Cached
    public static EClass getAsmEquivalent(EntityType entity, TransformationContext ctx) {
        return ctx.equivalent(entity, EClass.class);
    }

    public static boolean isMapped(EntityType entity) {
        return entity.getMapping() != null;
    }
}
```

---

## Registering Extension Methods

```java
ExtensionMethodRegistry registry = new ExtensionMethodRegistry();
registry.register(PsmTypeExtensions.class);
registry.register(PsmEntityExtensions.class);
registry.register(PsmAttributeExtensions.class);

TransformationContext ctx = new TransformationContext(
    modelProvider, sourceResourceSet, targetResourceSet, registry);
```

---

## Best Practices

### Do

1. **Use `ctx.call()` for @Cached methods** in rule bodies
2. **Profile before optimizing** - identify slow methods first
3. **Add manual caching** for frequently-called methods outside rules
4. **Clear caches** between transformations if reusing context
5. **Document uncached calls** when direct calls are necessary

### Don't

1. **Don't assume @Cached works with direct calls** - it doesn't
2. **Don't cache everything** - only expensive operations benefit
3. **Don't forget thread safety** - use `ConcurrentHashMap` for manual caches
4. **Don't cache mutable results** - only immutable values

---

## Common Extension Method Patterns

### Type Conversion

```java
public static EDataType convertPrimitiveType(PrimitiveType psm) {
    switch (psm.getName()) {
        case "String": return EcorePackage.Literals.ESTRING;
        case "Integer": return EcorePackage.Literals.EINT;
        case "Boolean": return EcorePackage.Literals.EBOOLEAN;
        default: return EcorePackage.Literals.ESTRING;
    }
}
```

### Container Navigation

```java
@Cached
public static EPackage getContainingPackage(NamedElement element, TransformationContext ctx) {
    EObject container = element.eContainer();
    while (container != null) {
        if (container instanceof Package) {
            return ctx.equivalent(container, EPackage.class);
        }
        container = container.eContainer();
    }
    return null;
}
```

### Predicate Helpers

```java
public static boolean isAbstractEntity(EntityType entity) {
    return entity.isAbstract() || entity.getAttributes().isEmpty();
}

public static boolean hasRequiredAttributes(TransferObjectType to) {
    return to.getAttributes().stream().anyMatch(Attribute::isRequired);
}
```
