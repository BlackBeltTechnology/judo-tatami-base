## Why

The current `STRUCTURAL` comparison mode silently skips all EAnnotations, hiding entire classes of bugs (wrong annotation source URIs, missing detail entries, incorrect values). The `STRICT` mode correctly checks annotations but is disabled because ETL and Zeta produce annotations in different orders — a cosmetic artifact of execution order, not a functional difference. `LENIENT` adds nothing over `STRUCTURAL` and is unused. The result is that no test currently validates annotation content at all.

## What Changes

- **Remove** `ComparisonMode.LENIENT` — unused, redundant with STRUCTURAL
- **Rename** `ComparisonMode.STRUCTURAL` → `ComparisonMode.SKELETON` — honest name for "structure only, no annotations"
- **Fix** `ComparisonMode.STRICT` — make EAnnotation list comparison order-insensitive (compare as sets keyed by `source` URI); annotation `details` entries also compared as unordered maps
- **Make STRICT the default** comparison mode everywhere (system property default, `AbstractDualTransformationTest`, `AbstractExternalModelTest`, discovery comparison tests)
- **Re-enable** the disabled `testEtlAndZetaProduceEquivalentModelsStrict` test in `Psm2AsmDualTransformationTest`

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `model-comparator`: Remove LENIENT, rename STRUCTURAL→SKELETON, fix STRICT to compare EAnnotations as order-insensitive sets, make STRICT the default mode

## Impact

- `judo-tatami-test-utils`: `ModelComparator.java` — mode enum, comparison logic, system property default
- `judo-tatami-psm2asm`: `AbstractDualTransformationTest`, `Psm2AsmDualTransformationTest` — default mode, re-enable STRICT test
- `judo-tatami-psm2asm`, `judo-tatami-asm2rdbms`, `judo-tatami-rdbms2liquibase`, `judo-tatami-psm2measure`, `judo-tatami-asm2keycloak`: discovery comparison tests — default mode switch
- Any caller passing `ComparisonMode.LENIENT` or `ComparisonMode.STRUCTURAL` by name — **BREAKING** rename
