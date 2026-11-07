## Context

The `judo-tatami-test-utils` module provides a JUnit 5 profiling extension (`ProfilingExtension`) that wraps async-profiler via `ap-loader`. async-profiler requires native libraries and kernel-level permissions (`perf_event_paranoid=1` on Linux, SIP disabling on macOS Apple Silicon). On macOS — the primary developer machine OS — profiling silently fails or errors.

Uber's jvm-profiler ([github.com/uber-common/jvm-profiler](https://github.com/uber-common/jvm-profiler)) is a pure Java agent that works on all JVM platforms. It writes JSON output files per metric type. It is not published to Maven Central but is available via JitPack from source.

Current files to replace/update:
- `Profiler.java` — wraps AsyncProfilerLoader; to be replaced with a timestamp recorder
- `ProfilingExtension.java` — starts/stops per test; to be updated to extract windows from agent JSON
- `ProfileConfig.java` — system property config; to be updated for new properties
- `JVM_PROFILER_SPEC.md` — documentation; full rewrite needed

## Goals / Non-Goals

**Goals:**
- Profiling works on macOS and Linux without any native library or kernel config
- Per-test collapsed stacktrace files produced in `target/profiler-output/`
- Per-test GC count, GC time, heap delta extracted and logged alongside stacktraces
- `@Profile` annotation and threshold logic preserved
- JitPack fetch-on-demand (no JAR committed to repo)

**Non-Goals:**
- Keeping flamegraph.svg output as a first-class output (collapsed .txt remains primary; SVG is optional post-processing)
- Preserving `alloc` and `lock` event types (jvm-profiler doesn't have these modes)
- Continuous distributed profiling to Kafka (not needed for test use case)

## Decisions

### D1: JitPack for artifact delivery

**Decision**: Depend on `com.github.uber-common:jvm-profiler` via JitPack, pinned to a specific commit hash.

**Rationale**: No published Maven Central artifact exists. JitPack builds from GitHub source on demand. Pinning to a commit hash ensures reproducibility (avoids `master-SNAPSHOT` rebuilds on every Maven update).

**Alternative considered**: Bundle a pre-built JAR in the repo. Rejected — binary files in source control are opaque and create a maintenance burden.

### D2: Agent delivered via surefire argLine, not programmatic attachment

**Decision**: Configure `-javaagent` in the surefire/failsafe `argLine` for the `performance` Maven profile. The JUnit extension does NOT load the agent dynamically.

**Rationale**: jvm-profiler has no programmatic start/stop API — it is agent-only. Dynamic agent attachment via `VirtualMachine.attach()` is fragile and requires `tools.jar`. The surefire argLine is the standard pattern for Java agents in Maven tests.

**Consequence**: The agent runs for the entire JVM lifetime of the test process, not just profiled tests. Per-test isolation is achieved by time-window filtering in post-processing.

### D3: Per-test isolation via timestamp bridging

**Decision**: `ProfilingExtension` records `startEpochMs` before each test and `endEpochMs` after. After all tests, an `AfterAll` hook reads `Stacktrace.json` and filters stacktrace entries whose `epochMillis` falls within each test's window, producing a per-test collapsed file.

**Rationale**: jvm-profiler writes all stacktraces to a single timestamped JSON file. Time-window filtering is the only way to approximate per-test isolation without agent start/stop support.

**Known limitation**: Very short tests (< one `sampleInterval`) may capture zero frames. The `thresholdMs` guard from `@Profile` mitigates this.

### D4: stackcollapse.py bundled as a test resource

**Decision**: Copy `stackcollapse.py` from the jvm-profiler repository into `src/test/resources/scripts/stackcollapse.py`. The extension invokes it via `ProcessBuilder` when producing collapsed output.

**Rationale**: Without this script, the JSON stacktrace format cannot be converted to the collapsed format expected by flamegraph.pl and the LLM analyzer. Bundling removes the external dependency on a Python script at runtime.

**Alternative**: Reimplement the collapse logic in Java. Rejected as unnecessary complexity; the Python script is small and stable.

### D5: GC/heap delta from CpuAndMemory.json

**Decision**: After each test, the extension reads `CpuAndMemory.json`, filters entries by the test's time window, and logs: initial heap, final heap, heap delta, GC collection count delta, and GC time delta.

**Rationale**: These metrics are free — jvm-profiler produces them with `metricInterval` regardless. Adding them alongside stacktraces gives a fuller picture of transformation performance without extra overhead.

## Risks / Trade-offs

- **JitPack availability** → If JitPack is unavailable during build, the dependency cannot be resolved. Mitigation: pin to a commit hash and document a fallback (build from source, `mvn install`).
- **sampleInterval granularity** → jvm-profiler samples at a fixed interval (default 100ms). Tests shorter than one interval produce no stacktrace data. Mitigation: `thresholdMs` guard skips post-processing for short tests; document minimum recommended test duration.
- **Python required for collapse** → `stackcollapse.py` requires Python 3 at test time. Mitigation: collapse step is optional; if Python is absent, the raw JSON is retained and collapse is skipped with a warning.
- **jvm-profiler maintenance** → The repo has had minimal activity since 2019. Mitigation: functionality needed (FileOutputReporter + stacktrace sampling) is stable and self-contained; no active maintenance required.

## Migration Plan

1. Update `pom.xml`: remove `ap-loader-all`, add JitPack repo + `jvm-profiler` dependency (test scope), add `-javaagent` to surefire argLine in `performance` profile
2. Replace `Profiler.java` with `JvmProfilerBridge.java` (timestamp recorder + JSON reader)
3. Update `ProfilingExtension.java`: remove AsyncProfilerLoader calls, add timestamp recording + AfterAll post-processing
4. Update `ProfileConfig.java`: replace async-profiler properties with jvm-profiler equivalents
5. Bundle `stackcollapse.py` in test resources
6. Rewrite `JVM_PROFILER_SPEC.md`
7. Run `Psm2AsmExternalModelTest -Pperformance` on macOS to verify output produced

Rollback: revert pom.xml changes; async-profiler code is deleted so a full revert via git is needed.

## Open Questions

- Which specific commit hash of `uber-common/jvm-profiler` to pin? (Latest `master` HEAD at time of implementation is fine; document it in pom.xml comment.)
- Should `flamegraph.pl` invocation be added as an optional step, or keep collapsed-only output for now?
