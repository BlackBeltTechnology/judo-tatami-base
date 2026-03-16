# Zeta PSM2ASM: Parameterized Annotation Bug

## Problem Summary

The Zeta PSM2ASM transformation was missing `parameterized` annotations on
`TransferAttribute` and `TransferObjectRelation` elements. This caused
`Asm2ExpressionWork` to fail downstream because `AsmModelAdapter` could not
resolve parameter types for parameterized getter expressions, leading to
"Unknown symbol: input" errors in `JqlNavigationTransformer`.

## Root Cause

Two independent bugs combined to produce the failure.

### Bug 1 — Inline parameterized annotation with wrong guard scope

**ETL** defines standalone rules for parameterized annotations with **broad guards**:

```
rule CreateTransferAttributeParameterizedAnnotation
    transform s : JUDOPSM!TransferAttribute
    to t : ASM!EAnnotation {
        guard: s.binding.isDefined()
           and s.binding.isKindOf(JUDOPSM!PrimitiveAccessor)
           and s.binding.getterExpression.parameterType.isDefined()
    }

rule CreateTransferObjectRelationParameterizedAnnotation
    transform s : JUDOPSM!TransferObjectRelation
    to t : ASM!EAnnotation {
        guard: s.binding.isDefined()
           and s.binding.isKindOf(JUDOPSM!ReferenceAccessor)
           and s.binding.getterExpression.parameterType.isDefined()
    }
```

These guards check **only** the binding type and whether `parameterType` exists.
They do **not** check the container type (`MappedTransferObjectType` vs
`UnmappedTransferObjectType`).

**Zeta** (commit c947108) inlined the parameterized annotation creation inside
`createDataReferenceBinding()` and `createNavigationReferenceBinding()`, which
have **stricter guards** that also check the container type:

```java
// hasDataBinding guard:
(container instanceof UnmappedTransferObjectType && binding instanceof PrimitiveAccessor) ||
(container instanceof MappedTransferObjectType && binding instanceof StaticData)

// hasNavigationBinding guard:
(container instanceof UnmappedTransferObjectType && binding instanceof ReferenceAccessor) ||
(container instanceof MappedTransferObjectType && binding instanceof StaticNavigation)
```

This missed cases where:
- A `MappedTransferObjectType` has a `PrimitiveAccessor` binding (not StaticData)
- A `MappedTransferObjectType` has a `ReferenceAccessor` binding (not StaticNavigation)

In these cases the ETL parameterized annotation rules fire, but the Zeta code
never reaches the inline parameterized block.

**Affected elements** (Northwind model):

| Element | Container Type | Binding Type | Missed By |
|---------|---------------|--------------|-----------|
| `internationalOrdersFrom` | MappedTransferObjectType | ReferenceAccessor | `hasNavigationBinding` |
| `heavyProducts` | MappedTransferObjectType | ReferenceAccessor | `hasNavigationBinding` |
| `hasMoreProductsThan` | MappedTransferObjectType | ReferenceAccessor | `hasNavigationBinding` |
| (and 11 more similar) | | | |

### Bug 2 — Wrong equivalent lookup in StaticRules

`StaticRules.java` used a **named rule** lookup for the parameter type:

```java
EClass paramType = ctx.equivalent(
    s.getGetterExpression().getParameterType(),
    CREATE_ENTITY_CLASS);  // ← Wrong: assumes parameterType is EntityType
```

The ETL equivalent uses polymorphic dispatch via `asmEquivalent()`:

```
s.getterExpression.parameterType.asmEquivalent()
```

Where `asmEquivalent()` dispatches to:
- `MappedTransferObjectType.asmEquivalent()` → `self.equivalent("CreateMappedTransferObject")`
- `UnmappedTransferObjectType.asmEquivalent()` → `self.equivalent("CreateUnmappedTransferObject")`

But `parameterType` returns `TransferObjectType` — typically an
`UnmappedTransferObjectType` like `IntegerQueryParameter`. The Zeta code looked
it up with `CREATE_ENTITY_CLASS` which only matches `EntityType` elements, so it
always returned `null`.

**Affected element**: `_numberOfOrdersWithDiscount_binding_Stats` with
`IntegerQueryParameter` as parameter type.

## Impact Chain

```
Missing parameterized annotation on TransferAttribute/TransferObjectRelation
  → AsmModelAdapter.getTransferAttributeParameterType() returns null
    → JqlNavigationTransformer.findBase("input") fails
      → "Unknown symbol: input" error
        → Asm2ExpressionWork fails
          → All downstream stages SKIPPED (Script, RDBMS, Liquibase)
```

## Fix

### Fix 1 — Extract standalone parameterized rules (TransferObjectRules.java)

Removed inline parameterized annotation code from `createDataReferenceBinding()`
and `createNavigationReferenceBinding()`. Added two new standalone `@Greedy`
rules matching the ETL guards exactly:

```java
@TransformRule(name = CREATE_TRANSFER_ATTRIBUTE_PARAMETERIZED_ANNOTATION)
@Guard(method = "hasPrimitiveAccessorWithParameterType")
@Transform(type = TransferAttribute.class)
@To(type = EAnnotation.class)
@Greedy
public TransformFunction<TransferAttribute, EAnnotation>
        createTransferAttributeParameterizedAnnotation() { ... }

@TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_PARAMETERIZED_ANNOTATION)
@Guard(method = "hasReferenceAccessorWithParameterType")
@Transform(type = TransferObjectRelation.class)
@To(type = EAnnotation.class)
@Greedy
public TransformFunction<TransferObjectRelation, EAnnotation>
        createTransferObjectRelationParameterizedAnnotation() { ... }
```

Guard methods check only binding type and parameterType, not container type:

```java
public TransformGuard hasPrimitiveAccessorWithParameterType() {
    return (source, ctx) -> {
        if (!(source instanceof TransferAttribute attr)) return false;
        Object binding = attr.getBinding();
        if (!(binding instanceof PrimitiveAccessor accessor)) return false;
        return accessor.getGetterExpression() != null
                && accessor.getGetterExpression().getParameterType() != null;
    };
}
```

### Fix 2 — Use type-based equivalent lookup (StaticRules.java)

Changed from named-rule to type-based equivalent:

```java
// Before (wrong — only finds EntityType results):
ctx.equivalent(s.getGetterExpression().getParameterType(), CREATE_ENTITY_CLASS)

// After (correct — finds any TransferObjectType → EClass mapping):
ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class)
```

## Verification

Pipeline comparison test results after fix:

| Stage | Before Fix | After Fix |
|-------|-----------|-----------|
| PSM | EQUIVALENT | EQUIVALENT |
| ASM | 14 differences | **EQUIVALENT** |
| Measure | EQUIVALENT | EQUIVALENT |
| Keycloak | EQUIVALENT | **EQUIVALENT** |
| Expression | SKIPPED (crash) | DIFFERENT (ordering) |
| Script | SKIPPED (crash) | DIFFERENT (24 diffs) |
| RDBMS | SKIPPED (crash) | DIFFERENT (XMI ID naming) |
| Liquibase | SKIPPED (crash) | DIFFERENT (XMI ID naming) |

The Zeta pipeline now runs end-to-end. The remaining differences are:

- **Expression**: Element ordering (718 vs 710 elements, order varies)
- **Script**: Cascading from Expression differences
- **RDBMS/Liquibase**: XMI ID naming convention (`EntityClass` vs `CreateEntityClass`)

## Lesson Learned

When porting ETL rules to Zeta, **do not inline standalone rules into other
rules with narrower guards**. ETL rules fire independently based on their own
guards. Inlining them inside another rule's execution path inherits that outer
rule's guard constraints, silently dropping elements that pass the original
guard but not the outer one.

**Pattern to follow:**

```
ETL: rule A (guard: X)        → Zeta: standalone @Greedy rule A (guard: X)
ETL: rule B (guard: X and Y)  → Zeta: standalone @Greedy rule B (guard: X and Y)
```

**Anti-pattern:**

```
ETL: rule A (guard: X)        → Zeta: inlined inside rule B
ETL: rule B (guard: X and Y)  → Zeta: standalone @Greedy rule B (guard: X and Y)
                                       ↑ rule A logic only runs when Y is also true!
```

## Files Modified

- `judo-tatami-psm2asm/src/main/java/.../zeta/rules/TransferObjectRules.java`
  - Removed inline parameterized code from `createDataReferenceBinding()`
  - Removed inline parameterized code from `createNavigationReferenceBinding()`
  - Added `createTransferAttributeParameterizedAnnotation()` rule + guard
  - Added `createTransferObjectRelationParameterizedAnnotation()` rule + guard

- `judo-tatami-psm2asm/src/main/java/.../zeta/rules/StaticRules.java`
  - Changed `ctx.equivalent(parameterType, CREATE_ENTITY_CLASS)` to
    `ctx.equivalent(parameterType, EClass.class)` (2 occurrences)

## Related

- ETL source: `src/main/epsilon/transformations/asm/modules/transferObject.etl` lines 265-285 and 335-355
- ETL source: `src/main/epsilon/transformations/asm/modules/static.etl` lines 60-81 and 247-268
- Pipeline comparison test: `judo-tatami-pipeline/judo-pipeline-testing/src/test/java/.../FullPipelineComparisonTest.java`
- `asmEquivalent()` definitions: `src/main/epsilon/transformations/asm/utils/transferObject.eol`
