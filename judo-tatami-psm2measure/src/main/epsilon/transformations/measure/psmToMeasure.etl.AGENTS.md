# `psmToMeasure.etl`

Root ETL module for PSM→measure. Reads `JUDOPSM!Measure` / `Unit` / `DerivedMeasure` and emits the `MEASURES!` model. Declares no rules — imports `modules/measure.etl` and `modules/unit.etl`.

## `post` block (determinism contract)

- Sorts `MEASURES!Measure.all` by name and replaces the resource contents with the sorted list; sorts each measure's `units` by name; sorts each `MEASURES!DerivedMeasure.terms` by `baseMeasure.name`.
- Sorting is required for reproducible model output — dropping it makes generated measure models differ run to run.
- The block reads `measures.first.eResource` to reach the target resource, so a model with zero measures fails here.
- `getContents().clear()` also drops any non-`Measure` root the rules put in the resource.