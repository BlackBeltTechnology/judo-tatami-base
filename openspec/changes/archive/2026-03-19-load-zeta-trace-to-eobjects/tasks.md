# Tasks

## Phase 1: Write failing round-trip tests (TDD — RED)

- [x] `Psm2AsmTransformationTrace` round-trip test
- [x] `Psm2MeasureTransformationTrace` round-trip test
- [x] `Asm2KeycloakTransformationTrace` round-trip test
- [x] `Asm2RdbmsTransformationTrace` round-trip test
- [x] Run tests and confirm all 4 fail

## Phase 2: Implement JSON loading in `fromModelsAndTrace()` (TDD — GREEN)

- [x] `Psm2AsmTransformationTrace.fromModelsAndTrace()` — use `ZetaTraceLoader` to parse JSON, reconstruct `ElementResolutionCache`, set `zetaTrace` on builder
- [x] `Psm2MeasureTransformationTrace.fromModelsAndTrace()` — same pattern
- [x] `Asm2KeycloakTransformationTrace.fromModelsAndTrace()` — same pattern
- [x] `Asm2RdbmsTransformationTrace.fromModelsAndTrace()` — same pattern
- [x] Fix `save()` writer flush bug (OutputStreamWriter not flushed after saveToJson)
- [x] Fix `ZetaTraceLoader.buildIdIndex()` in judo-tatami-core — add URI fragment fallback (save/load asymmetry bug)
- [x] Run all 4 round-trip tests and confirm they pass

## Phase 3: Refactor (TDD — REFACTOR)

- [x] Review for any duplication or cleanup opportunities across the 4 implementations
- [x] Ensure existing ETL trace tests still pass (292 tests, 0 failures)
- [x] Ensure tatami-core tests still pass (31 tests, 0 failures)

## Additional fixes discovered during implementation

### `save()` writer flush bug
All 4 `*TransformationTrace.save()` methods used `new OutputStreamWriter(outputStream)` without flushing. When writing to a `ByteArrayOutputStream`, the internal buffer was not flushed, causing truncated JSON output. Fixed by storing the writer and calling `writer.flush()` after `saveToJson()`.

### `ZetaTraceLoader.buildIdIndex()` save/load asymmetry (judo-tatami-core)
`getElementId()` (used during save) falls back to URI fragment when no `id` EAttribute exists. But `buildIdIndex()` (used during load) only indexed by `id` EAttribute — never by fragment. Elements without an explicit `id` (e.g., PSM TransferAttribute) got XMI fragment IDs written to JSON but couldn't be resolved during load. Fixed by also indexing by `EcoreUtil.getURI(eObject).fragment()` in `buildIdIndex()`.
