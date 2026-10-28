# Fix Zeta PSM2ASM ID Parity

## Summary

Fix `Psm2AsmHelper.getId()` to return the XMI resource fragment ID for PSM `NamespaceElement` instances (matching ETL behavior), instead of returning the qualified name with underscores.

## Problem Statement

ETL and Zeta produce different IDs for the same PSM source elements, causing cascading differences in ASM, RDBMS, and Liquibase outputs.

**ETL** uses `eResource.getId(self)` which returns the XMI fragment:
```
"_JYn4v-q1EemUZMITjXqp8w"
```

**Zeta** uses `Psm2AsmHelper.getId()` which hits an early return for `NamespaceElement`:
```java
if (element instanceof NamespaceElement) {
    return getQualifiedNameWithUnderscore((NamespaceElement) element);  // ← early return!
}
```
Producing:
```
"northwind_entities_Person"
```

Both are used in the same pattern `"(psm/" + getId(s) + ")/EntityClass"` to build ASM XMI IDs, causing divergence that propagates downstream.

## Solution

In `Psm2AsmHelper.getId()`, check the XMI resource fragment first for `NamespaceElement`, fall back to qualified name only when no fragment exists.

## Scope

### In Scope
- Fix `Psm2AsmHelper.getId()` in `judo-tatami-psm2asm`
- TDD: write failing test first, then fix

### Out of Scope
- Expression ordering differences (separate comparator fix)
- Changing ETL behavior
- Changing downstream transformations (the fix cascades automatically)
