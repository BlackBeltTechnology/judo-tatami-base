# ZETA Framework Optimization Plan: Type-Based Rule Filtering

## Executive Summary

The ZETA transformation framework has a performance bottleneck in guard evaluation. Currently, **all registered rules are evaluated against all input elements**, resulting in 96% rejection rate. This document provides a comprehensive plan for implementing type-based rule filtering to reduce guard evaluations by ~8x.

---

## Problem Statement

### Current Behavior

When `TransformationExecutor.transform()` processes input elements:

1. For each input element, it iterates ALL registered rules
2. For each rule, it calls `evaluateGuard()` to check if the rule applies
3. Guard methods often perform EMF model traversal (e.g., `anyMatch()` on tree iterators)
4. Most guard evaluations return `false` (rule doesn't apply to this element type)

### Metrics from Production Workload

**Model:** RackInspect (22,370 PSM elements → 1,284 ASM classifiers)

| Metric | Value | Notes |
|--------|-------|-------|
| Total transformation time | 4,598ms | ZETA is 8x faster than ETL |
| Guard evaluations | 80,695 | All rules checked against all elements |
| Successful matches | 3,210 | Only 4% result in rule execution |
| **Rejection rate** | **96%** | 77,485 wasted guard evaluations |
| Time in cache ops | 88.4% | Guard eval happens inside cache |
| Time in guard eval | 3,587ms | 78% of total time |

### JVM Profiler Evidence

Top CPU hotspots during ZETA transformation:

```
Samples  Method                                          Category
------   ------                                          --------
10       EContentsEList$FeatureIteratorImpl.hasNext      EMF traversal
7        itable stub                                      Interface dispatch
5        AbstractTreeIterator.next                        EMF traversal
4        EStructuralFeatureImpl.getFeatureID             EMF metadata
4        BasicEObjectImpl.eDerivedStructuralFeatureID    EMF metadata
3        EContentsEList.newResolvingListIterator         EMF traversal
```

**Root Cause:** Guard methods traverse EMF model structure to check element types. With 96% rejection rate, most of this traversal is wasted.

---

## Proposed Solution: Type-Based Rule Filtering

### Concept

Instead of evaluating all rules for every element, build a **type-to-rules index** during registration:

```
BEFORE (current):
  For each element:
    For each rule (ALL rules):
      if guard(element) → execute rule

AFTER (optimized):
  Build index: Map<EClass, List<Rule>> typeToRules
  For each element:
    rules = typeToRules.get(element.eClass())  // O(1) lookup
    For each rule in rules (FILTERED):
      if guard(element) → execute rule
```

### Expected Impact

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Guard evaluations | 80,695 | ~10,000 | ~8x fewer |
| Time in guards | 3,587ms | ~450ms | ~8x faster |
| Total time | 4,598ms | ~1,500ms | ~3x faster |

---

## Implementation Plan

### Phase 1: Add Source Type Declaration to Rules

**File:** `TransformRule.java` (annotation)

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TransformRule {
    String name();

    /**
     * Source type(s) this rule can process.
     * Used for type-based filtering optimization.
     * If empty, rule is evaluated for all element types (current behavior).
     */
    Class<?>[] sourceTypes() default {};
}
```

**Example usage in transformation rules:**

```java
@TransformRule(
    name = "CreateEntityClass",
    sourceTypes = { EntityType.class }  // Only evaluate for EntityType elements
)
@Lazy
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (source, ctx) -> { ... };
}

@TransformRule(
    name = "CreateTransferObjectRelation",
    sourceTypes = { TransferObjectRelation.class, AssociationEnd.class }
)
@Greedy
public TransformFunction<EObject, EReference> createTransferObjectRelation() {
    return (source, ctx) -> { ... };
}
```

### Phase 2: Build Type Index in TransformationRegistry

**File:** `TransformationRegistry.java`

```java
public class TransformationRegistry {
    // Existing
    private final List<TransformRuleDescriptor> rules = new ArrayList<>();

    // NEW: Type-based index
    private final Map<EClass, List<TransformRuleDescriptor>> typeToRules = new HashMap<>();
    private final List<TransformRuleDescriptor> universalRules = new ArrayList<>(); // sourceTypes = {}

    public void register(Class<?> ruleClass) {
        // ... existing registration code ...

        TransformRuleDescriptor descriptor = createDescriptor(method);
        rules.add(descriptor);

        // NEW: Build type index
        Class<?>[] sourceTypes = annotation.sourceTypes();
        if (sourceTypes.length == 0) {
            // No type restriction - add to universal rules
            universalRules.add(descriptor);
        } else {
            // Add to type-specific lists
            for (Class<?> sourceType : sourceTypes) {
                EClass eClass = findEClass(sourceType);
                typeToRules.computeIfAbsent(eClass, k -> new ArrayList<>()).add(descriptor);
            }
        }
    }

    /**
     * Get rules applicable to the given element type.
     * Returns type-specific rules + universal rules.
     */
    public List<TransformRuleDescriptor> getRulesForType(EClass eClass) {
        List<TransformRuleDescriptor> result = new ArrayList<>(universalRules);

        // Add rules for exact type
        List<TransformRuleDescriptor> typeRules = typeToRules.get(eClass);
        if (typeRules != null) {
            result.addAll(typeRules);
        }

        // Add rules for supertypes
        for (EClass superType : eClass.getEAllSuperTypes()) {
            List<TransformRuleDescriptor> superRules = typeToRules.get(superType);
            if (superRules != null) {
                result.addAll(superRules);
            }
        }

        return result;
    }
}
```

### Phase 3: Use Type Index in TransformationExecutor

**File:** `TransformationExecutor.java`

```java
public class TransformationExecutor {

    private void executeEagerRulesFor(EObject element) {
        // BEFORE (current):
        // for (TransformRuleDescriptor rule : registry.getAllRules()) {
        //     cache.getOrCreate(element, rule, () -> evaluateAndExecute(element, rule));
        // }

        // AFTER (optimized):
        EClass elementType = element.eClass();
        List<TransformRuleDescriptor> applicableRules = registry.getRulesForType(elementType);

        for (TransformRuleDescriptor rule : applicableRules) {
            cache.getOrCreate(element, rule, () -> evaluateAndExecute(element, rule));
        }
    }
}
```

### Phase 4: Metrics for Validation

Add metrics to track optimization effectiveness:

```java
public class TransformationMetrics {
    // Existing metrics...

    // NEW: Type filtering metrics
    private final AtomicLong rulesSkippedByTypeFilter = new AtomicLong();
    private final AtomicLong rulesEvaluatedAfterTypeFilter = new AtomicLong();

    public void recordTypeFilterSkip(int skipped) {
        rulesSkippedByTypeFilter.addAndGet(skipped);
    }

    public void recordTypeFilterPass(int evaluated) {
        rulesEvaluatedAfterTypeFilter.addAndGet(evaluated);
    }
}
```

---

## Test Cases for judo-zeta

These tests can be implemented in judo-zeta without any dependency on judo-tatami-base.

### Test 1: Basic Type Filtering

```java
@Test
void testTypeBasedRuleFiltering() {
    // Setup: Create simple metamodel
    EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
    pkg.setName("test");

    EClass typeA = EcoreFactory.eINSTANCE.createEClass();
    typeA.setName("TypeA");
    pkg.getEClassifiers().add(typeA);

    EClass typeB = EcoreFactory.eINSTANCE.createEClass();
    typeB.setName("TypeB");
    pkg.getEClassifiers().add(typeB);

    // Create instances
    EObject instanceA = EcoreUtil.create(typeA);
    EObject instanceB = EcoreUtil.create(typeB);

    // Register rules with type restrictions
    AtomicInteger ruleAEvaluations = new AtomicInteger();
    AtomicInteger ruleBEvaluations = new AtomicInteger();

    TransformationRegistry registry = new TransformationRegistry();
    registry.register(new TestRules(ruleAEvaluations, ruleBEvaluations));

    // Execute transformation
    TransformationExecutor executor = TransformationExecutor.builder()
        .registry(registry)
        .build();

    executor.transform(List.of(instanceA, instanceB));

    // Verify: Each rule only evaluated for matching type
    assertEquals(1, ruleAEvaluations.get(), "RuleA should only be evaluated for TypeA");
    assertEquals(1, ruleBEvaluations.get(), "RuleB should only be evaluated for TypeB");
}

// Test rule class
public static class TestRules {
    private final AtomicInteger ruleAEvals;
    private final AtomicInteger ruleBEvals;

    @TransformRule(name = "RuleA", sourceTypes = { /* TypeA.class reference */ })
    @Greedy
    public TransformFunction<EObject, EObject> ruleA() {
        return (source, ctx) -> {
            ruleAEvals.incrementAndGet();
            return ctx.createTarget(EcorePackage.Literals.EOBJECT);
        };
    }

    @TransformRule(name = "RuleB", sourceTypes = { /* TypeB.class reference */ })
    @Greedy
    public TransformFunction<EObject, EObject> ruleB() {
        return (source, ctx) -> {
            ruleBEvals.incrementAndGet();
            return ctx.createTarget(EcorePackage.Literals.EOBJECT);
        };
    }
}
```

### Test 2: Supertype Handling

```java
@Test
void testSupertypeRuleMatching() {
    // Setup: TypeB extends TypeA
    EClass typeA = EcoreFactory.eINSTANCE.createEClass();
    typeA.setName("TypeA");

    EClass typeB = EcoreFactory.eINSTANCE.createEClass();
    typeB.setName("TypeB");
    typeB.getESuperTypes().add(typeA);  // TypeB extends TypeA

    EObject instanceB = EcoreUtil.create(typeB);

    AtomicInteger ruleAEvaluations = new AtomicInteger();

    // Rule registered for TypeA should also match TypeB instances
    // ... register rule with sourceTypes = { TypeA.class }

    executor.transform(List.of(instanceB));

    // Verify: Rule for TypeA is evaluated for TypeB instance
    assertEquals(1, ruleAEvaluations.get(),
        "Rule for supertype TypeA should be evaluated for TypeB instance");
}
```

### Test 3: Universal Rules (No Type Restriction)

```java
@Test
void testUniversalRulesEvaluatedForAllTypes() {
    // Setup
    EClass typeA = createEClass("TypeA");
    EClass typeB = createEClass("TypeB");

    EObject instanceA = EcoreUtil.create(typeA);
    EObject instanceB = EcoreUtil.create(typeB);

    AtomicInteger universalRuleEvaluations = new AtomicInteger();

    // Rule with empty sourceTypes = {} should match all elements
    @TransformRule(name = "UniversalRule", sourceTypes = {})  // No restriction
    @Greedy
    public TransformFunction<EObject, EObject> universalRule() {
        return (source, ctx) -> {
            universalRuleEvaluations.incrementAndGet();
            return null;
        };
    }

    executor.transform(List.of(instanceA, instanceB));

    // Verify: Universal rule evaluated for both elements
    assertEquals(2, universalRuleEvaluations.get(),
        "Universal rule should be evaluated for all element types");
}
```

### Test 4: Performance Benchmark

```java
@Test
void testTypeFilteringPerformanceImprovement() {
    // Setup: Create 100 different types, 1000 elements each
    List<EClass> types = new ArrayList<>();
    List<EObject> elements = new ArrayList<>();

    for (int t = 0; t < 100; t++) {
        EClass type = EcoreFactory.eINSTANCE.createEClass();
        type.setName("Type" + t);
        types.add(type);

        for (int i = 0; i < 1000; i++) {
            elements.add(EcoreUtil.create(type));
        }
    }

    // Register 100 rules, each for one specific type
    AtomicLong guardEvaluations = new AtomicLong();
    for (int t = 0; t < 100; t++) {
        // Register rule with sourceTypes = { types.get(t) }
    }

    // Execute transformation
    long startTime = System.currentTimeMillis();
    executor.transform(elements);
    long endTime = System.currentTimeMillis();

    // Verify performance improvement
    // WITHOUT type filtering: 100 rules × 100,000 elements = 10,000,000 guard evals
    // WITH type filtering: 100 rules × 1,000 elements each = 100,000 guard evals

    long expectedMaxEvaluations = 150_000;  // Allow some overhead
    assertTrue(guardEvaluations.get() < expectedMaxEvaluations,
        "Guard evaluations should be ~100,000, not 10,000,000. Actual: " + guardEvaluations.get());

    System.out.println("Performance test results:");
    System.out.println("  Elements: " + elements.size());
    System.out.println("  Rules: 100");
    System.out.println("  Guard evaluations: " + guardEvaluations.get());
    System.out.println("  Time: " + (endTime - startTime) + "ms");
}
```

### Test 5: Backward Compatibility

```java
@Test
void testBackwardCompatibility_RulesWithoutSourceTypes() {
    // Rules without sourceTypes should work exactly as before

    @TransformRule(name = "LegacyRule")  // No sourceTypes specified
    @Greedy
    public TransformFunction<EObject, EObject> legacyRule() {
        return (source, ctx) -> { ... };
    }

    // Should be evaluated for all elements (current behavior)
    executor.transform(List.of(instanceA, instanceB, instanceC));

    assertEquals(3, legacyRuleEvaluations.get(),
        "Legacy rules without sourceTypes should be evaluated for all elements");
}
```

### Test 6: Multiple Source Types

```java
@Test
void testRuleWithMultipleSourceTypes() {
    EClass typeA = createEClass("TypeA");
    EClass typeB = createEClass("TypeB");
    EClass typeC = createEClass("TypeC");

    // Rule applies to both TypeA and TypeB, but not TypeC
    @TransformRule(name = "MultiTypeRule", sourceTypes = { TypeA.class, TypeB.class })
    @Greedy
    public TransformFunction<EObject, EObject> multiTypeRule() { ... }

    executor.transform(List.of(
        EcoreUtil.create(typeA),
        EcoreUtil.create(typeB),
        EcoreUtil.create(typeC)
    ));

    assertEquals(2, ruleEvaluations.get(),
        "Rule should be evaluated for TypeA and TypeB, not TypeC");
}
```

---

## Migration Guide

### For Existing Rules (Optional Optimization)

Existing rules without `sourceTypes` will continue to work. To optimize:

1. Identify the source type from the rule's guard or function signature
2. Add `sourceTypes` to the annotation

**Before:**
```java
@TransformRule(name = "CreateEntityClass")
@Lazy
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (source, ctx) -> {
        // source is always EntityType
        ...
    };
}
```

**After:**
```java
@TransformRule(name = "CreateEntityClass", sourceTypes = { EntityType.class })
@Lazy
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (source, ctx) -> {
        ...
    };
}
```

### Rules with Complex Guards

Some rules have guards that check multiple conditions. For these:

```java
// Guard checks: source instanceof TransferObjectType && isUnmapped(source)
// The type check (instanceof) can be moved to sourceTypes
// The semantic check (isUnmapped) remains in the guard

@TransformRule(
    name = "CreateUnmappedTransferObject",
    sourceTypes = { TransferObjectType.class }  // Type filter
)
public TransformFunction<TransferObjectType, EClass> createUnmappedTO() {
    return (source, ctx) -> {
        if (!isUnmapped(source)) return null;  // Semantic guard (still needed)
        ...
    };
}
```

---

## Rollout Plan

### Step 1: Framework Changes (judo-zeta)
1. Add `sourceTypes` to `@TransformRule` annotation
2. Implement type index in `TransformationRegistry`
3. Modify `TransformationExecutor` to use type index
4. Add metrics for validation
5. Add unit tests

### Step 2: Validation (judo-tatami-base)
1. Run existing tests - should pass (backward compatible)
2. Run benchmark - should see improvement even without rule changes
3. Optionally add `sourceTypes` to rules for maximum benefit

### Step 3: Gradual Rule Migration
1. Start with rules that have expensive guards
2. Add `sourceTypes` based on function signature
3. Measure improvement after each batch

---

## Appendix: Full Metrics from Production

```
========== ZETA TRANSFORMATION PERFORMANCE REPORT ==========

=== TOTAL TRANSFORMATION TIME: 4,123 ms ===

=== OPERATION COUNTS ===
  equivalent() calls:           39,282
    - Cache hits:               36,074 (91.8%)
    - Cache misses:             3,210 (8.2%)
  createTarget() calls:         30,012
  Rule iterations:              20,765
  Guard evaluations:            80,695      ← TARGET FOR OPTIMIZATION
  Rule executions:              3,210

=== TIMING BREAKDOWN ===
  Greedy rule execution:            285 ms (  6.9%)
    (Guard evaluation):           3,587 ms (1258.6% of greedy, 80,695 evals)
  Cache ops (exclusive):          3,643 ms ( 88.4%)

=== FINE-GRAINED CACHE BREAKDOWN ===
  Total getOrCreate ops:        80,734
    - Cache hits:               3,210 (4.0%)
    - Cache misses:             77,524 (96.0%)   ← 96% REJECTION RATE
```

---

## Version History

| Date | Author | Description |
|------|--------|-------------|
| 2026-01-16 | Claude | Initial plan based on judo-tatami-base profiling |
