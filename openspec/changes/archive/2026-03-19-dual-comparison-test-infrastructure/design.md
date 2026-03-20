## Context

`judo-tatami-test-utils` already provides `AbstractExternalModelTest` (1045 LOC) with TestResult recording, summary reporting, JSON export, model discovery, ExternalModelConfig, element counting, structural comparison, and transformation mode support. Five discovery/dual test classes in `judo-tatami-jsl` duplicate ~1200 LOC of this infrastructure because they predate `AbstractExternalModelTest` and were never migrated.

The new `AbstractDualComparisonTest<S, T>` extends `AbstractExternalModelTest` to provide the dual comparison orchestration pattern (warmup → timed ETL → timed ZETA → compare → record) as a reusable template method.

## Goals / Non-Goals

**Goals:**
- Extract dual comparison @TestFactory orchestration into a generic base class
- Reuse all existing `AbstractExternalModelTest` infrastructure (no duplication)
- Support both external model discovery tests and inline @Test methods
- Provide `PerformanceMeasurement` as a standalone timing utility

**Non-Goals:**
- Modifying `AbstractExternalModelTest` itself
- Migrating `FullPipelineComparisonTest` (different pattern: multi-stage pipeline)
- Migrating `Psm2AsmDualTransformationTest` (can be done later, not required)
- Adding new comparison algorithms (existing `ModelComparator` is sufficient)

## Decisions

### D1: Extend `AbstractExternalModelTest` rather than create parallel hierarchy

**Choice:** `AbstractDualComparisonTest<S, T> extends AbstractExternalModelTest`

**Alternatives considered:**
- *6 parallel classes (original proposal)* — Would duplicate TestResult, printSummary, writeJsonResults, loadModelConfigs, countElements. Rejected because `AbstractExternalModelTest` already provides all of these.
- *Composition (has-a) with a DualComparisonHelper* — Would require forwarding methods for discovery, reporting, etc. More boilerplate than inheritance for this case.

**Rationale:** The discovery tests need exactly what `AbstractExternalModelTest` provides plus dual orchestration. Classical template method pattern — "is-a" fits cleanly.

### D2: `getResource(T)` returns `Resource` not `EObject`

**Choice:** Subclasses return `Resource` from the target model.

**Alternatives considered:**
- *`getRoot(T)` returning `EObject`* — Forces single-root assumption. Some models have multiple root elements.

**Rationale:** `ModelComparator.compare(Resource, Resource)` already handles identity-based root matching. Returning `Resource` preserves this capability.

### D3: `parseSource()` called separately for ETL and ZETA

**Choice:** The base class calls `parseSource(config)` independently before each transformation.

**Alternatives considered:**
- *Single `S source` passed to both* — Risky. EMF transformations can mutate the input model in-place.

**Rationale:** All existing discovery tests already parse fresh source models for ETL and ZETA separately. This is the safe pattern.

### D4: `PerformanceMeasurement` as standalone utility class

**Choice:** Separate utility with `TimedResult<T>` record and `measure()`/`measureAvg()` static methods.

**Alternatives considered:**
- *Bake timing into the base class* — Couples timing to dual comparison. Timing is useful for single-engine benchmarks too.

**Rationale:** Small, focused utility (~40 LOC). Can be used independently.

### D5: Use `ExternalModelConfig.parameters` for per-model flags

**Choice:** `behaviours`, `companions`, `warmup`, `iterations` are read from `ExternalModelConfig.parameters` map by subclasses.

**Alternatives considered:**
- *New `DualComparisonConfig` POJO* — Unnecessary. `ExternalModelConfig` already parses `key=value` pairs from properties.

**Rationale:** Zero new config classes. Properties format: `modelName=path;behaviours=true;iterations=3` already works.

### D6: `assertDualEquivalent` for inline tests requires `ExternalModelConfig`-free path

**Choice:** Provide `assertDualEquivalent(S source, String testName)` that internally creates a temporary `ExternalModelConfig` and calls `parseSource` indirectly — but for inline tests, the source is already parsed, so it accepts `S` directly and calls `executeEtl(S)` / `executeZeta(S)`.

**Problem:** The base class must call `parseSource()` twice (D3), but for inline tests the source is a pre-built in-memory model, not file-based.

**Resolution:** `assertDualEquivalent(S, String)` is a separate code path that:
1. Calls `executeEtl(source)` directly (source is already parsed, assumed not mutated by caller)
2. Calls `executeZeta(source)` directly
3. Compares and fails if not equivalent

If source mutation is a concern, inline tests can pass a factory: `assertDualEquivalent(() -> buildModel(), name)` using a `Supplier<S>` overload.

## Risks / Trade-offs

**[Risk] `AbstractExternalModelTest` API may change** → Mitigation: The base class is in the same repo (`judo-tatami-test-utils`). Any API changes affect both old and new subclasses equally. Low risk.

**[Risk] Generic type erasure may complicate ResourceSet access** → Mitigation: `getResource(T)` is the bridge — subclass knows the concrete type and can access ResourceSet directly. No erasure issue.

**[Risk] Inline tests passing `S source` to both ETL and ZETA may hit mutation** → Mitigation: Provide `Supplier<S>` overload for `assertDualEquivalent`. Document that simple overload assumes source is not mutated.

**[Trade-off] Discovery tests must still implement `resolveJslFiles()` locally** → This is JSL-specific (companion file resolution). ~15 LOC per subclass. Acceptable — it's domain logic, not infrastructure.

## Open Questions

None — the design is straightforward given the existing infrastructure.
