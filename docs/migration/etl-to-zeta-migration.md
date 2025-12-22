# ETL to Zeta Migration Guide

This guide provides a step-by-step process for migrating transformation logic from Epsilon ETL to Zeta Java-based transformations.

## Prerequisites

- Familiarity with the existing ETL transformation
- Java development environment
- Understanding of the source and target metamodels

## Migration Process Overview

```
1. Analyze ETL Structure
       ↓
2. Create Zeta Directory Structure
       ↓
3. Define Rule Name Constants
       ↓
4. Migrate Rules by Category
       ↓
5. Implement Post-Processing
       ↓
6. Set Up Dual-Engine Testing
       ↓
7. Verify Output Equivalence
```

## Step 1: Analyze ETL Structure

### 1.1 Inventory ETL Files

List all ETL files in your transformation:

```
src/main/epsilon/transformations/
├── mainTransformation.etl      # Entry point
├── modules/
│   ├── namespace.etl
│   ├── type.etl
│   ├── data.etl
│   └── ...
└── utils/
    └── helpers.eol
```

### 1.2 Categorize Rules

For each ETL file, categorize rules:

| Category | ETL Marker | Count | Complexity |
|----------|------------|-------|------------|
| Greedy rules | `@greedy` | | Simple |
| Guarded rules | `guard:` | | Medium |
| Lazy rules | `@lazy` | | On-demand |
| Multi-output rules | Multiple `to` targets | | Complex |
| Primary rules | `@primary` | | Standard |

### 1.3 Identify Dependencies

Map rule dependencies:
- Which rules must execute before others?
- Which rules reference equivalents from other rules?

## Step 2: Create Zeta Directory Structure

```bash
mkdir -p src/main/java/hu/blackbelt/judo/tatami/<module>/zeta/rules
```

Create the following files:

```
src/main/java/hu/blackbelt/judo/tatami/<module>/zeta/
├── <Module>ZetaTransformation.java    # Main transformation class
├── <Module>Helper.java                 # Utility methods
├── <Module>RuleNames.java              # Rule name constants
└── rules/
    ├── NamespaceRules.java
    ├── TypeRules.java
    ├── DataRules.java
    └── ...
```

## Step 3: Define Rule Name Constants

Extract all rule names from ETL files into a constants class:

```java
public final class Psm2AsmRuleNames {
    private Psm2AsmRuleNames() {}

    // Namespace rules
    public static final String MODEL_TO_PACKAGE = "ModelToPackage";
    public static final String PACKAGE_TO_PACKAGE = "PackageToPackage";

    // Type rules
    public static final String CREATE_ENUMERATION = "CreateEnumeration";
    public static final String CREATE_STRING_TYPE = "CreateStringType";
    public static final String CREATE_INTEGER_TYPE = "CreateIntegerType";

    // Data rules
    public static final String CREATE_ENTITY_CLASS = "CreateEntityClass";
    public static final String CREATE_ATTRIBUTE = "CreateAttribute";
    // ...
}
```

## Step 4: Migrate Rules by Category

### 4.1 Start with Independent Rules

Begin with rules that have no dependencies on other rules:

**ETL:**
```etl
@greedy
rule CreateEnumeration
    transform s : JUDOPSM!EnumerationType
    to t : ASM!EEnum {
        t.setId("(psm/" + s.getId() + ")/Enumeration");
        t.name = s.name;
        // ...
    }
```

**Zeta:**
```java
@TransformRule(name = CREATE_ENUMERATION)
@Greedy
@Transform(type = EnumerationType.class)
@To(type = EEnum.class)
public TransformFunction<EnumerationType, EEnum> createEnumeration() {
    return (s, ctx) -> {
        EEnum t = ctx.createTarget(EEnum.class);
        setId(t, "(psm/" + getId(s) + ")/Enumeration");
        t.setName(s.getName());
        // ...
        return t;
    };
}
```

### 4.2 Migrate Guarded Rules

Create guard methods for conditional rules:

```java
// Guard method (must match signature)
public boolean isIntegerGuard(EObject source, TransformationContext ctx) {
    return source instanceof NumericType
        && Psm2AsmHelper.isInteger((NumericType) source);
}

@TransformRule(name = CREATE_INTEGER_TYPE)
@Greedy
@Guard(method = "isIntegerGuard")
@Transform(type = NumericType.class)
@To(type = EDataType.class)
public TransformFunction<NumericType, EDataType> createIntegerType() {
    // ...
}
```

### 4.3 Migrate Multi-Output Rules

Use `registerEquivalent` for additional outputs:

```java
@TransformRule(name = CREATE_ENTITY_CLASS)
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        // Primary output
        EClass entityClass = ctx.createTarget(EClass.class);
        entityClass.setName(s.getName());

        // Secondary output - register with qualifier
        EClass refClass = ctx.create(EClass.class);
        refClass.setName(s.getName() + "__Reference");
        ctx.registerEquivalent(s, refClass, "Reference");

        return entityClass;
    };
}
```

### 4.4 Convert Lazy Rules to Helper Methods

```java
// In Helper class
public static EAnnotation createDocumentationAnnotation(
        NamedElement source, TransformationContext ctx) {
    if (source.getDocumentation() == null) {
        return null;
    }
    EAnnotation annotation = ctx.create(EAnnotation.class);
    annotation.setSource(getAnnotationUri("documentation"));
    annotation.getDetails().put("value", source.getDocumentation());
    return annotation;
}
```

## Step 5: Implement Post-Processing

### 5.1 Identify Post-Processing Needs

Post-processing is required for:
- Adding root elements to resources
- Setting bidirectional references (EOpposite)
- Cross-referencing that requires all elements to exist
- Model enrichment (annotations, validation)

### 5.2 Create Post-Process Method

```java
private void postProcess(TransformationContext context) {
    // 1. Add root packages to resource
    psmUtils.all(Model.class).forEach(model -> {
        EPackage rootPkg = context.equivalent(model, EPackage.class);
        if (rootPkg != null && !targetResource.getContents().contains(rootPkg)) {
            targetResource.getContents().add(rootPkg);
        }
    });

    // 2. Set bidirectional references
    psmUtils.all(AssociationEnd.class).forEach(assocEnd -> {
        if (assocEnd.getPartner() != null) {
            EReference ref = context.equivalent(assocEnd, EReference.class);
            EReference partnerRef = context.equivalent(
                assocEnd.getPartner(), EReference.class);
            if (ref != null && partnerRef != null && ref.getEOpposite() == null) {
                ref.setEOpposite(partnerRef);
            }
        }
    });

    // 3. Model enrichment
    AsmUtils asmUtils = new AsmUtils(targetResourceSet);
    asmUtils.enrichWithAnnotations();
}
```

## Step 6: Set Up Main Transformation Class

```java
@Slf4j
public class Psm2AsmZetaTransformation {
    private final PsmModel psmModel;
    private final AsmModel asmModel;

    @Builder
    public Psm2AsmZetaTransformation(PsmModel psmModel, AsmModel asmModel, ...) {
        this.psmModel = psmModel;
        this.asmModel = asmModel;
    }

    public TransformationTrace execute() {
        // 1. Create registry and register rules
        TransformationRegistry registry = createRegistry();

        // 2. Create context
        TransformationContext context = createContext(registry);

        // 3. Execute transformation
        TransformationExecutor executor = TransformationExecutor.builder()
            .registry(registry)
            .context(context)
            .parallel(false)  // Sequential for complex dependencies
            .build();

        TransformationResult result = executor.transform();

        // 4. Post-processing
        postProcess(context);

        // 5. Return native Zeta trace
        return result.getTrace();
    }

    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();
        // Register in dependency order
        registry.register(NamespaceRules.class);
        registry.register(TypeRules.class);
        registry.register(DataRules.class);
        registry.register(TransferObjectRules.class);
        registry.register(DerivedRules.class);
        registry.register(OperationRules.class);
        registry.register(ActorRules.class);
        return registry;
    }
}
```

## Step 7: Handle Transformation Trace

### 7.1 Dual Trace Strategy

The tatami framework uses a dual trace strategy to support both ETL and Zeta engines:

| Engine | Trace Type | Storage | Access Method |
|--------|------------|---------|---------------|
| ETL | `Map<EObject, List<EObject>>` | `trace` field | `getTransformationTrace()` |
| Zeta | `TransformationTrace` | `zetaTrace` field | `getZetaTrace()` |

### 7.2 TransformationTrace Class Updates

Update your `*TransformationTrace` class to support both trace types:

```java
@Builder(builderMethodName = "psm2AsmTransformationTraceBuilder")
public class Psm2AsmTransformationTrace implements TransformationTrace {

    @NonNull @Getter PsmModel psmModel;
    @NonNull @Getter AsmModel asmModel;

    // ETL trace (null when Zeta used)
    Map<EObject, List<EObject>> trace;

    // Zeta trace (null when ETL used)
    hu.blackbelt.judo.zeta.transformation.core.TransformationTrace zetaTrace;

    @Override
    public Map<EObject, List<EObject>> getTransformationTrace() {
        // Return ETL trace or empty map for Zeta (backward compatibility)
        return trace != null ? trace : Collections.emptyMap();
    }

    /**
     * Get the native Zeta trace.
     * @return the Zeta TransformationTrace, or null if ETL was used
     */
    public hu.blackbelt.judo.zeta.transformation.core.TransformationTrace getZetaTrace() {
        return zetaTrace;
    }

    /**
     * Check if this trace was produced by Zeta transformation.
     * @return true if Zeta trace is available, false if ETL trace
     */
    public boolean isZetaTrace() {
        return zetaTrace != null;
    }
}
```

### 7.3 Work Class Updates

Update your Work class to use the appropriate trace field based on transformation mode:

```java
private Psm2AsmTransformationTrace executeZetaTransformation(...) {
    Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
            .psmModel(psmModel)
            .asmModel(asmModel)
            .build();

    // Execute Zeta transformation - returns native Zeta TransformationTrace
    TransformationTrace zetaTrace = transformation.execute();

    return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
            .psmModel(psmModel)
            .asmModel(asmModel)
            .zetaTrace(zetaTrace)  // Use Zeta trace field
            .build();
}

private Psm2AsmTransformationTrace executeEtlTransformation(...) {
    // ... ETL execution ...
    return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
            .psmModel(psmModel)
            .asmModel(asmModel)
            .trace(etlTrace)  // Use ETL trace field
            .build();
}
```

### 7.4 Zeta Trace API

The Zeta `TransformationTrace` provides rich trace data:

```java
// Get all trace entries
Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

// Each entry contains:
for (TraceEntry entry : entries) {
    EObject source = entry.getSource();      // Source element
    EObject target = entry.getTarget();      // Target element
    String ruleName = entry.getRuleName();   // Rule that created this mapping
    boolean isPrimary = entry.isPrimary();   // Primary or secondary output
    String discriminator = entry.getDiscriminator(); // For discriminated outputs
}

// Export to JSON
String json = trace.toJson();
trace.saveToJson(new File("trace.json"));
```

### 7.5 JSON Trace Format

The Zeta trace can be exported to JSON:

```json
{
  "traceEntries": [
    {
      "ruleName": "CreateEntityClass",
      "source": { "type": "EntityType", "id": "(psm/entity1)", "name": "Customer" },
      "target": { "type": "EClass", "id": "(psm/entity1)/EntityClass", "name": "Customer" },
      "primary": true,
      "discriminator": null
    }
  ],
  "entryCount": 150,
  "timestamp": 1702567890123
}
```

### 7.6 Hand-Written Transformations

For hand-written Zeta transformations that don't use `TransformationExecutor`, build the trace manually:

```java
public TransformationTrace execute() {
    // ... transformation logic with internal trace tracking ...

    // Build native Zeta trace from internal trace map
    return buildZetaTrace();
}

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

## Common Pitfalls and Solutions

### Pitfall 1: Recursive Update Issues

**Problem:** Setting bidirectional references during rule execution causes recursive updates.

**Solution:** Move to post-processing:
```java
// DON'T do this in a rule:
ref.setEOpposite(partnerRef);  // Can cause recursive updates

// DO this in postProcess():
private void postProcess(TransformationContext context) {
    psmUtils.all(AssociationEnd.class).forEach(assocEnd -> {
        if (assocEnd.getPartner() != null) {
            EReference ref = context.equivalent(assocEnd, EReference.class);
            EReference partnerRef = context.equivalent(
                assocEnd.getPartner(), EReference.class);
            if (ref != null && partnerRef != null) {
                ref.setEOpposite(partnerRef);
            }
        }
    });
}
```

### Pitfall 2: Order-Dependent Transformations

**Problem:** ETL automatically resolves dependencies; Zeta requires explicit ordering.

**Solution:** Register rule classes in dependency order:
```java
// Packages must exist before types
registry.register(NamespaceRules.class);  // Phase 1
registry.register(TypeRules.class);        // Phase 2
registry.register(DataRules.class);        // Phase 3
```

### Pitfall 3: Missing Container Addition

**Problem:** Elements created but not added to containers.

**Solution:** Add to container within rules:
```java
EPackage containerPkg = ctx.equivalent(s.eContainer(), EPackage.class);
if (containerPkg != null) {
    containerPkg.getEClassifiers().add(t);
}
```

### Pitfall 4: Null Equivalents

**Problem:** `ctx.equivalent()` returns null when element hasn't been transformed.

**Solution:** Check for null or defer to post-processing:
```java
EClass targetClass = ctx.equivalent(source.getTarget(), EClass.class);
if (targetClass != null) {
    ref.setEType(targetClass);
} else {
    // Will be set in post-processing
    log.trace("Target not yet available, deferring");
}
```

### Pitfall 5: Incorrect Property Access

**Problem:** ETL uses `s.name`; Java requires `s.getName()`.

**Solution:** Use proper Java accessors:
```java
// ETL: t.name = s.name
// Zeta:
t.setName(s.getName());
```

## Migration Checklist

- [ ] Inventoried all ETL files and rules
- [ ] Created Zeta directory structure
- [ ] Defined rule name constants
- [ ] Migrated namespace rules
- [ ] Migrated type rules
- [ ] Migrated data rules
- [ ] Migrated transfer object rules
- [ ] Migrated operation rules
- [ ] Migrated actor rules
- [ ] Converted lazy rules to helpers
- [ ] Implemented post-processing
- [ ] Set up TransformationType enum
- [ ] Created parameterized tests
- [ ] Verified ETL-Zeta output equivalence
- [ ] Added performance tests
- [ ] Updated `execute()` to return `TransformationTrace`
- [ ] Added `zetaTrace` field to `*TransformationTrace` class
- [ ] Added `getZetaTrace()` and `isZetaTrace()` methods
- [ ] Updated Work class to use dual code paths
- [ ] Added trace content tests

## Next Steps

After completing migration:

1. **Run tests** - Verify both engines produce equivalent output
2. **Performance benchmark** - Compare ETL vs Zeta execution times
3. **Update documentation** - Document any module-specific patterns
4. **Code review** - Have the migration reviewed
