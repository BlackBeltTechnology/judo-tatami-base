# AGENTS.md — `judo-tatami-psm2measure/src/main/java/hu/blackbelt/judo/tatami/psm2measure/zeta/rules`

Zeta transformation rule implementations translating PSM measures and units to Measure metamodel instances.

| File | Purpose |
| --- | --- |
| `MeasureRules.java` | Zeta transformation rules mapping `hu.blackbelt.judo.meta.psm.measure.Measure` to `hu.blackbelt.judo.meta.measure.Measure`. Exports guard `isNotDerivedMeasure()`, rule `createBaseMeasure()` producing `BaseMeasure`, and rule `createDerivedMeasure()` producing `DerivedMeasure` with linked `BaseMeasureTerm` elements. |
| `UnitRules.java` | Zeta transformation rules mapping `hu.blackbelt.judo.meta.psm.measure.Unit` to `hu.blackbelt.judo.meta.measure.Unit`. Exports guard `isNotDurationUnit()`, rule `createUnit()` producing `Unit` with `BigDecimal` conversion rates, and rule `createDurationUnit()` producing `DurationUnit` with mapped `DurationType` enums. |
