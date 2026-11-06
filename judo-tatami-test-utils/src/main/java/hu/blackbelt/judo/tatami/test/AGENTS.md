# AGENTS.md — `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test`

Synthetic metamodel generators producing realistic benchmark models for transformation testing.

| File | Purpose |
| --- | --- |
| `RealisticAsmModelGenerator.java` | Synthesizes realistic Ecore-based `AsmModel` instances mimicking RackInspect ratios (attributes, references, transfer objects, optional actors). Exports `generate(int)`, `generateWithActors(int, int)`, `countClassifiers(AsmModel)`, configuration class `GeneratorConfig`. Default configuration targets 70 entities with 11 transfer objects per entity. |
| `RealisticPsmModelGenerator.java` | Synthesizes realistic `PsmModel` instances containing entities, transfer objects, data/navigation properties, measures, and services. Exports `generate(int)`, `generateWithMeasures(int, int)`, `countElements(PsmModel)`, configuration class `GeneratorConfig`. Ratios mimic RackInspect production profile (~4.4 attributes and ~12 TOs per entity). |
| `RealisticRdbmsModelGenerator.java` | Synthesizes realistic `RdbmsModel` instances with primary keys, value fields, indices, foreign keys, and junction tables. Exports `generate(int)`, `generate(GeneratorConfig)`, `countTables(RdbmsModel)`, `countFields(RdbmsModel)`, configuration class `GeneratorConfig`. Default configuration builds 100 tables with 6 fields per table. |
