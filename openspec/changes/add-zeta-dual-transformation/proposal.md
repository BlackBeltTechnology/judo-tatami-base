# Add Zeta Java-Based Dual Transformation System

## Summary

Implement Java-based transformations using the Zeta framework alongside existing Epsilon ETL transformations. Both transformation engines will run in parallel (dual transformation mode) with test-time comparison to ensure functional equivalence. This migration provides better IDE support, debugging capabilities, and performance improvements while maintaining backward compatibility.

## Motivation

### Current State
- All model transformations use Epsilon ETL (35 ETL files across 6 modules)
- ETL scripts have limited IDE support and debugging capabilities
- Performance optimization is constrained by Epsilon runtime
- No type-safe transformation rules

### Benefits of Zeta Framework
1. **Type Safety**: Java-based rules with compile-time checking
2. **IDE Support**: Full IntelliJ/Eclipse support with refactoring, navigation, debugging
3. **Performance**: Native Java execution, parallel processing support (5000+ element threshold)
4. **Testability**: Standard JUnit testing patterns
5. **Maintainability**: Constants for constraint names, better code organization

## Scope

### In Scope
1. Add judo-zeta dependency (version 1.0.0.20251207_081454_0779b890_develop)
2. Implement Java transformation classes for all 6 transformation modules
3. Create dual-execution test infrastructure (parameterized tests)
4. Add performance benchmarks (10,000 element models)
5. Convert AsciiDoc documentation to Markdown
6. Document all transformation rules in `docs/transformations/`
7. Use constants for constraint/rule names in Zeta implementations

### Out of Scope
- Removing existing ETL transformations (kept for backward compatibility)
- Modifying metamodel definitions
- Changing build/release process

## Approach

### Phase 1: Infrastructure Setup
- Add Zeta dependency to parent POM with property `judo-zeta-version`
- Create base transformation infrastructure classes
- Set up dual-execution test framework

### Phase 2: Transformation Implementation
- Implement Java transformations module by module:
  1. `judo-tatami-psm2asm` (9 ETL files → Java classes)
  2. `judo-tatami-psm2measure` (3 ETL files)
  3. `judo-tatami-asm2rdbms` (8 ETL files)
  4. `judo-tatami-rdbms2liquibase` (12 ETL files)
  5. `judo-tatami-asm2keycloak` (3 ETL files)
- Each module gets corresponding Zeta transformation class

### Phase 3: Testing & Validation
- Convert existing tests to parameterized tests
- Run both ETL and Zeta transformations
- Compare output models for structural equivalence
- Add performance benchmarks

### Phase 4: Documentation
- Convert README.adoc, CONTRIBUTING.adoc, CIFLOW.adoc to Markdown
- Convert PlantUML diagrams to Mermaid
- Document all transformation rules in `docs/transformations/`
- Update AGENTS.md with actual project content

## Technical Design

### Transformation Mode Configuration

All Work classes and their parameter builders will support a `transformationMode` setting to control which transformation engine is used. **By default, Zeta transformations are used**, but this can be overridden to use ETL for backward compatibility or debugging purposes.

#### TransformationMode Enum

```java
package hu.blackbelt.judo.tatami.core;

/**
 * Defines the transformation engine to use for model transformations.
 */
public enum TransformationMode {
    /**
     * Use Java-based Zeta transformations (default).
     * Provides better performance, type safety, and debugging support.
     */
    ZETA,
    
    /**
     * Use Epsilon ETL transformations (legacy).
     * Kept for backward compatibility and validation.
     */
    ETL
}
```

#### Work Parameter Pattern

Each Work parameter class will include a `transformationMode` field with `ZETA` as default:

```java
@Builder(builderMethodName = "psm2AsmWorkParameter")
public static final class Psm2AsmWorkParameter {
    @Builder.Default
    Boolean createTrace = false;
    
    @Builder.Default
    Boolean parallel = true;
    
    @Builder.Default
    Boolean useCache = true;
    
    /**
     * The transformation engine to use. Defaults to ZETA.
     * Set to ETL for backward compatibility or debugging.
     */
    @Builder.Default
    TransformationMode transformationMode = TransformationMode.ZETA;
}
```

#### Work Execute Pattern

The `execute()` method in each Work class will dispatch to the appropriate transformation based on the mode:

```java
@Override
public void execute() throws Exception {
    // ... model setup code ...
    
    Psm2AsmWorkParameter workParam = getTransformationContext()
            .getByClass(Psm2AsmWorkParameter.class)
            .orElseGet(() -> Psm2AsmWorkParameter.psm2AsmWorkParameter().build());

    Psm2AsmTransformationTrace trace;
    
    if (workParam.transformationMode == TransformationMode.ZETA) {
        // Use Zeta Java-based transformation (default)
        trace = executeZetaTransformation(psmModel.get(), asmModel, workParam);
    } else {
        // Use ETL transformation (legacy)
        trace = executeEtlTransformation(psmModel.get(), asmModel, workParam);
    }
    
    getTransformationContext().put(trace);
}

private Psm2AsmTransformationTrace executeZetaTransformation(
        PsmModel psmModel, AsmModel asmModel, Psm2AsmWorkParameter workParam) {
    Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
            .psmModel(psmModel)
            .asmModel(asmModel)
            .build();
    
    Map<EObject, List<EObject>> trace = transformation.execute();
    return Psm2AsmTransformationTrace.builder()
            .trace(trace)
            .build();
}

private Psm2AsmTransformationTrace executeEtlTransformation(
        PsmModel psmModel, AsmModel asmModel, Psm2AsmWorkParameter workParam) throws Exception {
    return Psm2Asm.executePsm2AsmTransformation(
            Psm2Asm.Psm2AsmParameter.psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .scriptUri(transformationScriptRoot)
                    .createTrace(workParam.createTrace)
                    .useCache(workParam.useCache)
                    .parallel(workParam.parallel));
}
```

#### Usage Examples

**Default behavior (Zeta):**
```java
// No configuration needed - uses Zeta by default
Psm2AsmWork work = new Psm2AsmWork(context);
work.execute();
```

**Explicit Zeta mode:**
```java
context.put(Psm2AsmWorkParameter.psm2AsmWorkParameter()
        .transformationMode(TransformationMode.ZETA)
        .build());
```

**Override to ETL mode:**
```java
context.put(Psm2AsmWorkParameter.psm2AsmWorkParameter()
        .transformationMode(TransformationMode.ETL)
        .build());
```

**System property override (for CI/testing):**
```java
// Can be set via -Djudo.transformation.mode=ETL
TransformationMode mode = TransformationMode.valueOf(
    System.getProperty("judo.transformation.mode", "ZETA"));
```

#### Modules to Update

| Module | Work Class | Parameter Class |
|--------|-----------|-----------------|
| judo-tatami-psm2asm | `Psm2AsmWork` | `Psm2AsmWorkParameter` |
| judo-tatami-psm2measure | `Psm2MeasureWork` | `Psm2MeasureWorkParameter` |
| judo-tatami-asm2rdbms | `Asm2RdbmsWork` | `Asm2RdbmsWorkParameter` |
| judo-tatami-rdbms2liquibase | `Rdbms2LiquibaseWork` | `Rdbms2LiquibaseWorkParameter` |
| judo-tatami-asm2keycloak | `Asm2KeycloakWork` | `Asm2KeycloakWorkParameter` |

### Zeta Transformation Pattern

```java
@TransformationContext(
    sourceModel = PsmModel.class,
    targetModel = AsmModel.class
)
public class Psm2AsmZetaTransformation {
    
    public static final String RULE_ENTITY_TO_ECLASS = "EntityToEClass";
    public static final String RULE_ATTRIBUTE_TO_EATTRIBUTE = "AttributeToEAttribute";
    
    @TransformRule(name = RULE_ENTITY_TO_ECLASS)
    public EClass transformEntity(Entity entity, TransformationContext ctx) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName(entity.getName());
        // ... transformation logic
        return eClass;
    }
}
```

### Dual Test Pattern

```java
public enum TransformationType { ETL, ZETA }

@ParameterizedTest
@EnumSource(TransformationType.class)
void testPsm2AsmTransformation(TransformationType type) {
    AsmModel result = switch(type) {
        case ETL -> runEtlTransformation(psmModel);
        case ZETA -> runZetaTransformation(psmModel);
    };
    
    assertModelEquivalent(expectedModel, result);
}
```

### Performance Testing Framework

Performance tests compare ETL and Zeta transformation execution times using generated large-scale models. Tests are located in `src/test/java/.../perf/` directories and can be run separately from unit tests.

#### Performance Test Base Class

```java
package hu.blackbelt.judo.tatami.core.test;

/**
 * Base class for transformation performance tests.
 * Provides model generation utilities and timing infrastructure.
 */
public abstract class AbstractTransformationPerformanceTest {
    
    protected static final int SMALL_MODEL_SIZE = 100;
    protected static final int MEDIUM_MODEL_SIZE = 1_000;
    protected static final int LARGE_MODEL_SIZE = 10_000;
    
    protected static final int WARMUP_ITERATIONS = 3;
    protected static final int MEASUREMENT_ITERATIONS = 5;
    
    /**
     * Result of a performance measurement.
     */
    @Data
    @Builder
    public static class PerformanceResult {
        private final TransformationMode mode;
        private final int modelSize;
        private final long minTimeMs;
        private final long maxTimeMs;
        private final long avgTimeMs;
        private final long medianTimeMs;
        private final double stdDevMs;
        private final int elementCount;
        private final double elementsPerSecond;
    }
    
    /**
     * Measures transformation execution time with warmup.
     */
    protected PerformanceResult measureTransformation(
            TransformationMode mode,
            Supplier<Long> transformationRunner,
            int modelSize) {
        
        // Warmup iterations (not measured)
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            transformationRunner.get();
        }
        
        // Measurement iterations
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            long elementCount = transformationRunner.get();
            long endTime = System.nanoTime();
            times.add(TimeUnit.NANOSECONDS.toMillis(endTime - startTime));
        }
        
        return buildResult(mode, modelSize, times);
    }
    
    /**
     * Compares ETL and Zeta performance and logs results.
     */
    protected void compareAndReport(PerformanceResult etlResult, PerformanceResult zetaResult) {
        double speedup = (double) etlResult.avgTimeMs / zetaResult.avgTimeMs;
        
        log.info("Performance Comparison (model size: {})", etlResult.modelSize);
        log.info("  ETL:  avg={}ms, min={}ms, max={}ms", 
                etlResult.avgTimeMs, etlResult.minTimeMs, etlResult.maxTimeMs);
        log.info("  Zeta: avg={}ms, min={}ms, max={}ms", 
                zetaResult.avgTimeMs, zetaResult.minTimeMs, zetaResult.maxTimeMs);
        log.info("  Speedup: {:.2f}x (Zeta is {} than ETL)", 
                speedup, speedup > 1 ? "faster" : "slower");
    }
}
```

#### PSM to ASM Performance Test

```java
package hu.blackbelt.judo.tatami.psm2asm.perf;

@Tag("performance")
@Disabled("Run manually with -Dtest.performance=true")
public class Psm2AsmPerformanceTest extends AbstractTransformationPerformanceTest {
    
    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    void compareEtlVsZetaPerformance(int modelSize) {
        // Generate PSM model with specified number of entities
        PsmModel psmModel = PsmModelGenerator.builder()
                .entityCount(modelSize)
                .attributesPerEntity(5)
                .relationsPerEntity(2)
                .enumerationsCount(modelSize / 10)
                .build()
                .generate();
        
        // Measure ETL transformation
        PerformanceResult etlResult = measureTransformation(
                TransformationMode.ETL,
                () -> runEtlTransformation(psmModel),
                modelSize);
        
        // Measure Zeta transformation
        PerformanceResult zetaResult = measureTransformation(
                TransformationMode.ZETA,
                () -> runZetaTransformation(psmModel),
                modelSize);
        
        // Compare and report
        compareAndReport(etlResult, zetaResult);
        
        // Assert Zeta is not significantly slower (within 20%)
        assertThat(zetaResult.avgTimeMs)
                .isLessThan((long)(etlResult.avgTimeMs * 1.2));
    }
    
    private long runEtlTransformation(PsmModel psmModel) {
        AsmModel asmModel = buildAsmModel().build();
        Psm2AsmTransformationTrace trace = Psm2Asm.executePsm2AsmTransformation(
                Psm2AsmParameter.psm2AsmParameter()
                        .psmModel(psmModel)
                        .asmModel(asmModel)
                        .build());
        return trace.getTrace().size();
    }
    
    private long runZetaTransformation(PsmModel psmModel) {
        AsmModel asmModel = buildAsmModel().build();
        Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName(psmModel.getName())
                .build();
        return transformation.execute().size();
    }
}
```

#### Model Generator Utilities

```java
package hu.blackbelt.judo.tatami.core.test;

/**
 * Generates PSM models for performance testing.
 */
@Builder
public class PsmModelGenerator {
    @Builder.Default private int entityCount = 100;
    @Builder.Default private int attributesPerEntity = 5;
    @Builder.Default private int relationsPerEntity = 2;
    @Builder.Default private int enumerationsCount = 10;
    @Builder.Default private int transferObjectsPerEntity = 1;
    @Builder.Default private String modelName = "PerformanceTestModel";
    
    public PsmModel generate() {
        PsmModel psmModel = buildPsmModel().name(modelName).build();
        Model model = newModelBuilder().withName(modelName).build();
        psmModel.getResource().getContents().add(model);
        
        // Generate enumerations
        List<EnumerationType> enums = generateEnumerations(model);
        
        // Generate entities with attributes and relations
        List<EntityType> entities = generateEntities(model, enums);
        
        // Generate transfer objects
        generateTransferObjects(model, entities);
        
        return psmModel;
    }
    
    private List<EntityType> generateEntities(Model model, List<EnumerationType> enums) {
        List<EntityType> entities = new ArrayList<>();
        
        for (int i = 0; i < entityCount; i++) {
            EntityType entity = newEntityTypeBuilder()
                    .withName("Entity" + i)
                    .build();
            
            // Add attributes
            for (int j = 0; j < attributesPerEntity; j++) {
                Attribute attr = newAttributeBuilder()
                        .withName("attr" + j)
                        .withDataType(getRandomPrimitiveType())
                        .build();
                entity.getAttributes().add(attr);
            }
            
            entities.add(entity);
            model.getElements().add(entity);
        }
        
        // Add relations between entities
        for (int i = 0; i < entityCount; i++) {
            EntityType source = entities.get(i);
            for (int j = 0; j < relationsPerEntity; j++) {
                EntityType target = entities.get((i + j + 1) % entityCount);
                AssociationEnd relation = newAssociationEndBuilder()
                        .withName("rel" + j)
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0).withUpper(-1).build())
                        .build();
                source.getRelations().add(relation);
            }
        }
        
        return entities;
    }
}
```

#### Running Performance Tests

```bash
# Run all performance tests
mvn test -Dtest.performance=true -Dtest="**/*PerformanceTest"

# Run specific module performance test
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmPerformanceTest

# Run with specific model size
mvn test -Dtest=Psm2AsmPerformanceTest -Dperformance.model.size=10000

# Generate performance report
mvn test -Dtest.performance=true -Dperformance.report=true
```

#### Performance Test Modules

| Module | Performance Test Class | Model Generator |
|--------|----------------------|-----------------|
| judo-tatami-psm2asm | `Psm2AsmPerformanceTest` | `PsmModelGenerator` |
| judo-tatami-psm2measure | `Psm2MeasurePerformanceTest` | `PsmModelGenerator` |
| judo-tatami-asm2rdbms | `Asm2RdbmsPerformanceTest` | `AsmModelGenerator` |
| judo-tatami-rdbms2liquibase | `Rdbms2LiquibasePerformanceTest` | `RdbmsModelGenerator` |
| judo-tatami-asm2keycloak | `Asm2KeycloakPerformanceTest` | `AsmModelGenerator` |

#### Expected Performance Targets

| Transformation | Model Size | ETL Baseline | Zeta Target | Notes |
|---------------|-----------|--------------|-------------|-------|
| PSM→ASM | 10,000 entities | ~5000ms | <4000ms | 20%+ improvement |
| PSM→Measure | 1,000 measures | ~500ms | <400ms | 20%+ improvement |
| ASM→RDBMS | 10,000 classes | ~8000ms | <6000ms | 25%+ improvement |
| RDBMS→Liquibase | 10,000 tables | ~3000ms | <2500ms | 15%+ improvement |
| ASM→Keycloak | 100 actors | ~200ms | <150ms | 25%+ improvement |

#### CI Integration

Performance tests are excluded from regular CI builds but run nightly:

```yaml
# .github/workflows/performance.yml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Run at 2 AM daily
  workflow_dispatch:

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Performance Tests
        run: mvn test -Dtest.performance=true -Dtest="**/*PerformanceTest"
      - name: Upload Performance Report
        uses: actions/upload-artifact@v4
        with:
          name: performance-report
          path: target/performance-report/
```

## Impact

### Modules Affected
- All transformation modules (6)
- All validation modules (4) - test updates only
- Parent POM - dependency addition
- Documentation files

### Dependencies Added
- `hu.blackbelt.judo.zeta:zeta-annotations`
- `hu.blackbelt.judo.zeta:transformation-core`
- `hu.blackbelt.judo.zeta:validation-core`

### Breaking Changes
None. ETL transformations remain available and functional.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Transformation output differs | Medium | High | Comprehensive model comparison in tests |
| Performance regression | Low | Medium | Parallel execution, benchmark tests |
| Increased maintenance burden | Medium | Medium | Clear documentation, single source of truth for rules |
| Zeta API changes | Low | Medium | Pin specific Zeta version |

## Success Criteria

1. All existing tests pass with both ETL and Zeta transformations
2. Output models are structurally equivalent between implementations
3. Zeta transformation performance is comparable or better than ETL
4. All transformation rules documented
5. Documentation converted to Markdown format
