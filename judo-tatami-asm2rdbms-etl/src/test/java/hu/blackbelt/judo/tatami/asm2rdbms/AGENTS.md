# judo-tatami-asm2rdbms-etl/src/test/java/hu/blackbelt/judo/tatami/asm2rdbms

ASM→RDBMS ETL and dual-engine tests (moved from `judo-tatami-asm2rdbms`); ETL path imports `hu.blackbelt.judo.tatami.asm2rdbms.etl.Asm2Rdbms`. Also holds the CSV-vs-ETL mapping parity proof.

| File | Purpose |
|---|---|
| `Asm2RdbmsInheritanceTest.java` | Hierarchy tests `testBasicInheritance`/`testDiamondInheritance`/`testAbstractEntityProducesTable` over `TransformationMode` assert `RdbmsTable` parents via `assertParents` and expected field sets via `assertFields`, plus `compareTransformations` ETL-vs-Zeta equivalence. Altering synthetic attribute emission (`#_id`, `#_type`) or supertype wiring without updating expectations fails. |
| `Asm2RdbmsMappingTestBase.java` | Abstract fixture base for ASM-to-RDBMS transformation tests. Registers RDBMS metamodels, manages ASM/RDBMS models, and provides helper assertions `assertTables()` and `assertFields()`. |
| `Asm2RdbmsNameMappingTest.java` | Tests custom table, attribute, reference, and junction table naming overrides using `hu.blackbelt.judo.meta.rdbmsNameMapping` models. |
| `Asm2RdbmsRelationMappingTest.java` | Cardinality tests over `TransformationMode` drive `testOneWayRelation`/`testTwoWayRelation` across 0..1, 1..1, 0..*, 1..* combos with `isContainment`/`isSelf` variants, asserting `RdbmsForeignKey` and `#_id` columns via `assertFields`. Changing FK naming or junction-table emission without updating expectations fails. |
| `Asm2RdbmsTest.java` | End-to-end transformation test running Northwind Demo model across ETL and Zeta engines. Compares generated RDBMS models with `ModelComparator` and validates trace metadata creation. |
| `Asm2RdbmsTypeMappingTest.java` | Type-matrix tests `testNumericTypes`/`testStringlikeTypes`/`testDateTypes`/`testBooleanTypes` over `TransformationMode` build `EDataType`s via `customEDataTypeBuilder` (e.g. `java.time.LocalDate`) and check `typeAsserter` on `RdbmsField` — `getRdbmsTypeName()`, `getSize()`, `getPrecision()`, `getScale()`. Dropping `maxLength`/`precision`/`scale` constraint annotations breaks expected asserts. |
| `Asm2RdbmsZetaTraceTest.java` | Tests `TransformationTrace` mapping generation and round-trip cache serialization for `Asm2RdbmsZetaTransformation`. Verifies EClass to RdbmsTable and EStructuralFeature to RdbmsField mappings. |
| `ExcelMappingEtlReference.java` | Test-only verbatim copy of the original ETL implementation of `ExcelMappingModels2Rdbms` (`injectExcelMappings` over `excelTo*.etl` + `.xlsx`); reference side of the parity proof. See change: split-etl-artifacts. |
| `ExcelMappingParityTest.java` | Parameterized over hsqldb/postgresql/oracle; asserts core `ExcelMappingModels2Rdbms` (CSV) output equals `ExcelMappingEtlReference` (xlsx ETL) via dump and `EcoreUtil.equals`. Fails when CSV and workbooks drift. See change: split-etl-artifacts. |
