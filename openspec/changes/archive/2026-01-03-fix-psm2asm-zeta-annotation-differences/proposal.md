# Fix PSM2ASM Zeta Annotation Differences

## Summary

The PSM2ASM Zeta transformation produces output that differs from the ETL transformation in STRICT model comparison. STRICT comparison tests revealed 50 differences in the RackInspect production model, consisting of:

- **9 missing** `default` annotations on transfer object attributes/relations
- **2 unexpected** `exposedBy` annotations on default value attributes
- **39 value order mismatches** in `behaviour` annotation details (acceptable - order doesn't matter semantically)

## Root Cause Analysis

### Missing `default` Annotations

The ETL rules `AddDefaultAnnotationToTransferAttribute` and `AddDefaultAnnotationToTransferObjectRelation` in `transferObject.etl` add annotations when a `TransferAttribute` or `TransferObjectRelation` has a `defaultValue` defined. The corresponding Zeta rules are declared in `Psm2AsmRuleNames.java` but **not implemented** in `TransferObjectRules.java`.

ETL code (transferObject.etl lines 287-301):
```etl
rule AddDefaultAnnotationToTransferAttribute
    transform s : JUDOPSM!TransferAttribute
    to t : ASM!EAnnotation {
        guard: s.defaultValue.isDefined()
        t.source = asmUtils.getAnnotationUri("default");
        var defaultValue = new ASM!EStringToStringMapEntry;
        defaultValue.key = "value";
        defaultValue.value = s.defaultValue.name;
        t.details.add(defaultValue);
        s.equivalent("CreateTransferObjectAttribute").eAnnotations.add(t);
    }
```

### Extra `exposedBy` Annotations (Cascade Effect)

The extra `exposedBy` annotations on derived default attributes (e.g., `_startDate_default_UpdateExchangeRateForDateIntervalInput`) are a **cascade effect** of the missing `default` annotations.

In `AsmUtils.enrichWithAnnotations()` (line 1096), there's a filter:
```java
transferObjectType.getEAllAttributes().stream()
    .filter(a -> !transferObjectType.getEAllAttributes().stream()
        .anyMatch(d -> Objects.equals(a.getName(),
            AsmUtils.getExtensionAnnotationValue(d, "default", false).orElse("-"))))
    .forEach(a -> addExtensionAnnotation(a, EXPOSED_BY_ANNOTATION_NAME, actorTypeFqName));
```

**Logic**: Don't add `exposedBy` to attributes whose name matches another attribute's `default` annotation value.

**ETL behavior**:
1. `startDate` attribute has `default` annotation with value `_startDate_default_*`
2. Filter excludes `_startDate_default_*` from getting `exposedBy`

**Zeta behavior** (broken):
1. `startDate` is **missing** the `default` annotation
2. Filter doesn't exclude `_startDate_default_*`
3. `_startDate_default_*` incorrectly gets `exposedBy`

**Conclusion**: Once the missing `default` annotation rules are implemented, the extra `exposedBy` issue will automatically be resolved.

## Solution

Implement the missing rules in `TransferObjectRules.java`:
1. `AddDefaultAnnotationToTransferAttribute` - for TransferAttribute with defaultValue
2. `AddDefaultAnnotationToTransferObjectRelation` - for TransferObjectRelation with defaultValue

No separate fix needed for `exposedBy` - it's a cascade effect.

## Scope

- **Module**: judo-tatami-psm2asm
- **Files**:
  - `TransferObjectRules.java` - add missing default annotation rules

## Affected Tests

- `RackInspectPerformanceTest` with STRICT comparison mode
- `Psm2AsmDualTransformationTest` and other equivalence tests

## Related Specifications

- `psm2asm-transformation`
- `zeta-transformations`
