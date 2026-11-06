## 1. Script Skeleton

- [x] 1.1 Copy `run-comparison.sh` to `run-performance-tests.sh` and make it executable
- [x] 1.2 Update header comment — change title, description, and options list to reflect performance focus
- [x] 1.3 Add `--no-profiler` flag (default: profiler ON) — sets `PROFILER_ENABLED=true/false`
- [x] 1.4 Add `--zeta-only` flag — sets `TRANSFORMATION_MODE=ZETA` (default: DUAL)
- [x] 1.5 Remove `--perf-only` flag (replaced by `--no-profiler` semantics) or keep as alias
- [x] 1.6 Update `--help` output to show all new options with defaults and example commands

## 2. Maven Invocation with Profiler

- [x] 2.1 Add profiler system properties to the `mvnw test` invocation:
  - `-Djudo.test.profiler.enabled=${PROFILER_ENABLED}`
  - `-Djudo.test.profiler.format=collapsed`
  - `-Djudo.test.profiler.thresholdMs=0`
- [x] 2.2 Add transformation mode property: `-Djudo.test.transformation.mode=${TRANSFORMATION_MODE}`
- [x] 2.3 Verify profiler output directory is created after test run (`target/profiler-output/`)
- [x] 2.4 Print warning if profiler is enabled but output directory is empty after tests

## 3. Cross-Module Performance Table

- [x] 3.1 After all modules run, collect `target/comparison-results.json` from each module directory
- [x] 3.2 Parse JSON files using shell (awk/grep/sed — no jq dependency) to extract: module, totalEtlTimeMs, totalZetaTimeMs, avgSpeedup, passed, totalModels
- [x] 3.3 Print formatted performance table with columns: Module, Models, ETL(ms), ZETA(ms), Speedup, Pass
- [x] 3.4 Print totals row with aggregate ETL/ZETA times and overall average speedup
- [x] 3.5 Print `[WARN] No results found for <module>` when JSON is missing

## 4. Hotspot Analysis

- [x] 4.1 After all modules run, find all `*/target/profiler-output/*.txt` files
- [x] 4.2 For each file, run awk leaf-method aggregation
- [x] 4.3 Filter results to lines containing `hu.blackbelt` or `judo` (grep)
- [x] 4.4 Sort by CPU% descending and limit to top 20 across all modules
- [x] 4.5 Print hotspot table: rank, method name (truncated to 60 chars), CPU%, samples, source file
- [x] 4.6 Classify each hotspot: HIGH (≥15%), MEDIUM (≥5%), LOW (≥1%)
- [x] 4.7 Print "Optimization Candidates" section listing HIGH and MEDIUM items
- [x] 4.8 If no `.txt` files found, print info message and skip hotspot section

## 5. Verification

- [x] 5.1 Run `shellcheck run-performance-tests.sh` and fix any warnings (shellcheck not installed; bash -n passes)
- [x] 5.2 Run script with `--help` flag and verify all options and examples are shown
- [x] 5.3 Run script with `--no-profiler --basedir <models>` and verify timing table is produced
- [x] 5.4 Run script with `--basedir <models>` and verify profiler output is created and hotspot table appears
- [x] 5.5 Run script with `--module psm2asm --basedir <models>` and verify only PSM2ASM runs
