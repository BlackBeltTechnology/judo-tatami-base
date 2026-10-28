## Why

Running comparison and performance tests across all 5 transformation modules requires manually invoking Maven 5 times with verbose flags. There's no cross-module summary, and results must be manually pieced together from log output. LLM agents also can't efficiently consume the current human-formatted log output.

## What Changes

- **Framework-level summary (B)**: Add result collection and summary table printing to `AbstractExternalModelTest`. Each `*DiscoveryComparisonTest` will print a per-module summary table after all models complete. Results are also written as JSON to `target/comparison-results.json` for machine consumption.
- **Bash runner script (A)**: Add `run-comparison.sh` at project root that runs all 5 modules with configurable options and produces a final cross-module summary by aggregating the JSON outputs.

## Capabilities

### New Capabilities
- `test-result-summary`: Framework-level result collection, summary table, and JSON output in AbstractExternalModelTest
- `runner-script`: Bash script for running all comparison/performance tests with a single command

### Modified Capabilities

## Impact

- `AbstractExternalModelTest.java` - add result collection, summary printer, JSON writer
- All 5 `*DiscoveryComparisonTest` classes - call summary methods at end of testModel
- New file: `run-comparison.sh` at project root
