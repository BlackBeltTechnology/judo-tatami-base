## ADDED Requirements

### Requirement: ExternalModelConfig propagated to execute methods
`AbstractDualComparisonTest` SHALL pass `ExternalModelConfig` as a second parameter to both `executeEtl(S source, ExternalModelConfig config)` and `executeZeta(S source, ExternalModelConfig config)` when executing transformations from the `@TestFactory` orchestration.

#### Scenario: Config-dependent test accesses dialect
- **WHEN** a discovery comparison test needs a dialect parameter for RDBMS transformation
- **THEN** the subclass reads `config.getDialect()` directly in `executeEtl`/`executeZeta` without field-stashing

#### Scenario: Config-independent test ignores config
- **WHEN** a discovery comparison test does not need config parameters
- **THEN** the subclass ignores the `config` parameter in `executeEtl`/`executeZeta`

#### Scenario: Inline assertDualEquivalent passes null config
- **WHEN** `assertDualEquivalent(S source, String testName)` is called from an inline `@Test` method
- **THEN** `executeEtl` and `executeZeta` receive `null` as the `config` parameter

### Requirement: Discovery tests use AbstractDualComparisonTest
All 5 discovery comparison tests in `judo-tatami-base` SHALL extend `AbstractDualComparisonTest<S,T>` instead of `AbstractExternalModelTest`, implementing the 5 abstract methods (`parseSource`, `executeEtl`, `executeZeta`, `getResource`, `getModuleName`).

#### Scenario: Psm2AsmDiscoveryComparisonTest migration
- **WHEN** `Psm2AsmDiscoveryComparisonTest` extends `AbstractDualComparisonTest<PsmModel, AsmModel>`
- **THEN** it discovers models from `external-model-tests.properties`, runs dual comparison with warmup/timing/comparison, and produces summary identical to before

#### Scenario: Asm2RdbmsDiscoveryComparisonTest with dialect
- **WHEN** `Asm2RdbmsDiscoveryComparisonTest` extends `AbstractDualComparisonTest<AsmModel, RdbmsModel>`
- **THEN** it reads dialect from `config.getDialect()` in `executeEtl`/`executeZeta` and passes it to the transformation

#### Scenario: All migrated tests preserve skip behavior
- **WHEN** external model files are not available (e.g., CI without rackinspect checkout)
- **THEN** tests skip via `Assumptions.assumeTrue` as before
