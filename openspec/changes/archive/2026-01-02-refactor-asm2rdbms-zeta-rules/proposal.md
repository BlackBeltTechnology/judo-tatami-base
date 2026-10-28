# Proposal: Refactor Asm2Rdbms Zeta to Use ETL-Based Rules

## Summary

Refactor `Asm2RdbmsZetaTransformation` to use declarative rule classes with Zeta annotations, matching the ETL structure exactly. Currently the implementation uses inline methods instead of proper `@TransformRule`, `@Transform`, `@To`, `@Abstract`, `@Lazy`, and `@Primary` annotations.

## Problem Statement

The current `Asm2RdbmsZetaTransformation` implementation:
1. Uses inline methods (`createTableIdField()`, `createTableTypeField()`, etc.) instead of separate rule methods
2. Manually manages trace with `addTrace()` instead of using `ctx.equivalent()`
3. Does not use named equivalent lookups matching ETL patterns
4. Missing proper Zeta annotations: `@TransformRule`, `@Abstract`, `@Primary`, `@Lazy`
5. Does not follow the rule-per-file organization used by Psm2AsmZetaTransformation

### ETL Rules to Match

The ETL uses these rule files:
- `package.etl`: `rootPackegeToModel`, `rootPackegeToConfiguration`
- `class.etl`: `EClassToRdbmsTable` (@primary), `EClassToTableIdField`, `EClassToTableTypeField`, etc.
- `attribute.etl`: `EAttributeToRdbmsField` (@abstract), `EAttributeToTableValueField`, `EAttributeToIndex`
- `reference.etl`: Various FK rules, `EReferenceToRdbmsJunctionTable` (@lazy)

### Key ETL Patterns to Replicate

1. **Named `equivalent()` calls**: ETL uses `s.equivalent("RuleName")` to get specific rule results
   ```etl
   var table = s.equivalent("EClassToRdbmsTable");
   table.fields.add(t);
   ```

2. **`@primary` annotation**: `EClassToRdbmsTable` is marked primary
   ```etl
   @primary
   rule EClassToRdbmsTable
   ```

3. **`@abstract` annotation**: `EAttributeToRdbmsField` is abstract, extended by `EAttributeToTableValueField`
   ```etl
   @abstract
   rule EAttributeToRdbmsField
   ...
   rule EAttributeToTableValueField extends EAttributeToRdbmsField
   ```

4. **`@lazy` annotation**: `EReferenceToRdbmsJunctionTable` is lazy (called on-demand)
   ```etl
   @lazy
   rule EReferenceToRdbmsJunctionTable
   ```

## Proposed Solution

Refactor to match PSM2ASM transformation structure:

1. Create rule classes in `rules/` subdirectory:
   - `PackageRules.java` - package transformation rules
   - `ClassRules.java` - EClass to RdbmsTable rules
   - `AttributeRules.java` - attribute transformation rules
   - `ReferenceRules.java` - reference/FK transformation rules

2. Use proper Zeta annotations:
   - `@TransformRule(name = "...")` for each rule
   - `@Primary` for `EClassToRdbmsTable`
   - `@Abstract` for `EAttributeToRdbmsField`
   - `@Lazy` for `EReferenceToRdbmsJunctionTable`
   - `@Extends` for rule inheritance
   - `@Guard(method = "...")` for guard conditions

3. Use `ctx.equivalent(source, "ruleName")` for named lookups matching ETL

4. Use `TransformationRegistry` and `TransformationExecutor` like Psm2Asm

## Scope

- **In Scope**:
  - Refactor Asm2RdbmsZetaTransformation to use rule classes
  - Match ETL rule structure exactly
  - Use proper Zeta annotations
  - Use named equivalent lookups

- **Out of Scope**:
  - Changing transformation behavior
  - Adding new transformation rules
  - Modifying ETL implementation

## Success Criteria

1. Zeta produces identical RDBMS model output as ETL
2. Rule names match ETL exactly
3. Named equivalent lookups match ETL patterns
4. All annotations (@Primary, @Abstract, @Lazy, @Extends) are used correctly
5. Existing dual-engine tests continue to pass

## Related Specs

- `zeta-transformations` - Zeta transformation requirements
