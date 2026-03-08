## 1. Add comparison guard to all 5 DiscoveryComparisonTest classes

- [x] 1.1 Add `isComparisonEnabled()` guard to `Psm2AsmDiscoveryComparisonTest`
- [x] 1.2 Add `isComparisonEnabled()` guard to `Psm2MeasureDiscoveryComparisonTest`
- [x] 1.3 Add `isComparisonEnabled()` guard to `Asm2RdbmsDiscoveryComparisonTest`
- [x] 1.4 Add `isComparisonEnabled()` guard to `Rdbms2LiquibaseDiscoveryComparisonTest`
- [x] 1.5 Add `isComparisonEnabled()` guard to `Asm2KeycloakDiscoveryComparisonTest`

## 2. Verify

- [x] 2.1 Run all 5 discovery tests with `judo.test.comparison.enabled=false` and verify they pass without comparison
- [x] 2.2 Run all 5 discovery tests with `judo.test.comparison.enabled=true` (default) and verify comparison still works
