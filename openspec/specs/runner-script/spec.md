## ADDED Requirements

### Requirement: Bash script runs all comparison tests
A `run-comparison.sh` script at the project root SHALL run all 5 DiscoveryComparisonTest classes with configurable options.

#### Scenario: Run all modules with STRICT comparison
- **WHEN** user runs `./run-comparison.sh --basedir /path/to/models`
- **THEN** all 5 modules execute with STRICT comparison mode and performance profile enabled

#### Scenario: Run single module
- **WHEN** user runs `./run-comparison.sh --module psm2asm --basedir /path/to/models`
- **THEN** only the PSM2ASM module executes

#### Scenario: Performance-only mode
- **WHEN** user runs `./run-comparison.sh --perf-only --basedir /path/to/models`
- **THEN** all modules execute with comparison disabled (`judo.test.comparison.enabled=false`)

### Requirement: Script produces cross-module summary
The script SHALL aggregate `target/comparison-results.json` files from all modules and produce a final cross-module summary.

#### Scenario: Aggregated JSON output
- **WHEN** user runs `./run-comparison.sh --json --basedir /path/to/models`
- **THEN** aggregated JSON with all module results is written to stdout after all tests complete

#### Scenario: Human-readable cross-module summary
- **WHEN** all modules complete
- **THEN** the script prints a cross-module summary table showing per-module pass/fail counts and average speedups

### Requirement: Script has sensible defaults
The script SHALL auto-detect the basedir if `judo-tatami-tests/models` exists relative to the project, and default to STRICT comparison mode.

#### Scenario: Auto-detect basedir
- **WHEN** user runs `./run-comparison.sh` without `--basedir`
- **THEN** the script looks for `../judo-tatami-tests/models/` and uses it if found
- **THEN** if not found, the script exits with an error message explaining how to set basedir
