# Epsilon ETL vs Zeta Transformation Differences

This document provides a comprehensive comparison between the Epsilon ETL (Epsilon Transformation Language) and Zeta (Java-based transformation framework) implementations across all transformation modules in judo-tatami-base.

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [PSM to ASM Transformation](#psm-to-asm-transformation)
3. [ASM to RDBMS Transformation](#asm-to-rdbms-transformation)
4. [RDBMS to Liquibase Transformation](#rdbms-to-liquibase-transformation)
5. [PSM to Measure Transformation](#psm-to-measure-transformation)
6. [ASM to Keycloak Transformation](#asm-to-keycloak-transformation)
7. [Common Patterns and Differences](#common-patterns-and-differences)
8. [Recommendations](#recommendations)

---

## Executive Summary

| Transformation | ETL Files | Zeta Classes | Key Differences |
|----------------|-----------|--------------|-----------------|
| PSM to ASM | 8 .etl files | 9 Java files | Annotation creation inline vs separate rules |
| ASM to RDBMS | 5 .etl files | 3 Java files | Trace building explicit in Zeta |
| RDBMS to Liquibase | 11 .etl files | 11 Java files | Per-phase sub-transformations in Zeta |
| PSM to Measure | 3 .etl files | 4 Java files | Derived measure recursion explicit in Zeta |
| ASM to Keycloak | 3 .etl files | 4 Java files | Enhanced logging and error handling in Zeta |

### Key Takeaways

1. **Both produce equivalent output** - Verified by dual-engine tests with ModelComparator
2. **Zeta is more explicit** - Execution order, caching, and post-processing are explicitly managed
3. **ETL is more concise** - Fewer lines of code due to DSL syntax
4. **Zeta has better tooling** - Full IDE support, debugging, type safety

---

## PSM to ASM Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `psmToAsm.etl` (main) | `Psm2AsmZetaTransformation.java` |
| `modules/namespace.etl` | `rules/NamespaceRules.java` |
| `modules/type.etl` | `rules/TypeRules.java` |
| `modules/data.etl` | `rules/DataRules.java` |
| `modules/derived.etl` | `rules/DerivedRules.java` |
| `modules/static.etl` | `rules/StaticRules.java` |
| `modules/actor.etl` | `rules/ActorRules.java` |
| `modules/transferObject.etl` | `rules/TransferObjectRules.java` |
| `modules/operation.etl` | `rules/OperationRules.java` |

### Key Differences

#### 1. Annotation Creation Pattern

**ETL**: Creates annotations as separate rules that reference parent elements
```etl
rule CreateEntityAnnotationClass
    transform s : JUDOPSM!EntityType
    to t : ASM!EAnnotation {
        s.equivalent("CreateEntityClass").eAnnotations.add(t);
    }
```

**Zeta**: Creates annotations inline within main element transformation
```java
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        // Add entity annotation inline - avoids recursive update issues
        EAnnotation entityAnnotation = createAnnotation(...);
        t.getEAnnotations().add(entityAnnotation);
        return t;
    };
}
```

**Reason**: Zeta discovered that separate annotation rules caused recursive update issues with the TransformationContext. Inline creation avoids this problem.

**Suggestion**: Keep Zeta's inline approach - it's more robust and explicit about when annotations are created.

#### 2. ID Generation

**ETL**: Assigns IDs using `setId()` calls
```etl
t.setId("(psm/" + s.getId() + ")/Package");
```

**Zeta**: ID generation is a no-op
```java
public static void setId(EObject element, String id) {
    // No-op: ETL doesn't produce ID annotations, so neither should Zeta
}
```

**Reason**: Testing revealed ETL doesn't actually produce ID annotations in the output, so Zeta matches this behavior.

**Suggestion**: This is intentionally equivalent - no fix needed.

#### 3. Post-Processing

**ETL**: Minimal post-processing in `post { }` block

**Zeta**: Comprehensive 5-phase post-processing:
1. Add root packages to ASM model resource
2. Set EOpposite for bidirectional associations
3. Set target types for TransferObjectRelations
4. Set up inheritance for Reference classes
5. Enrich model with annotations

**Reason**: Zeta needs explicit post-processing because certain relationships require all elements to exist first.

**Suggestion**: Document these post-processing phases for maintainability.

#### 4. Caching

**ETL**: Framework-managed via `@cached` decorator

**Zeta**: Explicit `ConcurrentHashMap` caching in `Psm2AsmHelper`
```java
private static final Map<String, String> NAMESPACE_ELEMENT_STRING_CACHE = new ConcurrentHashMap<>();
```

**Reason**: Zeta needs thread-safe caching for potential parallel execution.

**Suggestion**: Profile and verify caching effectiveness.

---

## ASM to RDBMS Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `asmToRdbms.etl` (main) | `Asm2RdbmsZetaTransformation.java` |
| `modules/package.etl` | (inline in main class) |
| `modules/class.etl` | (inline in main class) |
| `modules/attribute.etl` | (inline in main class) |
| `modules/reference.etl` | (inline in main class) |
| `operations/*.eol` (utilities) | `rules/Asm2RdbmsRules.java` |

### Key Differences

#### 1. Rule Mapping Logic

**ETL**: Uses helper operations for rule decisions
```etl
guard: s.ruleMapping().foreignKey
```

**Zeta**: Centralizes rule decisions in `RuleMapping` class
```java
private static class RuleMapping {
    private final boolean foreignKey;
    private final boolean junctionTable;
    // ...
}

private RuleMapping getRuleMapping(EReference ref) {
    Rule rule = rules.getRuleFromReference(ref);
    return new RuleMapping(
        rule != null && rule.isForeignKey(),
        rule != null && rule.isJunctionTable()
    );
}
```

**Reason**: Zeta encapsulates rule decisions for better testability.

**Suggestion**: Consider extracting to a separate class for reusability.

#### 2. System Field Creation

**ETL**: 9 separate rules with abstract parent
```etl
@abstract
rule EAttributeToRdbmsField
    transform s : ASM!EAttribute
    to t : RDBMS!RdbmsField { ... }

rule EClassToTableIdField
    extends EAttributeToRdbmsField { ... }
```

**Zeta**: 9 separate methods called sequentially
```java
private void createTableIdField(RdbmsTable table, EClass eClass) { ... }
private void createTableTypeField(RdbmsTable table, EClass eClass) { ... }
// ... 7 more field creation methods
```

**Reason**: Java doesn't have rule inheritance; method calls achieve same result.

**Suggestion**: Consider extracting to a `SystemFieldFactory` for better organization.

#### 3. Junction Table Handling

**ETL**: Uses `@lazy` decorator for deferred creation
```etl
@lazy
rule EReferenceToRdbmsJunctionTable
    transform s : ASM!EReference
    to t : RDBMS!RdbmsJunctionTable { ... }
```

**Zeta**: Two-pass loop approach
```java
// Pass 1: Create all junction tables
for (EReference ref : references) {
    if (getRuleMapping(ref).junctionTable) {
        createJunctionTable(ref);
    }
}
// Pass 2: Create foreign keys
for (EReference ref : references) {
    createForeignKey(ref);
}
```

**Reason**: Zeta must explicitly manage the order that ETL handles implicitly.

**Suggestion**: Document the two-pass requirement in code comments.

---

## RDBMS to Liquibase Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `rdbmsToLiquibase.etl` | `Rdbms2LiquibaseZetaTransformation.java` |
| `rdbmsIncrementalToLiquibase.etl` | `Rdbms2LiquibaseIncrementalZetaTransformation.java` |
| `modules/table.etl` | (inline) |
| `modules/field.etl` | (inline) |
| `modules/incremental.etl` | `incremental/IncrementalZetaTransformation.java` |
| `modules/beforeIncremental.etl` | `incremental/BeforeIncrementalZetaTransformation.java` |
| `modules/afterIncremental.etl` | `incremental/AfterIncrementalZetaTransformation.java` |
| `modules/dbCheckup.etl` | `incremental/DbCheckupZetaTransformation.java` |
| `modules/dbBackup.etl` | `incremental/DbBackupZetaTransformation.java` |
| `modules/dbDropBackup.etl` | `incremental/DbDropBackupZetaTransformation.java` |
| `modules/dataUpdateBefore.etl` | `incremental/DataUpdateBeforeZetaTransformation.java` |
| `modules/dataUpdateAfter.etl` | `incremental/DataUpdateAfterZetaTransformation.java` |

### Key Differences

#### 1. Model Management

**ETL**: Creates 8 target models in `pre { }` block
```etl
pre {
    var targetModel : LIQUIBASE!databaseChangeLog = new LIQUIBASE!databaseChangeLog();
    var dbCheckupModel : DBCHECKUP!databaseChangeLog = ...
    var dbBackupModel : DBBACKUP!databaseChangeLog = ...
    // ... 5 more models
}
```

**Zeta**: Constructor receives 8 explicit model parameters
```java
@Builder
public Rdbms2LiquibaseIncrementalZetaTransformation(
    @NonNull RdbmsModel rdbmsModel,
    @NonNull LiquibaseModel dbCheckupLiquibaseModel,
    @NonNull LiquibaseModel dbBackupLiquibaseModel,
    @NonNull LiquibaseModel beforeIncrementalLiquibaseModel,
    // ... 5 more models
) { ... }
```

**Reason**: Zeta makes dependencies explicit for testability and clarity.

**Suggestion**: Consider using a ModelContainer/ModelBundle class to group related models.

#### 2. ChangeSet Caching

**ETL**: Uses `@cached` operation
```etl
@cached
operation LIQUIBASE!databaseChangeLog getOrCreateChangeSet(id : String, ...) { ... }
```

**Zeta**: Explicit HashMap caching
```java
private final Map<String, ChangeSet> changeSetCache = new HashMap<>();

private ChangeSet getOrCreateChangeSet(String id, String logicalFilePath) {
    String cacheKey = id + ":" + logicalFilePath;
    return changeSetCache.computeIfAbsent(cacheKey, k -> {
        // Create new ChangeSet
    });
}
```

**Reason**: Zeta needs explicit cache management per model.

**Suggestion**: This is working correctly - no fix needed.

#### 3. Unique Constraint Bug

**Both ETL and Zeta** have a potential issue with multi-column unique constraints:
```etl
// ETL comment:
for (field in s.fields) {
    // works in theory, but fails when executed on db if there are more then 1 field
}
```

**Suggestion**: Fix both implementations to handle multi-column unique constraints correctly by creating a single constraint with multiple columns.

---

## PSM to Measure Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `psmToMeasure.etl` (main) | `Psm2MeasureZetaTransformation.java` |
| `modules/measure.etl` | `rules/MeasureRules.java` |
| `modules/unit.etl` | `rules/UnitRules.java` |

### Key Differences

#### 1. Derived Measure Resolution

**ETL**: Relies on pre-computed `getBaseMeasures()`
```etl
for (m in s.getBaseMeasures().keySet()) {
    var term = new MEASURES!MeasuredTerm;
    term.exponent = s.getBaseMeasures().get(m);
    term.measure = m.equivalent();
    t.terms.add(term);
}
```

**Zeta**: Implements recursive resolution explicitly
```java
private Map<Measure, Integer> getBaseMeasures(DerivedMeasure derivedMeasure) {
    Map<Measure, Integer> result = new LinkedHashMap<>();
    for (Term term : derivedMeasure.getTerms()) {
        Measure termMeasure = term.getUnit().getMeasure();
        int exponent = term.getExponent();

        if (termMeasure instanceof DerivedMeasure) {
            // Recursively resolve nested derived measures
            Map<Measure, Integer> nestedBaseMeasures = getBaseMeasures((DerivedMeasure) termMeasure);
            for (Map.Entry<Measure, Integer> entry : nestedBaseMeasures.entrySet()) {
                int newExponent = entry.getValue() * exponent;
                result.merge(entry.getKey(), newExponent, (a, b) -> {
                    int sum = a + b;
                    return sum == 0 ? null : sum; // Filter zero exponents
                });
            }
        } else {
            result.merge(termMeasure, exponent, Integer::sum);
        }
    }
    return result;
}
```

**Reason**: Zeta explicitly handles nested derived measures and exponent combination.

**Suggestion**: Verify ETL's `getBaseMeasures()` implementation does the same recursion.

#### 2. ID Assignment

**ETL**: Explicit ID assignment
```etl
t.setId("(psm/" + s.getId() + ")/Measure");
```

**Zeta**: No visible ID assignment in rules

**Reason**: Unknown - may be handled by framework or intentionally omitted.

**Suggestion**: Verify if IDs are needed in output; add if missing.

---

## ASM to Keycloak Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `asmToKeycloak.etl` (main) | `Asm2KeycloakZetaTransformation.java` |
| `modules/realm.etl` | `rules/RealmRules.java` |
| `modules/client.etl` | `rules/ClientRules.java` |

### Key Differences

#### 1. Realm Tracking

**ETL**: Simple set of realm names
```etl
var realms = new Set();
for (actor in asmUtils.getAllActorTypes()) {
    var realm = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
    if (realm.present) {
        realms.add(realm.get);
    }
}
```

**Zeta**: Maps realm names to source actors for tracing
```java
Map<String, EClass> realmToFirstActor = new LinkedHashMap<>();
asmUtils.getAllActorTypes().forEach(actor -> {
    Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
    if (realmOpt.isPresent() && !realmOpt.get().trim().isEmpty()) {
        String realmName = realmOpt.get().trim();
        realmToFirstActor.putIfAbsent(realmName, actor);
    }
});
```

**Reason**: Zeta maintains source element references for transformation tracing.

**Suggestion**: This is correct - enables better debugging and traceability.

#### 2. Error Handling

**ETL**: Silent failures via guard conditions

**Zeta**: Explicit warnings when realms not found
```java
if (realm == null) {
    log.warn("Could not find realm '{}' for actor '{}'", realmName, s.getName());
    return null;
}
```

**Reason**: Better visibility into transformation failures.

**Suggestion**: Add similar logging to other transformations.

---

## Common Patterns and Differences

### Structural Patterns

| Pattern | ETL | Zeta |
|---------|-----|------|
| Rule inheritance | `extends RuleName` | `@Extends({"RuleName"})` |
| Guards | `guard: condition` | `@Guard(method = "methodName")` |
| Lazy execution | `@lazy` | Two-pass loops |
| Greedy matching | `@greedy` | `@Greedy` annotation |
| Pre-execution | `pre { }` block | `@PreExecution` method |
| Post-execution | `post { }` block | `postProcess()` method |
| Equivalent lookup | `s.equivalent()` | `ctx.equivalent(s, Type.class)` |
| Factory creation | `new TYPE!Element` | `factory.createElement()` |

### Code Size Comparison

| Transformation | ETL Lines | Zeta Lines | Ratio |
|----------------|-----------|------------|-------|
| PSM to ASM | ~1,500 | ~2,000 | 1.3x |
| ASM to RDBMS | ~400 | ~970 | 2.4x |
| RDBMS to Liquibase | ~800 | ~3,800 | 4.7x |
| PSM to Measure | ~150 | ~400 | 2.7x |
| ASM to Keycloak | ~150 | ~500 | 3.3x |

### Quality Comparison

| Aspect | ETL | Zeta |
|--------|-----|------|
| Type Safety | Weak (dynamic) | Strong (compile-time) |
| IDE Support | Limited | Full |
| Debugging | Difficult | Full debugger support |
| Testing | Integration only | Unit + Integration |
| Performance | Rule engine overhead | Direct Java |
| Parallelization | Framework-managed | Explicit control |
| Thread Safety | Unclear | ConcurrentHashMap caches |

---

## Recommendations

### High Priority

1. **Multi-Column Unique Constraints (RDBMS2Liquibase)**
   - Both ETL and Zeta have a bug with multi-column unique constraints
   - Fix: Create single constraint with multiple column names

2. **ID Assignment (PSM2Measure)**
   - Verify if IDs are needed in measure model output
   - Add ID generation to Zeta if required

### Medium Priority

3. **Consistent Logging**
   - Add warning logs to all Zeta transformations when guards fail
   - Helps debugging production issues

4. **Extract Helpers**
   - ASM2RDBMS: Extract `SystemFieldFactory` class
   - RDBMS2Liquibase: Create `ModelBundle` class for related models

5. **Document Post-Processing**
   - Add comments explaining why each post-processing phase exists
   - Critical for maintainability

### Low Priority

6. **Cache Profiling**
   - Profile PSM2ASM caches to verify effectiveness
   - Remove unused caches

7. **Parallel Execution**
   - Currently disabled in all transformations
   - Profile and enable where safe

---

## Conclusion

The Zeta implementations are faithful ports of ETL transformations with these key improvements:

1. **Type Safety**: Compile-time error detection
2. **Tooling**: Full IDE support for refactoring and debugging
3. **Testability**: Unit tests for individual rules
4. **Traceability**: Explicit transformation traces
5. **Error Handling**: Better logging and warnings

The main trade-off is increased code verbosity (~2-5x more lines) for these benefits. Both implementations produce equivalent output as verified by the dual-engine test framework.
