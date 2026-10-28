# `Psm2AsmPerformanceTest.java`

ETL-vs-Zeta timing harness for PSM→ASM over synthetic models generated in code.
`public class Psm2AsmPerformanceTest`, `@Slf4j`, `@Tag("performance")`.
Documented invocation: `mvn test -Pperformance -Dtest=Psm2AsmPerformanceTest`.

## Tests

- `@Test testSmallModelComparison()`
- `@Test testMediumModelComparison()`
- `@ParameterizedTest @ValueSource(ints = {100, 500, 1000}) testVariousModelSizesComparison(int entityCount)`

All three funnel into `runComparisonTest(entityCount, testName)`.

## Measurement protocol

`WARMUP_ITERATIONS = 3`, `MEASUREMENT_ITERATIONS = 5`. `runEtlPerformanceTest`
and `runZetaPerformanceTest` each warm up, then time 5 runs with
`System.nanoTime()` converted by `TimeUnit.NANOSECONDS.toMillis`.
`calculateResult(List<Long>)` folds them into the private
`PerformanceResult(min, max, average, median, stdDev)` record-like class via
`LongSummaryStatistics` plus `calculateStdDev`. `printComparison(...)` logs the
side-by-side table.

## Model generation

`generatePsmModel(entityCount)` builds a `Model` of `EntityType`s with
`ATTRIBUTES_PER_ENTITY = 5` (`StringType`/`NumericType` fields) and
`RELATIONS_PER_ENTITY = 2` `AssociationEnd`s, via `newEntityTypeBuilder`,
`newAttributeBuilder`, `newAssociationEndBuilder`, `newCardinalityBuilder`.
`countModelElements(entityCount)` derives the expected element total from those
same constants.

## The only hard assertion

`compareOutputModels(psmModel)` runs both engines once more and asserts
`assertEquals(etlClassCount, zetaClassCount)` on `EPackage.getEClassifiers().size()`
of the first resource root. Timing is logged only — a slow run never fails the
build; only a classifier-count divergence does.

## Contracts

- `calculateResult` sorts the supplied `times` list in place; do not reuse the
  list for order-sensitive reporting afterwards.
- `compareOutputModels` silently skips its assertion when either
  `ResourceSet.getResources()` is empty, so an engine that produced nothing
  passes. Check the logged classifier counts, not just a green test.
- Casts the first resource root to `EPackage` unchecked — a non-`EPackage` root
  fails with `ClassCastException`, not an assertion message.
