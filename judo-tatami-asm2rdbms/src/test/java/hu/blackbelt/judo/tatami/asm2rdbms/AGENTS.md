# AGENTS.md — `judo-tatami-asm2rdbms/src/test/java/hu/blackbelt/judo/tatami/asm2rdbms`

Unit and integration test suites covering ASM to RDBMS schema transformation, inheritance mapping, type coercion, and name mapping.

| File | Purpose |
| --- | --- |
| `AbbreviateUtilsTest.java` | Tests SQL identifier shortening rules via `AbbreviateUtils.abbreviate()`. Asserts target character length boundaries, word separator handling, and consonant compression for issue JNG-1303. |
| `Asm2RdbmsInheritanceTest.java` | Hierarchy tests `testBasicInheritance`/`testDiamondInheritance`/`testAbstractEntityProducesTable` over `TransformationMode` assert `RdbmsTable` parents via `assertParents` and expected field sets via `assertFields`, plus `compareTransformations` ETL-vs-Zeta equivalence. Altering synthetic attribute emission (`#_id`, `#_type`) or supertype wiring without updating expectations fails. |
| `Asm2RdbmsMappingTestBase.java` | Abstract fixture base for ASM-to-RDBMS transformation tests. Registers RDBMS metamodels, manages ASM/RDBMS models, and provides helper assertions `assertTables()` and `assertFields()`. |
| `Asm2RdbmsNameMappingTest.java` | Tests custom table, attribute, reference, and junction table naming overrides using `hu.blackbelt.judo.meta.rdbmsNameMapping` models. |
| `Asm2RdbmsRelationMappingTest.java` | Cardinality tests over `TransformationMode` drive `testOneWayRelation`/`testTwoWayRelation` across 0..1, 1..1, 0..*, 1..* combos with `isContainment`/`isSelf` variants, asserting `RdbmsForeignKey` and `#_id` columns via `assertFields`. Changing FK naming or junction-table emission without updating expectations fails. |
| `Asm2RdbmsTest.java` | End-to-end transformation test running Northwind Demo model across ETL and Zeta engines. Compares generated RDBMS models with `ModelComparator` and validates trace metadata creation. |
| `Asm2RdbmsTypeMappingTest.java` | Type-matrix tests `testNumericTypes`/`testStringlikeTypes`/`testDateTypes`/`testBooleanTypes` over `TransformationMode` build `EDataType`s via `customEDataTypeBuilder` (e.g. `java.time.LocalDate`) and check `typeAsserter` on `RdbmsField` — `getRdbmsTypeName()`, `getSize()`, `getPrecision()`, `getScale()`. Dropping `maxLength`/`precision`/`scale` constraint annotations breaks expected asserts. |
| `Asm2RdbmsWorkTest.java` | Tests `Asm2RdbmsWork` pipeline step execution with `WorkFlowEngine` across multiple database dialects (hsqldb, oracle). Asserts completion status across dialects. |
| `Asm2RdbmsZetaTraceTest.java` | Tests `TransformationTrace` mapping generation and round-trip cache serialization for `Asm2RdbmsZetaTransformation`. Verifies EClass to RdbmsTable and EStructuralFeature to RdbmsField mappings. |
