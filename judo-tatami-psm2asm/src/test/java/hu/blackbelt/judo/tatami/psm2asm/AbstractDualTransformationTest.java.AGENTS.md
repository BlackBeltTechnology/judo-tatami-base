# `AbstractDualTransformationTest.java`

Reusable base for dual-engine (ETL vs Zeta) PSM→ASM transformation tests.
`abstract class AbstractDualTransformationTest`, `@Slf4j`, no JUnit lifecycle
annotations of its own beyond the tests it contributes.

## Abstract hooks a subclass MUST implement

- `protected abstract PsmModel createSourceModel()` — supplies the input model.
- `protected abstract AsmModel runEtlTransformation(PsmModel source)`.
- `protected abstract AsmModel runZetaTransformation(PsmModel source)`.

## Tests it contributes to every subclass

- `@ParameterizedTest(name = "Transformation with {0}")` +
  `@EnumSource(TransformationMode.class)` → `testTransformation(TransformationMode)`:
  builds the source model, runs the engine selected by the mode, calls
  `verifyResult`, and stores the result via `storeTransformationResult`.
- `@Test testDualEquivalence()`: runs both engines and compares.

## Overridable behaviour

`verifyResult(AsmModel, TransformationMode)` (default: result and its
`ResourceSet` non-null), `getComparisonMode()`, `compareModels`,
`compareModelsStructural`, `assertModelsEquivalent(EObject, EObject[, …])`,
`getRootElement`.

## Configuration — system properties

| Property | Effect |
|---|---|
| `judo.test.comparison.enabled` | enable/disable comparison (default `true`) |
| `judo.test.comparison.mode` | `STRICT` or `SKELETON` (default `STRICT`) |
| `judo.test.comparison.maxDifferences` | max differences reported (default `50`) |
| `judo.test.comparison.reportFile` | optional diff-report output file |
| `judo.test.comparison.structural` | checksum-based comparison via `StructuralModelComparator` (default `false`) |
| `judo.test.structural.exportJson` | export model structures to JSON (default `false`) |
| `judo.test.structural.outputDir` | JSON export directory (default `target/comparison`) |

## Contracts

- All three abstract hooks must be implemented; the parameterized test cannot
  run otherwise.
- `compareStoredResults()` requires BOTH engines already stored — the private
  `etlResult`/`zetaResult` fields are populated only by
  `storeTransformationResult(mode, result)`. Calling it after a single-mode run
  compares against a null side.
- Collaborators: `ModelComparator`, `ModelChecksumCalculator`,
  `StructuralModelComparator`, `ComparisonResult`, `ModelNode` from
  `hu.blackbelt.judo.tatami.test.util`; extends nothing but sits beside
  `AbstractExternalModelTest`.
