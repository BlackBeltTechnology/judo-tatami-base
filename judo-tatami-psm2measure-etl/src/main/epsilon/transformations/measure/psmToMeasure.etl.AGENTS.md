# `psmToMeasure.etl`

Root ETL module for PSM→measure. Reads `JUDOPSM!Measure` / `Unit` / `DerivedMeasure` and emits the `MEASURES!` model. Declares no rules — imports `modules/measure.etl` and `modules/unit.etl`.

## `post` block (determinism contract)

- Sorts `MEASURES!Measure.all` by name and replaces the resource contents with the sorted list; sorts each measure's `units` by name; sorts each `MEASURES!DerivedMeasure.terms` by `baseMeasure.name`.
- Sorting is required for reproducible model output — dropping it makes generated measure models differ run to run.
- Resource reorder is guarded by `if (measures.notEmpty())`: it reads `measures.first.eResource` to reach the target resource. Unguarded (upstream commit 337ce41) a PSM without measures threw `NoSuchElementException`; found via JSL loader in ETL mode. Empty input now yields an empty measure model.
- Guard regression: `Psm2MeasureEtlNoMeasuresTest`.
- `getContents().clear()` also drops any non-`Measure` root the rules put in the resource.

See change: split-etl-artifacts.
