# judo-tatami-psm2measure-etl/src/test/java/hu/blackbelt/judo/tatami/psm2measure

PSM→MEASURE ETL vs Zeta equivalence test (moved from `judo-tatami-psm2measure`). ETL path imports `hu.blackbelt.judo.tatami.psm2measure.etl.Psm2Measure`.

| File | Purpose |
|---|---|
| `Psm2MeasureEtlNoMeasuresTest.java` | PSM with only a Model root: `Psm2MeasureEtlEngineImpl.execute` succeeds, measure model empty. Fails without the `measures.notEmpty()` guard in `psmToMeasure.etl`. See change: split-etl-artifacts. |
| `Psm2MeasureTest.java` | Tests PSM-to-MEASURE transformation parameterized over `TransformationMode` using Northwind demo model; validates ETL and Zeta output equivalence via `ModelComparator`. |

