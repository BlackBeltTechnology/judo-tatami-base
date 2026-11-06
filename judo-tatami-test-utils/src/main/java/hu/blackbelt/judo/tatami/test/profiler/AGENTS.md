# AGENTS.md — `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/profiler`

JVM profiling infrastructure, JUnit 5 extensions, stacktrace sample filters, and collapsed profile analyzers.

| File | Purpose |
| --- | --- |
| `CLAUDE.md` | Claude memory context tracker file for session history within the profiler package. |
| `Hotspot.java` | Record representing an identified CPU hotspot with method name, sample count, and percentage; exports `toDisplayString()`. |
| `JvmProfilerBridge.java` | Reads and filters Uber `jvm-profiler` JSON output (`Stacktrace.json`, `CpuAndMemory.json`) by epoch timestamp window; computes `JvmMetricsDelta`. |
| `Profile.java` | JUnit 5 test annotation triggering `ProfilingExtension` at class or method level with configurable `thresholdMs`. |
| `ProfileAnalyzer.java` | Parses collapsed stack profiles into sorted `Hotspot` records; exports `analyze()` and `truncateForLlm()`. |
| `ProfileConfig.java` | Immutable configuration record loaded from `judo.test.profiler.*` system properties via `fromSystemProperties()`. |
| `ProfilingExtension.java` | JUnit 5 test extension capturing per-test execution windows, invoking stack collapsing, and extracting JVM memory deltas. |
