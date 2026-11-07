## Context

Zeta transformation rules construct XMI IDs manually with hardcoded prefix strings (e.g., `"(psm/" + getId(s) + ")/EntityClass"`). The ID suffix often differs from the ETL rule name because Zeta rules were named differently (e.g., `CreateActorType` vs ETL's `ActorType`). The `ModelComparator` compensates with ~150 lines of flexible matching logic (`isRuleNameMatch`, `ETL_TO_ZETA_RULE_MAPPINGS`, substring containment). This hides real differences and makes tests unreliable.

The `TransformationContext` already knows the source resource alias (set via `registerResource("psm", ...)`) and builds source paths via the private `getSourcePath(EObject)` method. The infrastructure for clean ID construction exists but isn't exposed.

## Goals / Non-Goals

**Goals:**
- Zeta rules produce XMI IDs byte-identical to ETL for the same source elements
- ModelComparator uses exact string matching for XMI IDs
- ID construction uses framework API instead of manual string concatenation
- All psm2asm and asm2rdbms Zeta rules migrated to new API

**Non-Goals:**
- Changing ETL rule names or ETL ID generation
- Migrating other transformation modules (psm2measure, asm2keycloak, rdbms2liquibase) — can be done later
- Removing `getId()` helper methods entirely — they may still be used for non-ID purposes

## Decisions

### Decision 1: Add `createTarget(Class<T>, EObject, String)` and `buildSourceBasedId(EObject, String)` to TransformationContext

The `TransformationContext` gets two new public methods:

```java
public <T extends EObject> T createTarget(Class<T> type, EObject source, String suffix) {
    String customId = getSourcePath(source) + "/" + suffix;
    return createTarget(type, customId);
}

public String buildSourceBasedId(EObject source, String suffix) {
    return getSourcePath(source) + "/" + suffix;
}
```

**Why over alternatives:**
- `getSourcePath()` already resolves the alias from `registerResource()` — no need for a `sourcePrefix` field
- The existing `createTarget(Class, String)` handles all the internal ID registration
- `buildSourceBasedId` enables inline elements that use `ctx.setElementId()` to follow the same pattern

### Decision 2: Zeta rules use ETL rule names as suffixes

Every Zeta rule passes the **ETL rule name** (not the Zeta rule name) as the suffix. Both BTO/UO split variants use the original ETL suffix since source element IDs differ and keep full IDs unique.

Examples of suffix changes:
- `BoundAnnotationForBoundTransferOperation` → `BoundOperationAnnotation`
- `ImmutableFlagForBoundTransferOperation` → `ImmutableAnnotationOnOperation`
- `QueryCustomizerAnnotation` → `QueryCustomizerAnnotationForQueryCustomizerClass`
- `StatefulAnnotationOnBoundTransferOperation` → `StatefulAnnotationOnOperation`

### Decision 3: Keep `setUuid()` calls for field parity

RDBMS model elements have a `uuid` field that ETL sets to the same value as the XMI ID. Zeta rules keep calling `t.setUuid(id)` after `createTarget` to maintain this parity.

### Decision 4: Remove flexible matching from ModelComparator

Delete:
- `ETL_TO_ZETA_RULE_MAPPINGS` map (~50 entries)
- `ZETA_TO_ETL_RULE_MAPPINGS` computed map
- `normalizeRuleName()` method
- `isRuleNameMatch()` method with substring/mapping logic

Replace with direct `String.equals()` comparison of XMI IDs.

## Risks / Trade-offs

- **[Risk] Suffix typo causes silent test failure** → Each suffix is a string literal. A typo would cause a test failure that's easy to diagnose from the comparison output showing mismatched IDs.
- **[Risk] judo-zeta release dependency** → The new `TransformationContext` methods require a judo-zeta release before tatami-base can use them. → Mitigation: judo-zeta is at 1.0.0-SNAPSHOT, changes can be made and installed locally.
- **[Trade-off] Inline elements still use manual `ctx.setElementId()`** → `buildSourceBasedId` only constructs the ID string; the caller still calls `setElementId`. This is acceptable because inline elements are created by EMF factory, not `createTarget`.
