# ETL to ZETA Conversion Guide

[← Back to ZETA Index](../ZETA.md)

---

## Rule Declaration

**ETL:**
```etl
rule CreateTransferObject
    transform s: PSM!TransferObjectType
    to t: ASM!EClass
    extends CreateNamedElement {
      t.setId("(psm/" + s.getId() + ")/TransferObject");
      log.debug("Created: " + t.name);
}
```

**ZETA:**
```java
@TransformRule(name = CREATE_TRANSFER_OBJECT)
@Primary
@Extends(CREATE_NAMED_ELEMENT)
@Transform(type = TransferObjectType.class)
@Guard(method = "isTransferObject")
public TransformFunction<TransferObjectType, EClass> createTransferObject() {
    return (source, ctx) -> {
        EClass target = createForNamedElement(source, ctx, EClass.class);
        setElementId(target, source, "TransferObject");
        LOG.debug("Created: {}", target.getName());
        return target;
    };
}
```

---

## Annotation Mappings

| ETL Annotation | ZETA Annotation | Description |
|----------------|-----------------|-------------|
| `@abstract` | `@Abstract` | Abstract rule (not directly executed) |
| `@lazy` | `@Lazy @Greedy` | Lazy rule (executed on-demand) |
| `@primary` | `@Primary` | Primary transformation rule |
| `@greedy` | `@Greedy` | Greedy rule (transforms all matching) |
| `extends RuleName` | `@Extends("RuleName")` | Inherit from parent rule |
| `guard: condition` | `@Guard(method = "guardMethod")` | Guard condition |

**Important:** ETL `@lazy` requires BOTH `@Lazy` AND `@Greedy` in ZETA!

---

## Guard Conditions

**ETL (inline guard):**
```etl
rule CreateOperation
    transform s: PSM!TransferOperation
    to t: ASM!EOperation {
        guard: s.output.isDefined()
        // ...
}
```

**ZETA (method reference):**
```java
@TransformRule(name = "CreateOperation")
@Guard(method = "hasOutput")
public TransformFunction<TransferOperation, EOperation> createOperation() {
    return (source, ctx) -> { /* ... */ };
}

public boolean hasOutput(EObject eObject, TransformationContext ctx) {
    if (!(eObject instanceof TransferOperation)) {
        return false;
    }
    return ((TransferOperation) eObject).getOutput() != null;
}
```

**ZETA (inline guard - alternative):**
```java
public TransformFunction<TransferOperation, EOperation> createOperation() {
    return (source, ctx) -> {
        if (source.getOutput() == null) {
            return null;  // Skip - equivalent to guard
        }
        // ... transformation logic
    };
}
```

---

## Equivalent/Resolution Lookup

**ETL:**
```etl
// Named rule equivalent
s.equivalent("CreateModel")

// Default equivalent
s.getASMEquivalent()

// Container navigation
s.eContainer.getASMEquivalent().getEClassifiers().add(t);
```

**ZETA:**
```java
// Named rule lookup
ctx.executeParentRule("CreateModel", source);

// Type-specific equivalent
ctx.equivalent(source, EClass.class);

// Container navigation
EPackage pkg = ctx.equivalent(source.eContainer(), EPackage.class);
pkg.getEClassifiers().add(target);
```

---

## Collection Operations

### collect

**ETL:**
```etl
t.eSuperTypes = s.superTypes.collect(st | st.getASMEquivalent());
```

**ZETA:**
```java
for (TransferObjectType superType : source.getSuperTypes()) {
    EClass superClass = ctx.equivalent(superType, EClass.class);
    if (superClass != null) {
        target.getESuperTypes().add(superClass);
    }
}
```

### exists

**ETL:**
```etl
s.attributes.exists(a | a.binding == attribute and a.defaultValue.isDefined())
```

**ZETA:**
```java
source.getAttributes().stream()
    .anyMatch(a -> a.getBinding() == attribute && a.getDefaultValue() != null);
```

### select

**ETL:**
```etl
s.attributes.select(a | a.required)
```

**ZETA:**
```java
source.getAttributes().stream()
    .filter(Attribute::isRequired)
    .collect(Collectors.toList());
```

### reject

**ETL:**
```etl
s.attributes.reject(a | a.derived)
```

**ZETA:**
```java
source.getAttributes().stream()
    .filter(a -> !a.isDerived())
    .collect(Collectors.toList());
```

---

## Type Checks

**ETL:**
```etl
s.isKindOf(PSM!EntityType)    // instanceof (includes subtypes)
s.isTypeOf(PSM!EntityType)    // exact type match
```

**ZETA:**
```java
source instanceof EntityType                    // isKindOf equivalent
source.getClass().equals(EntityType.class)      // isTypeOf equivalent
```

---

## Null Checks

**ETL:**
```etl
s.isDefined()      // not null
s.isUndefined()    // null
```

**ZETA:**
```java
source != null     // isDefined
source == null     // isUndefined
```

---

## Creating New Elements

**ETL:**
```etl
var annotation = new ASM!EAnnotation();
annotation.setId("(psm/" + s.getId() + ")/Annotation");
annotation.source = "http://example.com";
t.eAnnotations.add(annotation);
```

**ZETA:**
```java
EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
ctx.setElementId(annotation, "(psm/" + getId(source) + ")/Annotation");
annotation.setSource("http://example.com");
target.getEAnnotations().add(annotation);
```

---

## Quick Reference Table

| ETL | ZETA |
|-----|------|
| `@lazy` | `@Lazy @Greedy` |
| `@abstract` | `@Abstract` |
| `@primary` | `@Primary` |
| `@greedy` | `@Greedy` |
| `extends Rule` | `@Extends("Rule")` |
| `guard: cond` | `if (!cond) return null;` |
| `s.equivalent()` | `ctx.equivalent(s, Type.class)` |
| `s.equivalent("Rule")` | `ctx.equivalent(s, "Rule")` |
| `s.equivalentDiscriminated("R", d)` | `ctx.equivalentDiscriminated(s, T.class, "R", d)` |
| `s.isDefined()` | `s != null` |
| `s.isUndefined()` | `s == null` |
| `s.isKindOf(Type)` | `s instanceof Type` |
| `s.isTypeOf(Type)` | `s.getClass().equals(Type.class)` |
| `col.collect(x \| x.f())` | `col.stream().map(x -> x.f()).collect(...)` |
| `col.exists(x \| x.f())` | `col.stream().anyMatch(x -> x.f())` |
| `col.select(x \| x.f())` | `col.stream().filter(x -> x.f()).collect(...)` |
| `col.reject(x \| x.f())` | `col.stream().filter(x -> !x.f()).collect(...)` |
| `col.first()` | `col.stream().findFirst().orElse(null)` |
| `col.size()` | `col.size()` |
| `col.isEmpty()` | `col.isEmpty()` |
