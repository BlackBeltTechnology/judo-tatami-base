# Fix PSM2ASM Zeta Additional Annotation Differences

## Summary

The RackInspect STRICT comparison test revealed additional differences between ETL and Zeta outputs beyond the `default` annotation issues fixed in the previous change. This proposal addresses the remaining annotation differences to achieve full STRICT mode equivalence.

## Differences Found

### 1. Missing `claim` Annotations (TransferAttribute)

**ETL Rule**: `CreateTransferAttributeClaimAnnotation` (transferObject.etl:373-387)
```etl
rule CreateTransferAttributeClaimAnnotation
    transform s : JUDOPSM!TransferAttribute
    to t : ASM!EAnnotation {
        guard: s.claimType.isDefined()
        t.source = asmUtils.getAnnotationUri("claim");
        t.details.add({key="value", value=s.claimType});
        s.equivalent("CreateTransferObjectAttribute").eAnnotations.add(t);
    }
```

**Status**: Rule name constant `CREATE_TRANSFER_ATTRIBUTE_CLAIM_ANNOTATION` exists but rule is not implemented in Zeta.

### 2. Missing `metadata` Annotations (TransferObjectType)

**ETL Rule**: `CreateMetadataAnnotationForMetadataClass` (transferObject.etl:649-662)
```etl
rule CreateMetadataAnnotationForMetadataClass
    transform s : JUDOPSM!TransferObjectType
    to t : ASM!EAnnotation {
        guard: s.isMetadataType()
        t.source = asmUtils.getAnnotationUri("metadata");
        t.details.add({key="value", value="true"});
        s.asmEquivalent().eAnnotations.add(t);
    }
```

**isMetadataType() logic** (from transferObject.eol:22-26):
```eol
operation JUDOPSM!TransferObjectType isMetadataType() : Boolean {
    return JUDOPSM!TransferOperation.all()
        .exists(o | o.behaviour.isDefined() and
                o.behaviour.behaviourType == GET_METADATA and
                o.output.isDefined() and
                (o.output.type == self or o.output.type.relations.exists(r | r.target == self)));
}
```

A TransferObjectType is metadata type if there exists a GET_METADATA operation whose output type is this type (or has relations targeting it).

**Status**: Rule name constant `CREATE_METADATA_ANNOTATION` exists but rule is not implemented in Zeta.

### 3. Missing `queryCustomizer` Annotations (TransferObjectType)

**ETL Rule**: `CreateQueryCustomizerAnnotationForQueryCustomizerClass` (transferObject.etl:631-645)
```etl
rule CreateQueryCustomizerAnnotationForQueryCustomizerClass
    transform s : JUDOPSM!TransferObjectType
    to t : ASM!EAnnotation {
        guard: s.queryCustomizer
        t.source = asmUtils.getAnnotationUri("queryCustomizer");
        t.details.add({key="value", value="true"});
        s.asmEquivalent().eAnnotations.add(t);
    }
```

**Status**: Rule name constant `CREATE_QUERY_CUSTOMIZER_ANNOTATION` exists but rule is not implemented in Zeta.

### 4. Incorrect `inputRange` Annotations (TransferOperation)

**ETL Rule**: `CreateTransferOperationInputRangeAnnotation` (operation.etl:514-528)
```etl
rule CreateTransferOperationInputRangeAnnotation
    transform s : JUDOPSM!TransferOperation
    to t : ASM!EAnnotation {
        guard: s.inputRange.isDefined()
        t.source = asmUtils.getAnnotationUri("inputRange");
        range.value = asmUtils.getReferenceFQName(s.inputRange.asmEquivalent());
        t.details.add(range);
        s.asmEquivalent().eAnnotations.add(t);
    }
```

**Zeta Bug Analysis** (OperationRules.java:1355-1381):

The current Zeta implementation is completely wrong:

1. **Wrong Guard**: Uses `isInputRangeOperation` which checks for `GET_RANGE` behaviour type:
   ```java
   op.getBehaviour().getBehaviourType() == TransferOperationBehaviourType.GET_RANGE
   ```
   Should check: `s.getInputRange() != null`

2. **Wrong Value**: Adds `"true"` instead of the reference FQ name:
   ```java
   addAnnotationDetail(t, "value", "true");  // WRONG
   ```
   Should be: `getReferenceFQName(ctx.equivalent(s.getInputRange(), EReference.class))`

3. **Extra Detail**: Adds spurious "operation" detail not present in ETL

**Status**: Rule needs complete rewrite - guard, value, and logic are all incorrect.

### 5. `mappedEntityType` Annotation Missing `filter`/`filterDialect` Details

**ETL Rule**: `CreateMappedEntityTypeAnnotationOnMappedTransferObject` adds additional details when `s.filter.isDefined()`:
- `filter` key with value `s.filter.expression`
- `filterDialect` key with value `s.filter.dialect.asString()`

**Status**: Zeta rule adds only `value` detail but misses `filter` and `filterDialect` details.

## Solution

1. Implement missing `claim` annotation rule in `TransferObjectRules.java`
2. Implement missing `metadata` annotation rule in `TransferObjectRules.java`
3. Implement missing `queryCustomizer` annotation rule in `TransferObjectRules.java`
4. Fix `inputRange` annotation guard in `OperationRules.java`
5. Add `filter` and `filterDialect` details to `mappedEntityType` annotation

## Scope

- **Module**: judo-tatami-psm2asm
- **Files**:
  - `TransferObjectRules.java` - add claim, metadata, queryCustomizer annotations
  - `OperationRules.java` - fix inputRange annotation guard

## Affected Tests

- `RackInspectPerformanceTest` with STRICT comparison mode

## Related Changes

- `fix-psm2asm-zeta-annotation-differences` - fixed `default` annotations

## Related Specifications

- `psm2asm-transformation`
- `zeta-transformations`
