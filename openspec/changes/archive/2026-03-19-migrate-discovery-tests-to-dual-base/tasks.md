## 1. API Change

- [x] 1.1 Add `ExternalModelConfig config` parameter to `executeEtl` and `executeZeta` abstract methods in `AbstractDualComparisonTest`
- [x] 1.2 Update `testModel()` to pass `config` to `executeEtl` and `executeZeta` calls (including warmup)
- [x] 1.3 Update `assertDualEquivalent` methods to pass `null` as config

## 2. Migrate Simple Tests (no config params needed)

- [x] 2.1 Migrate `Psm2MeasureDiscoveryComparisonTest` to extend `AbstractDualComparisonTest<PsmModel, MeasureModel>`
- [x] 2.2 Migrate `Psm2AsmDiscoveryComparisonTest` to extend `AbstractDualComparisonTest<PsmModel, AsmModel>`
- [x] 2.3 Migrate `Asm2KeycloakDiscoveryComparisonTest` to extend `AbstractDualComparisonTest<AsmModel, KeycloakModel>`

## 3. Migrate Config-Dependent Tests (need dialect)

- [x] 3.1 Migrate `Asm2RdbmsDiscoveryComparisonTest` to extend `AbstractDualComparisonTest<AsmModel, RdbmsModel>` using `config.getDialect()`
- [x] 3.2 Migrate `Rdbms2LiquibaseDiscoveryComparisonTest` to extend `AbstractDualComparisonTest<RdbmsModel, LiquibaseModel>` using `config.getDialect()`

## 4. Verify

- [x] 4.1 Build all modules: `mvn clean install -DskipTests`
- [x] 4.2 Run migrated tests (skip if external models not available): `mvn test -pl judo-tatami-psm2asm,judo-tatami-psm2measure,judo-tatami-asm2rdbms,judo-tatami-asm2keycloak,judo-tatami-rdbms2liquibase -Dtest="*DiscoveryComparisonTest"` — BUILD SUCCESS, 0 tests ran (external models not available, gracefully skipped)
