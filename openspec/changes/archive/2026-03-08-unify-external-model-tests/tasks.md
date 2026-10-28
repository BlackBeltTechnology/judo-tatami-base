## 1. Update DiscoveryComparisonTest classes to support both model sources

- [x] 1.1 Update `Psm2AsmDiscoveryComparisonTest` - add properties support, warmup, iterations, dual tags
- [x] 1.2 Update `Psm2MeasureDiscoveryComparisonTest` - same changes
- [x] 1.3 Update `Asm2RdbmsDiscoveryComparisonTest` - same changes
- [x] 1.4 Update `Rdbms2LiquibaseDiscoveryComparisonTest` - same changes
- [x] 1.5 Update `Asm2KeycloakDiscoveryComparisonTest` - same changes

## 2. Delete ExternalModelTest classes

- [x] 2.1 Delete all 5 `*ExternalModelTest.java` files

## 3. Verify

- [x] 3.1 Run all tests with discovery basedir (auto-discovery mode)
- [x] 3.2 Run all tests without discovery basedir (properties-file only mode)
