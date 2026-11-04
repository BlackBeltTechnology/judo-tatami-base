## 1. Framework-level result collection and summary

- [x] 1.1 Add `TestResult` record and result collection methods to `AbstractExternalModelTest`
- [x] 1.2 Add `printSummary(moduleName)` method with formatted table output
- [x] 1.3 Add `writeJsonResults(moduleName, targetDir)` method for JSON output
- [x] 1.4 Update all 5 `*DiscoveryComparisonTest` classes to record results and add sentinel summary test

## 2. Bash runner script

- [x] 2.1 Create `run-comparison.sh` with option parsing (--mode, --basedir, --perf-only, --module, --json)
- [x] 2.2 Add cross-module summary aggregation from JSON files

## 3. Verify

- [x] 3.1 Run single module via script and verify summary table + JSON output
- [x] 3.2 Run all modules via script and verify cross-module summary
