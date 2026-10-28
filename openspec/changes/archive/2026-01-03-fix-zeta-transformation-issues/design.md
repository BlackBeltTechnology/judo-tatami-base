# Design: Fix Zeta Transformation Issues

## Overview

This document describes the technical approach for fixing the issues discovered during STRICT mode testing of Zeta transformations.

## Issue Analysis

### 1. PSM2Measure - Wrong Measure/Unit Association (P1)

**Problem Location**: `UnitRules.java:203-222`

```java
private Measure findEquivalentMeasure(hu.blackbelt.judo.meta.psm.measure.Unit unit, TransformationContext ctx) {
    PsmUtils psmUtils = (PsmUtils) ctx.getAttribute("psmUtils");

    // This stream-based search can return wrong measure!
    hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure = psmUtils.all(
            ctx.getSourceResourceSet(), hu.blackbelt.judo.meta.psm.measure.Measure.class)
            .filter(m -> m.getUnits().contains(unit))
            .findFirst()
            .orElse(null);
```

**Root Cause**: The `psmUtils.all()` method may iterate measures in different order than ETL. The `findFirst()` returns the first match, but with parallel streams or different iteration order, this can be a different measure.

**Fix Approach**: Use direct EMF containment relationship instead of searching:
```java
private Measure findEquivalentMeasure(hu.blackbelt.judo.meta.psm.measure.Unit unit, TransformationContext ctx) {
    EObject container = unit.eContainer();
    if (container instanceof hu.blackbelt.judo.meta.psm.measure.Measure) {
        hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure =
            (hu.blackbelt.judo.meta.psm.measure.Measure) container;
        if (psmMeasure instanceof DerivedMeasure) {
            return ctx.equivalent(psmMeasure, hu.blackbelt.judo.meta.measure.DerivedMeasure.class);
        } else {
            return ctx.equivalent(psmMeasure, BaseMeasure.class);
        }
    }
    return null;
}
```

### 2. RDBMS2Liquibase - ArrayIndexOutOfBoundsException (P1)

**Problem Location**: `TableRules.java:270`

```java
changeLog.getChangeSet().add(t);
```

**Root Cause**: The `getChangeSet()` returns an EMF EList which is NOT thread-safe. When multiple transformation threads try to add to the same list concurrently, an ArrayIndexOutOfBoundsException occurs.

**Fix Approach**: Use synchronized access wrapper:
```java
// Option A: Use helper method with synchronization
private static synchronized void addChangeSet(databaseChangeLog changeLog, ChangeSet changeSet) {
    changeLog.getChangeSet().add(changeSet);
}

// Option B: Synchronize on the list
synchronized (changeLog.getChangeSet()) {
    changeLog.getChangeSet().add(changeSet);
}
```

**Note**: This same pattern needs to be applied to all places where ELists are modified from transformation rules.

### 3. PSM2ASM - Behavior Annotation Ordering (P3)

**Problem Location**: `OperationRules.java` - `createTransferOperationBehaviourAnnotation()`

**Root Cause**: When multiple bound transfer operations share the same entity operation binding, each adds its behavior annotation. The order depends on thread execution order.

**Fix Approach**: Sort the owner values deterministically (e.g., alphabetically):
```java
// When adding multiple annotations with same type, sort by owner FQ name
List<TransferOperation> sortedOps = transferOps.stream()
    .sorted(Comparator.comparing(op ->
        mapBehaviourOwner(op.getBehaviour(), ctx)))
    .collect(Collectors.toList());
```

**Alternative**: Mark these annotations as order-independent in the comparison tool.

### 4. PSM2ASM - Missing Operation (P2)

**Problem Location**: PSM2ASM Zeta transformation (likely OperationRules.java or TransferObjectRules.java)

**Investigation Finding**: The operation `_listCountryForRackinspect_entities_CompanyAddress` is:
- Created by ESM2PSM transformation (see `esm2psm/zeta/rules/OperationRules.java:1275`)
- Pattern: `"_list" + capitalize(relationName) + "For" + capitalize(containerFQName)`
- Present in the PSM model as input to PSM2ASM

The issue is that Zeta PSM2ASM transformation is not processing this operation while ETL does. This requires:
1. Finding which Zeta rule should transform this operation (likely `CreateBoundTransferOperation` or `CreateUnboundOperation`)
2. Checking guard conditions that might exclude it
3. Fixing the guard or adding the missing rule

### 5. PSM2ASM - Extension Package nsURI (P2)

**Problem Location**: `NamespaceRules.java:packageToPackage()` - line 191-194

**Investigation Finding**: Extension packages are created in ESM2PSM transformation (via `CreateExtensionPackage` rule in `EsmPackageExtensions.java`). They become regular PSM Packages. When PSM2ASM transforms these to EPackages:
- The `PackageToPackage` rule uses `getContainerPackage(s, ctx)` to get parent nsURI
- With parallel execution, if parent isn't transformed yet, `parentPkg.getNsURI()` returns `null`
- Result: `null + "/" + name` produces `null/entities`

**Root Cause**: Parallel execution race condition - child packages may be processed before parents.

**Fix Approach**: Ensure parent package is available before deriving nsURI:
```java
// In packageToPackage rule
EPackage parentPkg = getContainerPackage(s, ctx);
if (parentPkg != null) {
    // Wait for parent to be fully initialized
    String parentNsURI = parentPkg.getNsURI();
    if (parentNsURI == null) {
        // Fallback: compute from context or model root
        String modelName = ctx.getAttribute("modelName");
        String nsURIBase = ctx.getAttribute("nsURI");
        parentNsURI = nsURIBase + "/" + modelName;
    }
    t.setNsURI(parentNsURI + "/" + s.getName());
    t.setNsPrefix(parentPkg.getNsPrefix() + capitalize(s.getName()));
}
```

## Thread Safety Considerations

The Zeta transformation framework runs rules in parallel. Any modification to shared state (like adding to ELists) must be thread-safe:

1. **Use synchronization** for list modifications
2. **Use ConcurrentHashMap** for caches
3. **Consider EList subclasses** that may have different thread-safety characteristics

## Testing Strategy

1. Run external model tests in STRICT mode after each fix
2. Verify fix count decreases progressively
3. Ensure no performance regression (Zeta should remain faster than ETL)
4. Run existing unit tests to prevent regressions
