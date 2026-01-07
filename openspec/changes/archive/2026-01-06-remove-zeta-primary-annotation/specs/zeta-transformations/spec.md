# zeta-transformations Specification

## MODIFIED Requirements

### Requirement: All Equivalent Lookups Must Use Named Rules

All `ctx.equivalent()` calls in Zeta transformations MUST use the 2-argument form with explicit rule name. Type-based lookups relying on `@Primary` are NOT allowed.

#### Scenario: Replace type-based equivalent with named rule
Given a Zeta rule that uses `ctx.equivalent(source, TargetType.class)`
When refactoring to use named equivalent
Then replace with `ctx.equivalent(source, RULE_NAME_CONSTANT)`
And use the rule name constant from the appropriate `*RuleNames.java` file

#### Scenario: Equivalent lookup for inheritance
Given an EntityType with super types
When looking up the parent's equivalent
Then use `ctx.equivalent(superType, CREATE_ENTITY_CLASS)`

### Requirement: No @Primary Annotations in Zeta Transformations

Zeta transformations MUST NOT use the `@Primary` annotation. All rule lookups must be explicit.

#### Scenario: Remove @Primary from ClassRules
Given `ClassRules.java` has `@Primary` annotation on `eClassToRdbmsTable()`
When removing @Primary
Then remove the annotation from the method
And update all `ctx.equivalent()` calls to use named rules

### Requirement: Document ETL Pattern Differences

All rule definitions and equivalent calls MUST have documentation comments.

#### Scenario: Equivalent call comments
Given a `ctx.equivalent()` call
When adding documentation
Then add inline comment:
- Reference to ETL pattern: `s.equivalent("RuleName")`

## ADDED Requirements

### Requirement: Polymorphic Type Handling

For types that have multiple rules (e.g., EDataType for StringType, IntegerType, etc.), type-based lookup MUST be refactored to use specific rule names based on actual type.

#### Scenario: Attribute data type lookup
Given an Attribute with a data type
When looking up the corresponding EClassifier
Then check the actual type and use specific rule:
```java
EDataType dataType = null;
if (Psm2AsmHelper.isInteger(s.getDataType())) {
    dataType = ctx.equivalent(s.getDataType(), CREATE_INTEGER_TYPE);
} else if (Psm2AsmHelper.isDecimal(s.getDataType())) {
    dataType = ctx.equivalent(s.getDataType(), CREATE_DECIMAL_TYPE);
}
```

### Requirement: Rule Name Constants Must Be Used

All rule lookups MUST use constants from `*RuleNames.java` files, not string literals.

#### Scenario: Using rule name constant
Given a `ctx.equivalent()` call
When specifying the rule name
Then use the static constant from the appropriate RuleNames class
Example: `CREATE_ENTITY_CLASS` instead of `"CreateEntityClass"`
