# Load Zeta Trace JSON to EObjects

## Summary

Fix `fromModelsAndTrace()` in all `*TransformationTrace` classes to properly deserialize Zeta JSON traces into a fully populated `zetaTrace` field with live EObject references, enabling downstream consumers to use the full `ElementResolutionCache` API (rule-based lookups, discriminated lookups, primary flag).

## Problem Statement

When `fromModelsAndTrace()` detects JSON format, it builds the trace object **without** populating either `trace` or `zetaTrace`:

```java
if (b == '{' || b == -1) {
    return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
            .asmModel(asmModel)
            .psmModel(psmModel)
            .build();  // Both trace and zetaTrace are null!
}
```

This means:
- `getTransformationTrace()` returns `Collections.emptyMap()`
- `getZetaTrace()` returns `null`
- All trace data from the JSON file is silently discarded

## Solution

Use `ZetaTraceLoader` (from `judo-tatami-core`) to parse JSON and resolve EObjects, then reconstruct a Zeta `TransformationTrace` wrapping an `ElementResolutionCache`:

```
JSON InputStream
    → ZetaTraceLoader.loadTrace(is, [sourceRS, targetRS])
    → List<TraceEntry> (with live EObject refs)
    → ElementResolutionCache (populated via addMapping/addDiscriminatedMapping)
    → new TransformationTrace(cache)
    → builder.zetaTrace(trace).build()
```

### Shared Utility Method

Extract the `List<TraceEntry>` → `TransformationTrace` conversion into a shared utility to avoid duplication across 4 modules:

```java
public static TransformationTrace toZetaTransformationTrace(List<TraceEntry> entries) {
    ElementResolutionCache cache = new ElementResolutionCache(true);
    for (TraceEntry entry : entries) {
        for (EObject source : entry.getSources()) {
            for (EObject target : entry.getTargets()) {
                if (entry.getDiscriminator() != null) {
                    cache.addDiscriminatedMapping(source, target,
                        entry.getRuleName(), entry.getDiscriminator());
                } else {
                    cache.addMapping(source, entry.getRuleName(),
                        target, entry.isPrimary());
                }
            }
        }
    }
    return new TransformationTrace(cache);
}
```

## Affected Modules

| Module | TransformationTrace Class | Source RS | Target RS |
|--------|---------------------------|-----------|-----------|
| psm2asm | `Psm2AsmTransformationTrace` | `psmModel.getResourceSet()` | `asmModel.getResourceSet()` |
| psm2measure | `Psm2MeasureTransformationTrace` | `psmModel.getResourceSet()` | `measureModel.getResourceSet()` |
| asm2keycloak | `Asm2KeycloakTransformationTrace` | `asmModel.getResourceSet()` | `keycloakModel.getResourceSet()` |
| asm2rdbms | `Asm2RdbmsTransformationTrace` | `asmModel.getResourceSet()` | `rdbmsModel.getResourceSet()` |

## Scope

### In Scope
1. Shared utility method for `List<TraceEntry>` → `TransformationTrace` conversion
2. Update `fromModelsAndTrace()` JSON branch in all 4 `*TransformationTrace` classes
3. Tests verifying round-trip: save Zeta trace → load from JSON → verify EObject mappings match

### Out of Scope
- Modifying `judo-tatami-core` or `zeta-transformation-core`
- Changing ETL trace loading (XMI path unchanged)
- Modifying how Zeta transformations produce traces at runtime
