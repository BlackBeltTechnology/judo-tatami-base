## 1. ETL — Add post-transformation sorting

- [x] 1.1 Update `psmToMeasure.etl` `post {}` block — sort `MEASURES!Measure.all` by name, sort each measure's `units` by name, sort each DerivedMeasure's `terms` by referenced base measure name

## 2. Zeta — Add post-transformation sorting

- [x] 2.1 Add `sortMeasureModel()` method to `Psm2MeasureZetaTransformation.java` — sorts resource contents (Measures by name), units within each Measure by name, and terms within each DerivedMeasure by referenced measure name
- [x] 2.2 Call `sortMeasureModel()` in `execute()` before `postProcess()` (XMI ID application)

## 3. Verify

- [x] 3.1 Run psm2measure comparison tests and verify ETL/Zeta ordering matches — PASS
- [x] 3.2 Run full psm2measure test suite — 13 tests, 0 failures
