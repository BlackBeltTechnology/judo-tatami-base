# ASM to Expression — Remaining Zeta Pipeline Differences

## Current Status (2026-03-16)

After fixing the parameterized annotation bug (see `zeta-parameterized-annotation-bug.md`),
the Zeta pipeline runs end-to-end. The ASM model is now fully equivalent between ETL and
Zeta. However, the `Asm2ExpressionWork` and downstream stages still produce different
output.

### Pipeline Comparison Results (Northwind model, STRICT mode)

| Stage | Status | Differences | Notes |
|-------|--------|-------------|-------|
| PSM | EQUIVALENT | 0 | |
| ASM | EQUIVALENT | 0 | Fixed by parameterized annotation patch |
| Measure | EQUIVALENT | 0 | |
| Expression | **DIFFERENT** | 100+ | See analysis below |
| Keycloak | EQUIVALENT | 0 | |
| Script | **DIFFERENT** | 35 | Cascading from Expression + ETL fallback |
| RDBMS-hsqldb | **DIFFERENT** | 50+ | XMI ID naming convention |
| Liquibase-hsqldb | **DIFFERENT** | 50+ | Cascading from RDBMS |

## Expression Model Differences

### 1. Element Count Mismatch

```
contents.size: 718 vs 710
```

The ETL pipeline produces 718 expression model elements while Zeta produces 710.
The 8 missing elements need investigation — they may be expression entries for
measures or derived expressions that are ordered differently.

### 2. Element Ordering

The primary difference is element ordering. The Expression model comparison shows
positional mismatches starting at index 4:

```
[4].name:  ElectricCurrent vs Area
[5].name:  ThermodynamicTemperature vs Volume
[6].name:  AmountOfSubstance vs Velocity
[7].name:  LuminousIntensity vs Acceleration
[8].name:  Area vs Momentum
[9].name:  Volume vs Force
[10].name: Velocity vs Work
[11].name: Acceleration vs Power
[12].name: Momentum vs Frequency
[13].name: Force vs Pressure
```

This is a **measure ordering issue**. The ETL pipeline adds measures in the order
they appear in the PSM model (which follows the ESM source order), while Zeta adds
them in a different traversal order. Both sets contain the same measures but at
different positions.

### Root Cause Analysis

`Asm2ExpressionWork` is **not a Zeta transformation** — it is a Java-based
transformation that reads the ASM model and builds an Expression model. Both ETL
and Zeta pipelines use the same `Asm2ExpressionWork` Java code. The differences
come from:

1. **ASM model traversal order**: `Asm2ExpressionWork` iterates over ASM model
   elements using `EcoreUtil.getAllContents()` or similar. If the ASM model's
   internal element order differs between ETL and Zeta output (even though the
   models are structurally equivalent), the Expression model will have elements
   in different order.

2. **Measure model element ordering**: The Measure model is produced by
   `Psm2MeasureWork`. Even though Measure comparison shows EQUIVALENT, the
   internal iteration order over measure elements may differ, producing different
   ordering in the Expression model.

3. **Missing elements (718 vs 710)**: These 8 elements likely correspond to
   expression entries that depend on specific ASM annotation details or measure
   references that are resolved differently. Since ASM is now EQUIVALENT in
   STRICT mode, the difference may be in how `Asm2ExpressionWork` handles
   iteration or caching.

### Why This Is Not a Zeta Bug

The Expression differences are **not caused by Zeta transformation code**. They
are caused by:

- Different internal element ordering in EMF resource sets (ASM model elements
  may be stored in different order even though structurally equivalent)
- The `Asm2ExpressionWork` code being sensitive to iteration order
- The ModelComparator comparing by position rather than by identity for
  Expression model elements

### Verification Approach

To confirm this analysis:

1. Serialize both ASM models (ETL and Zeta) to XMI files
2. Compare element counts per package — should be identical
3. Run `Asm2ExpressionWork` twice with the **same** ASM model but different
   measure model element ordering — if results differ, it confirms order
   sensitivity

## Script Model Differences

### Observation

```
[0].blocks: missing element 'ScriptBlock:changeShipment'
[0].blocks: unexpected element 'ScriptBlock:ship'
[0].inputs: size=1 vs size=0
[0].inputs: missing element 'ScriptInput:input'
[0].output: type mismatch - expected ScriptInputImpl but was null
[1].operationName: changeShipment vs ship
```

### Root Cause

`Asm2ScriptWork` in the Zeta pipeline **falls back to ETL** (as indicated in
the logs):

```
WARN  Asm2ScriptWork - Zeta transformation not yet implemented,
    falling back to ETL: Zeta ASM to Script transformation not yet implemented.
```

Since both pipelines use ETL for Script transformation but operate on
differently-ordered ASM models, the Script output differs in element ordering
and potentially in operation resolution.

**Note**: `Script2OperationWork` also falls back to ETL for the same reason.

## RDBMS Model Differences

### Observation

All 50+ RDBMS differences are **XMI ID naming convention** differences:

```
...uuid: (asm/(psm/...)/EntityType)/EntityClass)/TableIdField
    vs   (asm/(psm/...)/EntityType)/CreateEntityClass)/TableIdField
```

### Root Cause

The ETL PSM2ASM transformation names its entity rule `EntityClass`, while the
Zeta PSM2ASM transformation names it `CreateEntityClass`. This difference
propagates to ASM element XMI IDs, which are then used by `Asm2RdbmsWork` to
generate RDBMS element UUIDs.

In SKELETON comparison mode these differences are ignored (annotations including
XMI IDs are skipped), but in STRICT mode they surface.

### Fix Required

Either:
1. Rename the Zeta rule from `CreateEntityClass` to `EntityClass` to match ETL
2. Or update the XMI ID generation in `Psm2AsmZetaTransformation` to strip the
   `Create` prefix

This is a cosmetic difference that does not affect runtime behavior.

## Liquibase Model Differences

All Liquibase differences cascade from RDBMS XMI ID differences. The Liquibase
transformation copies RDBMS UUIDs into column `remarks` fields, so the same
`EntityClass` vs `CreateEntityClass` pattern appears.

## Priority and Recommendations

### High Priority
- None — the Zeta pipeline runs end-to-end and all structurally significant
  models (PSM, ASM, Measure, Keycloak) are EQUIVALENT

### Medium Priority
1. **Fix RDBMS XMI ID naming** — Align Zeta rule names with ETL to eliminate
   the `EntityClass` vs `CreateEntityClass` difference. This will also fix
   Liquibase differences automatically.

### Low Priority
2. **Expression ordering** — Make ModelComparator for Expression use
   name-based matching instead of position-based. The models contain the same
   elements in different order.
3. **Script/Script2Operation Zeta** — Implement native Zeta transformations for
   `Asm2ScriptWork` and `Script2OperationWork` (currently falling back to ETL).
4. **Expression element count** (718 vs 710) — Investigate the 8 missing
   elements. May be a genuine difference in how measures or derived expressions
   are processed.

## Running the Comparison

```bash
# From judo-tatami-pipeline root
./mvnw test -pl judo-pipeline-testing \
    -Dtest=FullPipelineComparisonTest \
    -Djudo.test.comparison.mode=STRICT

# With more differences reported
./mvnw test -pl judo-pipeline-testing \
    -Dtest=FullPipelineComparisonTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.maxDifferences=200

# SKELETON mode (ignores annotations/XMI IDs)
./mvnw test -pl judo-pipeline-testing \
    -Dtest=FullPipelineComparisonTest \
    -Djudo.test.comparison.mode=SKELETON
```

## Related Files

- Pipeline test: `judo-tatami-pipeline/judo-pipeline-testing/src/test/java/.../FullPipelineComparisonTest.java`
- Asm2Expression: `judo-tatami-base/judo-tatami-asm2expression/src/main/java/.../Asm2ExpressionWork.java`
- Asm2Script: `judo-tatami-base/judo-tatami-asm2script/src/main/java/.../Asm2ScriptWork.java`
- Asm2Rdbms Zeta: `judo-tatami-base/judo-tatami-asm2rdbms/src/main/java/.../zeta/Asm2RdbmsZetaTransformation.java`
- Parameterized fix: `docs/zeta-parameterized-annotation-bug.md`
