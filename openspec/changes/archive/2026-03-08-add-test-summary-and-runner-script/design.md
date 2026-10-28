## Context

The 5 `*DiscoveryComparisonTest` classes each run ETL vs Zeta comparison tests. Currently `printResults()` logs per-model results but there's no aggregate summary and no machine-readable output.

## Goals / Non-Goals

**Goals:**
- Per-module summary table printed at end of each test class run
- JSON output per module written to `target/comparison-results.json`
- Bash script that runs all 5 modules and produces a cross-module summary
- JSON output consumable by LLM agents for analysis

**Non-Goals:**
- Not changing the per-model `printResults()` format
- Not adding CI integration (just the script)

## Decisions

**Result collection via static list in AbstractExternalModelTest**: Add a `TestResult` record and a thread-safe list. Each `testModel()` call adds a result. A `printSummary()` method prints the table and writes JSON. Each test class calls `printSummary()` after all models complete.

Since `@TestFactory` returns `Collection<DynamicTest>`, there's no `@AfterAll` hook that runs after dynamic tests. Instead, add a sentinel DynamicTest at the end of the collection that calls `printSummary()`.

**JSON format**: Each module writes `target/comparison-results.json` with structure:
```json
{
  "module": "psm2asm",
  "timestamp": "2026-03-08T21:50:00",
  "comparisonMode": "STRICT",
  "results": [
    {
      "model": "ActionGroupTest",
      "etlTimeMs": 638,
      "zetaTimeMs": 88,
      "speedup": 7.25,
      "etlOutputCount": 1234,
      "zetaOutputCount": 1234,
      "comparisonResult": "EQUIVALENT",
      "differenceCount": 0
    }
  ],
  "summary": {
    "totalModels": 8,
    "passed": 8,
    "failed": 0,
    "skipped": 0,
    "avgSpeedup": 8.5
  }
}
```

**Bash script options**:
```
./run-comparison.sh [options]
  --mode STRICT|STRUCTURAL|LENIENT   (default: STRICT)
  --basedir /path/to/models          (default: auto-detect judo-tatami-tests/models)
  --perf-only                        (disable comparison)
  --module psm2asm|psm2measure|...   (single module, default: all)
  --json                             (output aggregated JSON to stdout)
```

The script aggregates all `target/comparison-results.json` files into a final summary.

## Risks / Trade-offs

- Static list for result collection means results accumulate across test methods in the same JVM. Using `@BeforeAll` equivalent to clear is needed, but `@TestFactory` handles this naturally since each factory creates a fresh collection.
- The sentinel DynamicTest approach is slightly unconventional but reliable.
