# XMI ID Naming Mismatch Between ETL and Zeta

## Problem Summary

The ETL and Zeta PSM2ASM transformations produce structurally equivalent ASM
models, but the XMI IDs embedded in elements differ. This causes downstream
models (RDBMS, Liquibase) to show differences because they copy source element
XMI IDs into their own UUID fields.

## The Mismatch

**ETL** sets XMI IDs manually in each rule with a custom suffix:

```etl
rule CreateEntityClass
    transform s : JUDOPSM!EntityType
    to t : ASM!EClass {
        t.setId("(psm/" + s.getId() + ")/EntityClass");
        ...
    }
```

**Zeta** uses structured ID auto-generation based on the rule name:

```java
@TransformRule(name = "CreateEntityClass", ...)
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        // No manual setId() — Zeta auto-generates from rule name
        ...
    };
}
```

### Resulting IDs

| Element | ETL ID | Zeta ID |
|---------|--------|---------|
| EntityType → EClass | `(psm/{id})/EntityClass` | `(psm/{id})/CreateEntityClass` |
| Attribute → EAttribute | `(psm/{id})/Attribute` | `(psm/{id})/CreateAttribute` |
| AssociationEnd → EReference | `(psm/{id})/AssociationEndRelation` | `(psm/{id})/CreateAssociationEndRelation` |
| ContainmentRelation → EReference | `(psm/{id})/ContainmentRelation` | `(psm/{id})/CreateContainmentRelation` |

The pattern: Zeta prepends `Create` because the rule names follow `Create*`
convention, while ETL strips that prefix in the manual `setId()` call.

## How Structured IDs Work in Zeta

When `context.setUseStructuredIds(true)` is configured (in
`Psm2AsmZetaTransformation.java`), the Zeta framework auto-generates XMI IDs
using this format:

```
({preferredSourceAlias}/{sourceXmiId})/{ruleName}
```

The components:
- `preferredSourceAlias` = `"psm"` (set via `context.setPreferredSourceAlias("psm")`)
- `sourceXmiId` = the XMI ID of the source PSM element
- `ruleName` = the `name` parameter from `@TransformRule(name = "...")`

Since rule names are `CreateEntityClass`, `CreateAttribute`, etc., the generated
IDs contain `Create` while ETL IDs do not.

## Downstream Impact

### RDBMS Model

`Asm2RdbmsZetaTransformation` uses ASM element XMI IDs to generate RDBMS element
UUIDs. Each RDBMS table field's `uuid` field contains the ASM source element's
XMI ID:

```
ETL:  (asm/(psm/{id})/EntityClass)/TableIdField
Zeta: (asm/(psm/{id})/CreateEntityClass)/TableIdField
```

This causes 50+ differences in the RDBMS model comparison (STRICT mode).

### Liquibase Model

`Rdbms2LiquibaseZetaTransformation` copies RDBMS UUIDs into Liquibase column
`remarks` fields. The same `EntityClass` vs `CreateEntityClass` pattern
propagates:

```
ETL:  (asm/(psm/{id})/EntityClass)/TableIdField
Zeta: (asm/(psm/{id})/CreateEntityClass)/TableIdField
```

This causes 50+ differences in the Liquibase model comparison.

### Expression, Script, Keycloak

These transformations do **not** use ASM XMI IDs in their output, so they are
not affected by this naming difference.

## Fix Options

### Option A — Override IDs in Zeta rules (explicit `setElementId()`)

Add explicit `setElementId()` calls in each Zeta rule to match ETL suffixes:

```java
// In DataRules.java:
@TransformRule(name = "CreateEntityClass", ...)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        setElementId(t, s, "EntityClass");  // Override auto-generated ID
        ...
    };
}
```

Where `setElementId()` is a helper that constructs
`(psm/{sourceId})/{suffix}` and calls `ctx.setPendingXmiId(target, id)`.

**Pros**: Exact match with ETL IDs, surgical fix per rule.
**Cons**: Must be added to every rule that produces a model element. Error-prone
if a rule is missed.

### Option B — Rename Zeta rules to match ETL ID suffixes

Change rule name constants from `Create*` to match the ETL suffix:

```java
// Before:
public static final String CREATE_ENTITY_CLASS = "CreateEntityClass";

// After:
public static final String CREATE_ENTITY_CLASS = "EntityClass";
```

**Pros**: One-line change per rule name constant. Automatic ID match.
**Cons**: Breaks the naming convention (`Create*` prefix). May affect
`ctx.equivalent()` lookups that reference rule names. Requires updating all
`@TransformRule(name = ...)` annotations.

### Option C — Configure Zeta to strip `Create` prefix from IDs

Add a transformation context option that strips a prefix from rule names when
generating structured IDs:

```java
context.setStructuredIdRuleNamePrefix("Create");  // Strips "Create" from IDs
```

This would generate `(psm/{id})/EntityClass` from rule `CreateEntityClass`.

**Pros**: Clean, centralized fix. No per-rule changes.
**Cons**: Requires changes to judo-zeta framework. Only applicable if ALL rules
follow the `Create*` convention (some don't, e.g., `AddUnmappedDefaultOnly*`).

### Option D — Use custom ID suffix map

Provide a map of `{ruleName → idSuffix}` to the transformation context:

```java
Map<String, String> idSuffixes = Map.of(
    "CreateEntityClass", "EntityClass",
    "CreateAttribute", "Attribute",
    "CreateAssociationEndRelation", "AssociationEndRelation",
    ...
);
context.setRuleNameToIdSuffix(idSuffixes);
```

**Pros**: Flexible, doesn't change rule names.
**Cons**: Requires judo-zeta framework changes. Large map to maintain.

## Recommendation

**Option A** is the safest and most pragmatic approach. It requires no framework
changes, works within the existing Zeta API, and makes the ID mapping explicit
in each rule. The `Psm2AsmHelper` class already has utility methods for ID
generation that can be extended.

The ETL rules that need matching ID suffixes:

| Rule Name | ETL ID Suffix | File |
|-----------|---------------|------|
| `CreateEntityClass` | `EntityClass` | DataRules.java |
| `CreateAttribute` | `Attribute` | DataRules.java |
| `CreateAssociationEndRelation` | `AssociationEndRelation` | DataRules.java |
| `CreateContainmentRelation` | `ContainmentRelation` | DataRules.java |
| `CreateEntityAnnotationClass` | `EntityAnnotationClass` | DataRules.java |
| `CreateEntityDefaultRepresentationAnnotation` | `EntityDefaultRepresentationAnnotation` | DataRules.java |
| `CreateNamespaceSequence` | `NamespaceSequence` | DataRules.java |
| `CreateEntitySequence` | `EntitySequence` | DataRules.java |
| `ModelToPackage` | `Model` | NamespaceRules.java |
| `PackageToPackage` | `Package` | NamespaceRules.java |
| (and all other rules with `Create` prefix) | (strip `Create` prefix) | Various |

## Scope of the Problem

In **SKELETON** comparison mode (which skips EAnnotation comparison), the ID
difference does not surface in the ASM comparison because XMI IDs are metadata
rather than structural content. However, it propagates to RDBMS and Liquibase
where the IDs become part of the structural model (UUID fields, column remarks).

In **STRICT** comparison mode, the ID differences surface in:
- RDBMS: 50+ differences (all UUID fields)
- Liquibase: 50+ differences (all column remarks fields)
- ASM: 0 differences (XMI IDs are compared as resource-level metadata, not as
  EAnnotation content — STRICT mode compares annotations as sets by source URI)

## Related Files

- ID generation: `judo-zeta/transformation-core/src/main/java/.../TransformationContext.java`
  (`generateStructuredId()`, `buildStructuredId()`, `getSourcePath()`)
- PSM2ASM setup: `judo-tatami-psm2asm/src/main/java/.../zeta/Psm2AsmZetaTransformation.java`
  (lines 216-227: `setUseStructuredIds`, `setPreferredSourceAlias`)
- Rule names: `judo-tatami-psm2asm/src/main/java/.../zeta/Psm2AsmRuleNames.java`
- ETL ID patterns: `judo-tatami-psm2asm/src/main/epsilon/transformations/asm/modules/data.etl`
  (line 30: `t.setId("(psm/" + s.getId() + ")/EntityClass")`)
- RDBMS UUID usage: `judo-tatami-asm2rdbms/src/main/java/.../zeta/Asm2RdbmsZetaTransformation.java`
