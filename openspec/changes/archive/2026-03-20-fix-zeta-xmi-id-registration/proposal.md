## Why

In all Zeta `asm2rdbms` transformation rules, `t.setUuid(...)` sets a plain EMF attribute but does **not** register the value with `XMLResource.setID()`. The `TransformationContext` manages XMI IDs in `pendingXmiIds` using the auto-generated rule-name ID from `createTarget()`, so the `uuid` field and the actual XMI resource ID are completely decoupled. This causes `assertXmiIdsEquivalent()` to report every RDBMS element as missing from the Zeta output when the `--xmiids` comparison flag is used.

## What Changes

- Replace all `t.setUuid("...")` calls in Zeta `asm2rdbms` rule classes with `ctx.createTarget(Type.class, id)` (ID set at creation time) so the XMI ID flows correctly through `pendingXmiIds` → `XMLResource.setID()`
- Fix `eAttributeToTableValueField()` to use `/TableValueField` as its ID suffix (not inherit `/RdbmsField` from the parent abstract rule) — the existing comment "UUID already set by parent rule - don't override it" is incorrect
- Fix `eAttributeToIndex()` similarly to use `ctx.createTarget(..., id)` pattern
- Fix all rules in `ClassRules.java`, `ReferenceRules.java`, `PackageRules.java` that use `setUuid()`
- Remove dead `// Set XMI ID` placeholder comment in `ClassRules.eClassToRdbmsTable()`

## Capabilities

### New Capabilities

- `zeta-xmi-id-registration`: Correct XMI ID registration pattern for Zeta transformation rules — `createTarget(Type, id)` ensures IDs flow through `TransformationContext.pendingXmiIds` into `XMLResource.setID()` at commit time

### Modified Capabilities

- `asm2rdbms-zeta-rules`: The XMI ID contract changes — every rule MUST set its element's ID at `createTarget()` time (not via `setUuid()` after the fact), and extending rules MUST override the parent ID suffix

## Impact

- `judo-tatami-asm2rdbms/src/main/java/.../zeta/rules/ClassRules.java` — 6+ rules
- `judo-tatami-asm2rdbms/src/main/java/.../zeta/rules/AttributeRules.java` — 3 rules
- `judo-tatami-asm2rdbms/src/main/java/.../zeta/rules/ReferenceRules.java` — multiple rules
- `judo-tatami-asm2rdbms/src/main/java/.../zeta/rules/PackageRules.java` — if `setUuid()` is used
- All `Asm2RdbmsInheritanceTest` and `Asm2RdbmsTest` assertions should continue to pass (structural model unchanged)
- `assertXmiIdsEquivalent()` comparison will now pass for `asm2rdbms`
