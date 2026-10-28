# Design: Fix Psm2Asm Zeta Remaining Differences

## Overview

This document details the implementation approach for fixing 50 remaining differences between ETL and Zeta Psm2Asm transformation outputs.

## Implementation Details

### Fix 1: BoundOperation Faults (eExceptions)

**File:** `judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm/zeta/rules/OperationRules.java`

**Current code (line ~166):**
```java
@TransformRule(name = "CreateBoundOperation", description = "Transform BoundOperation to EOperation")
@Transform(type = BoundOperation.class)
@To(type = EOperation.class)
public TransformFunction<BoundOperation, EOperation> createBoundOperation() {
    return (s, ctx) -> {
        EOperation t = ctx.createTarget(EOperation.class);
        setId(t, "(psm/" + getId(s) + ")/BoundOperation");
        t.setName(s.getName());

        // Set output type and cardinality
        if (s.getOutput() != null) {
            // ... existing output handling
        }

        // Add to owning entity class
        // ... existing code

        return t;
    };
}
```

**Modified code:**
```java
public TransformFunction<BoundOperation, EOperation> createBoundOperation() {
    return (s, ctx) -> {
        EOperation t = ctx.createTarget(EOperation.class);
        setId(t, "(psm/" + getId(s) + ")/BoundOperation");
        t.setName(s.getName());

        // Set output type and cardinality
        if (s.getOutput() != null) {
            // ... existing output handling
        }

        // Add faults as exceptions (matching ETL CreateOperation abstract rule)
        for (Fault fault : s.getFaults()) {
            if (fault.getType() != null) {
                EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
                if (faultType != null) {
                    t.getEExceptions().add(faultType);
                }
            }
        }

        // Add to owning entity class
        // ... existing code

        return t;
    };
}
```

**Required import:**
```java
import hu.blackbelt.judo.meta.psm.data.Fault;
```

### Fix 2: unmappedDefaultOnly Annotations

**File:** `judo-tatami-psm2asm/src/main/java/hu/blackbelt/judo/tatami/psm2asm/zeta/rules/DataRules.java`

#### Rule 1: AddUnmappedDefaultOnlyAttributeAnnotation

```java
/**
 * rule AddUnmappedDefaultOnlyAttributeAnnotation
 *     transform s : JUDOPSM!Attribute
 *     to t : ASM!EAnnotation
 *     guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
 *            and s.eContainer.defaultRepresentation.attributes.exists(
 *                a | a.binding == s and a.defaultValue.isDefined())
 */
@TransformRule(name = "AddUnmappedDefaultOnlyAttributeAnnotation", 
               description = "Add unmappedDefaultOnly annotation for attributes with default values")
@Transform(type = Attribute.class)
@To(type = EAnnotation.class)
public TransformFunction<Attribute, EAnnotation> addUnmappedDefaultOnlyAttributeAnnotation() {
    return (s, ctx) -> {
        // Guard: entity container with defaultRepresentation having transfer attribute 
        // with this attribute as binding and a default value
        EObject container = s.eContainer();
        if (!(container instanceof EntityType)) {
            return null;
        }
        EntityType entity = (EntityType) container;
        MappedTransferObjectType defaultRep = entity.getDefaultRepresentation();
        if (defaultRep == null) {
            return null;
        }
        
        // Check if any transfer attribute binds to this attribute and has defaultValue
        boolean hasDefaultValue = defaultRep.getAttributes().stream()
                .anyMatch(a -> a.getBinding() == s && a.getDefaultValue() != null);
        if (!hasDefaultValue) {
            return null;
        }
        
        // Create annotation
        EAnnotation t = createAnnotation(
                "(psm/" + getId(s) + ")/UnmappedDefaultOnlyAttributeAnnotation",
                getAnnotationUri("unmappedDefaultOnly"));
        addAnnotationDetail(t, "value", String.valueOf(s.isUnmappedDefaultOnly()));
        
        // Add to the equivalent EAttribute
        EAttribute attr = ctx.equivalent(s, EAttribute.class);
        if (attr != null) {
            attr.getEAnnotations().add(t);
        }
        
        return t;
    };
}
```

#### Rule 2: AddUnmappedDefaultOnlyReferenceAnnotation

```java
/**
 * rule AddUnmappedDefaultOnlyReferenceAnnotation
 *     transform s : JUDOPSM!AssociationEnd
 *     to t : ASM!EAnnotation
 *     guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
 *            and s.eContainer.defaultRepresentation.relations.exists(
 *                r | r.binding == s and r.defaultValue.isDefined())
 */
@TransformRule(name = "AddUnmappedDefaultOnlyReferenceAnnotation", 
               description = "Add unmappedDefaultOnly annotation for references with default values")
@Transform(type = AssociationEnd.class)
@To(type = EAnnotation.class)
public TransformFunction<AssociationEnd, EAnnotation> addUnmappedDefaultOnlyReferenceAnnotation() {
    return (s, ctx) -> {
        // Guard: entity container with defaultRepresentation having transfer relation 
        // with this associationEnd as binding and a default value
        EObject container = s.eContainer();
        if (!(container instanceof EntityType)) {
            return null;
        }
        EntityType entity = (EntityType) container;
        MappedTransferObjectType defaultRep = entity.getDefaultRepresentation();
        if (defaultRep == null) {
            return null;
        }
        
        // Check if any transfer relation binds to this associationEnd and has defaultValue
        boolean hasDefaultValue = defaultRep.getRelations().stream()
                .anyMatch(r -> r.getBinding() == s && r.getDefaultValue() != null);
        if (!hasDefaultValue) {
            return null;
        }
        
        // Create annotation
        EAnnotation t = createAnnotation(
                "(psm/" + getId(s) + ")/UnmappedDefaultOnlyReferenceAnnotation",
                getAnnotationUri("unmappedDefaultOnly"));
        addAnnotationDetail(t, "value", String.valueOf(s.isUnmappedDefaultOnly()));
        
        // Add to the equivalent EReference
        EReference ref = ctx.equivalent(s, EReference.class);
        if (ref != null) {
            ref.getEAnnotations().add(t);
        }
        
        return t;
    };
}
```

**Required imports:**
```java
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.service.MappedTransferObjectType;
```

## Testing

### Test Command
```bash
cd /Users/robson/Project/judo-ng/runtime/judo-tatami-base
mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance
```

### Expected Output After Fix
```
SUCCESS: ETL and Zeta models are structurally equivalent
Classifier count: 1245 (both match)
>>> ZETA is 45x FASTER than ETL <<<
```

## Difference Count Reduction

| Phase | Differences | Fixed |
|-------|-------------|-------|
| Before | 50 | - |
| After Fix 1 (faults) | 29 | 21 (7 operations × 3 lines each) |
| After Fix 2 (unmappedDefaultOnly) | 0 | 29 (12 attributes × ~2 lines + 5 count mismatches) |
