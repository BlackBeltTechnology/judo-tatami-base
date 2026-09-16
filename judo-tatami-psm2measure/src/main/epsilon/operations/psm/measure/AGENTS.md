# judo-tatami-psm2measure/src/main/epsilon/operations/psm/measure

EOL query operations over the PSM measure model (`JUDOPSM!Measure`, `Unit`, `DerivedMeasure`, `DurationUnit`), consumed by the PSM→measure ETL rules. Leaf of the `../../_importAll.eol` import chain.

| File | Purpose |
|---|---|
| `_importMeasure.eol` | Measure level of the import chain. Imports `measure.eol` only. Keeps the chain uniform so ETL modules never import a leaf `.eol` directly. |
| `measure.eol` | Dimensional-analysis queries over PSM measures: `@cached` `JUDOPSM!Unit getMeasure()`, `getBaseUnit()`, `getBaseMeasures()` (`Map{baseMeasure = exponent}` recursion through `DerivedMeasure.terms`), `getDimensions()`, `DerivedMeasure hasCircularReference()`, `DurationUnit getRates(durationType)`. → see `measure.eol.AGENTS.md` |
