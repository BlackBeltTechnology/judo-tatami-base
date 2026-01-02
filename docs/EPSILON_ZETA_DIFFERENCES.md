# Epsilon ETL vs Zeta Transformation Differences

This document provides a comprehensive comparison between the Epsilon ETL (Epsilon Transformation Language) and Zeta (Java-based transformation framework) implementations across all transformation modules in judo-tatami-base.

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Zeta Framework Architecture](#zeta-framework-architecture)
3. [PSM to ASM Transformation](#psm-to-asm-transformation)
4. [ASM to RDBMS Transformation](#asm-to-rdbms-transformation)
5. [RDBMS to Liquibase Transformation](#rdbms-to-liquibase-transformation)
6. [PSM to Measure Transformation](#psm-to-measure-transformation)
7. [ASM to Keycloak Transformation](#asm-to-keycloak-transformation)
8. [Zeta Validation Framework](#zeta-validation-framework)
9. [Common Patterns and Differences](#common-patterns-and-differences)
10. [Zeta API Reference](#zeta-api-reference)
11. [Recommendations](#recommendations)

---

## Executive Summary

| Transformation | ETL Files | Zeta Classes | Key Differences |
|----------------|-----------|--------------|-----------------|
| PSM to ASM | 8 .etl files | 9 Java files | Annotation creation inline vs separate rules |
| ASM to RDBMS | 5 .etl files | 3 Java files | Trace building explicit in Zeta |
| RDBMS to Liquibase | 11 .etl files | 11 Java files | Per-phase sub-transformations in Zeta |
| PSM to Measure | 3 .etl files | 4 Java files | Derived measure recursion explicit in Zeta |
| ASM to Keycloak | 3 .etl files | 4 Java files | Enhanced logging and error handling in Zeta |
| PSM Validation | 1 .evl file | 1 Work class | Uses PsmValidator from judo-meta-psm |
| ASM Validation | 1 .evl file | 1 Work class | Uses AsmValidator from judo-meta-asm |
| Expression Validation | 2 .evl files | 2 Work classes | Uses ExpressionZetaValidator with model adapters |

### Key Takeaways

1. **Both produce equivalent output** - Verified by dual-engine tests with ModelComparator
2. **Zeta is more explicit** - Execution order, caching, and post-processing are explicitly managed
3. **ETL is more concise** - Fewer lines of code due to DSL syntax
4. **Zeta has better tooling** - Full IDE support, debugging, type safety
5. **Validation is unified** - Zeta validators in metamodel projects, Work classes in tatami

---

## Zeta Framework Architecture

### Core Classes

The Zeta framework provides these core transformation classes:

| Class | Purpose |
|-------|---------|
| `TransformationRegistry` | Scans and registers rule classes |
| `TransformationExecutor` | Executes transformations in order |
| `TransformationContext` | Holds transformation state, equivalents, attributes |
| `TransformationResult` | Contains transformation trace after execution |
| `TransformationTrace` | Source-to-target element mappings |
| `ElementResolutionCache` | Caches equivalent lookups for performance |
| `ModelProvider` | Provides model traversal functionality |
| `ExtensionMethodRegistry` | Registers extension methods |

### Transformation Flow

```
1. Create TransformationRegistry
2. Register rule classes: registry.register(RuleClass.class)
3. Create TransformationContext with ModelProvider
4. Configure context (aliases, structured IDs, attributes)
5. Create TransformationExecutor with registry + context
6. Execute: TransformationResult result = executor.transform()
7. Post-process: apply XMI IDs, set cross-references
8. Return: result.getTrace()
```

### Rule Class Structure

```java
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = SourceType.class, target = TargetType.class)
public class MyRules {

    // Guard method
    public boolean guardCondition(EObject source, TransformationContext ctx) {
        return /* condition */;
    }

    // Abstract rule
    @TransformRule(name = "AbstractRule", description = "Base rule")
    @Abstract
    @Transform(type = SourceType.class)
    @To(type = TargetType.class)
    public TransformFunction<SourceType, TargetType> abstractRule() {
        return (s, ctx) -> {
            TargetType t = ctx.createTarget(TargetType.class);
            t.setName(s.getName());
            return t;
        };
    }

    // Concrete rule extending abstract
    @TransformRule(name = "ConcreteRule", description = "Extends AbstractRule")
    @Extends({"AbstractRule"})
    @Guard(method = "guardCondition")
    @Transform(type = ConcreteSourceType.class)
    @To(type = TargetType.class)
    public TransformFunction<ConcreteSourceType, TargetType> concreteRule() {
        return (s, ctx) -> {
            TargetType t = ctx.executeParentRule("AbstractRule", s);
            // Additional processing
            return t;
        };
    }
}
```

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

#### 1. TransformationExecutor Usage

**Zeta**: Uses TransformationRegistry and TransformationExecutor
```java
// Create registry and register all rule classes
TransformationRegistry registry = new TransformationRegistry();
registry.register(NamespaceRules.class);
registry.register(TypeRules.class);
registry.register(DataRules.class);
// ... more rules

// Create context
TransformationContext context = new TransformationContext(
        modelProvider, sourceResourceSet, targetResourceSet, extensionRegistry);
context.setTransformationRegistry(registry);
context.setUseStructuredIds(true);
context.setPreferredSourceAlias("psm");

// Create executor with sequential execution
TransformationExecutor executor = TransformationExecutor.builder()
        .registry(registry)
        .context(context)
        .parallel(false)  // Sequential for complex dependencies
        .build();

// Execute transformation
TransformationResult result = executor.transform();
```

#### 2. TransformFunction Pattern

**ETL**: Rule blocks with implicit context
```etl
rule CreateEntityClass
    transform s : JUDOPSM!EntityType
    to t : ASM!EClass {
        t.name = s.name;
        t.abstract = s.abstract;
    }
```

**Zeta**: TransformFunction lambdas with explicit context
```java
@TransformRule(name = "CreateEntityClass")
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        EClass t = ctx.createTarget(EClass.class);
        t.setName(s.getName());
        t.setAbstract(s.isAbstract());
        return t;
    };
}
```

#### 3. Rule Inheritance with @Extends

**ETL**: `extends` keyword
```etl
rule CreateEntityClass extends NamespaceElementToEClassifier
```

**Zeta**: `@Extends` annotation with `ctx.executeParentRule()`
```java
@TransformRule(name = "CreateEntityClass")
@Extends({"NamespaceElementToEClassifier"})
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        // Execute parent rule first
        EClass t = ctx.executeParentRule("NamespaceElementToEClassifier", s);
        // Then apply EntityType-specific logic
        t.setAbstract(s.isAbstract());
        return t;
    };
}
```

#### 4. Guard Conditions

**ETL**: Inline guard expression
```etl
rule ModelToPackageVersion
    guard: s.version.isDefined()
```

**Zeta**: Guard method reference
```java
// Guard method
public boolean hasVersion(EObject source, TransformationContext ctx) {
    if (source instanceof Model) {
        String version = ((Model) source).getVersion();
        return version != null && !version.isEmpty();
    }
    return false;
}

@TransformRule(name = "ModelToPackageVersion")
@Guard(method = "hasVersion")
@Transform(type = Model.class)
@To(type = EAnnotation.class)
public TransformFunction<Model, EAnnotation> modelToPackageVersion() { ... }
```

#### 5. XMI ID Generation

**Zeta**: Structured IDs with configurable alias
```java
// In context setup
context.setUseStructuredIds(true);
context.setPreferredSourceAlias("psm");  // IDs like "(psm/Model_Entity)/EClass"

// Post-process to apply pending IDs
private void applyPendingXmiIds(EObject element, TransformationContext context, XMIResource xmiResource) {
    for (EObject child : element.eContents()) {
        String pendingId = context.getPendingXmiId(child);
        if (pendingId != null) {
            xmiResource.setID(child, pendingId);
        }
        applyPendingXmiIds(child, context, xmiResource);
    }
}
```

#### 6. Equivalent Lookups

**ETL**: `s.equivalent()` and `s.equivalents()`
```etl
var eClass = entityType.equivalent("CreateEntityClass");
```

**Zeta**: `ctx.equivalent()` and `ctx.equivalents()`
```java
EClass eClass = ctx.equivalent(entityType, EClass.class);
// Get all equivalents (for multiple rules producing same target type)
Collection<EClass> allClasses = ctx.equivalents(entityType, EClass.class);
```

#### 7. Post-Processing Phases

**Zeta**: Explicit 5-phase post-processing
```java
private void postProcess(TransformationContext context) {
    // 1. Add root packages to ASM model resource
    // 2. Apply pending XMI IDs recursively
    // 3. Set EOpposite for bidirectional associations
    // 4. Set target types for TransferObjectRelations
    // 5. Set up inheritance for Reference classes
    // 6. Enrich model with annotations (asmUtils.enrichWithAnnotations())
}
```

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
| `operations/*.eol` (utilities) | `Asm2RdbmsRuleNames.java` (constants) |

### Key Differences

#### 1. Manual Trace Building

**Zeta**: Explicit trace map with `ElementResolutionCache`
```java
// Internal trace map
private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

// Add trace entry
private void addTrace(EObject source, String ruleName, EObject target) {
    traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
            .put(ruleName, target);
}

// Get equivalent
private EObject getEquivalent(EObject source, String ruleName) {
    Map<String, EObject> rules = traceMap.get(source);
    return rules != null ? rules.get(ruleName) : null;
}

// Build native Zeta trace
private TransformationTrace buildZetaTrace() {
    ElementResolutionCache cache = new ElementResolutionCache();
    for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
        EObject source = entry.getKey();
        for (Map.Entry<String, EObject> targetEntry : entry.getValue().entrySet()) {
            String ruleName = targetEntry.getKey();
            EObject target = targetEntry.getValue();
            cache.addMapping(source, ruleName, target, true);
        }
    }
    return new TransformationTrace(cache);
}
```

#### 2. Rule Name Constants

**Zeta**: Centralized rule name constants in `Asm2RdbmsRuleNames.java`
```java
public static final String ROOT_PACKAGE_TO_MODEL = "rootPackageToModel";
public static final String ROOT_PACKAGE_TO_CONFIGURATION = "rootPackageToConfiguration";
public static final String ECLASS_TO_RDBMS_TABLE = "EClassToRdbmsTable";
public static final String ECLASS_TO_TABLE_ID_FIELD = "EClassToTableIdField";
// ... more constants
```

#### 3. XMI ID Setting

**Zeta**: Direct XMI ID setting via `setXmiId()` helper
```java
private void setXmiId(RdbmsElement element) {
    if (element.getUuid() != null && rdbmsModel.getResource() instanceof XMIResource) {
        ((XMIResource) rdbmsModel.getResource()).setID(element, element.getUuid());
    }
}
```

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

**Zeta**: Constructor receives 8 explicit model parameters
```java
@Builder
public Rdbms2LiquibaseIncrementalZetaTransformation(
    @NonNull RdbmsModel rdbmsModel,
    @NonNull LiquibaseModel dbCheckupLiquibaseModel,
    @NonNull LiquibaseModel dbBackupLiquibaseModel,
    @NonNull LiquibaseModel beforeIncrementalLiquibaseModel,
    @NonNull LiquibaseModel incrementalLiquibaseModel,
    @NonNull LiquibaseModel afterIncrementalLiquibaseModel,
    @NonNull LiquibaseModel dbDropBackupLiquibaseModel,
    @NonNull LiquibaseModel dataUpdateBeforeLiquibaseModel,
    @NonNull LiquibaseModel dataUpdateAfterLiquibaseModel
) { ... }
```

---

## PSM to Measure Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `psmToMeasure.etl` (main) | `Psm2MeasureZetaTransformation.java` |
| `modules/measure.etl` | `rules/MeasureRules.java` |
| `modules/unit.etl` | `rules/UnitRules.java` |

### Key Differences

#### 1. Custom XMI ID Handling

**Zeta**: Manual XMI ID tracking via context attribute
```java
// In rules - store custom XMI IDs
@SuppressWarnings("unchecked")
Map<EObject, String> customXmiIds = (Map<EObject, String>) ctx.getAttribute("customXmiIds");
if (customXmiIds == null) {
    customXmiIds = new HashMap<>();
    ctx.setAttribute("customXmiIds", customXmiIds);
}
customXmiIds.put(targetElement, "(psm/" + sourceId + ")/Measure");

// In post-process - apply XMI IDs
private void applyPendingXmiIds(EObject element, TransformationContext context,
                                 XMIResource xmiResource, Map<EObject, String> customXmiIds) {
    String xmiId = customXmiIds.get(element);
    if (xmiId != null) {
        xmiResource.setID(element, xmiId);
    }
    for (EObject child : element.eContents()) {
        applyPendingXmiIds(child, context, xmiResource, customXmiIds);
    }
}
```

---

## ASM to Keycloak Transformation

### File Structure

| ETL | Zeta |
|-----|------|
| `asmToKeycloak.etl` (main) | `Asm2KeycloakZetaTransformation.java` |
| `modules/realm.etl` | `rules/RealmRules.java` |
| `modules/client.etl` | `rules/ClientRules.java` |

---

## Zeta Validation Framework

### Overview

Zeta validation provides native Java validators as alternatives to Epsilon EVL. The validators are implemented in metamodel projects and exposed via Work classes in judo-tatami.

### Validation Work Classes

| EVL Work Class | Zeta Work Class | Validator |
|----------------|-----------------|-----------|
| `PsmValidationWork` | `PsmValidationZetaWork` | `PsmValidator` from judo-meta-psm |
| `AsmValidationWork` | `AsmValidationZetaWork` | `AsmValidator` from judo-meta-asm |
| `ExpressionValidationOnPsmWork` | `ExpressionValidationOnPsmZetaWork` | `ExpressionZetaValidator` with `PsmModelAdapter` |
| `ExpressionValidationOnAsmWork` | `ExpressionValidationOnAsmZetaWork` | `ExpressionZetaValidator` with `AsmModelAdapter` |

### Validation API

#### PSM Validation
```java
// Returns list of validation results
List<ValidationResult> results = PsmValidator.validate(log, psmModel);

// Check for errors
List<ValidationResult> errors = results.stream()
        .filter(r -> r.getSeverity() == Severity.ERROR)
        .collect(Collectors.toList());

if (!errors.isEmpty()) {
    throw new PsmJavaValidationException(message, results, errorDetails, ...);
}
```

#### ASM Validation
```java
// Throws AsmValidationException if validation fails
AsmValidator.validateAsm(log, asmModel);
```

#### Expression Validation
```java
// Create model adapter (PSM or ASM)
PsmModelAdapter modelAdapter = new PsmModelAdapter(
        psmModel.getResourceSet(),
        psmModel.getResourceSet()  // measureResourceSet same as psmResourceSet
);

// Or for ASM
AsmModelAdapter modelAdapter = new AsmModelAdapter(
        asmModel.getResourceSet(),
        measureModel.getResourceSet()
);

// Throws ExpressionValidationException if validation fails
ExpressionZetaValidator.validateExpression(log, expressionModel, modelAdapter);
```

### ValidationResult API
```java
public interface ValidationResult {
    Severity getSeverity();        // ERROR, WARNING, INFO
    String getConstraintName();    // Rule name (e.g., "ObjectTypeIsValid")
    String getMessage();           // Detailed error message
    EObject getElement();          // Element that failed validation
}
```

---

## Common Patterns and Differences

### Structural Patterns

| Pattern | ETL | Zeta |
|---------|-----|------|
| Rule declaration | `rule RuleName transform s : Type to t : Type { }` | `@TransformRule @Transform @To public TransformFunction<S,T> ruleName()` |
| Rule inheritance | `extends RuleName` | `@Extends({"RuleName"})` + `ctx.executeParentRule()` |
| Guards | `guard: condition` | `@Guard(method = "methodName")` |
| Abstract rules | `@abstract rule` | `@Abstract` annotation |
| Lazy execution | `@lazy` | Two-pass loops or post-processing |
| Greedy matching | `@greedy` | `@Greedy` annotation |
| Pre-execution | `pre { }` block | Constructor or context setup |
| Post-execution | `post { }` block | `postProcess()` method |
| Equivalent lookup | `s.equivalent()` | `ctx.equivalent(s, Type.class)` |
| All equivalents | `s.equivalents()` | `ctx.equivalents(s, Type.class)` |
| Factory creation | `new TYPE!Element` | `ctx.createTarget(Type.class)` |
| Resource access | `source!Type.all` | `modelProvider.getAllContents(resourceSet, Type.class)` |
| Context attributes | `ctx.variable` | `ctx.getAttribute("key")` / `ctx.setAttribute("key", value)` |

### Annotation Mapping

| ETL Annotation | Zeta Annotation |
|----------------|-----------------|
| `@abstract` | `@Abstract` |
| `@lazy` | (manual two-pass) |
| `@greedy` | `@Greedy` |
| `@cached` | `ConcurrentHashMap` caching |
| `@primary` | (rule registration order) |

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

## Zeta API Reference

### TransformationContext Methods

| Method | Description |
|--------|-------------|
| `createTarget(Class<T>)` | Creates new target element of given type |
| `equivalent(source, Class<T>)` | Gets equivalent target element |
| `equivalents(source, Class<T>)` | Gets all equivalent target elements |
| `executeParentRule(ruleName, source)` | Executes parent rule and returns result |
| `addToResource(element)` | Adds element to target resource |
| `getPendingXmiId(element)` | Gets pending XMI ID for element |
| `setAttribute(key, value)` | Sets context attribute |
| `getAttribute(key)` | Gets context attribute |
| `registerResource(alias, resourceSet)` | Registers resource with alias |
| `setUseStructuredIds(boolean)` | Enables/disables structured XMI IDs |
| `setPreferredSourceAlias(alias)` | Sets alias for ID generation |
| `setTransformationRegistry(registry)` | Sets the rule registry |
| `setTargetPackage(EPackage)` | Sets target metamodel package |

### TransformationRegistry Methods

| Method | Description |
|--------|-------------|
| `register(Class<?>)` | Registers a rule class |
| `getRules()` | Gets all registered rules |

### TransformationExecutor Methods

| Method | Description |
|--------|-------------|
| `transform()` | Executes transformation, returns TransformationResult |
| `builder()` | Creates executor builder |

### TransformationExecutor.Builder Methods

| Method | Description |
|--------|-------------|
| `registry(TransformationRegistry)` | Sets rule registry |
| `context(TransformationContext)` | Sets transformation context |
| `parallel(boolean)` | Enables/disables parallel execution |
| `build()` | Builds executor instance |

### TransformationResult Methods

| Method | Description |
|--------|-------------|
| `getTrace()` | Gets TransformationTrace with mappings |

### ElementResolutionCache Methods

| Method | Description |
|--------|-------------|
| `addMapping(source, ruleName, target, primary)` | Adds source-to-target mapping |

---

## Recommendations

### High Priority

1. **Multi-Column Unique Constraints (RDBMS2Liquibase)**
   - Both ETL and Zeta have a bug with multi-column unique constraints
   - Fix: Create single constraint with multiple column names

### Medium Priority

2. **Consistent Logging**
   - Add warning logs to all Zeta transformations when guards fail
   - Helps debugging production issues

3. **Extract Helpers**
   - ASM2RDBMS: Extract `SystemFieldFactory` class
   - RDBMS2Liquibase: Create `ModelBundle` class for related models

4. **Document Post-Processing**
   - Add comments explaining why each post-processing phase exists
   - Critical for maintainability

### Low Priority

5. **Cache Profiling**
   - Profile PSM2ASM caches to verify effectiveness
   - Remove unused caches

6. **Parallel Execution**
   - Currently disabled in all transformations due to EMF thread-safety issues
   - Profile and enable where safe (requires thread-safe EMF collections)

---

## Conclusion

The Zeta implementations are faithful ports of ETL transformations with these key improvements:

1. **Type Safety**: Compile-time error detection via generics
2. **Tooling**: Full IDE support for refactoring and debugging
3. **Testability**: Unit tests for individual rules
4. **Traceability**: Explicit transformation traces via ElementResolutionCache
5. **Error Handling**: Better logging and validation exceptions
6. **Validation**: Native Java validators with consistent API

The main trade-off is increased code verbosity (~2-5x more lines) for these benefits. Both implementations produce equivalent output as verified by the dual-engine test framework with ModelComparator.

### Migration Checklist

When adding new transformations with Zeta:

1. Create rule classes with `@TransformRule`, `@Transform`, `@To` annotations
2. Implement `TransformFunction` lambdas for each rule
3. Use `@Extends` and `ctx.executeParentRule()` for inheritance
4. Use `@Guard` with guard methods for conditional execution
5. Register rules with `TransformationRegistry` in execution order
6. Configure `TransformationContext` with structured IDs if needed
7. Add post-processing for cross-references and XMI ID application
8. Build `TransformationTrace` using `ElementResolutionCache`
9. Create corresponding Work class extending `AbstractTransformationWork`
10. Add dual-engine tests comparing ETL and Zeta output
