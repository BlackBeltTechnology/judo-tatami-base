## Why

Zeta transformation rules produce XMI IDs with different suffixes than their ETL counterparts (e.g., Zeta uses `/BoundAnnotationForBoundTransferOperation` where ETL uses `/BoundOperationAnnotation`). The comparison framework compensates with flexible matching (substring containment, rule mapping tables, "Create" prefix stripping), which masks real differences and makes the tests unreliable. Switching to exact XMI ID matching requires Zeta rules to produce identical ID suffixes to ETL.

## What Changes

- **Add `createTarget(Class<T>, EObject source, String suffix)` to `TransformationContext` in judo-zeta** — constructs `"{sourcePath}/{suffix}"` using the existing `getSourcePath()` infrastructure. No `sourcePrefix` field needed; the alias is already resolved from `registerResource()`.
- **Add `buildSourceBasedId(EObject source, String suffix)` to `TransformationContext`** — public method for inline elements that use `ctx.setElementId()` pattern.
- **Migrate all psm2asm Zeta rules (~103 rules)** to use the new `createTarget(type, source, suffix)` API and fix ~27 suffix mismatches (operations split rules, shortened annotation names).
- **Migrate all asm2rdbms Zeta rules (~21 rules)** to use the new API. Suffixes already match ETL; this is a pure API migration.
- **Switch ModelComparator to exact XMI ID matching** — remove `isRuleNameMatch()` flexible matching, `ETL_TO_ZETA_RULE_MAPPINGS`, `ZETA_TO_ETL_RULE_MAPPINGS`, and `normalizeRuleName()`.
- **Keep `setUuid()` calls** alongside `createTarget` for model-level field parity with ETL.

## Capabilities

### New Capabilities
- `exact-xmiid-api`: New `TransformationContext` methods for source-based ID construction (`createTarget` 3-arg overload, `buildSourceBasedId`)

### Modified Capabilities
- `zeta-parity`: Zeta rules must produce XMI IDs with exact ETL-matching suffixes
- `model-comparator`: Remove flexible rule-name matching; switch to exact XMI ID comparison

## Impact

- **judo-zeta (TransformationContext)**: 2 new public methods. No breaking changes; existing `createTarget(Class, String)` remains.
- **psm2asm rules (8 files)**: All `createTarget` and `setElementId` calls updated. ~27 suffix string changes.
- **asm2rdbms rules (4 files)**: All `createTarget` calls updated. No suffix changes needed.
- **ModelComparator**: ~150 lines of flexible matching code removed.
- **Psm2AsmHelper / Asm2RdbmsHelper**: `getId()` no longer used for ID construction (may be removable if unused elsewhere).
