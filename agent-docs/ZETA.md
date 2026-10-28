# ZETA Framework Documentation

## Overview

ZETA is a Java-based model transformation framework used as a high-performance alternative to ETL (Epsilon Transformation Language). It provides:
- Compiled (not interpreted) transformation execution
- Parallel rule execution
- Type-based rule filtering
- Guard-based element matching

---

## Documentation Index

| Document | Description |
|----------|-------------|
| [Annotations Reference](zeta/ANNOTATIONS.md) | All ZETA annotations (@Transform, @Lazy, @Greedy, etc.) |
| [API Reference](zeta/API.md) | TransformContext methods (equivalent, createTarget, etc.) |
| [ETL Conversion Guide](zeta/ETL_CONVERSION.md) | How to port ETL rules to ZETA |
| [Extension Methods](zeta/EXTENSIONS.md) | @Cached annotation and extension patterns |
| [Parallel Execution](zeta/PARALLEL.md) | Thread-safety guidelines and patterns |
| [Known Limitations](zeta/LIMITATIONS.md) | Caching behavior, workarounds |

---

## Quick Start

### Basic Rule

```java
@Transform(type = SourceType.class)
public TransformFunction<SourceType, TargetType> ruleName() {
    return (source, ctx) -> {
        TargetType target = ctx.createTarget(TargetType.class);
        target.setName(source.getName());
        return target;
    };
}
```

### With Guard

```java
@Transform(type = TransferOperation.class)
public TransformFunction<TransferOperation, EOperation> createOperation() {
    return (source, ctx) -> {
        if (source.getOutput() == null) {
            return null;  // Skip
        }
        // ... transformation
    };
}
```

### Lazy Rule (ETL @lazy equivalent)

```java
@Lazy
@Greedy  // Both required for equivalent() to work!
@Transform(type = Element.class)
public TransformFunction<Element, EAnnotation> createAnnotation() {
    // Triggered by ctx.equivalent(source, "createAnnotation")
}
```

---

## Execution Flow

```
1. Registration    → Rules registered with executor
2. Type Filtering  → Elements filtered by @Transform type
3. Guard Evaluation → Guards checked for remaining candidates
4. Parallel Execution → Matching rules execute in parallel
5. Post-Processing → Cross-references fixed up
```

---

## Key Concepts

### Type-Based Filtering

Use specific types in `@Transform` to reduce guard evaluations:

```java
// Broad type - many evaluations
@Transform(type = TransferObject.class)

// Specific type - fewer evaluations
@Transform(type = BoundTransferObjectType.class)
```

This reduces guard evaluations from 2M+ to ~80k in PSM2ASM.

### TransformContext

```java
ctx.equivalent(source, TargetType.class)     // Get/create target
ctx.equivalentDiscriminated(source, T, rule, disc)  // With discriminator
ctx.createTarget(TargetType.class)           // Create new element
ctx.getElementId(element)                    // Get XMI ID (deferred-safe)
ctx.call(source, "method", ReturnType.class) // Cached extension method
```

### ETL @lazy = ZETA @Lazy @Greedy

ETL's `@lazy` requires BOTH annotations in ZETA:

```java
@Lazy
@Greedy  // Without this, equivalent() can't find the rule!
```

---

## Performance

| Aspect | Typical Value |
|--------|---------------|
| Speedup over ETL | ~7.5x |
| Guard evaluation overhead | 87% of transformation time |
| Post-processing overhead | ~10% of transformation time |

---

## Best Practices

### Do
- Use specific types in `@Transform` for better filtering
- Use `@Lazy @Greedy` for ETL `@lazy` rules
- Use `ctx.call()` for cached extension methods
- Use `ctx.getElementId()` for target element IDs
- Use post-processing for cross-references
- Use synchronized helpers for parallel safety

### Don't
- Use `@Lazy` alone (rule won't be found by `equivalent()`)
- Call @Cached methods directly (bypasses caching)
- Assume execution order in parallel mode
- Modify elements from other rules

---

## Related Documentation

### Patterns
- [Rule Architecture](patterns/RULE_ARCHITECTURE.md) - How rules are structured
- [Post-Processing](patterns/POST_PROCESSING.md) - Cross-reference fixup steps
- [ETL vs ZETA](patterns/ETL_VS_ZETA_DIFFERENCES.md) - Behavioral differences
- [Profiling](patterns/PROFILING.md) - Performance analysis
- [Failed Approaches](patterns/FAILED_APPROACHES.md) - What NOT to try

### ZETA Subpages
- [Annotations](zeta/ANNOTATIONS.md) - Complete annotation reference
- [API](zeta/API.md) - TransformContext methods
- [ETL Conversion](zeta/ETL_CONVERSION.md) - Migration guide
- [Extensions](zeta/EXTENSIONS.md) - @Cached and helpers
- [Parallel](zeta/PARALLEL.md) - Thread safety
- [Limitations](zeta/LIMITATIONS.md) - Known issues
