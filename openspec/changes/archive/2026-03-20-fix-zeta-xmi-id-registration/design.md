## Context

The `TransformationContext` in `judo-zeta` manages XMI IDs for target elements via a `pendingXmiIds` map. When `createTarget(Type.class)` is called, it auto-generates a structured ID (using `buildStructuredId(source, ruleName)`) and stores it in `pendingXmiIds`. At commit time, `commitStagedElements()` applies all pending IDs to the `XMLResource` via `resource.setID(element, id)`.

The current Zeta `asm2rdbms` rules call `t.setUuid("(asm/.../...)/Table")` **after** `createTarget()`. The `setUuid()` method only calls `eDynamicSet()` — it does NOT call `TransformationContext.setElementIdInternal()` and therefore does NOT update `pendingXmiIds`. The `uuid` field and the XMI resource ID used by `ModelComparator.getXmiId()` (which reads `XMLResource.getID()`) are completely decoupled.

There are **19 affected `setUuid()` calls** across 3 rule files:
- `ClassRules.java`: 10 calls (Table, TableIdField, TableTypeField, and 7 audit fields)
- `AttributeRules.java`: 2 calls (RdbmsField in abstract rule, Index)
- `ReferenceRules.java`: 7 calls (FKs, junction tables, PKs)

Additionally, `eAttributeToTableValueField()` must produce XMI ID `/TableValueField` but currently inherits `/RdbmsField` from the abstract parent — the comment "UUID already set by parent rule - don't override it" is wrong.

## Goals / Non-Goals

**Goals:**
- All 19 `setUuid()` calls replaced with the correct pattern so XMI IDs flow through `pendingXmiIds` → `XMLResource.setID()`
- `eAttributeToTableValueField()` registers `/TableValueField` as its element's XMI ID
- `assertXmiIdsEquivalent(etlResource, zetaResource)` passes for `asm2rdbms`
- Structural model output remains identical (no behavioral change to the transformation)

**Non-Goals:**
- Changing the ID format or content (they stay as `(asm/<sourceId>)/<suffix>`)
- Fixing XMI IDs in other Zeta transformation modules (psm2asm, asm2keycloak, etc.)
- Changing the `uuid` EAttribute semantics in the RDBMS metamodel

## Decisions

### Decision 1: Use `ctx.createTarget(Type.class, id)` instead of separate `setUuid()`

**Chosen:** Replace the two-step pattern:
```java
T t = ctx.createTarget(T.class);
t.setUuid("(asm/" + ctx.getElementId(s) + ")/Suffix");
```
with the one-step pattern:
```java
String id = "(asm/" + ctx.getElementId(s) + ")/Suffix";
T t = ctx.createTarget(T.class, id);
```

**Why:** `createTarget(Type, id)` calls `setElementIdInternal(instance, customId)` which correctly updates `pendingXmiIds` and calls `XMLResource.setID()` if the element is already in a resource.

**Alternative considered:** Use `ctx.setElementId(t, id)` after `createTarget()`. This would also work (it calls `setElementIdInternal()`), but `createTarget(Type, id)` is cleaner, sets the ID before any other rule can read it, and avoids the `IllegalStateException` guard in `setElementId()` if another rule reads the auto-generated ID first.

### Decision 2: `eAttributeToTableValueField()` must override the XMI ID after parent rule executes

**Chosen:** After `ctx.executeParentRule(EATTRIBUTE_TO_RDBMS_FIELD, s)` returns the `RdbmsValueField`, explicitly call `ctx.setElementId(t, "(asm/" + ctx.getElementId(s) + ")/TableValueField")`.

**Why:** The parent rule `eAttributeToRdbmsField()` sets XMI ID to `/RdbmsField`. The concrete ETL rule overrides this with `/TableValueField` via `t.setId(...)`. Zeta must match this behaviour. The comment "don't override" was wrong — it conflated `uuid` attribute with XMI resource ID.

**Note:** `ctx.setElementId()` is used here (not `createTarget(Type, id)`) because the element is already created by the parent rule. The guard in `setElementId()` only fires if a *different* rule has read the ID — within the same rule execution that's fine.

### Decision 3: Keep `setUuid()` call alongside `ctx.createTarget(..., id)` for uuid field parity

**Chosen:** Keep `t.setUuid(id)` calls in addition to the `createTarget(Type, id)` pattern, so both the `uuid` model attribute AND the XMLResource ID are populated (matching ETL which sets both via `t.setId()` and `t.uuid = ...`).

**Why:** ETL explicitly sets both `t.setId(...)` (XMI resource ID) and `t.uuid = "..."` (model field). The `uuid` field may be used for other purposes (e.g., trace resolution, debug output). Setting both is safe and preserves full parity.

## Risks / Trade-offs

- **ID collision during `@Extends` execution**: The parent rule `eAttributeToRdbmsField()` will register an ID of `(asm/...)/TableValueField` via `createTarget(RdbmsField.class, id)`, but the extending rule must override it to `(asm/...)/TableValueField` — these are actually the same suffix now. Wait — re-reading: the abstract rule creates with `/RdbmsField`? No — if we change it to `createTarget(RdbmsField.class, "(asm/.../RdbmsField")`, and the concrete rule calls `ctx.setElementId(t, "(asm/.../TableValueField")`, there will be a brief pending ID `/RdbmsField` that gets overridden. The `setElementIdInternal()` handles this: it removes the old entry from `pendingXmiIdIndex` when the ID changes. **→ No risk.**

- **Parallel execution**: All `setSynchronizedXmiId()` calls synchronize on the resource. No change here. **→ No risk.**

- **Test breakage from XMI ID change**: Existing `Asm2RdbmsTest` and `Asm2RdbmsInheritanceTest` do not assert specific XMI IDs — they check structural model content. **→ No risk to existing tests.**

## Migration Plan

1. Update all 19 `setUuid()` sites in order: `ClassRules.java` → `AttributeRules.java` → `ReferenceRules.java`
2. Remove dead `// Set XMI ID` comment placeholder in `ClassRules.eClassToRdbmsTable()`
3. Run `Asm2RdbmsTest` — all structural tests must pass
4. Run `Asm2RdbmsInheritanceTest` — must pass
5. Run discovery comparison with `--xmiids` flag — `assertXmiIdsEquivalent` must pass

## Open Questions

None — root cause is fully traced and fix is unambiguous.
