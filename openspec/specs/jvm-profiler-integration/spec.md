## ADDED Requirements

### Requirement: Agent-based profiling via jvm-profiler
The system SHALL use Uber jvm-profiler as a Java agent (configured in surefire argLine) to collect CPU stacktraces and JVM metrics during performance test runs. The agent SHALL write output to `target/profiler-output/` using `FileOutputReporter`.

#### Scenario: Agent active during performance test run
- **WHEN** Maven runs tests with the `performance` profile
- **THEN** the JVM process is started with `-javaagent` pointing to the jvm-profiler JAR
- **THEN** `target/profiler-output/Stacktrace.json` is produced after the test run
- **THEN** `target/profiler-output/CpuAndMemory.json` is produced after the test run

#### Scenario: No profiling without performance profile
- **WHEN** Maven runs tests without the `performance` profile
- **THEN** no `-javaagent` flag is added to the JVM
- **THEN** no profiler output files are produced

### Requirement: Per-test timestamp recording
The ProfilingExtension SHALL record the wall-clock start and end time (epoch milliseconds) for each test execution when profiling is enabled.

#### Scenario: Timestamps recorded for profiled tests
- **WHEN** a test annotated with `@Profile` (or in a `@Profile`-annotated class) starts
- **THEN** the extension records `startEpochMs = System.currentTimeMillis()`
- **WHEN** the test completes
- **THEN** the extension records `endEpochMs = System.currentTimeMillis()`

#### Scenario: Threshold guard suppresses short tests
- **WHEN** a test completes and its duration is less than `thresholdMs` (from `@Profile` or system property)
- **THEN** no post-processing is triggered for that test

### Requirement: Per-test collapsed stacktrace extraction
After all tests in a suite complete, the extension SHALL filter `Stacktrace.json` by each test's time window and produce a per-test collapsed stack file.

#### Scenario: Collapsed file produced per test
- **WHEN** `AfterAll` runs after a suite with recorded timestamps
- **THEN** for each recorded test, `Stacktrace.json` entries with `epochMillis` in `[startEpochMs, endEpochMs]` are extracted
- **THEN** `stackcollapse.py` is invoked to convert the filtered JSON to collapsed format
- **THEN** output is written to `target/profiler-output/<TestClass>_<testMethod>.txt`

#### Scenario: Missing stackcollapse.py skips collapse gracefully
- **WHEN** Python 3 is not available on the system or `stackcollapse.py` is not found
- **THEN** the raw filtered JSON is written to `target/profiler-output/<TestClass>_<testMethod>.json`
- **THEN** a warning is logged: "stackcollapse.py unavailable, raw JSON retained"
- **THEN** no exception is thrown

### Requirement: Per-test GC and heap delta metrics
After each test, the extension SHALL extract GC and heap metrics from `CpuAndMemory.json` for the test's time window and log them.

#### Scenario: Metrics logged after test
- **WHEN** a profiled test completes and its duration exceeds `thresholdMs`
- **THEN** heap used at start and end of the test window is logged
- **THEN** GC collection count delta for the test window is logged
- **THEN** GC time delta (ms) for the test window is logged
- **THEN** log format SHALL be: `[Profiler] <testName> heap: +<N>MB gc: <N> collections <N>ms`

### Requirement: Platform portability
The profiling extension SHALL work on macOS (including Apple Silicon) and Linux without any native library installation or kernel configuration.

#### Scenario: Profiling works on macOS
- **WHEN** performance tests are run on macOS with the `performance` Maven profile
- **THEN** profiler output files are produced without error
- **THEN** no `perf_event_paranoid` configuration is required

#### Scenario: Profiling works on Linux CI
- **WHEN** performance tests are run on Linux (GitHub Actions) with the `performance` Maven profile
- **THEN** profiler output files are produced without error
- **THEN** no additional system configuration is required

### Requirement: JitPack dependency resolution
The jvm-profiler JAR SHALL be resolved from JitPack at build time, pinned to a specific commit hash.

#### Scenario: Dependency resolved during build
- **WHEN** Maven resolves dependencies with JitPack repository configured
- **THEN** `com.github.uber-common:jvm-profiler:<commit-hash>` is downloaded and cached
- **THEN** the JAR is available as a test-scoped dependency

#### Scenario: Reproducible builds via commit pin
- **WHEN** two developers build the project at different times
- **THEN** both resolve the same JAR because the version is pinned to a commit hash (not `master-SNAPSHOT`)
