# Design: Realistic Performance Tests

## Architecture Overview

Each transformation module will have its own `RealisticPerformanceTest` that uses shared model generators to create synthetic input models matching RackInspect characteristics.

```
Shared Test Utilities (judo-tatami-test-utils or test-jar)
├── RealisticPsmModelGenerator   → Used by: psm2asm, psm2measure
├── RealisticAsmModelGenerator   → Used by: asm2rdbms, asm2keycloak  
└── RealisticRdbmsModelGenerator → Used by: rdbms2liquibase

Transformation Tests:
- Psm2Asm: RealisticPerformanceTest (update existing) - uses PsmGenerator
- Psm2Measure: RealisticPerformanceTest (new) - uses PsmGenerator
- Asm2Rdbms: RealisticPerformanceTest (new) - uses AsmGenerator directly
- Rdbms2Liquibase: RealisticPerformanceTest (new) - uses RdbmsGenerator directly
- Asm2Keycloak: RealisticPerformanceTest (new) - uses AsmGenerator with actors
```

## Shared Test Utilities Module

### Option A: Test-Jar in Parent Module

Add to `judo-tatami-base/pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>test-jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Option B: New Module (Recommended)

Create `judo-tatami-test-utils` module:
```
judo-tatami-test-utils/
├── pom.xml
└── src/main/java/hu/blackbelt/judo/tatami/test/
    ├── RealisticPsmModelGenerator.java
    ├── RealisticAsmModelGenerator.java
    ├── RealisticRdbmsModelGenerator.java
    └── PerformanceTestBase.java
```

Dependencies in transformation test modules:
```xml
<dependency>
    <groupId>hu.blackbelt.judo.tatami</groupId>
    <artifactId>judo-tatami-test-utils</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

## Model Generators

### RealisticPsmModelGenerator

```java
public class RealisticPsmModelGenerator {
    
    // RackInspect ratios
    public static final int ATTRIBUTES_PER_ENTITY = 4;
    public static final int ASSOCIATION_ENDS_PER_ENTITY = 1;
    public static final int DATA_PROPERTIES_PER_ENTITY = 5;
    public static final int NAV_PROPERTIES_PER_ENTITY = 3;
    public static final int MAPPED_TOS_PER_ENTITY = 2;
    public static final int UNMAPPED_TOS_PER_ENTITY = 9;
    public static final int TO_ATTRIBUTES_PER_TO = 3;
    public static final int TO_RELATIONS_PER_TO = 3;
    
    public PsmModel generate(int entityCount) {
        return generate(entityCount, false);
    }
    
    public PsmModel generate(int entityCount, boolean includeMeasures) {
        PsmModel model = buildPsmModel().build();
        
        // Create types
        List<Type> types = createTypes(includeMeasures);
        
        // Create entities with attributes and relations
        List<EntityType> entities = createEntities(entityCount, types);
        
        // Create transfer objects
        List<TransferObjectType> transferObjects = createTransferObjects(entities, types);
        
        // If measures requested, create measures and units
        if (includeMeasures) {
            createMeasuresAndUnits(model);
        }
        
        // Build package structure
        buildPackageStructure(model, types, entities, transferObjects);
        
        return model;
    }
}
```

### RealisticAsmModelGenerator

```java
public class RealisticAsmModelGenerator {
    
    private static final String EXTENDED_METADATA_URI = "http://blackbelt.hu/judo/meta/ExtendedMetadata";
    
    // RackInspect ASM ratios
    public static final int ATTRIBUTES_PER_CLASS = 5;
    public static final int REFERENCES_PER_CLASS = 3;
    public static final int TRANSFER_OBJECTS_PER_ENTITY = 11; // 2 mapped + 9 unmapped
    
    public AsmModel generate(int entityCount) {
        return generate(entityCount, false);
    }
    
    public AsmModel generate(int entityCount, boolean includeActors) {
        AsmModel model = buildAsmModel().build();
        EPackage rootPackage = EcoreFactory.eINSTANCE.createEPackage();
        rootPackage.setName("test");
        rootPackage.setNsPrefix("test");
        rootPackage.setNsURI("http://test");
        
        // Create entity package with EClasses
        EPackage entitiesPackage = createEntitiesPackage(entityCount);
        rootPackage.getESubpackages().add(entitiesPackage);
        
        // Create services package with transfer object EClasses
        EPackage servicesPackage = createServicesPackage(entityCount);
        rootPackage.getESubpackages().add(servicesPackage);
        
        // Create default transfer objects package
        EPackage defaultTOPackage = createDefaultTransferObjectsPackage(entityCount);
        rootPackage.getESubpackages().add(defaultTOPackage);
        
        // If actors requested, create actor classes
        if (includeActors) {
            createActorClasses(rootPackage, entityCount);
        }
        
        model.getResource().getContents().add(rootPackage);
        return model;
    }
    
    private void addEntityAnnotation(EClass eClass) {
        EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
        annotation.setSource(EXTENDED_METADATA_URI + "/entity");
        annotation.getDetails().put("value", "true");
        eClass.getEAnnotations().add(annotation);
    }
    
    private void addActorAnnotations(EClass actor, String realm) {
        // Principal annotation
        EAnnotation principal = EcoreFactory.eINSTANCE.createEAnnotation();
        principal.setSource(EXTENDED_METADATA_URI + "/principal");
        principal.getDetails().put("value", "true");
        actor.getEAnnotations().add(principal);
        
        // Realm annotation
        EAnnotation realmAnn = EcoreFactory.eINSTANCE.createEAnnotation();
        realmAnn.setSource(EXTENDED_METADATA_URI + "/realm");
        realmAnn.getDetails().put("value", realm);
        actor.getEAnnotations().add(realmAnn);
    }
}
```

### RealisticRdbmsModelGenerator

```java
public class RealisticRdbmsModelGenerator {
    
    // RackInspect RDBMS ratios
    public static final int FIELDS_PER_TABLE = 6;
    public static final int INDEXES_PER_TABLE = 2;
    public static final int FK_PER_TABLE = 1;
    
    public RdbmsModel generate(int tableCount) {
        RdbmsModel model = RdbmsModel.buildRdbmsModel().build();
        registerMetamodels(model);
        
        // Create tables
        List<RdbmsTable> tables = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) {
            RdbmsTable table = createTable(i, tableCount);
            tables.add(table);
        }
        
        // Create junction tables for many-to-many (approximately tableCount/3)
        for (int i = 0; i < tableCount / 3; i++) {
            RdbmsJunctionTable junctionTable = createJunctionTable(i, tables);
            // Add to model
        }
        
        // Add foreign keys between tables
        addForeignKeys(tables);
        
        // Add all to model resource
        model.getResource().getContents().addAll(tables);
        
        return model;
    }
    
    private RdbmsTable createTable(int index, int totalTables) {
        RdbmsTable table = RdbmsFactory.eINSTANCE.createRdbmsTable();
        table.setName("T_ENTITY" + index);
        table.setSqlName("T_ENTITY" + index);
        
        // Add ID field
        RdbmsIdentifierField idField = RdbmsFactory.eINSTANCE.createRdbmsIdentifierField();
        idField.setName("ID");
        idField.setSqlName("ID");
        table.getFields().add(idField);
        
        // Add value fields
        for (int j = 0; j < FIELDS_PER_TABLE - 1; j++) {
            RdbmsValueField field = createValueField(j);
            table.getFields().add(field);
        }
        
        // Add indexes
        for (int j = 0; j < INDEXES_PER_TABLE; j++) {
            RdbmsIndex index = createIndex(j, table);
            table.getIndexes().add(index);
        }
        
        return table;
    }
}
```

## PerformanceTestBase

Common base class for all performance tests:

```java
public abstract class PerformanceTestBase {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected void runPerformanceTest(int entityCount, String testName,
            Supplier<Object> etlTransformation,
            Supplier<Object> zetaTransformation,
            BiFunction<Object, Object, Boolean> comparator) {
        
        log.info("");
        log.info("================================================================");
        log.info("Performance Test: {} ({} entities)", testName, entityCount);
        log.info("================================================================");
        
        // Warmup
        log.info("--- Warmup ---");
        etlTransformation.get();
        zetaTransformation.get();
        log.info("Warmup complete");
        
        // ETL measurement
        log.info("--- ETL Transformation ---");
        long etlStart = System.currentTimeMillis();
        Object etlResult = etlTransformation.get();
        long etlTime = System.currentTimeMillis() - etlStart;
        log.info("ETL completed in {}ms", etlTime);
        
        // Zeta measurement
        log.info("--- ZETA Transformation ---");
        long zetaStart = System.currentTimeMillis();
        Object zetaResult = zetaTransformation.get();
        long zetaTime = System.currentTimeMillis() - zetaStart;
        log.info("Zeta completed in {}ms", zetaTime);
        
        // Comparison
        log.info("--- Model Comparison ---");
        boolean equivalent = comparator.apply(etlResult, zetaResult);
        if (equivalent) {
            log.info("SUCCESS: ETL and Zeta models are equivalent");
        } else {
            log.warn("DIFFERENCE: Models are not equivalent");
        }
        
        // Results
        printResults(testName, etlTime, zetaTime);
    }
    
    protected void printResults(String testName, long etlTime, long zetaTime) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {}", testName);
        log.info("================================================================");
        log.info("ETL:  {}ms", etlTime);
        log.info("ZETA: {}ms", zetaTime);
        
        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / zetaTime;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else {
            double slowdown = (double) zetaTime / etlTime;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        }
        log.info("================================================================");
    }
}
```

## Test Class Structure

Each test follows this pattern:

```java
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest extends PerformanceTestBase {
    
    @Test
    void testRackInspectLikeModel() { runTest(70, "RackInspect-like"); }
    
    @Test
    void testSmallModel() { runTest(20, "Small"); }
    
    @Test
    void testLargeModel() { runTest(100, "Large"); }
    
    private void runTest(int entityCount, String testName) {
        // Generate input model using shared generator
        InputModel inputModel = generator.generate(entityCount);
        
        runPerformanceTest(entityCount, testName,
            () -> executeEtlTransformation(inputModel),
            () -> executeZetaTransformation(inputModel),
            this::compareModels);
    }
}
```

## File Locations

```
judo-tatami-test-utils/                              (NEW MODULE)
  src/main/java/hu/blackbelt/judo/tatami/test/
    RealisticPsmModelGenerator.java
    RealisticAsmModelGenerator.java
    RealisticRdbmsModelGenerator.java
    PerformanceTestBase.java

judo-tatami-psm2asm/
  src/test/java/.../perf/RealisticPerformanceTest.java (UPDATE)

judo-tatami-psm2measure/
  src/test/java/.../perf/RealisticPerformanceTest.java (NEW)

judo-tatami-asm2rdbms/
  src/test/java/.../perf/RealisticPerformanceTest.java (NEW)

judo-tatami-rdbms2liquibase/
  src/test/java/.../perf/RealisticPerformanceTest.java (NEW)

judo-tatami-asm2keycloak/
  src/test/java/.../perf/RealisticPerformanceTest.java (NEW)

rackinspect/                                          (DELETE)
  rackinspect-*.model                                 (DELETE ALL)
```

## Expected Performance Results

Based on RackInspect tests:

| Transformation | Expected Zeta Speedup |
|---------------|----------------------|
| Psm2Asm | 30-40x faster |
| Psm2Measure | ~1x (small model, overhead dominant) |
| Asm2Rdbms | 2-3x faster |
| Rdbms2Liquibase | 40-50x faster |
| Asm2Keycloak | 1.3-1.5x faster |
