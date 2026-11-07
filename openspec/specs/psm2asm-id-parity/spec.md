# psm2asm-id-parity Specification

## Purpose
Ensures that Psm2AsmHelper.getId() returns values consistent with ETL's XMI fragment IDs, so ASM XMI IDs match between ETL and Zeta transformations.

## Requirements

# PSM2ASM ID Parity Spec

## Behavior

### `Psm2AsmHelper.getId(Object element)` for NamespaceElement

When `element` is a `NamespaceElement` with a non-null `eResource()`:
1. Get the XMI fragment via `element.eResource().getURIFragment(element)`
2. If fragment is non-null and not a positional path (doesn't start with `/`), return it
3. Otherwise fall back to `getQualifiedNameWithUnderscore(element)`

When `element` is a `NamespaceElement` without an `eResource()`:
- Fall back to `getQualifiedNameWithUnderscore(element)` (current behavior preserved)

### Post-condition

For any PSM element loaded from an XMI resource, `Psm2AsmHelper.getId(element)` returns the same value as ETL's `element.eResource.getId(element)`.

### Cascade effect

ASM XMI IDs built as `"(psm/" + getId(s) + ")/RuleName"` will match between ETL and Zeta, which fixes:
- RDBMS `uuid` field differences
- Liquibase `remarks` differences
