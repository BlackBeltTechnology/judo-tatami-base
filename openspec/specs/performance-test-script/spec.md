## ADDED Requirements

### Requirement: Script runs performance tests for all modules
A `run-performance-tests.sh` script at the project root SHALL run all 5 `DiscoveryComparisonTest` classes with the JVM profiler enabled (by default) and produce comprehensive performance output.

#### Scenario: Run all modules with profiling
- **WHEN** user runs `./run-performance-tests.sh --basedir /path/to/models`
- **THEN** all 5 modules execute with `judo.test.profiler.enabled=true`, `judo.test.transformation.mode=DUAL`, and `judo.test.profiler.format=collapsed`
- **THEN** profiler output files are written to each module's `target/profiler-output/` directory

#### Scenario: Run single module
- **WHEN** user runs `./run-performance-tests.sh --module psm2asm --basedir /path/to/models`
- **THEN** only the PSM2ASM module executes
- **THEN** all other behavior (profiling, reporting) applies to that single module

#### Scenario: Run without profiling
- **WHEN** user runs `./run-performance-tests.sh --no-profiler --basedir /path/to/models`
- **THEN** all modules execute with `judo.test.profiler.enabled=false`
- **THEN** only the performance timing table is produced (hotspot analysis section is omitted)

#### Scenario: Run ZETA-only for clean profiling
- **WHEN** user runs `./run-performance-tests.sh --zeta-only --basedir /path/to/models`
- **THEN** all modules execute with `judo.test.transformation.mode=ZETA`
- **THEN** no ETL timing is collected; speedup column shows N/A

### Requirement: Script produces cross-module performance table
The script SHALL aggregate `target/comparison-results.json` from all executed modules and print a performance comparison table after all tests complete.

#### Scenario: Multi-model performance table
- **WHEN** all modules complete with multiple models each
- **THEN** the script prints a table with columns: Module, Models, ETL(ms), ZETA(ms), Speedup, Result
- **THEN** totals row shows aggregate timing and overall average speedup
- **THEN** output matches this format:
  ```
  ═══════════════════════════════════════════════════════
   Module        Models   ETL(ms)   ZETA(ms)   Speedup
   PSM2ASM          3      2,550       290      8.79x
   ASM2RDBMS        3      3,600       710      5.07x
   TOTAL            6      6,150     1,000      6.15x
  ═══════════════════════════════════════════════════════
  ```

#### Scenario: No JSON results available
- **WHEN** a module produces no `comparison-results.json` (e.g., no models found)
- **THEN** that module is omitted from the aggregated table
- **THEN** a warning is printed: `[WARN] No results found for <module>`

### Requirement: Script analyzes profiler output for CPU hotspots
When profiler output files exist, the script SHALL parse collapsed stack format files and produce a ranked CPU hotspot table filtered to JUDO/Zeta packages.

#### Scenario: Hotspot table produced
- **WHEN** profiler output `.txt` files exist in `target/profiler-output/` directories
- **THEN** the script parses all files using awk (leaf-method aggregation)
- **THEN** results are filtered to lines containing `hu.blackbelt` or `judo`
- **THEN** a ranked table is printed: rank, method name, CPU%, samples, module
- **THEN** top 20 hotspots are shown by default

#### Scenario: Optimization candidates classified
- **WHEN** hotspot table is produced
- **THEN** each hotspot is classified:
  - CPU% ≥ 15%: **HIGH** priority
  - CPU% ≥ 5%: **MEDIUM** priority
  - CPU% ≥ 1%: **LOW** priority
- **THEN** an "Optimization Candidates" section lists HIGH and MEDIUM items with their classification

#### Scenario: No profiler output available
- **WHEN** no `.txt` files exist in any `target/profiler-output/` directory
- **THEN** hotspot analysis section is omitted
- **THEN** a note is printed: `[INFO] No profiler output found — run with profiler enabled or check perf_event_paranoid`

### Requirement: Script has sensible defaults and auto-detects basedir
The script SHALL auto-detect the basedir using the same logic as `run-comparison.sh` and provide helpful error messages.

#### Scenario: Auto-detect basedir
- **WHEN** user runs `./run-performance-tests.sh` without `--basedir`
- **THEN** the script looks for `../judo-tatami-tests/models/` relative to the project
- **THEN** if found, uses it automatically and prints: `Auto-detected basedir: <path>`
- **THEN** if not found, exits with error explaining how to set `--basedir`

#### Scenario: Show help
- **WHEN** user runs `./run-performance-tests.sh --help`
- **THEN** usage, all options with defaults, and example commands are printed
- **THEN** script exits with code 0
