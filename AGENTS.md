# Judo Tatami Base - Project Documentation

## Project Overview

**Repository:** BlackBeltTechnology/judo-tatami-base  
**License:** Eclipse Public License 2.0 (EPL-2.0)  
**Java Version:** 21  
**Build System:** Maven 3.9.4+ with OSGi bundles

This project contains the model transformation pipeline for the JUDO platform. It transforms source models (PSM - Platform Specific Model) into target models (ASM, RDBMS, Liquibase, Keycloak, Measure) using Epsilon ETL (Epsilon Transformation Language).

## Directory Structure

```
judo-tatami-base/
├── judo-tatami-psm2asm/          # PSM to ASM transformation
├── judo-tatami-psm2measure/      # PSM to Measure transformation
├── judo-tatami-asm2rdbms/        # ASM to RDBMS transformation
├── judo-tatami-rdbms2liquibase/  # RDBMS to Liquibase transformation
├── judo-tatami-asm2expression/   # ASM to Expression transformation
├── judo-tatami-asm2keycloak/     # ASM to Keycloak transformation
├── judo-tatami-psm-validation/   # PSM model validation
├── judo-tatami-asm-validation/   # ASM model validation
├── judo-tatami-expression-asm-validation/  # Expression on ASM validation
├── judo-tatami-expression-psm-validation/  # Expression on PSM validation
├── osgi-itest/                   # OSGi integration tests
├── p2/                           # Eclipse P2 repository
└── openspec/                     # OpenSpec change management
```

## Transformation Pipeline

```
PSM (Platform Specific Model)
  ↓ (judo-tatami-psm2asm)
ASM (Abstract Semantic Model)
  ├→ (judo-tatami-asm2rdbms) → RDBMS Schema
  │   └→ (judo-tatami-rdbms2liquibase) → Liquibase Changelog
  ├→ (judo-tatami-asm2expression) → Expression Model
  └→ (judo-tatami-asm2keycloak) → Keycloak Config
PSM
  └→ (judo-tatami-psm2measure) → Measure Model
```

## Transformation Modules

| Module | Source | Target | ETL Files | Description |
|--------|--------|--------|-----------|-------------|
| judo-tatami-psm2asm | PSM | ASM | 9 | Main transformation from platform model to abstract semantic model |
| judo-tatami-psm2measure | PSM | Measure | 3 | Extract measurement units and definitions |
| judo-tatami-asm2rdbms | ASM | RDBMS | 8 | Generate relational database schema |
| judo-tatami-rdbms2liquibase | RDBMS | Liquibase | 12 | Generate database migration scripts |
| judo-tatami-asm2expression | ASM | Expression | 0 | Build expression model (Java-based) |
| judo-tatami-asm2keycloak | ASM | Keycloak | 3 | Generate Keycloak authentication config |

## ETL File Locations

Each transformation module follows this structure:
```
judo-tatami-<source>2<target>/
├── src/main/epsilon/
│   └── transformations/
│       ├── <source>To<Target>.etl   # Main transformation file
│       ├── modules/                  # Domain-specific transformations
│       │   ├── <domain1>.etl
│       │   └── <domain2>.etl
│       └── utils/                    # Utility functions
│           └── <utility>.eol
└── src/test/java/
    └── hu/blackbelt/judo/tatami/<source>2<target>/
        ├── <Source>2<Target>Test.java
        └── <Source>2<Target>WorkTest.java
```

## Technology Stack

### Core Technologies
- **Epsilon Runtime** 2.8.0 - ETL/EOL model transformation engine
- **Eclipse Modeling Framework (EMF)** - Metamodel foundation
- **Apache Felix** 5.1.8 - OSGi bundle plugin

### Testing
- **JUnit 5** - Test framework
- **Hamcrest** - Assertion library
- **Mockito** - Mocking framework

### Build & Quality
- **Maven 3.9.4+** with wrapper
- **JaCoCo** 0.8.12 - Code coverage
- **Lombok** 1.18.34 - Annotation processing

## Key Dependencies

```xml
<epsilon-runtime-version>2.8.0.20250820_074004_d018a661_develop</epsilon-runtime-version>
<judo-tatami-core-version>1.1.4.20250819_223206_e27883c8_develop</judo-tatami-core-version>
<judo-meta-psm-version>1.3.0.20250919_125549_76114348_develop</judo-meta-psm-version>
<judo-meta-asm-version>1.1.4.20250820_074844_0c801b69_develop</judo-meta-asm-version>
<judo-meta-rdbms-version>1.0.2.20250820_074908_e92e146c_develop</judo-meta-rdbms-version>
<judo-meta-liquibase-version>1.0.2.20250820_074950_9d7b02ff_develop</judo-meta-liquibase-version>
```

## Build Commands

```bash
# Standard build
mvn clean install
# or with wrapper
./mvnw clean install

# Run tests only
mvn clean test

# Skip tests
mvn clean install -DskipTests

# Build with specific profile
mvn clean install -Pmodules
```

## Test Files

### PSM2ASM Tests (11 files)
- `Psm2AsmTest.java` - Main transformation test
- `Psm2AsmWorkTest.java` - Work class test
- `Psm2AsmDataTest.java` - Data transformation test
- `Psm2AsmTypeTest.java` - Type transformation test
- `Psm2AsmDerivedTest.java` - Derived attribute test
- `Psm2AsmNamespaceTest.java` - Namespace test
- `Psm2AsmInheritanceTest.java` - Inheritance test
- `Psm2AsmAccessPointTest.java` - Access point test
- `Psm2AsmServiceTest.java` - Service test
- `OperationTest.java` - Operation test
- `AccessPointTest.java` - Actor/access point test

### Other Module Tests
- `Psm2MeasureTest.java`, `Psm2MeasureWorkTest.java`
- `Asm2RdbmsRelationMappingTest.java` and related tests
- `Rdbms2LiquibaseTest.java`, `Rdbms2LiquibaseContentTest.java`
- `Asm2KeycloakTest.java`, `Asm2KeycloakWorkTest.java`
- `Asm2ExpressionTest.java`, `Asm2ExpressionWorkTest.java`

## Code Patterns

### Transformation Execution

```java
// Execute PSM to ASM transformation
Psm2AsmTransformationTrace trace = executePsm2AsmTransformation(
    psm2AsmParameter()
        .psmModel(psmModel)
        .asmModel(asmModel)
);

// Access transformation trace
Map<EObject, List<EObject>> resolvedTrace = trace.getTransformationTrace();
```

### Work Class Pattern

```java
public class Psm2AsmWork {
    private final PsmModel psmModel;
    private final AsmModel asmModel;
    
    public void execute() {
        // Execute transformation
    }
}
```

## Related Projects

- **judo-zeta** (`/Users/robson/Project/judo-ng/runtime/judo-zeta`) - Zeta validation and transformation framework
- **judo-ng/models** (`/Users/robson/Project/judo-ng/models`) - All metamodel definitions (PSM, ASM, RDBMS, etc.)
- **judo-tatami-jsl** - JSL to PSM workflow using these transformations
- **judo-community** - Parent aggregator project

## OpenSpec Usage

This project uses OpenSpec for change management. See `openspec/AGENTS.md` for:
- Creating proposals
- Change workflow
- Spec format conventions

## ETL-Zeta Model Comparison

The project includes infrastructure for comparing ETL and Zeta transformation outputs. This is useful for validating that both transformation engines produce equivalent models.

### ModelComparator

Each transformation module includes a `ModelComparator` utility class that provides:
- **Order-independent comparison** - Collections matched by identifier, not position
- **Typed difference reporting** - `MissingElement`, `ExtraElement`, `ValueMismatch`, `TypeMismatch`
- **Comparison modes**:
  - `STRICT` - All attributes and references must match exactly
  - `STRUCTURAL` - Element structure must match, annotation differences tolerated (default)
  - `LENIENT` - Major structural elements must match, minor differences allowed

### Configuration via System Properties

```bash
# Enable/disable comparison (default: true)
-Djudo.test.comparison.enabled=true

# Comparison mode (default: STRUCTURAL)
-Djudo.test.comparison.mode=STRICT|STRUCTURAL|LENIENT

# Maximum differences to report (default: 50)
-Djudo.test.comparison.maxDifferences=100

# Output file for diff report (optional)
-Djudo.test.comparison.reportFile=target/comparison-report.txt
```

### Usage in Tests

```java
// Assert models are equivalent (uses configured mode)
ModelComparator.assertEquivalent(expectedModel, actualModel);

// Compare with specific mode
ModelComparator.assertEquivalent(expectedModel, actualModel, ComparisonMode.STRICT);

// Get detailed comparison result
ComparisonResult result = ModelComparator.compare(model1, model2);
if (!result.isEquivalent()) {
    System.out.println(result.getSummary());
    System.out.println(result.getDetailedReport());
}

// Filter differences by type
List<MissingElement> missing = result.getDifferencesOfType(MissingElement.class);
```

### Running Comparison Tests

```bash
# Run with default STRUCTURAL mode
mvn test -Dtest=Psm2AsmDualTransformationTest

# Run with STRICT mode
mvn test -Dtest=Psm2AsmDualTransformationTest -Djudo.test.comparison.mode=STRICT

# Disable comparison (skip comparison tests)
mvn test -Djudo.test.comparison.enabled=false
```

## Zeta Transformation Implementation

The project includes a high-performance Zeta-based transformation engine alongside the original ETL engine. Zeta transformations are implemented in Java and provide significant performance improvements (30-45x faster than ETL).

### Architecture

Each transformation module that supports Zeta has this structure:
```
judo-tatami-<source>2<target>/
├── src/main/java/hu/blackbelt/judo/tatami/<source>2<target>/
│   ├── <Source>2<Target>Work.java           # Main work class (supports both engines)
│   └── zeta/
│       ├── <Source>2<Target>ZetaTransformation.java  # Main Zeta transformation
│       ├── <Source>2<Target>RuleNames.java           # Rule name constants
│       ├── <Source>2<Target>Helper.java              # Helper utilities
│       └── rules/                                     # Rule implementations
│           ├── NamespaceRules.java
│           ├── DataRules.java
│           ├── TransferObjectRules.java
│           ├── OperationRules.java
│           ├── DerivedRules.java
│           └── StaticRules.java
```

### Rule Implementation Pattern

Zeta rules use annotations to define transformations:

```java
@TransformRule(name = "CreateMappedTransferObject", description = "Transform MappedTransferObjectType to EClass")
@Transform(type = MappedTransferObjectType.class)
@To(type = EClass.class)
@Greedy
public TransformFunction<MappedTransferObjectType, EClass> createMappedTransferObject() {
    return (s, ctx) -> {
        // Guard condition - return null to skip
        if (!guardCondition(s)) {
            return null;
        }
        
        // Create target element
        EClass t = ctx.createTarget(EClass.class);
        setId(t, "(psm/" + getId(s) + ")/MappedTransferObject");
        t.setName(s.getName());
        
        // Add to container
        EPackage pkg = ctx.equivalent(s.eContainer(), EPackage.class);
        pkg.getEClassifiers().add(t);
        
        // Add annotations inline (consolidate related rules)
        EAnnotation annotation = createAnnotation(
                "(psm/" + getId(s) + ")/TypeAnnotation",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(annotation, "value", "true");
        t.getEAnnotations().add(annotation);
        
        return t;
    };
}
```

### Key Patterns

1. **Guards**: Return `null` from the transform function to skip elements that don't match the guard condition
2. **Inline consolidation**: Combine multiple related ETL rules into a single Zeta rule for efficiency
3. **ID convention**: Use `(psm/{sourceId})/{RuleName}` pattern for traceability
4. **Helper methods**: Use static imports from `*Helper.java` for common operations

### Common Issues and Solutions

#### Missing Classifiers (e.g., "46 classifiers missing")

**Symptom**: Zeta output has fewer classifiers than ETL output.

**Diagnosis**: 
1. Run comparison test to identify which classifiers are missing
2. Check package names - often missing in generated packages like `_generated_navigations`
3. Compare ETL rules in `static.etl`, `transferObject.etl`, etc.

**Solution**: Ensure all source types from ETL are covered in Zeta:
- `StaticData` → covered by `StaticRules.java`
- `StaticNavigation` → must also be covered (often overlooked!)
- Check for `@Greedy` rules that transform base types

**Example**: The `_generated_navigations` package contains `StaticNavigation` elements that need the following rules:
- `CreateUnmappedTransferObjectForStaticNavigation`
- `CreateStaticNavigationQueryAnnotation`
- `CreateStaticQueryNavigation`
- `CreateNavigationReferenceBindingForStaticNavigation`
- `CreateTransferObjectRelationParameterizedAnnotationForStaticNavigation`

#### Missing Annotations

**Symptom**: Model comparison shows missing annotations like `unmappedDefaultOnly`, `eExceptions`.

**Common causes**:
1. Guard condition too restrictive
2. Missing rule for specific annotation type
3. Expression/parameter type lookup returning null

**Diagnosis**: Check the ETL rule guards and ensure Zeta implementation matches exactly.

#### Missing eExceptions on Operations

**Symptom**: Bound operations have empty `eExceptions` list in Zeta output.

**Cause**: The faults-to-exceptions loop from ETL's `CreateOperation` abstract rule was not ported.

**Solution**: Add fault handling in operation creation rules:
```java
// Add faults as exceptions (from CreateOperation abstract rule in ETL)
for (var fault : s.getFaults()) {
    if (fault.getType() != null) {
        EClass faultType = ctx.equivalent(fault.getType(), EClass.class);
        if (faultType != null) {
            t.getEExceptions().add(faultType);
        }
    }
}
```

#### Annotations with Complex Guard Conditions

**Symptom**: Annotations like `unmappedDefaultOnly` missing for attributes/references.

**Cause**: ETL guard conditions navigate through entity's `defaultRepresentation` to check for bindings with `defaultValue`.

**Solution**: For annotations that depend on related transfer objects:
```java
@TransformRule(name = "AddUnmappedDefaultOnlyAttributeAnnotation")
@Transform(type = Attribute.class)
@To(type = EAnnotation.class)
@Greedy
public TransformFunction<Attribute, EAnnotation> addUnmappedDefaultOnlyAttributeAnnotation() {
    return (s, ctx) -> {
        // Guard: container must be EntityType with defaultRepresentation
        if (!(s.eContainer() instanceof EntityType entity) ||
            entity.getDefaultRepresentation() == null) {
            return null;
        }

        // Guard: must have binding with defaultValue in defaultRepresentation
        boolean hasBindingWithDefault = entity.getDefaultRepresentation().getAttributes().stream()
                .anyMatch(a -> a.getBinding() == s && a.getDefaultValue() != null);
        if (!hasBindingWithDefault) {
            return null;
        }

        // Create annotation...
    };
}
```

**Key insight**: When porting ETL guards that use `exists()` or similar collection operations, translate to Java streams with `anyMatch()`, `filter()`, or `findFirst()`.

### Performance Testing

Performance tests use the RackInspect real-world model:

```bash
# Run performance tests
mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance

# Run all performance tests
mvn test -Pperformance -Dgroups=performance
```

Expected results:
- **Psm2Asm**: ~30-45x faster than ETL
- **Rdbms2Liquibase**: ~40-50x faster than ETL

### Adding New Zeta Rules

1. Add rule name constant to `*RuleNames.java`
2. Implement rule in appropriate `*Rules.java` class
3. Register the rules class in transformation initialization
4. Run comparison tests to verify equivalence

## Important Notes

1. **ETL files are the source of truth** for transformation logic
2. **Tests use the Northwind demo model** from judo-meta-psm
3. **OSGi compatibility** is maintained through Felix bundle plugin
4. **Transformation traces** allow mapping between source and target elements
5. **Validation modules** use Java WorkClass pattern (not EVL)
6. **Model comparison** uses `ModelComparator` with configurable modes and detailed reporting
7. **Zeta rules must cover all source types** - check both main types and subtypes (e.g., StaticData AND StaticNavigation)
