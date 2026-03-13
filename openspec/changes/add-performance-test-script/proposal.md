## Why

`run-comparison.sh` focuses on ETL vs ZETA **correctness** (STRICT comparison). There is no dedicated script for **performance analysis** — profiling Zeta transformations, identifying CPU hotspots, and producing optimization guidance. The JVM profiler integration (`add-jvm-profiler-integration`) is in place but no tooling ties it to the multi-model discovery workflow.

## What Changes

- **New script** `run-performance-tests.sh` at the project root — a sibling to `run-comparison.sh` focused on performance analysis
- Runs `DiscoveryComparisonTest` classes with JVM profiler enabled and `ZETA`-mode transformation by default (clean metrics, no ETL overhead)
- Aggregates per-module timing JSON into a **cross-module performance table** (ETL vs ZETA speedup per model)
- Parses `target/profiler-output/*.txt` (collapsed stack format) and produces a ranked **CPU hotspot table** filtered to JUDO/Zeta packages
- Classifies hotspots into optimization priority (HIGH / MEDIUM / LOW) and prints an **optimization candidates** report

## Capabilities

### New Capabilities
- `performance-test-script`: Shell script `run-performance-tests.sh` — orchestrates per-module performance runs, aggregates timing data, analyzes profiler output, and outputs comprehensive comparison and hotspot tables

### Modified Capabilities
- `runner-script`: Existing `run-comparison.sh` spec — no requirement changes; `run-performance-tests.sh` is a separate script, not a replacement

## Impact

- New file: `run-performance-tests.sh` (project root)
- Reads existing infrastructure: `DiscoveryComparisonTest` classes, `target/comparison-results.json`, `target/profiler-output/*.txt`
- No Java changes required; depends on `add-jvm-profiler-integration` being implemented
- Performance profile (`-Pperformance`) and profiler system properties already wired in `pom.xml`
