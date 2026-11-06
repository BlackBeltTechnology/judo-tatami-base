# Design: Remove @Primary Annotation from Zeta Transformations

## Overview

This document provides detailed design rationale for removing `@Primary` annotations and using explicit named equivalent calls in Zeta transformations.

## Findings

### @Primary Location

**Confirmed**: `@Primary` is only used in one location:
- `judo-tatami-asm2rdbms/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/zeta/rules/ClassRules.java:144`

The mention in `Asm2RdbmsZetaTransformation.java:66` is just a documentation comment, not an actual annotation.

## Named Equivalent Pattern (2-argument form)

### Before (with @Primary)

```java
@Primary
@Transform(type = EClass.class)
@To(type = RdbmsTable.class)
public TransformFunction<EClass, RdbmsTable> eClassToRdbmsTable() {
    return (s, ctx) -> {
        // Later code can use type-based equivalent
        RdbmsTable table = ctx.equivalent(s, RdbmsTable.class);
    };
}
```

### After (named equivalent only, 2-argument form)

```java
@Transform(type = EClass.class)
@To(type = RdbmsTable.class)
public TransformFunction<EClass, RdbmsTable> eClassToRdbmsTable() {
    return (s, ctx) -> {
        // Explicitly name the rule (2-argument form)
        RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);
    };
}
```

## Polymorphic Type Handling

For types that have multiple rules (e.g., `EDataType` for StringType, IntegerType, BooleanType, etc.), type-based lookup doesn't work. Must use specific rule names.

### Pattern from TypeRules.java (lines 243-247)

```java
// Add to equivalent type using named rule (thread-safe)
// First try to find the target via the integer or decimal rule
EDataType dataType = null;
if (Psm2AsmHelper.isInteger(s)) {
    dataType = ctx.equivalent(s, EDataType.class, CREATE_INTEGER_TYPE);
} else if (Psm2AsmHelper.isDecimal(s)) {
    dataType = ctx.equivalent(s, EDataType.class, CREATE_DECIMAL_TYPE);
}
```

### When refactoring DataRules.java:163

Original:
```java
EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
```

Must be refactored to use specific type rules based on the actual data type.

## Comment Template for Equivalent Calls

```java
// Use named equivalent (ETL uses s.equivalent("RuleName"))
EClass entityClass = ctx.equivalent(s, CREATE_ENTITY_CLASS);
```

## File-by-File Changes Summary

### ClassRules.java (asm2rdbms)
- Remove `@Primary` annotation (line 144)
- 11 `ctx.equivalent()` calls need ETL difference comments

### DataRules.java (psm2asm)
- Line 131: `ctx.equivalent(superType, EClass.class)` → `ctx.equivalent(superType, CREATE_ENTITY_CLASS)`
- Line 163: Refactor polymorphic type lookup to use specific rules

### Other psm2asm files
- ActorRules, DerivedRules, OperationRules, StaticRules, TransferObjectRules
- Each needs review to replace type-based equivalents with named rule calls

### rdbms2liquibase files
- Already use named rules, just need ETL difference comments

## Validation Strategy

All existing tests should pass without modification because:
1. `@Primary` and named equivalent use the same rule
2. Transformation logic is unchanged
3. Only rule selection is made explicit

## Migration Order

1. **Phase 1**: Update `ClassRules.java` - remove @Primary, add comments
2. **Phase 2**: Update `DataRules.java` - fix polymorphic type lookup
3. **Phase 3-6**: Update other psm2asm rules
4. **Phase 7**: Update rdbms2liquibase (comments only)
5. **Phase 8**: Validate all tests pass
