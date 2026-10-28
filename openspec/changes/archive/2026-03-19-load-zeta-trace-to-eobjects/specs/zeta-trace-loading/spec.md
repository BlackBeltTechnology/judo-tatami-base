# Zeta Trace Loading Spec

## Behavior

### Utility: `TraceEntry` list to Zeta `TransformationTrace`

A shared static method converts tatami-core `List<TraceEntry>` into a Zeta `TransformationTrace`:

- Creates `ElementResolutionCache` in sequential mode
- Iterates all entries, expanding multi-source/multi-target into individual `addMapping()` or `addDiscriminatedMapping()` calls
- Wraps cache in `new TransformationTrace(cache)`
- Location: utility class in a shared location accessible by all 4 modules (e.g., `judo-tatami-util` or inline in each module if no shared util exists)

### `fromModelsAndTrace()` JSON branch

When JSON format is detected (first non-whitespace byte is `{`):

1. Use `ZetaTraceLoader` to parse JSON and resolve EObject IDs against source + target ResourceSets
2. Convert resulting `List<TraceEntry>` to Zeta `TransformationTrace` via the utility
3. Set `zetaTrace` on the builder

When the stream is empty (first byte is `-1`):
- Build with both `trace` and `zetaTrace` null (current behavior, no trace data)

### Post-conditions after JSON load

- `isZetaTrace()` returns `true`
- `getZetaTrace()` returns non-null `TransformationTrace` with entries
- `getTransformationTrace()` returns populated `Map<EObject, List<EObject>>` (via existing conversion in `getTransformationTrace()`)
- `getZetaTrace().getEntries()` contains entries with resolved live EObject references

## Constraints

- Must not modify `judo-tatami-core` or `zeta-transformation-core`
- Must use existing `ZetaTraceLoader` API from tatami-core
- EObject resolution depends on models having `id` EAttribute — this is guaranteed for all JUDO metamodels
- Empty JSON stream (`-1`) should not attempt loading (no trace to resolve)
