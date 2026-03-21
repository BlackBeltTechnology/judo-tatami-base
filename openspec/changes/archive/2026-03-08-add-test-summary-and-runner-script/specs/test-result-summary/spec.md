## ADDED Requirements

### Requirement: TestResult record captures per-model results
AbstractExternalModelTest SHALL define a `TestResult` record with fields: model name, ETL time, Zeta time, speedup, ETL output count, Zeta output count, output label, comparison result (EQUIVALENT/FAILED/SKIPPED), difference count.

#### Scenario: Result recorded after each model test
- **WHEN** a model's ETL and Zeta transformations complete
- **THEN** a TestResult is added to the results list with all timing and comparison data

### Requirement: Summary table printed after all models
AbstractExternalModelTest SHALL provide a `printSummary(moduleName)` method that prints a formatted table with one row per model showing: model name, ETL time, Zeta time, speedup, comparison result.

#### Scenario: Summary table at end of test run
- **WHEN** all dynamic tests for a module complete
- **THEN** a summary table is printed showing all models, their performance, and comparison results
- **THEN** a totals row shows aggregate ETL time, aggregate Zeta time, average speedup, and pass/fail/skip counts

### Requirement: JSON results written to target directory
AbstractExternalModelTest SHALL provide a `writeJsonResults(moduleName)` method that writes results as JSON to `target/comparison-results.json`.

#### Scenario: JSON output for LLM consumption
- **WHEN** summary is printed
- **THEN** a JSON file is written to `target/comparison-results.json` containing module name, timestamp, comparison mode, per-model results array, and summary stats

### Requirement: Sentinel dynamic test triggers summary
Each DiscoveryComparisonTest SHALL add a final DynamicTest named "== Summary ==" at the end of the test collection that calls `printSummary()` and `writeJsonResults()`.

#### Scenario: Summary appears as last test in JUnit output
- **WHEN** all model tests have executed
- **THEN** the "== Summary ==" test executes, printing the summary table and writing JSON
