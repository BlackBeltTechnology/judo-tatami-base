# Design: Refactor Asm2Rdbms Zeta to Use ETL-Based Rules

## Architecture Overview

The refactored implementation will follow the same pattern as `Psm2AsmZetaTransformation`:

```
judo-tatami-asm2rdbms/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/zeta/
├── Asm2RdbmsZetaTransformation.java  (orchestrator - simplified)
├── Asm2RdbmsRuleNames.java           (rule name constants - existing)
├── Asm2RdbmsHelper.java              (utility methods - new)
└── rules/
    ├── PackageRules.java             (package.etl)
    ├── ClassRules.java               (class.etl)
    ├── AttributeRules.java           (attribute.etl)
    └── ReferenceRules.java           (reference.etl)
```

## ETL to Zeta Rule Mapping

### package.etl

| ETL Rule | Zeta Method | Annotations |
|----------|-------------|-------------|
| `rootPackegeToModel` | `rootPackegeToModel()` | `@TransformRule` |
| `rootPackegeToConfiguration` | `rootPackegeToConfiguration()` | `@TransformRule` |

**ETL Pattern**:
```etl
rule rootPackegeToConfiguration
    transform s : ASM!EPackage
    to t : RDBMS!RdbmsConfiguration {
        guard : s.eSuperPackage.isUndefined()
        s.equivalent().configuration = t;  // Uses default equivalent (rootPackegeToModel)
```

**Zeta Pattern**:
```java
@TransformRule(name = ROOT_PACKAGE_TO_CONFIGURATION)
@Guard(method = "isRootPackage")
@Transform(type = EPackage.class)
@To(type = RdbmsConfiguration.class)
public TransformFunction<EPackage, RdbmsConfiguration> rootPackegeToConfiguration() {
    return (s, ctx) -> {
        RdbmsConfiguration t = ctx.createTarget(RdbmsConfiguration.class);
        // ...
        RdbmsModel model = ctx.equivalent(s, ROOT_PACKAGE_TO_MODEL);  // Named equivalent
        model.setConfiguration(t);
        return t;
    };
}
```

### class.etl

| ETL Rule | Zeta Method | Annotations |
|----------|-------------|-------------|
| `EClassToRdbmsTable` | `eClassToRdbmsTable()` | `@TransformRule`, `@Primary` |
| `EClassToTableIdField` | `eClassToTableIdField()` | `@TransformRule` |
| `EClassToTableTypeField` | `eClassToTableTypeField()` | `@TransformRule` |
| `EClassToTableVersionField` | `eClassToTableVersionField()` | `@TransformRule` |
| `EClassToTableCreateUsernameField` | `eClassToTableCreateUsernameField()` | `@TransformRule` |
| `EClassToTableCreateUserIdField` | `eClassToTableCreateUserIdField()` | `@TransformRule` |
| `EClassToTableCreateTimestampField` | `eClassToTableCreateTimestampField()` | `@TransformRule` |
| `EClassToTableUpdateUsernameField` | `eClassToTableUpdateUsernameField()` | `@TransformRule` |
| `EClassToTableUpdateUserIdField` | `eClassToTableUpdateUserIdField()` | `@TransformRule` |
| `EClassToTableUpdateTimestampField` | `eClassToTableUpdateTimestampField()` | `@TransformRule` |

**Key ETL Pattern - Named equivalent lookup**:
```etl
rule EClassToTableIdField
    transform s : ASM!EClass
    to t : RDBMS!RdbmsIdentifierField {
        var table = s.equivalent("EClassToRdbmsTable");  // Named lookup
        table.fields.add(t);
        table.primaryKey = t;
```

**Zeta Pattern**:
```java
@TransformRule(name = ECLASS_TO_TABLE_ID_FIELD)
@Guard(method = "isEntityType")
@Transform(type = EClass.class)
@To(type = RdbmsIdentifierField.class)
public TransformFunction<EClass, RdbmsIdentifierField> eClassToTableIdField() {
    return (s, ctx) -> {
        RdbmsIdentifierField t = ctx.createTarget(RdbmsIdentifierField.class);
        // ...
        RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE);  // Named lookup
        table.getFields().add(t);
        table.setPrimaryKey(t);
        return t;
    };
}
```

### attribute.etl

| ETL Rule | Zeta Method | Annotations |
|----------|-------------|-------------|
| `EAttributeToRdbmsField` | `eAttributeToRdbmsField()` | `@TransformRule`, `@Abstract` |
| `EAttributeToTableValueField` | `eAttributeToTableValueField()` | `@TransformRule`, `@Extends` |
| `EAttributeToIndex` | `eAttributeToIndex()` | `@TransformRule` |

**ETL Pattern - Abstract rule with extends**:
```etl
@abstract
rule EAttributeToRdbmsField
    transform s : ASM!EAttribute
    to t : RDBMS!RdbmsField {
        guard : s.eContainingClass.isEntityType()
        // Common field setup...
}

rule EAttributeToTableValueField
    transform s : ASM!EAttribute
    to t : RDBMS!RdbmsValueField
    extends EAttributeToRdbmsField {
        guard : s.eContainingClass.isEntityType() and not s.derived
        s.eContainingClass.equivalent("EClassToRdbmsTable").fields.add(t);
}
```

**Zeta Pattern**:
```java
@TransformRule(name = EATTRIBUTE_TO_RDBMS_FIELD)
@Abstract
@Guard(method = "isAttributeInEntityType")
@Transform(type = EAttribute.class)
@To(type = RdbmsField.class)
public TransformFunction<EAttribute, RdbmsField> eAttributeToRdbmsField() {
    return (s, ctx) -> {
        RdbmsField t = ctx.createTarget(RdbmsField.class);
        // Common field setup...
        return t;
    };
}

@TransformRule(name = EATTRIBUTE_TO_TABLE_VALUE_FIELD)
@Extends({EATTRIBUTE_TO_RDBMS_FIELD})
@Guard(method = "isAttributeInEntityTypeNotDerived")
@Transform(type = EAttribute.class)
@To(type = RdbmsValueField.class)
public TransformFunction<EAttribute, RdbmsValueField> eAttributeToTableValueField() {
    return (s, ctx) -> {
        RdbmsValueField t = ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s);
        RdbmsTable table = ctx.equivalent(s.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
        table.getFields().add(t);
        return t;
    };
}
```

### reference.etl

| ETL Rule | Zeta Method | Annotations |
|----------|-------------|-------------|
| `EReferenceToRdbmsTableForeignKey` | `eReferenceToRdbmsTableForeignKey()` | `@TransformRule` |
| `EReferenceToRdbmsTableInverseForeignKey` | `eReferenceToRdbmsTableInverseForeignKey()` | `@TransformRule` |
| `EReferenceToRdbmsJunctionTable` | `eReferenceToRdbmsJunctionTable()` | `@TransformRule`, `@Lazy` |
| `EReferenceToRdbmsJunctionTablePrimaryKey` | `eReferenceToRdbmsJunctionTablePrimaryKey()` | `@TransformRule` |
| `EReferenceToRdbmsJunctionTableForeignKeyBidirectional` | `eReferenceToRdbmsJunctionTableForeignKeyBidirectional()` | `@TransformRule` |
| `EReferenceToRdbmsJunctionTableForeignKeyUnidirectional` | `eReferenceToRdbmsJunctionTableForeignKeyUnidirectional()` | `@TransformRule` |

**ETL Pattern - Lazy rule**:
```etl
@lazy
rule EReferenceToRdbmsJunctionTable
    transform s : ASM!EReference
    to t : RDBMS!RdbmsJunctionTable {
        // Only called when another rule does s.equivalent("EReferenceToRdbmsJunctionTable")
}

rule EReferenceToRdbmsJunctionTablePrimaryKey
    transform s : ASM!EReference
    to p : RDBMS!RdbmsIdentifierField {
        guard : s.ruleMapping().joinTable and s.ruleMapping().first
        var table = s.equivalent("EReferenceToRdbmsJunctionTable");  // Triggers lazy creation
```

**Zeta Pattern**:
```java
@TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE)
@Lazy
@Guard(method = "isValidReferenceForJunction")
@Transform(type = EReference.class)
@To(type = RdbmsJunctionTable.class)
public TransformFunction<EReference, RdbmsJunctionTable> eReferenceToRdbmsJunctionTable() {
    return (s, ctx) -> {
        RdbmsJunctionTable t = ctx.createTarget(RdbmsJunctionTable.class);
        // Setup...
        return t;
    };
}

@TransformRule(name = EREFERENCE_TO_RDBMS_JUNCTION_TABLE_PRIMARY_KEY)
@Guard(method = "isJoinTableFirst")
@Transform(type = EReference.class)
@To(type = RdbmsIdentifierField.class)
public TransformFunction<EReference, RdbmsIdentifierField> eReferenceToRdbmsJunctionTablePrimaryKey() {
    return (s, ctx) -> {
        RdbmsIdentifierField p = ctx.createTarget(RdbmsIdentifierField.class);
        // Triggers lazy junction table creation
        RdbmsJunctionTable table = ctx.equivalent(s, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
        table.getFields().add(p);
        table.setPrimaryKey(p);
        return p;
    };
}
```

## Helper Class Design

Extract utility methods to `Asm2RdbmsHelper.java`:

```java
public final class Asm2RdbmsHelper {
    // ID methods
    public static String getId(EObject element);

    // SQL name methods
    public static String tableSqlName(EClass eClass, int maxSize, String prefix);
    public static String fieldSqlName(EAttribute attr, int maxSize, String prefix);
    public static String referenceIdentifierSqlName(EReference ref, int maxSize, String prefix);
    public static String referenceFkSqlName(EReference ref);
    // ... more methods

    // Type filling
    public static void fillType(RdbmsField field, String javaType, EAttribute attr, Map<String, TypeMapping> typeMappings);

    // MD5 hashing
    public static String md5(String input);
}
```

## Transformation Orchestrator

The main `Asm2RdbmsZetaTransformation` becomes simplified:

```java
public TransformationTrace execute() {
    TransformationRegistry registry = new TransformationRegistry();

    // Register rule classes in order matching ETL imports
    registry.register(PackageRules.class);
    registry.register(ClassRules.class);
    registry.register(AttributeRules.class);
    registry.register(ReferenceRules.class);

    // Create context with configuration
    TransformationContext context = createContext(registry);

    // Execute
    TransformationExecutor executor = TransformationExecutor.builder()
            .registry(registry)
            .context(context)
            .parallel(false)
            .build();

    TransformationResult result = executor.transform();

    // Post-processing (name mappings)
    postProcess(context);

    return result.getTrace();
}
```

## Trade-offs

### Pros
1. **Consistency** - Same pattern as Psm2AsmZetaTransformation
2. **Maintainability** - Rule organization matches ETL files
3. **Debugging** - Named equivalents make tracing easier
4. **Correctness** - Using framework properly ensures correct execution order

### Cons
1. **Code volume** - More files and boilerplate
2. **Migration effort** - Significant refactoring required
3. **Testing** - Need to verify identical output

## Risk Mitigation

1. **Dual-engine tests** - Existing tests compare ETL and Zeta output
2. **Incremental migration** - Migrate one rule file at a time
3. **Rule constants** - Existing `Asm2RdbmsRuleNames.java` ensures consistent naming
