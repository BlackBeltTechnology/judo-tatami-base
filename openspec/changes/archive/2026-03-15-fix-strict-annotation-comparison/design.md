## Context

`ModelComparator` lives in `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/ModelComparator.java`.

Key current state:
- `DEFAULT_MODE = ComparisonMode.STRUCTURAL` (line 73)
- `ComparisonMode` enum: `STRICT`, `STRUCTURAL`, `LENIENT`
- Lines 652–654: STRUCTURAL and LENIENT skip `eAnnotations` lists entirely before even checking size
- Lines 677–680: STRICT delegates to `compareAnnotationSets()` — compares annotations as a set keyed by a "signature" string
- Lines 684–687: `EStringToStringMapEntry` (annotation details) already uses `compareAnnotationDetails()`, which is already order-independent (multimap comparison)
- `getConfiguredMode()` returns `DEFAULT_MODE` when no system property is set

Callers of the mode enum by name:
- `AbstractDualTransformationTest` — calls `ModelComparator.getConfiguredMode()`
- `AbstractExternalModelTest` — calls `ModelComparator.getConfiguredMode()`
- Discovery comparison tests in psm2asm, asm2rdbms, rdbms2liquibase, psm2measure, asm2keycloak — use `ModelComparator.assertEquivalent` without an explicit mode (so they inherit `DEFAULT_MODE`)
- `Psm2AsmDualTransformationTest.testEtlAndZetaProduceEquivalentModelsStrict` — disabled; explicitly passes `ComparisonMode.STRICT`

## Goals / Non-Goals

**Goals**
- Every model attribute, reference, and annotation is validated by default
- Annotation comparison is immune to execution-order differences between ETL and Zeta
- Enum surface is honest: two modes with clear semantics, no dead weight

**Non-Goals**
- Changing annotation comparison logic for non-EAnnotation collections
- Changing `compareAnnotationDetails` (already order-independent)
- Touching transformation logic (ETL or Zeta)

## Decisions

### 1. Remove `LENIENT`

`LENIENT` only adds skipping of `documentation`/`comment`/`description` features on top of `STRUCTURAL`. No existing test passes `LENIENT` explicitly. Remove the enum variant and the `LENIENT` branches.

### 2. Rename `STRUCTURAL` → `SKELETON`

`STRUCTURAL` is misleading — it doesn't compare the full structure, it skips annotations. `SKELETON` is honest: "structure only, no annotations."
All references to `ComparisonMode.STRUCTURAL` (system property value `"STRUCTURAL"`) become `SKELETON`. This is a breaking rename. Callers using the system property `-Djudo.test.comparison.mode=STRUCTURAL` must switch to `SKELETON`.

### 3. Fix `STRICT` annotation comparison to be order-insensitive

Current `compareAnnotationSets()` already builds signatures and uses set matching. The bug is that signature building uses `getAnnotationSignatureFromEObject()` which serialises the `details` EList in iteration order — so two annotations with the same key-value pairs in different orders produce different signatures.

Fix: when building a signature for an `EAnnotation`, sort `details` entries by `key` before serialising. This makes the signature deterministic regardless of insertion order.

No other change to `compareAnnotationSets` is needed.

### 4. Make `STRICT` the default

Change `DEFAULT_MODE = ComparisonMode.STRICT`.
`getConfiguredMode()` already falls through to `DEFAULT_MODE`, so all call sites that rely on the default automatically switch.

### 5. Re-enable `testEtlAndZetaProduceEquivalentModelsStrict`

Remove the `@Disabled` annotation. With order-insensitive annotation comparison this test should now pass, since the only known annotation ordering difference between ETL and Zeta is cosmetic.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Callers using `STRUCTURAL` or `LENIENT` string constants in test config will get `IllegalArgumentException` at runtime | Documented as breaking; search confirms no such uses in this repo |
| Zeta implementation still has unknown annotation content differences that STRICT will now expose | Desired — this is the point of the change; failures should be fixed |
| `compareAnnotationSets` signature fix could theoretically mask two annotations with swapped details | Impossible: `details` is a map from key→value with unique keys per annotation |
