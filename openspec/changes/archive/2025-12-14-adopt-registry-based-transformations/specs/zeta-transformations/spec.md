# Registry-Based Transformation Specification

## ADDED Requirements

### Requirement: All Zeta Transformations Must Use TransformationRegistry

All Zeta-based transformations MUST use the `TransformationRegistry` and `TransformationExecutor` pattern from the Zeta framework.

#### Scenario: Transformation uses registry pattern
Given a Zeta transformation class
When the transformation is executed
Then it must create a TransformationRegistry
And register all rule classes with the registry in correct execution order
And create a TransformationContext with proper configuration
And use TransformationExecutor with parallel set to true

### Requirement: Rule Classes Must Use TransformRule Annotations

All transformation rules MUST be defined in separate rule classes with proper annotations.

#### Scenario: Rule class structure
Given a transformation rule class
When defining transformation rules
Then each rule method must have TransformRule annotation with name and description
And use Transform annotation to specify source type
And use To annotation to specify target type
And optionally use Guard annotation for conditional execution

### Requirement: Performance Tests Must Use Single Iteration

All Zeta transformation performance tests MUST use exactly 1 iteration.

#### Scenario: Performance test configuration
Given a Zeta transformation performance test
When configuring test iterations
Then the test must use RepeatedTest with value 1 or equivalent single iteration
And no warmup phase is required
And test must measure and log execution time

## Pattern Reference

### Standard Transformation Structure

```java
public class XxxZetaTransformation {

    public Map<EObject, List<EObject>> execute() {
        // 1. Create registry and register rule classes
        TransformationRegistry registry = createRegistry();
        
        // 2. Create context
        TransformationContext context = createContext(registry);
        
        // 3. Create executor with parallel execution enabled
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(true)  // Always enable parallel execution
                .build();
        
        // 4. Execute
        TransformationResult result = executor.transform();
        
        // 5. Post-processing if needed
        postProcess(context);
        
        return buildTraceResult(context);
    }
    
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();
        registry.register(XxxRules.class);
        registry.register(YyyRules.class);
        return registry;
    }
}
```

### Standard Rule Class Structure

```java
public class XxxRules {

    @TransformRule(name = "RuleName", description = "Rule description")
    @Transform(type = SourceType.class)
    @To(type = TargetType.class)
    public TransformFunction<SourceType, TargetType> ruleName() {
        return (source, ctx) -> {
            // Create target element
            TargetType target = factory.createTargetType();
            // Configure target
            target.setName(source.getName());
            // Return target
            return target;
        };
    }
    
    @TransformRule(name = "ConditionalRule", description = "Rule with guard")
    @Guard(method = "guardMethodName")
    @Transform(type = SourceType.class)
    @To(type = TargetType.class)
    public TransformFunction<SourceType, TargetType> conditionalRule() {
        return (source, ctx) -> {
            // Only executed if guard returns true
            return factory.createTargetType();
        };
    }
    
    public boolean guardMethodName(SourceType source) {
        return source.someCondition();
    }
}
```

### Standard Performance Test Structure

```java
@Slf4j
class XxxPerformanceTest {

    private static final org.slf4j.Logger log = 
            org.slf4j.LoggerFactory.getLogger(XxxPerformanceTest.class);

    @RepeatedTest(1)
    void testZetaTransformationPerformance() throws Exception {
        // Setup models
        SourceModel source = loadSourceModel();
        TargetModel target = createEmptyTargetModel();
        
        // Execute transformation
        long startTime = System.currentTimeMillis();
        
        XxxZetaTransformation transformation = XxxZetaTransformation.builder()
                .sourceModel(source)
                .targetModel(target)
                .build();
        transformation.execute();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Zeta transformation completed in {}ms", duration);
        
        // Assert threshold (adjust based on model size)
        assertTrue(duration < 5000, "Transformation should complete within 5 seconds");
    }
}
```

## Module-Specific Notes

### Psm2Measure
- Two phases: measures first (base before derived), then units
- Order: MeasureRules (order=1), UnitRules (order=2)

### Asm2Rdbms
- Five phases: packages, tables, attributes, references, post-processing
- Complex type mappings require access to TypeMappings model
- Name abbreviation logic for SQL name constraints

### Asm2Keycloak
- Two phases: realms (pre-block), then clients
- Realm caching required for client creation

### Rdbms2Liquibase (Full)
- Four phases: tables, fields, indexes, constraints
- Creates multiple changeset types per table

### Rdbms2Liquibase (Incremental)
- Eight target models for different migration phases
- Complex precondition and backup handling
- Operation-based source model (RdbmsCreateTableOperation, etc.)
