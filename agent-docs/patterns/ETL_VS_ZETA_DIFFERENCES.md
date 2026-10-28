# ETL vs ZETA Differences - PSM2ASM Transformation

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |
| Understand rule architecture | [RULE_ARCHITECTURE.md](RULE_ARCHITECTURE.md) |

---

## Overview

PSM2ASM transformation has two implementations:
- **ETL** (Epsilon Transformation Language) - Original, interpreted
- **ZETA** (Java-based) - New, compiled, ~7.5x faster

This document captures behavioral differences between them.

---

## Key Differences

### 1. Execution Model

| Aspect | ETL | ZETA |
|--------|-----|------|
| Execution | Sequential, interpreted | Parallel, compiled |
| Rule matching | Pattern-based | Type-filtered + guards |
| Cross-references | Set during transformation | Set in post-processing |

### 2. Guard Evaluation

**ETL:**
```etl
rule TransformFoo
    transform s : PSM!FooType
    to t : ASM!Bar {
    guard : s.isApplicable()
    // body
}
```

**ZETA:**
```java
@Transform(type = FooType.class)
public TransformFunction<FooType, Bar> transformFoo() {
    return (ctx, s) -> {
        if (!s.isApplicable()) return null;  // Guard
        // body
    };
}
```

### 3. Caching Behavior (CRITICAL DIFFERENCE)

**ETL:** Caches elements at **START** of rule execution
- Circular calls return partially-constructed element
- Allows progressive population

**ZETA:** Caches elements at **END** of rule execution
- Circular calls return **NULL**
- Requires fallback pattern for circular dependencies

```java
// ZETA pattern for circular dependencies
Element result = ctx.equivalentDiscriminated(source, Element.class, RULE_NAME, discriminator);
if (result == null) {
    // Fallback - create inline
    result = factory.create(Element.class);
    result.setProperty(value);
}
```

### 4. @Lazy Semantic Difference

**ETL:**
- `@lazy` = Rule is registered for all sources AND only executes when called via `equivalent()`

**ZETA:**
- `@Lazy` alone = May NOT be registered for all sources
- `@Greedy` = Registered for all matching sources
- `@Lazy @Greedy` = Equivalent to ETL `@lazy`

**Implication:** When porting ETL `@lazy` rules to ZETA:
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

### 5. @Greedy Execution

**ETL:** `@greedy` annotation makes rule execute for all matching elements regardless of whether they're "claimed" by other rules.

**ZETA:** No direct equivalent. Each rule executes independently. Use `@Detached` for elements that shouldn't be added to Resource.contents.

---

## Behavioral Parity Issues

### Issue: Extension Type Attributes

**Symptom:** Null eType in extension transfer object attributes

**ETL Behavior:** Resolves types during sequential execution

**ZETA Behavior:** `ctx.equivalent()` returns null for cross-resource references

**Resolution:** Post-processing step 6 fixes null eTypes by name lookup

---

### Issue: Reference Resolution Timing

**Symptom:** Missing cross-references between elements

**ETL Behavior:** References resolved as elements are created

**ZETA Behavior:** References must be deferred to post-processing

**Resolution:** Six post-processing steps handle cross-references

---

### Issue: XMI ID Generation

**Symptom:** Different XMI IDs between ETL and ZETA output

**ETL Behavior:** IDs generated during traversal order

**ZETA Behavior:** Parallel execution changes creation order

**Resolution:** Deterministic ID generation based on element properties, not creation order

---

### Issue: Deferred ID Assignment

**Symptom:** `XMIResource.getID()` returns null for target elements

**ETL Behavior:** IDs immediately available on elements

**ZETA Behavior:** Uses deferred ID assignment:
1. `ctx.setElementId(element, id)` stores in `pendingXmiIds` map
2. IDs applied to resource during commit phase
3. Use `ctx.getElementId(element)` to get ID (handles deferred)

**Resolution:**
```java
// For source elements (PSM) - IDs already in resource
String sourceId = IdExtensions.getId(psmElement);

// For target elements (ASM) - use ctx method
String targetId = ctx.getElementId(asmElement);
```

---

## Performance Comparison

| Metric | ETL | ZETA | Factor |
|--------|-----|------|--------|
| Transformation time | ~38,000ms | ~5,000ms | 7.5x |
| Memory usage | Higher | Lower | ~2x |
| Parallelization | None | Full | - |

---

## Migration Notes

### When Porting ETL Rules to ZETA

1. **Replace `guard:` with conditional return:**
   ```java
   if (!condition) return null;
   ```

2. **Replace `equivalent()` calls:**
   ```java
   // ETL: s.type.equivalent()
   // ZETA: ctx.equivalent(source.getType())
   ```

3. **Add `@Greedy` to `@Lazy` rules:**
   ```java
   // ETL @lazy needs BOTH in ZETA
   @Lazy
   @Greedy
   ```

4. **Handle circular dependencies:**
   ```java
   Element e = ctx.equivalentDiscriminated(...);
   if (e == null) {
       e = createInline();  // Fallback
   }
   ```

5. **Handle cross-references in post-processing:**
   - Add step to `postProcess()` method
   - Use cache to store/retrieve elements

6. **Use `@Detached` for contained elements:**
   - Elements added to containers, not Resource root
   - Caller responsible for adding to parent

7. **Use `ctx.getElementId()` for target IDs:**
   - Not `XMIResource.getID()` (may return null)

### Testing Parity

```bash
# Run both ETL and ZETA, compare results
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true
```

---

## Quick Reference: ETL to ZETA

| ETL | ZETA |
|-----|------|
| `@lazy` | `@Lazy @Greedy` |
| `@abstract` | `@Abstract` |
| `@primary` | `@Primary` |
| `@greedy` | `@Greedy` |
| `extends Rule` | `@Extends("Rule")` |
| `guard: cond` | `if (!cond) return null;` |
| `s.equivalent()` | `ctx.equivalent(s, Type.class)` |
| `s.equivalent("Rule")` | `ctx.equivalent(s, "Rule")` |
| `s.equivalentDiscriminated("R", d)` | `ctx.equivalentDiscriminated(s, T.class, "R", d)` |
| `s.isDefined()` | `s != null` |
| `s.isUndefined()` | `s == null` |
| `s.isKindOf(Type)` | `s instanceof Type` |
| `s.isTypeOf(Type)` | `s.getClass().equals(Type.class)` |
| `col.collect(x \| x.f())` | `col.stream().map(x -> x.f()).collect(...)` |
| `col.exists(x \| x.f())` | `col.stream().anyMatch(x -> x.f())` |
| `col.select(x \| x.f())` | `col.stream().filter(x -> x.f()).collect(...)` |
