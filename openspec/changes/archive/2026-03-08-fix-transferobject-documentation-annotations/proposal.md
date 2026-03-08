## Why

The Zeta PSM2ASM transformation is missing documentation annotation rules for TransferObjectType, TransferAttribute, and TransferObjectRelation. This causes STRICT comparison failures on models that have documentation on these types (e.g., rackinspect's `ModifyStandardMinuteInput`). The ETL rules exist but were never ported to Zeta.

## What Changes

- Add 3 missing documentation annotation rules to `TransferObjectRules.java`:
  - `createDocumentationAnnotationForTransferObjectType`
  - `createDocumentationAnnotationForTransferAttribute`
  - `createDocumentationAnnotationForTransferObjectRelation`

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `TransferObjectRules.java` - 3 new rule methods added
- Fixes rackinspect PSM2ASM STRICT comparison failure (2 differences → 0)
