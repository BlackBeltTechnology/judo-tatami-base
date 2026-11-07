# Implement Zeta Transformation Trace Support

## Summary

Integrate Zeta framework's native `TransformationTrace` with tatami transformations using tatami-core's trace loading support. **All Zeta-based transformations must produce native Zeta trace format**, including both TransformationExecutor-based and hand-written transformations. ETL continues using `Map<EObject, List<EObject>>`.

## Problem Statement

Currently, Zeta transformations discard the trace produced by the Zeta framework:

```java
// In Psm2AsmZetaTransformation.java
TransformationResult result = executor.transform();
// result.getTrace() is available but not used!

// Returns empty map instead
private Map<EObject, List<EObject>> buildTraceResult(TransformationContext context) {
    return new HashMap<>();  // Trace data lost
}
```

## Solution

### Dual Trace Strategy

| Engine | Trace Type | Storage | Access Method |
|--------|------------|---------|---------------|
| ETL | `Map<EObject, List<EObject>>` | `trace` field | `getTransformationTrace()` |
| Zeta | `TransformationTrace` | `zetaTrace` field | `getZetaTrace()` |

### Zeta Transformation Return Type

All Zeta transformations must return native `TransformationTrace`:

#### TransformationExecutor-based transformations (psm2asm, psm2measure, asm2keycloak)

```java
public TransformationTrace execute() {
    TransformationResult result = executor.transform();
    postProcess(context);
    return result.getTrace();  // Return native Zeta trace from executor
}
```

#### Hand-written transformations (asm2rdbms)

Hand-written Zeta transformations that don't use `TransformationExecutor` must build and return a native `TransformationTrace`:

```java
public TransformationTrace execute() {
    // ... transformation logic with internal trace tracking ...

    // Build native Zeta trace from internal trace map
    return buildZetaTrace();
}

private TransformationTrace buildZetaTrace() {
    TransformationTrace trace = new TransformationTrace();
    for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
        EObject source = entry.getKey();
        for (Map.Entry<String, EObject> targetEntry : entry.getValue().entrySet()) {
            String ruleName = targetEntry.getKey();
            EObject target = targetEntry.getValue();
            trace.addEntry(source, target, ruleName, true, null);
        }
    }
    return trace;
}
```

### TransformationTrace Class Updates

```java
@Builder(builderMethodName = "psm2AsmTransformationTraceBuilder")
public class Psm2AsmTransformationTrace implements TransformationTrace {

    @NonNull @Getter
    PsmModel psmModel;

    @NonNull @Getter
    AsmModel asmModel;

    // ETL trace (null when Zeta used)
    Map<EObject, List<EObject>> trace;

    // Zeta trace (null when ETL used)
    hu.blackbelt.judo.zeta.transformation.core.TransformationTrace zetaTrace;

    @Override
    public Map<EObject, List<EObject>> getTransformationTrace() {
        // Return ETL trace or empty map for Zeta
        return trace != null ? trace : Collections.emptyMap();
    }

    public hu.blackbelt.judo.zeta.transformation.core.TransformationTrace getZetaTrace() {
        return zetaTrace;
    }

    public boolean isZetaTrace() {
        return zetaTrace != null;
    }
}
```

### tatami-core Trace Loading API

```java
// Load Zeta trace from JSON file
List<TraceEntry> entries = TransformationTraceLoader.loadTrace(
    traceFile,
    Arrays.asList(sourceResourceSet, targetResourceSet)
);
```

Resolution strategies:

| Format | Resolution Strategy |
|--------|---------------------|
| Zeta JSON | Matches `id` field against EObject's `id` EAttribute |
| ETL XMI | 1. URI fragment match, 2. Falls back to `id` EAttribute |

### JSON Trace Format

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

## Architecture

### ETL Path
```
ETL Executor → Map<EObject, List<EObject>>
                        ↓
      *TransformationTrace.trace = map
                        ↓
      getTransformationTrace() → Map
```

### Zeta Path (TransformationExecutor-based)
```
TransformationExecutor.transform() → TransformationResult.getTrace() → TransformationTrace
                        ↓
      *TransformationTrace.zetaTrace = trace
                        ↓
      getZetaTrace() → TransformationTrace
      getTransformationTrace() → empty Map (backward compat)
```

### Zeta Path (Hand-written)
```
Hand-written transformation logic with internal traceMap
                        ↓
      buildZetaTrace() → TransformationTrace
                        ↓
      *TransformationTrace.zetaTrace = trace
                        ↓
      getZetaTrace() → TransformationTrace
      getTransformationTrace() → empty Map (backward compat)
```

## Scope

### In Scope

1. Change all Zeta transformation `execute()` methods to return `TransformationTrace`
2. Add `zetaTrace` field and `getZetaTrace()` method to `*TransformationTrace` classes
3. Update Work classes with two code paths (ETL vs Zeta)
4. Apply to all modules with Zeta transformations:
   - **psm2asm** - TransformationExecutor-based
   - **psm2measure** - TransformationExecutor-based
   - **asm2keycloak** - TransformationExecutor-based
   - **asm2rdbms** - Hand-written, must build native Zeta trace from internal trace map

### Out of Scope

- Modifying Zeta framework
- Modifying tatami-core
- Converting between trace formats at runtime

## Testing

### Trace Content Verification

Each transformation module must verify that Zeta trace contains expected entries:

```java
@Test
void testZetaTraceContents() {
    // Execute Zeta transformation
    TransformationTrace trace = transformation.execute();

    // Verify trace is non-null and has entries
    assertNotNull(trace);
    Collection<TraceEntry> entries = trace.getEntries();
    assertFalse(entries.isEmpty());

    // Verify specific source-to-target mappings exist
    boolean foundEntityMapping = entries.stream()
        .anyMatch(e -> e.getRuleName().equals("CreateEntityTable")
            && e.getSource() instanceof EClass
            && e.getTarget() instanceof RdbmsTable);
    assertTrue(foundEntityMapping, "Expected CreateEntityTable trace entry");

    // Verify trace can be serialized to JSON
    String json = trace.toJson();
    assertNotNull(json);
    assertTrue(json.contains("traceEntries"));
    assertTrue(json.contains("ruleName"));
}
```

### Test Categories

| Category | Description | Verification |
|----------|-------------|--------------|
| **Entry Count** | Trace has expected number of entries | `entries.size() >= expectedMinimum` |
| **Rule Coverage** | All transformation rules produce trace entries | Check each rule name appears in trace |
| **Source Types** | Source elements are correct metamodel types | `entry.getSource() instanceof ExpectedType` |
| **Target Types** | Target elements are correct metamodel types | `entry.getTarget() instanceof ExpectedType` |
| **JSON Export** | Trace can be serialized to valid JSON | `trace.toJson()` returns valid JSON |
| **JSON Structure** | JSON contains required fields | Contains `traceEntries`, `ruleName`, `source`, `target` |

### Module-Specific Tests

#### psm2asm
- Verify `CreateEntityClass` rule maps `EntityType` → `EClass`
- Verify `CreateEntityAttribute` rule maps `Attribute` → `EAttribute`
- Verify `CreateTransferObjectClass` rule maps `TransferObjectType` → `EClass`

#### asm2rdbms
- Verify `CreateEntityTable` rule maps `EClass` → `RdbmsTable`
- Verify `CreateAttributeColumn` rule maps `EAttribute` → `RdbmsColumn`
- Verify `CreateForeignKey` rule maps `EReference` → `RdbmsForeignKey`

#### psm2measure
- Verify `CreateUnit` rule maps `Unit` → measurement elements
- Verify `CreateMeasure` rule maps `Measure` → measurement elements

#### asm2keycloak
- Verify `CreateKeycloakClient` rule maps actor types → Keycloak clients
- Verify `CreateKeycloakClientClaim` rule maps claims correctly

## Benefits

- **Native Zeta trace** - No conversion, full metadata preserved
- **Rich trace data** - Rule names, discriminators, timestamps
- **JSON persistence** - Human-readable format
- **Backward compatible** - ETL path unchanged, `getTransformationTrace()` still works
- **Fail-fast loading** - `TraceLoadException` on unresolved elements
