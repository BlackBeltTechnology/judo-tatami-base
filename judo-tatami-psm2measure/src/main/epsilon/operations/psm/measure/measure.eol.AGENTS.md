# `measure.eol`

Dimensional-analysis queries over PSM measures. Consumed by the PSM→measure ETL rules through `../../_importAll.eol`.

## Exported operations

- `JUDOPSM!Unit getMeasure()` — `@cached`; scans `JUDOPSM!Measure.all` for the measure owning the unit.
- `JUDOPSM!Measure getBaseUnit()` — `@cached`; the unit whose `rateDividend = rateDivisor`; a measure with no such unit returns undefined, not an error.
- `JUDOPSM!Measure getBaseMeasures()` — `@cached`; returns `Map{baseMeasure = exponent}`, recursing through `DerivedMeasure.terms` and multiplying by `term.exponent`; zero exponents are removed so the map stays canonical; base measures return `Map{self = 1}`.
- `getDimensions()` — `@cached`; global map from base-measure signature to measure; two measures sharing a dimension signature collide, the later one wins.
- `JUDOPSM!DerivedMeasure hasCircularReference()` plus its `(visited : Collection)` overload — depth-first over `terms.collect(t | t.unit.getMeasure())`, returns true when a measure reappears in `visited`.
- `JUDOPSM!DurationUnit getRates(durationType)` — returns a `Sequence` of `Map{'unitName','divisor','dividend'}` cross-rating `self` against every `DurationUnit` of the given `unitType`; cross-multiplies `rateDividend`/`rateDivisor`, so both must be non-zero.

## Contracts

- `getBaseMeasures()` recurses without a visited set — calling it on a `DerivedMeasure` cycle never terminates, so `hasCircularReference()` must gate it.
- No-arg `hasCircularReference()` is `@cached` while the `(visited)` overload is not, by design: the recursive form's result depends on the caller's path.