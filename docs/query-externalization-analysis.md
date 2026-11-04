# Query Model Externalization: From Runtime-Core to Tatami-Base

## Executive Summary

The **query model** is currently built at runtime inside `judo-runtime-core` by the `QueryFactory` class. It transforms ASM + Expression models into a logical query model (`judo-meta-query`) that represents SQL SELECT structures for mapped transfer object types. This document analyzes how to externalize this build-time transformation into `judo-tatami-base` as a new `judo-tatami-asm2query` module, following the same pattern as existing transformations (psm2asm, asm2rdbms, etc.).

---

## 1. What Is the Query Model?

The query model (`judo-meta-query`) is a **logical SQL representation** of how transfer object types map to database queries. It contains:

| Query Element | Purpose |
|---------------|---------|
| `Select` | Root query for a mapped transfer object type (maps to SQL SELECT) |
| `SubSelect` | Nested query for navigation/collection references |
| `Target` | Projection definition (which columns appear in the result) |
| `Feature` | A computed value (attribute expression translated to SQL column/expression) |
| `Join` / `SubSelectJoin` / `CustomJoin` / `CastJoin` | Various JOIN types connecting entity tables |
| `Filter` | WHERE clause conditions |
| `OrderBy` | ORDER BY definitions |
| `Node` | Abstract base for query tree nodes (Select, Join, Filter, etc.) |
| `FeatureTargetMapping` | Maps a Feature to its Target + attribute |
| `ReferencedTarget` | Links a reference to its target in a join |

### Key semantics:
- One `Select` per mapped transfer object type
- `SubSelect`s for navigation (exposed graph) and collection references
- `Feature`s are expression-to-SQL translations (attribute getters, computed fields, aggregations)
- `Join`s represent entity relationships traversed during query resolution
- The model is **database-agnostic** — it's a logical plan, not SQL

---

## 2. Current Architecture (Runtime-Core)

### 2.1 Module Structure

```
judo-runtime-core-expression/        ← Expression binding collector
  TransferObjectTypeBindingsCollector  ← Walks ASM + Expression models
  MappedTransferObjectTypeBindings     ← Per-type binding data
  EntityTypeExpressions                ← Per-entity-type expression map
  UnmappedTransferObjectTypeBindings   ← For unmapped transfer objects

judo-runtime-core-query/             ← Query model builder
  QueryFactory                         ← Main entry point (builds entire query model)
  FeatureFactory                       ← Converts expressions to Features
  JoinFactory                          ← Converts navigations to Joins
  Context                              ← Builder context (counters, variables, node stack)
  CustomJoinDefinition                 ← Extension point for custom SQL joins
  Constants                            ← Shared constants
  feature/                             ← 70+ ExpressionToFeatureConverter implementations
    ExpressionToFeatureConverter        ← Base converter interface
    AttributeToFeatureConverter
    ConstantToFeatureConverter
    ContainsExpressionToFeatureConverter
    ... (one per expression type)
    aggregated/
      CountExpressionToFeatureConverter
      DecimalAggregatedExpressionToFeatureConverter
      ...
```

### 2.2 How QueryFactory Works

```
Inputs:
  ├── ASM ResourceSet (transfer object types, entity types, annotations)
  ├── Measure ResourceSet (unit definitions)
  ├── Expression ResourceSet (JQL expressions extracted from ASM annotations)
  └── Coercer (type conversion service from mapper-api)

Processing:
  1. TransferObjectTypeBindingsCollector walks ASM + Expression models
     → builds per-type binding maps (attribute→expression, reference→expression)
  2. QueryFactory.createQueries():
     a. Creates Select skeleton for each mapped transfer object type (parallel)
     b. Adds attributes (Features) to each Select (parallel)
     c. Adds references (Joins/SubSelects) to each Select (parallel)
  3. QueryFactory.createNavigations():
     → Creates SubSelects for static/exposed navigation properties
  4. All created objects are added to QueryModelResourceSupport
  5. Model is validated

Output:
  └── QueryModelResourceSupport containing the full query model
      (accessible via queryFactory.getQueryModelResourceSupport())
```

### 2.3 Construction Sites (Where QueryFactory Is Created at Runtime)

| Location | How |
|----------|-----|
| `JudoDefaultSpringConfiguration.getQueryFactory()` | `new QueryFactory(asmRS, measureRS, asmJqlExtractor.extractExpressions(), coercer, customJoins)` |
| `QueryFactoryProvider.get()` (Guice) | Same pattern |
| `JudoRuntimeFixture.initQueryFactory()` (test) | Same pattern |

All three follow the same pattern:
1. Create `AsmJqlExtractor` from ASM + Measure ResourceSets
2. Call `asmJqlExtractor.extractExpressions()` → returns Expression ResourceSet
3. Create `QueryFactory` with ASM RS, Measure RS, Expression RS, Coercer

---

## 3. Dependency Analysis

### 3.1 What QueryFactory Depends On

| Dependency | From | Purpose | Externalizable? |
|------------|------|---------|-----------------|
| `judo-meta-asm` | models | ASM metamodel (EClasses, EReferences, AsmUtils) | Yes — already in tatami |
| `judo-meta-expression` | models | Expression metamodel | Yes — already in tatami |
| `judo-meta-expression.adapter.asm` | models | AsmModelAdapter, AsmMeasureProvider | Yes — already in tatami |
| `judo-meta-expression.builder.jql` | models | JqlExpressionBuilder, JqlExtractor | Yes — already used by Asm2Expression |
| `judo-meta-expression.builder.jql.asm` | models | AsmJqlExtractor | Yes — already used by Asm2Expression |
| `judo-meta-measure` | models | MeasureModelResourceSupport | Yes — already in tatami |
| `judo-meta-query` | models | Query metamodel, QueryModelResourceSupport, QueryUtils | Yes — no runtime dependency |
| `judo-runtime-core-expression` | **runtime** | TransferObjectTypeBindingsCollector, MappedTransferObjectTypeBindings, etc. | **Must be moved** |
| `mapper-api` (Coercer) | external | Type coercion for constant expressions | **Needs abstraction or inclusion** |
| `judo-dispatcher-api` | external | Only transitively (no direct query use) | Can be removed |
| `commons-lang3` | external | StringUtils.leftPad (trivial) | Yes |

### 3.2 The Coercer Problem

`Coercer` (from `hu.blackbelt.mapper:mapper-api`) is used in **one place**: `FeatureFactory` and `ConstantToFeatureConverter` to coerce constant expression values to their target types. This is the **only runtime-specific dependency** in the query module.

Options:
1. **Include mapper-api as dependency in tatami-base** (simplest — it's a lightweight API jar)
2. **Abstract Coercer behind an interface** and provide a default implementation
3. **Pass a `Function<Object, Object>` coercion function** instead of the Coercer interface

**Recommendation:** Option 1 — mapper-api is lightweight and already used in the JUDO ecosystem.

### 3.3 The Expression Module Problem

`judo-runtime-core-expression` contains 4 classes that are essential to QueryFactory:

| Class | Lines | Purpose |
|-------|-------|---------|
| `TransferObjectTypeBindingsCollector` | ~450 | Walks ASM+Expression models, builds binding maps |
| `MappedTransferObjectTypeBindings` | ~60 | Data class: per-type attribute/reference expression bindings |
| `EntityTypeExpressions` | ~40 | Data class: per-entity getter expressions |
| `UnmappedTransferObjectTypeBindings` | ~30 | Data class: for unmapped transfer objects |

These classes have **zero runtime dependencies** — they only depend on:
- `judo-meta-asm`
- `judo-meta-expression`
- Lombok

**These must be moved to the new tatami module** (or to a shared module).

---

## 4. Externalization Plan

### 4.1 New Module: `judo-tatami-asm2query`

```
judo-tatami-base/
  judo-tatami-asm2query/
    pom.xml
    src/main/java/hu/blackbelt/judo/tatami/asm2query/
      Asm2Query.java                    ← Static entry point (like Asm2Expression)
      Asm2QueryWork.java                ← Workflow Work (like Asm2ExpressionWork)
      Asm2QueryConfiguration.java       ← Configuration options
    src/main/java/hu/blackbelt/judo/tatami/asm2query/internal/
      QueryFactory.java                 ← Moved from runtime-core-query
      FeatureFactory.java               ← Moved from runtime-core-query
      JoinFactory.java                  ← Moved from runtime-core-query
      Context.java                      ← Moved from runtime-core-query
      CustomJoinDefinition.java         ← Moved from runtime-core-query
      Constants.java                    ← Moved from runtime-core-query
      feature/                          ← All 70+ converters moved
      expression/                       ← Moved from runtime-core-expression
        TransferObjectTypeBindingsCollector.java
        MappedTransferObjectTypeBindings.java
        EntityTypeExpressions.java
        UnmappedTransferObjectTypeBindings.java
```

### 4.2 Public API Design

```java
// Asm2Query.java — Static entry point
public class Asm2Query {

    @Builder(builderMethodName = "asm2QueryParameter")
    public static class Asm2QueryParameter {
        @NonNull AsmModel asmModel;
        MeasureModel measureModel;
        @NonNull ExpressionModel expressionModel;
        @NonNull Coercer coercer;
        Map<EReference, CustomJoinDefinition> customJoinDefinitions;
    }

    public static QueryModel executeAsm2Query(Asm2QueryParameter parameter) {
        // 1. Build QueryFactory with inputs
        // 2. Extract QueryModelResourceSupport
        // 3. Wrap in QueryModel and return
    }
}

// Asm2QueryWork.java — Tatami workflow integration
public class Asm2QueryWork extends AbstractTransformationWork {
    @Override
    public void execute() {
        AsmModel asm = context.getByClass(AsmModel.class).orElseThrow(...);
        ExpressionModel expr = context.getByClass(ExpressionModel.class).orElseThrow(...);
        MeasureModel measure = context.getByClass(MeasureModel.class).orElse(null);
        Coercer coercer = context.getByClass(Coercer.class).orElse(defaultCoercer());

        QueryModel queryModel = Asm2Query.executeAsm2Query(...);
        context.put(queryModel);
    }
}
```

### 4.3 Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Model dependencies (same as existing tatami modules) -->
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.asm.model</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.expression.model</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.expression.model.adapter.asm</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.expression.builder.jql</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.expression.builder.jql.asm</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.measure.model</artifactId>
    </dependency>
    <dependency>
        <groupId>hu.blackbelt.judo.meta</groupId>
        <artifactId>hu.blackbelt.judo.meta.query.model</artifactId>
    </dependency>

    <!-- Tatami core (for AbstractTransformationWork, TransformationContext) -->
    <dependency>
        <groupId>hu.blackbelt.judo.tatami</groupId>
        <artifactId>judo-tatami-core</artifactId>
    </dependency>

    <!-- Coercer API -->
    <dependency>
        <groupId>hu.blackbelt.mapper</groupId>
        <artifactId>mapper-api</artifactId>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>
</dependencies>
```

### 4.4 Workflow Integration

The new module fits into the existing tatami workflow after `Asm2Expression`:

```
ESM → PSM → ASM → Expression → Query → RDBMS → Liquibase
                                  ↑
                           NEW: Asm2QueryWork
```

In `WorkflowHelper.java`:
```java
public Work createAsm2QueryWork() {
    return aNewConditionalFlow()
        .named("Conditional when Expression model exists then Execute Asm2Query")
        .execute(new CheckWork(() ->
            transformationContext.transformationContextVerifier
                .verifyClassPresent(ExpressionModel.class),
            "Verify Expression model is presented"))
        .when(WorkReportPredicate.COMPLETED)
        .then(new Asm2QueryWork(transformationContext).withMetricsCollector(workflowMetrics))
        .otherwise(new NoOpWork())
        .build();
}
```

---

## 5. Impact on Runtime-Core

After externalization, `judo-runtime-core` will:

1. **Remove** `judo-runtime-core-query` module entirely
2. **Remove** `judo-runtime-core-expression` module (moved to tatami)
3. **Depend on** `judo-tatami-asm2query` for `QueryFactory` (or consume the `QueryModel` directly)
4. **Receive** `QueryModel` as a pre-built input instead of building it at startup

### 5.1 Changes to Runtime Bootstrap

**Before (current):**
```java
// Spring
AsmJqlExtractor extractor = new AsmJqlExtractor(asm, measure, uri, config);
QueryFactory queryFactory = new QueryFactory(asm, measure, extractor.extractExpressions(), coercer, customJoins);
// queryFactory is injected into DAO layer
```

**After (externalized):**
```java
// Option A: Use pre-built QueryModel from tatami workflow
QueryModel queryModel = loadQueryModel(...);  // loaded from file like other models
QueryFactory queryFactory = new QueryFactory(queryModel, asmResourceSet, coercer);

// Option B: Build at runtime using tatami module
QueryModel queryModel = Asm2Query.executeAsm2Query(
    Asm2Query.Asm2QueryParameter.asm2QueryParameter()
        .asmModel(asmModel)
        .expressionModel(expressionModel)
        .measureModel(measureModel)
        .coercer(coercer)
        .build());
```

### 5.2 The QueryFactory API for Runtime Consumers

The runtime DAO layer uses these `QueryFactory` methods:

| Method | Used By | Purpose |
|--------|---------|---------|
| `getQuery(EClass)` | SelectStatementExecutor | Get SELECT for a transfer object type |
| `getNavigation(EReference)` | SelectStatementExecutor | Get SubSelect for a navigation |
| `getDataQuery(EAttribute)` | SelectStatementExecutor | Get SubSelect for static data |
| `isOrdered(EReference)` | SelectStatementExecutor, RdbmsDAOImpl | Check if relation maintains order |
| `isStaticReference(EReference)` | SelectStatementExecutor, RdbmsDAOImpl | Check if navigation is static |
| `isStaticAttribute(EAttribute)` | SelectStatementExecutor | Check if attribute is static |
| `getModelAdapter()` | PayloadDaoProcessor, AttributeSelectorTranslator | Get ASM model adapter |
| `getEntityTypeExpressionsMap()` | AttributeSelectorTranslator | Get expression map for entity types |
| `dataExpressionToFeature(...)` | SelectStatementExecutor | Dynamic filter expression conversion |
| `getNextSourceIndex()` | SelectStatementExecutor | Counter for dynamic aliases |
| `getNextTargetIndex()` | SelectStatementExecutor | Counter for dynamic targets |

**Important:** `dataExpressionToFeature()`, `getNextSourceIndex()`, and `getNextTargetIndex()` are used for **dynamic query building** (runtime filter expressions). This means `QueryFactory` must remain available at runtime, not just the built model. The externalization should move the **model building** to tatami but keep `QueryFactory` available at runtime for dynamic features.

---

## 6. Files to Move

### From `judo-runtime-core-expression` → `judo-tatami-asm2query/internal/expression/`

| File | Lines |
|------|-------|
| `TransferObjectTypeBindingsCollector.java` | ~450 |
| `MappedTransferObjectTypeBindings.java` | ~60 |
| `EntityTypeExpressions.java` | ~40 |
| `UnmappedTransferObjectTypeBindings.java` | ~30 |

### From `judo-runtime-core-query` → `judo-tatami-asm2query/internal/`

| File | Lines |
|------|-------|
| `QueryFactory.java` | ~700 |
| `FeatureFactory.java` | ~400 |
| `JoinFactory.java` | ~600 |
| `Context.java` | ~100 |
| `CustomJoinDefinition.java` | ~30 |
| `Constants.java` | ~20 |
| `feature/ExpressionToFeatureConverter.java` | ~50 |
| `feature/*.java` (70+ converters) | ~3000 total |

**Total: ~5,480 lines of code to move**

---

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Dynamic query building must stay at runtime | HIGH | Keep QueryFactory accessible; externalize only model-building |
| `Coercer` dependency crosses ecosystem boundary | MEDIUM | Include mapper-api in tatami-base |
| 70+ feature converters are tightly coupled | LOW | Move as a unit; they're self-contained |
| Runtime-core tests depend on QueryFactory | MEDIUM | Tests will depend on new tatami module |
| Expression ResourceSet created dynamically at runtime | MEDIUM | Already done by `Asm2ExpressionWork` in tatami |
| CustomJoinDefinition is a runtime extension point | HIGH | Must be passed as parameter; cannot be pre-built |

---

## 8. Recommended Approach

### Phase 1: Create `judo-tatami-asm2query` module
- Move expression binding collector classes
- Move QueryFactory and all feature/join converters
- Create `Asm2Query` static entry point
- Create `Asm2QueryWork` for workflow integration
- Add to tatami workflow after `Asm2ExpressionWork`

### Phase 2: Adapt `judo-runtime-core`
- Remove `judo-runtime-core-expression` module
- Remove `judo-runtime-core-query` module
- Add dependency on `judo-tatami-asm2query`
- Refactor Spring/Guice configs to use pre-built or tatami-built QueryFactory
- Keep QueryFactory accessible for dynamic query building at runtime

### Phase 3: Enable pre-built query model
- Add `QueryModel` save/load to `DefaultWorkflowSave`
- Add `QueryModel` loading to `WorkflowHelper`
- Support passing pre-built `QueryModel` to runtime (eliminating runtime build cost)

---

## 9. Key Design Decision: QueryFactory Stays or Goes?

The critical question is whether `QueryFactory` becomes a **build-time artifact** or stays as a **runtime component**.

**Analysis:** Because of `dataExpressionToFeature()` (dynamic filter translation), `getNextSourceIndex/TargetIndex()` (dynamic alias generation), and `CustomJoinDefinition` (runtime extension), **QueryFactory must remain available at runtime**.

**Recommended split:**
1. **Build-time** (tatami): Expression extraction + QueryFactory construction + model validation
2. **Runtime** (runtime-core): QueryFactory instance used for query lookups + dynamic query building

The tatami module builds the QueryFactory and serializes the resulting QueryModel. At runtime, the QueryModel is loaded and a lightweight QueryFactory wrapper provides the lookup + dynamic query API.

Alternatively, the QueryFactory can be built at runtime using the tatami module directly (as a library dependency), which is the simplest migration path.
