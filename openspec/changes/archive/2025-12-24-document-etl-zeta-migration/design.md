# ETL to Zeta Migration Documentation - Design

## Overview

This document outlines the detailed design for the ETL to Zeta migration documentation, including the comprehensive comparison of both transformation engines, migration patterns, and testing strategies.

## Documentation Architecture

```
docs/
├── transformations/
│   ├── README.md                    # Updated with migration links
│   ├── psm2asm.md                   # Updated with Zeta mapping
│   ├── asm2rdbms.md                 # Updated with Zeta mapping
│   ├── rdbms2liquibase.md           # Updated with Zeta mapping
│   ├── psm2measure.md               # Updated with Zeta mapping
│   └── asm2keycloak.md              # Updated with Zeta mapping
└── migration/
    ├── README.md                    # Migration documentation index
    ├── etl-zeta-comparison.md       # Detailed syntax/pattern comparison
    ├── etl-to-zeta-migration.md     # Step-by-step migration guide
    └── dual-engine-testing.md       # Testing framework documentation
```

## ETL vs Zeta: Comprehensive Comparison

### 1. Rule Definition Patterns

#### Simple Transformation Rule

**ETL Pattern:**
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

**Zeta Pattern:**
```java
@TransformRule(name = "CreateStringType", description = "Transform StringType to EDataType")
@Greedy
@Transform(type = StringType.class)
@To(type = EDataType.class)
public TransformFunction<StringType, EDataType> createStringType() {
    return (s, ctx) -> {
        EDataType t = ctx.createTarget(EDataType.class);
        setId(t, "(psm/" + getId(s) + ")/StringType");
        t.setName(s.getName());
        t.setInstanceClassName("java.lang.String");

        EPackage containerPkg = getContainerPackage(s, ctx);
        if (containerPkg != null) {
            containerPkg.getEClassifiers().add(t);
        }
        return t;
    };
}
```

**Key Differences:**
| Aspect | ETL | Zeta |
|--------|-----|------|
| Rule annotation | `@greedy` | `@Greedy` annotation |
| Source type | `transform s : Type` | `@Transform(type = Type.class)` |
| Target type | `to t : Type` | `@To(type = Type.class)` |
| Object creation | Implicit with `to` | `ctx.createTarget(Type.class)` |
| Property access | `s.name` | `s.getName()` |
| Container resolution | `s.eContainer.asmEquivalent()` | `ctx.equivalent(s.eContainer(), EPackage.class)` |

#### Guarded Rule

**ETL Pattern:**
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

**Zeta Pattern:**
```java
public boolean isIntegerGuard(EObject source, TransformationContext ctx) {
    if (source instanceof NumericType) {
        return Psm2AsmHelper.isInteger((NumericType) source);
    }
    return false;
}

@TransformRule(name = "CreateIntegerType", description = "Transform NumericType (integer) to EDataType")
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

**Reason for Difference:** Guards in ETL are inline boolean expressions, while Zeta uses method references for type safety and reusability.

#### Multi-Output Rule (One-to-Many)

**ETL Pattern:**
```etl
rule CreateEntityClass
    transform s : JUDOPSM!EntityType
    to
        t : ASM!EClass,      // Main entity class
        ref : ASM!EClass {   // Reference class

        t.name = s.name;
        t.abstract = s.`abstract`;

        ref.name = s.name + "__Reference";
        ref.abstract = s.`abstract`;
    }
```

**Zeta Pattern:**
```java
@TransformRule(name = "CreateEntityClass")
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (s, ctx) -> {
        // Create main entity class
        EClass t = ctx.createTarget(EClass.class);
        t.setName(s.getName());
        t.setAbstract(s.isAbstract());

        // Create reference class as additional output
        EClass ref = ctx.create(EClass.class);
        ref.setName(s.getName() + "__Reference");
        ref.setAbstract(s.isAbstract());

        // Register reference class as additional equivalent
        ctx.registerEquivalent(s, ref, "Reference");

        return t;  // Return primary target
    };
}
```

**Key Difference:** ETL allows multiple named outputs in the rule signature. Zeta uses `ctx.registerEquivalent()` with a qualifier to store additional outputs.

### 2. Equivalent Resolution Patterns

**ETL Pattern:**
```etl
// Get single equivalent
var targetPkg = s.eContainer.equivalent();

// Get specific rule's output
var entityClass = s.equivalent("CreateEntityClass");

// Get all equivalents
var allTargets = s.equivalents();
```

**Zeta Pattern:**
```java
// Get single equivalent
EPackage targetPkg = ctx.equivalent(s.eContainer(), EPackage.class);

// Get named equivalent (from multi-output rule)
EClass refClass = ctx.equivalent(entityType, EClass.class, "Reference");

// Get all equivalents of a type
List<EClass> allClasses = ctx.equivalents(source, EClass.class);
```

### 3. Post-Processing Patterns

**ETL Pattern:**
```etl
post {
    asmUtils.enrichWithAnnotations();
    for (a in ASM!EAnnotation.all) {
        // Post-processing logic
    }
}
```

**Zeta Pattern:**
```java
private void postProcess(TransformationContext context) {
    // 1. Add root packages to resource
    psmUtils.all(psmModel.getResourceSet(), Model.class).forEach(model -> {
        EPackage rootPkg = context.equivalent(model, EPackage.class);
        if (rootPkg != null && !asmModel.getResource().getContents().contains(rootPkg)) {
            asmModel.getResource().getContents().add(rootPkg);
        }
    });

    // 2. Set bidirectional references (must be done after all elements created)
    psmUtils.all(psmModel.getResourceSet(), AssociationEnd.class).forEach(assocEnd -> {
        if (assocEnd.getPartner() != null) {
            EReference ref = context.equivalent(assocEnd, EReference.class);
            EReference partnerRef = context.equivalent(assocEnd.getPartner(), EReference.class);
            if (ref != null && partnerRef != null && ref.getEOpposite() == null) {
                ref.setEOpposite(partnerRef);
            }
        }
    });

    // 3. Enrich with annotations
    AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
    asmUtils.enrichWithAnnotations();
}
```

**Reason:** Post-processing in Zeta is explicit Java code. Operations that depend on all transformations being complete (like setting EOpposite references) must be handled in post-processing to avoid recursive update issues.

### 4. Utility Functions

**ETL Pattern (in .eol file):**
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

**Zeta Pattern (Helper class):**
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

### 5. XMI ID Handling

**ETL Pattern:**
```etl
t.setId("(psm/" + s.getId() + ")/EntityClass");
```

**Zeta Pattern:**
```java
// In helper class
public static void setId(EObject obj, String id) {
    if (obj.eResource() != null) {
        obj.eResource().setID(obj, id);
    }
    // Store ID in cache for later reference
    idCache.put(obj, id);
}

public static String getId(EObject obj) {
    if (obj instanceof NamedElement) {
        return ((NamedElement) obj).getId();
    }
    return null;
}
```

## Testing Framework Design

### 1. TransformationMode Enum Pattern

The `TransformationMode` enum is provided by `judo-tatami-core` for dual-engine testing:

```java
import hu.blackbelt.judo.tatami.core.TransformationMode;

public enum TransformationMode {
    /** Use Epsilon ETL transformation engine. */
    ETL,
    /** Use Zeta Java transformation engine. */
    ZETA;

    public boolean isZeta() {
        return this == ZETA;
    }
}
```

### 2. Parameterized Test Pattern

```java
@ParameterizedTest
@EnumSource(TransformationMode.class)
void testTransformation(TransformationMode transformationMode) throws Exception {
    // Setup source model
    PsmModel psmModel = createTestModel();

    // Execute transformation based on mode
    AsmModel result;
    if (transformationMode.isZeta()) {
        result = runZetaTransformation(psmModel);
    } else {
        result = runEtlTransformation(psmModel);
    }

    // Verify results
    assertNotNull(result);
    verifyExpectedOutput(result);
}
```

### 3. ModelComparator Usage

```java
// Compare two model resources
ModelComparator.assertEquivalent(
    etlResult.getResource(),
    zetaResult.getResource(),
    ModelComparator.ComparisonMode.STRUCTURAL
);

// Detailed comparison with result analysis
ComparisonResult result = ModelComparator.compare(etlRoot, zetaRoot);
if (!result.isEquivalent()) {
    log.warn("Differences found: {}", result.getDifferenceCount());
    for (Difference diff : result.getDifferenceList()) {
        log.warn("  - {}", diff.describe());
    }
}
```

### 4. Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `judo.test.comparison.enabled` | `true` | Enable/disable comparison |
| `judo.test.comparison.mode` | `STRUCTURAL` | Comparison strictness |
| `judo.test.comparison.maxDifferences` | `50` | Max differences to report |
| `judo.test.comparison.reportFile` | `null` | Output file for diff report |

## Migration Checklist

### Per-Module Migration Steps

1. **Create Zeta directory structure**
   ```
   src/main/java/hu/blackbelt/judo/tatami/<module>/zeta/
   ├── <Module>ZetaTransformation.java
   ├── <Module>Helper.java (if needed)
   ├── <Module>RuleNames.java
   └── rules/
       ├── NamespaceRules.java
       ├── TypeRules.java
       └── ...
   ```

2. **Create rule name constants** (`*RuleNames.java`)
   - Extract all rule names from ETL files
   - Define as static final String constants

3. **Migrate rules by category**
   - Start with independent rules (no dependencies)
   - Progress to dependent rules
   - Handle multi-output rules with `registerEquivalent`
   - Add guards as separate methods

4. **Implement post-processing**
   - Move post{} block logic to `postProcess()` method
   - Handle bidirectional references
   - Handle containment additions

5. **Create/update tests**
   - Import `TransformationMode` from `judo-tatami-core`
   - Update tests to use `@EnumSource(TransformationMode.class)`
   - Add model comparison assertions

6. **Add performance tests**
   - Create `perf/RealisticPerformanceTest.java`
   - Use shared `RealisticModelGenerator`

## Common Pitfalls and Solutions

### Pitfall 1: Recursive Update Issues

**Problem:** Setting bidirectional references (EOpposite) during rule execution causes recursive updates.

**Solution:** Move to post-processing:
```java
private void postProcess(TransformationContext context) {
    psmUtils.all(AssociationEnd.class).forEach(assocEnd -> {
        if (assocEnd.getPartner() != null) {
            EReference ref = context.equivalent(assocEnd, EReference.class);
            EReference partnerRef = context.equivalent(assocEnd.getPartner(), EReference.class);
            if (ref != null && partnerRef != null) {
                ref.setEOpposite(partnerRef);
            }
        }
    });
}
```

### Pitfall 2: Order-Dependent Transformations

**Problem:** ETL automatically resolves dependencies; Zeta requires explicit ordering.

**Solution:** Use transformation phases via registry order:
```java
// Register rule classes in transformation order
registry.register(NamespaceRules.class);  // Phase 1: Packages first
registry.register(TypeRules.class);        // Phase 2: Types
registry.register(DataRules.class);        // Phase 3: Entities
// ...
```

### Pitfall 3: Container Addition Timing

**Problem:** Elements not added to containers properly.

**Solution:** Add elements to containers within rules or post-processing:
```java
EPackage containerPkg = getContainerPackage(s, ctx);
if (containerPkg != null) {
    containerPkg.getEClassifiers().add(t);
}
```

### Pitfall 4: Missing Equivalents

**Problem:** `ctx.equivalent()` returns null when element hasn't been transformed yet.

**Solution:** Check for null and handle gracefully, or ensure rule ordering:
```java
EClass targetClass = ctx.equivalent(source.getTarget(), EClass.class);
if (targetClass != null) {
    ref.setEType(targetClass);
} else {
    // Handle in post-processing
    log.trace("Target class not yet available, deferring");
}
```
