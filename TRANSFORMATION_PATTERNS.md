# Transformation Patterns - ETL vs Zeta

## ETL vs ZETA Differences

### Architectural Post-Processing Steps (Correct by Design)

These post-processing steps in Zeta's `Psm2AsmZetaTransformation.postProcess()` are architecturally necessary because Zeta caches elements at the END of rule execution, while ETL caches at the START. This means cross-element references cannot be resolved during rule execution in Zeta.

| Step | Description | Why ETL doesn't need it | Status |
|------|-------------|-------------------------|--------|
| 1 | Add root packages to resource | ETL adds elements to resource inline during rule execution | Required |
| 2 | Set EOpposite for bidirectional associations | ETL resolves cross-references via `equivalent()` inline because elements are cached at start of rule | Required |
| 3 | Set target types for TransferObjectRelations | Same as step 2 - cross-element reference | Required |
| 4 | Set up inheritance for Reference classes | Same as step 2 - cross-element reference | Required |
| 5 | `enrichWithAnnotations()` (exposedBy, etc.) | ETL also calls this in its post block | Required (shared) |

**Conclusion:** Steps 2, 3, 4 are not bugs or workarounds - they are architecturally correct compensations for Zeta's caching model. They should NOT be removed.

### Eliminated Workarounds

These post-processing steps were workarounds that have been fixed at the root cause level:

| Former Step | Description | Root Cause | Fix Applied |
|-------------|-------------|------------|-------------|
| `applyPendingXmiIds()` | Recursively apply XMI IDs after adding to resource | `addToResource()` only set XMI ID for root element, not children in sequential mode | Framework fix: added `applyPendingIdsRecursively()` call to sequential path in `TransformationContext.addToResource()` |
| `fixNullAttributeTypes()` | Fix null eType on extension transfer attributes (_default_, _binding_) | `ctx.equivalent()` uses object identity; extension types have different Java instances than what TypeRules transformed | Rule-level fix: name-based type resolution fallback in `TransferObjectRules.createTransferAttribute()` |
| `removeDuplicateAnnotations()` | Remove duplicate annotations | Dead code - was never called by either ETL or Zeta | Deleted |

### Framework Changes

**`TransformationContext.addToResource()` (judo-zeta)**
- Sequential mode now calls `applyPendingIdsRecursively()` after adding elements to resource
- Parallel mode already did this in the commit phase
- This eliminates the need for per-module `applyPendingXmiIds()` workarounds

**Affected modules cleaned up:**
- `judo-tatami-psm2asm` - Removed `applyPendingXmiIds()` method and call site
- `judo-tatami-asm2rdbms` - Removed `applyPendingXmiIds()` method and call site

### Rule-Level Fixes

**`TransferObjectRules.createTransferAttribute()` (psm2asm)**
- When `ctx.equivalent(s.getDataType(), EClassifier.class)` returns null, falls back to name-based lookup via `Psm2AsmHelper.resolveTypeByName()`
- Fixes extension types (_default_, _binding_) whose PSM `dataType` references point to different Java object instances

## Equivalence Test Results

All four Zeta-enabled modules produce equivalent models to ETL:

| Module | Test Method | Mode | Result |
|--------|------------|------|--------|
| psm2asm | `Psm2AsmDualTransformationTest#testEtlAndZetaProduceEquivalentModels` | STRUCTURAL | PASS |
| psm2measure | `Psm2MeasureTest#testEtlAndZetaEquivalence` | STRUCTURAL | PASS |
| asm2rdbms | `Asm2RdbmsTest#testEtlAndZetaEquivalence` | STRUCTURAL | PASS |
| asm2keycloak | `Asm2KeycloakTest#testEtlAndZetaEquivalence` | STRUCTURAL | PASS (empty - Demo has no realm actors) |
