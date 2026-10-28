# Tasks

## Phase 1: Fix Missing eExceptions for BoundOperation

- [x] Add faults handling to `createBoundOperation()` in `OperationRules.java`
  - Location: `judo-tatami-psm2asm/src/main/java/.../zeta/rules/OperationRules.java:166`
  - Add loop: `for (var fault : s.getFaults()) { ... t.getEExceptions().add(faultType); }`
- [x] Run RackInspect test to verify eExceptions differences are fixed

## Phase 2: Implement unmappedDefaultOnly Annotations

- [x] Implement `addUnmappedDefaultOnlyAttributeAnnotation()` in `DataRules.java`
  - Source type: `Attribute`
  - Target type: `EAnnotation`
  - Guard: entity has defaultRepresentation with transfer attribute binding to this attribute with defaultValue
  - Annotation URI: `unmappedDefaultOnly`
  - Detail: key="value", value=s.unmappedDefaultOnly.toString()

- [x] Implement `addUnmappedDefaultOnlyReferenceAnnotation()` in `DataRules.java`
  - Source type: `AssociationEnd`
  - Target type: `EAnnotation`
  - Guard: entity has defaultRepresentation with transfer relation binding to this associationEnd with defaultValue
  - Annotation URI: `unmappedDefaultOnly`
  - Detail: key="value", value=s.unmappedDefaultOnly.toString()

- [x] Run RackInspect test to verify unmappedDefaultOnly differences are fixed

## Phase 3: Verification

- [x] Run full RackInspect performance test
- [x] Verify 0 differences between ETL and Zeta output
- [x] Run full Psm2Asm test suite to ensure no regressions
- [x] Update AGENTS.md with lessons learned if applicable

## Summary of Changes

### OperationRules.java
Added faults handling to `createBoundOperation()` method at line 180:
```java
// Add faults as exceptions (from CreateOperation abstract rule in ETL)
for (var fault : s.getFaults()) {
    if (fault.getType() != null) {
        EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
        if (faultType != null) {
            t.getEExceptions().add(faultType);
        }
    }
}
```

### DataRules.java
Added two new `@Greedy` rules:
1. `addUnmappedDefaultOnlyAttributeAnnotation()` - Adds unmappedDefaultOnly annotation for entity attributes that have default values in the entity's default representation
2. `addUnmappedDefaultOnlyReferenceAnnotation()` - Adds unmappedDefaultOnly annotation for entity association ends that have default values in the entity's default representation

Both rules use guard conditions to check:
- Container is an EntityType
- EntityType has a defaultRepresentation
- The attribute/relation binding matches and has a defaultValue

## ETL Reference

### AddUnmappedDefaultOnlyAttributeAnnotation (data.etl:236-248)
```etl
rule AddUnmappedDefaultOnlyAttributeAnnotation
    transform s : JUDOPSM!Attribute
    to t : ASM!EAnnotation {
        guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
               and s.eContainer.defaultRepresentation.attributes.exists(
                   a | a.binding == s and a.defaultValue.isDefined())
        t.setId("(psm/" + s.getId() + ")/UnmappedDefaultOnlyAttributeAnnotation");
        t.source = asmUtils.getAnnotationUri("unmappedDefaultOnly");

        var unmappedDefaultOnly = new ASM!EStringToStringMapEntry;
        unmappedDefaultOnly.setId(t.getId() + "/UnmappedDefaultOnly");
        unmappedDefaultOnly.key = "value";
        unmappedDefaultOnly.value = s.unmappedDefaultOnly.asString();
        t.details.add(unmappedDefaultOnly);

        s.equivalent("CreateAttribute").eAnnotations.add(t);
    }
```

### AddUnmappedDefaultOnlyReferenceAnnotation (data.etl:250-263)
```etl
rule AddUnmappedDefaultOnlyReferenceAnnotation
    transform s : JUDOPSM!AssociationEnd
    to t : ASM!EAnnotation {
        guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
               and s.eContainer.defaultRepresentation.relations.exists(
                   r | r.binding == s and r.defaultValue.isDefined())
        t.setId("(psm/" + s.getId() + ")/UnmappedDefaultOnlyReferenceAnnotation");
        t.source = asmUtils.getAnnotationUri("unmappedDefaultOnly");

        var unmappedDefaultOnly = new ASM!EStringToStringMapEntry;
        unmappedDefaultOnly.setId(t.getId() + "/UnmappedDefaultOnly");
        unmappedDefaultOnly.key = "value";
        unmappedDefaultOnly.value = s.unmappedDefaultOnly.asString();
        t.details.add(unmappedDefaultOnly);

        s.equivalent("CreateAssociationEndRelation").eAnnotations.add(t);
    }
```

### CreateOperation faults handling (operation.etl:197-199)
```etl
for (f in s.faults) {
    t.eExceptions.add(f.type.asmEquivalent());
}
```
