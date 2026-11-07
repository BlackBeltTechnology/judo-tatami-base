# Fix Remaining Psm2Asm Zeta Transformation Differences

## Summary

Fix the remaining 50 differences between ETL and Zeta transformation outputs in the Psm2Asm module. These differences were identified after fixing the 46 missing StaticNavigation classifiers.

## Motivation

The Psm2Asm Zeta transformation is 45x faster than ETL but currently produces output with 50 minor differences. To ensure complete equivalence and enable Zeta as the default transformation engine, these remaining differences must be fixed.

## Analysis

The 50 differences fall into 3 categories:

### Category 1: Missing `unmappedDefaultOnly` Annotation (12 attributes)

**ETL Rules (data.etl:236-262):**
- `AddUnmappedDefaultOnlyAttributeAnnotation` - for Attributes  
- `AddUnmappedDefaultOnlyReferenceAnnotation` - for AssociationEnds

**Guard condition:**
```etl
guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
       and s.eContainer.defaultRepresentation.attributes.exists(
           a | a.binding == s and a.defaultValue.isDefined())
```

**Status:** Rule names defined in `Psm2AsmRuleNames.java` but NOT implemented in any Zeta rules class.

**Affected attributes:**
- DimensionTemplateParameter.isRequired
- Partner.isEszamla, active, genericValid
- DimensionTemplateGroup.isRequired, multiLine
- PhoneNumber.isFax, active
- PaymentMethod.active
- DimensionGroup.isRequired, multiLine
- RepairCategory.withoutDimensionParameters

### Category 2: Missing `eExceptions` for Operations (7 operations)

**ETL Rule (operation.etl:197-199, in abstract CreateOperation):**
```etl
for (f in s.faults) {
    t.eExceptions.add(f.type.asmEquivalent());
}
```

**Status:** The `CreateBoundOperation` Zeta rule in `OperationRules.java:166` is missing faults handling.

**Affected operations:**
- Partner: _instanceOperationCreateRack, _instanceOperationCopyRack, _instanceOperationCreateWarehouse
- Item: _instanceOperationCreateCostPrice
- DimensionGroup: _instanceOperationQueryTemplateGroup
- JobSheetItem: _instanceOperationStatusToWontStart, _instanceOperationStatusToDone

### Category 3: Annotation Count Mismatch (5 attributes)

These are a side effect of Category 1 - attributes that should have both `unmappedDefaultOnly` AND another annotation but are missing the former.

**Affected attributes:**
- DimensionTemplateGroup.attributePerRow (size=2 vs size=1)
- Item.standardPriceModifier (size=2 vs size=1)
- DimensionGroup.attributePerRow (size=2 vs size=1)
- RepairCategory.dimensionParameterSummary (size=2 vs size=1)
- RepairCategory.multiplier (size=2 vs size=1)

## Approach

### Fix 1: Add faults handling to CreateBoundOperation

Add the missing faults loop to `OperationRules.java`:

```java
// In createBoundOperation() method, after setting output type:

// Add faults as exceptions
for (var fault : s.getFaults()) {
    if (fault.getType() != null) {
        EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
        if (faultType != null) {
            t.getEExceptions().add(faultType);
        }
    }
}
```

### Fix 2: Implement unmappedDefaultOnly annotations

Add two new rules to `DataRules.java`:

1. `addUnmappedDefaultOnlyAttributeAnnotation()` - transforms Attribute to EAnnotation
2. `addUnmappedDefaultOnlyReferenceAnnotation()` - transforms AssociationEnd to EAnnotation

Both rules need the complex guard condition that checks if the entity's default representation has a transfer attribute with this attribute as binding and has a default value.

## Impact

- **Modules affected:** judo-tatami-psm2asm
- **Breaking changes:** None (additive fix)
- **Files modified:**
  - `OperationRules.java` - add faults handling
  - `DataRules.java` - add unmappedDefaultOnly annotation rules

## Verification

After implementation, run:
```bash
mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance
```

Expected result: "SUCCESS: ETL and Zeta models are structurally equivalent" with 0 differences.

## Risks

- **Low:** The guard condition for unmappedDefaultOnly is complex and may need careful implementation to match ETL behavior exactly.
- **Mitigation:** Use the RackInspect model comparison test to verify each fix.
