# ETL vs Zeta: Syntax and Pattern Comparison

This document provides a comprehensive comparison of ETL (Epsilon Transformation Language) and Zeta (Java-based transformation framework) syntax and patterns.

## Rule Definition Patterns

### Simple Transformation Rule

**ETL:**
```etl
@greedy
rule CreateStringType
    transform s : JUDOPSM!StringType
    to t : ASM!EDataType {
        t.setId("(psm/" + s.getId() + ")/StringType");
        t.name = s.name;
        t.instanceClassName = "java.lang.String";
        s.eContainer.asmEquivalent().eClassifiers.add(t);
    }
```

**Zeta:**
```java
@TransformRule(name = "CreateStringType")
@Greedy
@Transform(type = StringType.class)
@To(type = EDataType.class)
public TransformFunction<StringType, EDataType> createStringType() {
    return (s, ctx) -> {
        EDataType t = ctx.createTarget(EDataType.class);
        setId(t, "(psm/" + getId(s) + ")/StringType");
        t.setName(s.getName());
        t.setInstanceClassName("java.lang.String");

        EPackage containerPkg = ctx.equivalent(s.eContainer(), EPackage.class);
        if (containerPkg != null) {
            containerPkg.getEClassifiers().add(t);
        }
        return t;
    };
}
```

### Guarded Rule

**ETL:**
```etl
@greedy
rule CreateIntegerType
    transform s : JUDOPSM!NumericType
    to t : ASM!EDataType {
        guard: s.isInteger()
        t.setId("(psm/" + s.getId() + ")/IntegerType");
        t.name = s.name;
        t.instanceClassName = getIntegerClassName(s);
    }
```

**Zeta:**
```java
public boolean isIntegerGuard(EObject source, TransformationContext ctx) {
    return source instanceof NumericType && isInteger((NumericType) source);
}

@TransformRule(name = "CreateIntegerType")
@Greedy
@Guard(method = "isIntegerGuard")
@Transform(type = NumericType.class)
@To(type = EDataType.class)
public TransformFunction<NumericType, EDataType> createIntegerType() {
    return (s, ctx) -> {
        EDataType t = ctx.createTarget(EDataType.class);
        setId(t, "(psm/" + getId(s) + ")/IntegerType");
        t.setName(s.getName());
        t.setInstanceClassName(getIntegerClassName(s));
        return t;
    };
}
```

### Multi-Output Rule (One-to-Many)

**ETL:**
```etl
rule CreateEntityClass
    transform s : JUDOPSM!EntityType
    to t : ASM!EClass, ref : ASM!EClass {
        t.name = s.name;
        t.abstract = s.`abstract`;

        ref.name = s.name + "__Reference";
        ref.abstract = s.`abstract`;
    }
```

**Zeta:**
```java
@TransformRule(name = "CreateEntityClass")
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        // Primary target
        EClass t = ctx.createTarget(EClass.class);
        t.setName(s.getName());
        t.setAbstract(s.isAbstract());

        // Additional output - register with qualifier
        EClass ref = ctx.create(EClass.class);
        ref.setName(s.getName() + "__Reference");
        ref.setAbstract(s.isAbstract());
        ctx.registerEquivalent(s, ref, "Reference");

        return t;  // Return primary target
    };
}
```

### Lazy Rule

**ETL:**
```etl
@lazy
rule CreateAnnotation
    transform s : JUDOPSM!NamedElement
    to t : ASM!EAnnotation {
        t.source = "documentation";
        t.details.put("value", s.documentation);
    }
```

**Zeta:**
```java
// Lazy rules become on-demand methods called from other rules
private EAnnotation createAnnotation(NamedElement s, TransformationContext ctx) {
    EAnnotation t = ctx.create(EAnnotation.class);
    t.setSource("documentation");
    t.getDetails().put("value", s.getDocumentation());
    return t;
}
```

## Syntax Mapping Table

| ETL Construct | Zeta Equivalent | Notes |
|---------------|-----------------|-------|
| `@greedy` | `@Greedy` | Annotation on method |
| `@lazy` | Helper method | Called on-demand |
| `@primary` | `@Primary` | Marks primary rule |
| `@abstract` | Abstract class | Not commonly needed |
| `guard: expr` | `@Guard(method = "name")` | Separate method |
| `transform s : Type` | `@Transform(type = Type.class)` | Source type |
| `to t : Type` | `@To(type = Type.class)` | Target type |
| `s.name` | `s.getName()` | Property access |
| `s.\`abstract\`` | `s.isAbstract()` | Reserved word handling |
| `new Type` | `ctx.create(Type.class)` | Object creation |
| `s.equivalent()` | `ctx.equivalent(s, Type.class)` | Get equivalent |
| `s.equivalents()` | `ctx.equivalents(s, Type.class)` | Get all equivalents |
| `s.equivalent("Rule")` | `ctx.equivalent(s, Type.class, "qualifier")` | Named equivalent |
| `Type.all` | `psmUtils.all(resourceSet, Type.class)` | All instances |
| `post { }` | `postProcess(context)` method | Post-processing |

## Equivalent Resolution Patterns

### Single Equivalent

**ETL:**
```etl
var targetPkg = s.eContainer.equivalent();
```

**Zeta:**
```java
EPackage targetPkg = ctx.equivalent(s.eContainer(), EPackage.class);
```

### Named Equivalent (from multi-output rule)

**ETL:**
```etl
var refClass = entity.equivalent("CreateEntityClass_ref");
```

**Zeta:**
```java
EClass refClass = ctx.equivalent(entity, EClass.class, "Reference");
```

### All Equivalents

**ETL:**
```etl
var allTargets = s.equivalents();
```

**Zeta:**
```java
List<EClass> allClasses = ctx.equivalents(source, EClass.class);
```

## Post-Processing Patterns

**ETL:**
```etl
post {
    asmUtils.enrichWithAnnotations();
    for (a in ASM!EAnnotation.all) {
        // post-processing logic
    }
}
```

**Zeta:**
```java
private void postProcess(TransformationContext context) {
    // 1. Add root packages to resource
    psmUtils.all(Model.class).forEach(model -> {
        EPackage rootPkg = context.equivalent(model, EPackage.class);
        if (rootPkg != null) {
            asmModel.getResource().getContents().add(rootPkg);
        }
    });

    // 2. Set bidirectional references
    psmUtils.all(AssociationEnd.class).forEach(assocEnd -> {
        if (assocEnd.getPartner() != null) {
            EReference ref = context.equivalent(assocEnd, EReference.class);
            EReference partnerRef = context.equivalent(assocEnd.getPartner(), EReference.class);
            if (ref != null && partnerRef != null) {
                ref.setEOpposite(partnerRef);
            }
        }
    });

    // 3. Enrich with annotations
    AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
    asmUtils.enrichWithAnnotations();
}
```

## Utility Functions

**ETL (.eol file):**
```eol
operation JUDOPSM!NumericType isInteger() : Boolean {
    return self.scale <= 0;
}

operation getIntegerClassName(numericType : JUDOPSM!NumericType) : String {
    if (numericType.precision <= 9 and numericType.precision > 0) {
        return "java.lang.Integer";
    } else if (numericType.precision <= 19) {
        return "java.lang.Long";
    } else {
        return "java.math.BigDecimal";
    }
}
```

**Zeta (Helper class):**
```java
public class Psm2AsmHelper {
    public static boolean isInteger(NumericType numericType) {
        return numericType.getScale() <= 0;
    }

    public static String getIntegerClassName(NumericType s) {
        if (s.getPrecision() <= 9 && s.getPrecision() > 0) {
            return "java.lang.Integer";
        } else if (s.getPrecision() <= 19) {
            return "java.lang.Long";
        } else {
            return "java.math.BigDecimal";
        }
    }
}
```

## XMI ID Handling

**ETL:**
```etl
t.setId("(psm/" + s.getId() + ")/EntityClass");
```

**Zeta:**
```java
// Helper method
public static void setId(EObject obj, String id) {
    if (obj.eResource() != null) {
        obj.eResource().setID(obj, id);
    }
    idCache.put(obj, id);
}

// Usage
setId(t, "(psm/" + getId(s) + ")/EntityClass");
```

## Collection Operations

**ETL:**
```etl
// Filter
var entities = JUDOPSM!EntityType.all.select(e | not e.`abstract`);

// Transform
var names = entities.collect(e | e.name);

// Check existence
var hasAbstract = entities.exists(e | e.`abstract`);
```

**Zeta:**
```java
// Filter
List<EntityType> entities = psmUtils.all(EntityType.class)
    .filter(e -> !e.isAbstract())
    .toList();

// Transform
List<String> names = entities.stream()
    .map(EntityType::getName)
    .toList();

// Check existence
boolean hasAbstract = entities.stream()
    .anyMatch(EntityType::isAbstract);
```

## Feature Equivalence Matrix

| Feature | ETL | Zeta |
|---------|-----|------|
| Type safety | Runtime | Compile-time |
| IDE support | Limited | Full (Java IDE) |
| Debugging | ETL debugger | Standard Java debugger |
| Refactoring | Manual | IDE-assisted |
| Performance | Interpreted | Compiled |
| Learning curve | Lower | Higher (for non-Java devs) |
| Rule discovery | File-based | Annotation scanning |
| Parallel execution | Limited | Built-in support |

## Key Differences Summary

1. **Object Creation**: ETL creates implicitly with `to`; Zeta requires explicit `ctx.createTarget()`

2. **Guard Conditions**: ETL uses inline expressions; Zeta uses separate guard methods

3. **Multi-Output**: ETL lists multiple targets in signature; Zeta uses `registerEquivalent()`

4. **Container Resolution**: ETL's `asmEquivalent()` is a single call; Zeta requires explicit type

5. **Post-Processing**: ETL's `post {}` block becomes explicit Java method in Zeta

6. **Lazy Rules**: ETL's `@lazy` becomes on-demand helper methods in Zeta
