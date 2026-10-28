# AGENTS.md — `judo-tatami-psm2measure/src/main/epsilon/transformations/measure/modules`

ETL transformation rules mapping PSM measures and units into MEASURES model elements.

| File | Purpose |
| --- | --- |
| `measure.etl` | Transforms `JUDOPSM!Measure` into `MEASURES!Measure` instances. Defines abstract `CreateMeasure` rule mapping id, namespace, name, and symbol, `CreateBaseMeasure` for non-derived measures, and `CreateDerivedMeasure` generating `BaseMeasureTerm` elements with exponents linked to equivalent base measures. |
| `unit.etl` | Transforms `JUDOPSM!Unit` into `MEASURES!Unit` instances. Defines `CreateUnit` rule mapping conversion rates as `BigDecimal` and appending unit to equivalent measure, and `CreateDurationUnit` mapping temporal units with typed `DurationType` enums and suffixed identifiers. |
