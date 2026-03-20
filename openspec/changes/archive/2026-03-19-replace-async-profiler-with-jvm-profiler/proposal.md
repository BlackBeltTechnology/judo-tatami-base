## Why

The existing profiling extension uses async-profiler (via ap-loader), which does not work on macOS due to native library and kernel permission constraints. This makes per-test CPU profiling unavailable for developers on the primary development platform. Replacing it with Uber's jvm-profiler (a pure Java agent) restores profiling capability across all platforms while also adding richer JVM metrics (GC, heap, threads).

## What Changes

- Remove `ap-loader-all` dependency from `judo-tatami-test-utils`
- Remove all async-profiler native library loading and invocation code from `ProfilingExtension` and `Profiler`
- Add `com.github.uber-common:jvm-profiler` via JitPack as a test-scoped dependency
- Configure jvm-profiler as a Java agent in surefire/failsafe `argLine` for performance test profiles
- Update `ProfilingExtension` to record per-test timestamps and filter jvm-profiler's `Stacktrace.json` output into per-test collapsed stack files
- Add post-test extraction of GC and heap delta metrics from `CpuAndMemory.json`
- Bundle `stackcollapse.py` (from jvm-profiler repo) for converting JSON stacktraces to collapsed format
- Update `JVM_PROFILER_SPEC.md` to reflect the new architecture
- Remove Linux-only environment prerequisites (`perf_event_paranoid`) from documentation

## Capabilities

### New Capabilities
- `jvm-profiler-integration`: JUnit 5 profiling extension backed by Uber jvm-profiler agent — provides per-test CPU stacktraces (via time-window filtering of agent output) and GC/heap delta metrics, working on both macOS and Linux without native library requirements.

### Modified Capabilities
- (none — no existing spec covers profiling)

## Impact

- `judo-tatami-test-utils/pom.xml`: remove `ap-loader-all`, add JitPack repository + `jvm-profiler` dependency
- `judo-tatami-test-utils/src/main/java/.../profiler/`: replace `Profiler.java` and update `ProfilingExtension.java`, `ProfileConfig.java`
- `JVM_PROFILER_SPEC.md`: full rewrite to document new architecture
- CI (`pom.xml` surefire config in performance profile): add `-javaagent` argLine
- macOS developers gain working profiling; Linux CI unaffected
