# AGENTS.md — `judo-tatami-psm2measure/src/main/java/hu/blackbelt/judo/tatami/psm2measure/zeta`

Zeta-based declarative transformation engine and rule naming constants for PSM to Measure transformations.

| File | Purpose |
| --- | --- |
| `CLAUDE.md` | Persistent Claude memory scratchpad for recent activity tracking in this package. |
| `Psm2MeasureRuleNames.java` | String constants identifying transformation rule names matching ETL ID suffixes for XMI ID compatibility. Defines identifiers `BASE_MEASURE`, `DERIVED_MEASURE`, `BASE_MEASURE_TERM`, `UNIT`, and `DURATION_UNIT`. |
| `Psm2MeasureZetaTransformation.java` | Orchestrates Zeta declarative rule-based transformation from `PsmModel` to `MeasureModel`. Configures `TransformationRegistry` with `MeasureRules` and `UnitRules`, sets up `ModelProvider` adapters, and invokes `TransformationExecutor.execute()`. |
