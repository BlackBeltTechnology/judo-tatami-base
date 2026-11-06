## Why

The `psm2asm` Zeta transformation creates 73 target elements using `ctx.createTarget(Type.class)` without an explicit XMI ID, causing those elements to receive auto-generated structured IDs (e.g., `(psm/...)/CreateMappedTransferObjectTypeClass`) that do not match the ETL-produced IDs (e.g., `(psm/...)/MappedTransferObjectTypeClass`). This means XMI ID comparison (`assertXmiIdsEquivalent`) cannot be enabled for `psm2asm` without first fixing all affected call sites.

## What Changes

- Fix 73 `createTarget(Type.class)` call sites in 6 rule files to pass an explicit ID as the second argument, matching the suffix used by the corresponding ETL rule's `t.setId(...)` call.
- Add `assertXmiIdsEquivalent` assertion to `Psm2AsmDualTransformationTest` once all IDs are correct.
- No behavior change — purely XMI ID registration correctness.

Affected files:
- `OperationRules.java` — 38 sites
- `TransferObjectRules.java` — 24 sites
- `DerivedRules.java` — 6 sites
- `ActorRules.java` — 3 sites
- `NamespaceRules.java` — 1 site
- `DataRules.java` — 1 site

## Capabilities

### New Capabilities
- `psm2asm-xmi-id-correctness`: All psm2asm Zeta target elements register deterministic XMI IDs matching the ETL output, enabling XMI ID equivalence assertions.

### Modified Capabilities

## Impact

- `judo-tatami-psm2asm` rule classes (6 files, 73 call sites)
- `Psm2AsmDualTransformationTest` — gains `assertXmiIdsEquivalent` call
- No API changes, no model behavior changes, no breaking changes
