# Comprehensive Abstract Analysis: Zeta Framework Transformation Differences

## Overview

When comparing output from **ETL (Epsilon Transformation Language)** and **Zeta (Java annotation-based)** transformations on identical input models, systematic differences emerge. These differences fall into **three distinct categories**, each pointing to specific framework semantics that may differ between ETL and Zeta.

---

## Category 1: Element Count Differences (Missing Elements)

### Pattern Observed
```
ETL produces N elements, Zeta produces M elements (where N ≠ M)
```

### Statistical Distribution
| ETL Count | Zeta Count | Occurrences | Description |
|-----------|------------|-------------|-------------|
| 7 | 5 | 30 | ETL produces 2 more elements |
| 1 | 0 | 22 | ETL produces element, Zeta produces none |
| 9 | 6 | 14 | ETL produces 3 more elements |
| 8 | 5 | 12 | ETL produces 3 more elements |
| 3 | 4 | 10 | Zeta produces 1 more element |

### Abstract Root Causes

#### 1.1 Multiple Rules Targeting Same Source → Different Target Types

In ETL, a single source element can be transformed by **multiple rules** that each produce **different target types**. All targets are collected.

```
ETL Semantics:
Source S → Rule1 → Target T1
Source S → Rule2 → Target T2  
Source S → Rule3 → Target T3
Result: [T1, T2, T3] all attached to parent container
```

**Zeta Question**: When multiple `@TransformRule` methods match the same source element but produce different target types, does Zeta execute ALL of them or only ONE?

#### 1.2 Rule Inheritance Chain Execution

ETL `extends` causes parent rule to execute first, then child rule extends the result. But parent may also add elements to containers independently.

```
ETL:
@abstract rule Parent: S → T1 { container.add(T1) }
rule Child extends Parent: S → T1 { /* inherits T1, may add T2 */ }

Result: Both T1 from parent logic AND any additions from child
```

**Zeta Question**: When `@Extends` is used with `ctx.executeParentRule()`, does the parent rule's side effects (adding to containers) also execute?

#### 1.3 Guard Condition Differences

ETL guards may have subtly different evaluation semantics than Zeta `@Guard` methods.

```
ETL: guard: s.someProperty.isDefined() and s.someProperty.size() > 0
Zeta: @Guard(method="hasProperty") where hasProperty checks differently
```

**Zeta Question**: Are guard evaluation semantics identical? Null handling? Short-circuit evaluation?

---

## Category 2: Element Ordering Differences

### Pattern Observed
```
Same elements exist in both outputs, but in different order within collections
```

### Examples
```
ETL:  [binding, constraints, permissions, range]
Zeta: [constraints, binding, range, permissions]
```

### Abstract Root Causes

#### 2.1 Rule Execution Order

ETL processes rules in **declaration order** within files, and files in **import order**.

```
ETL file order:
  import "module1.etl"  -- rules execute first
  import "module2.etl"  -- rules execute second
  rule LocalRule { }     -- executes last
```

**Zeta Question**: What determines the execution order of `@TransformRule` methods? Is it:
- Declaration order in Java file?
- Alphabetical by rule name?
- Registration order?
- Undefined/non-deterministic?

#### 2.2 Multiple Rules Adding to Same Container

When multiple rules add elements to the same parent container, the order depends on rule execution sequence.

```
Rule A adds: annotation1 to element.annotations
Rule B adds: annotation2 to element.annotations
Rule C adds: annotation3 to element.annotations

Final order = execution order of A, B, C
```

**Zeta Question**: Is the execution order of rules adding to the same container guaranteed to match ETL order?

#### 2.3 Lazy vs Eager Evaluation Timing

ETL `@lazy` rules execute when `equivalent()` is called. This affects when elements are added to containers relative to other rules.

```
ETL:
rule Eager: S1 → T1 { container.add(T1) }
@lazy rule Lazy: S2 → T2 { container.add(T2) }

If Eager calls s2.equivalent(), Lazy executes mid-Eager, affecting order
```

**Zeta Question**: Does `@Lazy` annotation produce identical timing behavior to ETL `@lazy`?

---

## Category 3: Map/Detail Entry Ordering

### Pattern Observed
```
Key-value pairs in maps have same content but different order
```

### Example
```
ETL:  details = {precision: 2, scale: 4, measure: Mass, unit: gram}
Zeta: details = {precision: 2, scale: 4, unit: gram, measure: Mass}
```

### Abstract Root Causes

#### 3.1 Iteration Order Over Source Collections

When populating target maps by iterating over source collections, the iteration order matters.

```java
// If source iteration order differs:
for (Property p : source.getProperties()) {
    target.getDetails().put(p.getName(), p.getValue());
}
```

**Zeta Question**: Does EMF collection iteration order match between ETL and Zeta contexts?

#### 3.2 HashMap vs LinkedHashMap Semantics

ETL may use insertion-order-preserving maps internally, while Zeta implementation might not.

**Zeta Question**: What Map implementation backs `EStringToStringMapEntry` collections in Zeta context?

---

## Category 4: Structural Differences (Missing Structural Features)

### Pattern Observed
```
eGenericSuperTypes: 1 vs 0
eParameters: 1 vs 0
```

These indicate entire structural features are missing, not just elements within collections.

### Abstract Root Causes

#### 4.1 Rules Not Executing At All

Some transformation rules are not being invoked by Zeta when they should be.

```
ETL: rule CreateParameter: Parameter → EParameter { ... }
     This rule executes for each Parameter in source

Zeta: @TransformRule CreateParameter may not be discovered/executed
```

**Zeta Question**: How does Zeta discover and invoke `@TransformRule` methods? Are all annotated methods guaranteed to be found?

#### 4.2 Source Element Filtering

The set of source elements being transformed may differ.

```
ETL: for each element in Source!Type.allInstances() { transform }
Zeta: source element collection may be different
```

**Zeta Question**: How does Zeta determine which source elements to transform? Does it match ETL's `allInstances()` behavior?

---

## Specific Investigation Points for Zeta Framework

### A. Rule Discovery and Registration
1. How are `@TransformRule` annotated methods discovered?
2. Are methods in parent classes also discovered?
3. What happens if two rules have the same name?

### B. Rule Execution Orchestration
1. What determines rule execution order?
2. How are rules grouped by source type?
3. When multiple rules match same source, which execute?

### C. `@Extends` Semantics
1. Does `executeParentRule()` execute parent's side effects?
2. What if parent is `@Abstract` - is it skipped or executed?
3. Can a rule extend multiple parents?

### D. Target Element Creation and Attachment
1. When is target attached to parent container?
2. Are multiple targets from same source all attached?
3. What controls attachment order?

### E. Collection Ordering Guarantees
1. Is insertion order preserved in target collections?
2. Does iteration over source collections have defined order?
3. Are EMF collection implementations order-preserving?

---

## Recommended Zeta Framework Verification Tests

```java
// Test 1: Multiple rules, same source, different targets
@TransformRule(name = "RuleA")
@Transform(type = Source.class)
@To(type = TargetA.class)
TransformFunction<Source, TargetA> ruleA();

@TransformRule(name = "RuleB")
@Transform(type = Source.class)
@To(type = TargetB.class)
TransformFunction<Source, TargetB> ruleB();

// Verify: Both TargetA and TargetB are created for same Source

// Test 2: Rule execution order
@TransformRule(name = "First")  // Should execute first
@TransformRule(name = "Second") // Should execute second

// Verify: Execution order matches declaration order

// Test 3: Extends with side effects
@TransformRule(name = "Parent")
@Abstract
TransformFunction<S, T> parent() {
    return (s, ctx) -> {
        T t = ctx.createTarget(T.class);
        container.getChildren().add(t);  // Side effect
        return t;
    };
}

@TransformRule(name = "Child")
@Extends("Parent")
TransformFunction<S, T> child() {
    return (s, ctx) -> {
        T t = ctx.executeParentRule("Parent", s);
        // Verify: container.getChildren() already has t from parent
        return t;
    };
}
```

---

## Summary

The differences between ETL and Zeta output stem from fundamental questions about:

1. **Multi-rule execution**: Does Zeta execute ALL matching rules or just one?
2. **Execution ordering**: Is rule execution order deterministic and matching ETL?
3. **Inheritance side effects**: Do `@Extends` chains preserve parent side effects?
4. **Collection ordering**: Are insertion and iteration orders preserved?

These are **framework-level semantics** questions that need verification in the Zeta framework implementation.

---

## Test Command

To reproduce these differences, run:

```bash
cd /Users/robson/Project/judo-ng/runtime/judo-tatami-base
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDualTransformationTest -U
```

The test compares ETL and Zeta transformation outputs and reports all differences.
