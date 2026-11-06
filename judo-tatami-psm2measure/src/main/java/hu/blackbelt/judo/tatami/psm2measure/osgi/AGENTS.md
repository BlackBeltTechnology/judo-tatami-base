# AGENTS.md — `judo-tatami-psm2measure/src/main/java/hu/blackbelt/judo/tatami/psm2measure/osgi`

OSGi declarative services and trackers managing runtime lifecycle of PSM to Measure transformations.

| File | Purpose |
| --- | --- |
| `Psm2MeasureTransformationPsmModelTracker.java` | OSGi Declarative Service component extending `AbstractModelTracker<PsmModel>` to track `PsmModel` services. Automatically triggers transformation via `Psm2MeasureTransformationService`, registering resulting `MeasureModel` instances into the OSGi service registry. |
| `Psm2MeasureTransformationService.java` | DS component; `install(PsmModel)` runs `Psm2MeasureWork` (ZETA default; ETL needs `-etl` bundle), generates `MeasureModel`, registers its trace; `uninstall(PsmModel)` tears registrations down. No direct Epsilon use. See change: split-etl-artifacts. |
