# judo-tatami-psm2measure/src/main/epsilon/transformations/measure

Epsilon ETL entry point for the PSM→measure lowering. Rule bodies live in `modules/`; EOL queries come from `../../operations/_importAll.eol`.

| File | Purpose |
|---|---|
| `psmToMeasure.etl` | Root ETL module for PSM→measure: reads `JUDOPSM!Measure`/`Unit`/`DerivedMeasure`, emits `MEASURES!`; declares no rules, imports `modules/measure.etl` + `modules/unit.etl`; `post` block sorts `MEASURES!Measure.all`, `units`, `terms` for reproducible output. → see `psmToMeasure.etl.AGENTS.md` |
