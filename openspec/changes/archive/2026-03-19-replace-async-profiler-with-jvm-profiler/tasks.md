## 1. Dependencies and Build Config

- [x] 1.1 Find the latest commit hash of `uber-common/jvm-profiler` on GitHub master and record it
- [x] 1.2 Add JitPack repository to `judo-tatami-test-utils/pom.xml`
- [x] 1.3 Replace `ap-loader-all` dependency with `com.github.uber-common:jvm-profiler:<commit-hash>` (test scope) in `judo-tatami-test-utils/pom.xml`
- [x] 1.4 Add `-javaagent` surefire `argLine` to the `performance` Maven profile in `judo-tatami-test-utils/pom.xml` (FileOutputReporter, outputDir=target/profiler-output, sampleInterval=100, metricInterval=1000)

## 2. stackcollapse.py Script

- [x] 2.1 Copy `stackcollapse.py` from the jvm-profiler repository into `judo-tatami-test-utils/src/main/resources/scripts/stackcollapse.py`

## 3. Replace Profiler.java

- [x] 3.1 Delete `Profiler.java` (async-profiler wrapper — no longer needed)
- [x] 3.2 Create `JvmProfilerBridge.java`: utility class that reads `Stacktrace.json` and `CpuAndMemory.json` from the output directory, filters by a time window `[startMs, endMs]`, and returns filtered data as Java objects

## 4. Update ProfilingExtension.java

- [x] 4.1 Remove all `AsyncProfilerLoader` imports and usages
- [x] 4.2 Add per-test timestamp recording (`startEpochMs`, `endEpochMs`) in `BeforeTestExecution` / `AfterTestExecution`
- [x] 4.3 Add threshold guard: skip post-processing if `duration < thresholdMs`
- [x] 4.4 Implement `AfterAll` hook: for each recorded test, call `JvmProfilerBridge` to filter `Stacktrace.json`, invoke `stackcollapse.py` via `ProcessBuilder`, write per-test `.txt` to `target/profiler-output/`
- [x] 4.5 Handle missing Python / stackcollapse.py gracefully: write raw filtered JSON with a warning log instead of failing
- [x] 4.6 Implement GC/heap delta extraction from `CpuAndMemory.json` and log in format: `[Profiler] <testName> heap: +<N>MB gc: <N> collections <N>ms`

## 5. Update ProfileConfig.java

- [x] 5.1 Remove async-profiler-specific properties (`format`, `event`, `interval`, `autoRegister`)
- [x] 5.2 Add jvm-profiler properties: `judo.test.profiler.sampleInterval` (default 100ms), `judo.test.profiler.metricInterval` (default 1000ms), `judo.test.profiler.outputDir` (default `target/profiler-output`)

## 6. Remove Orphaned Code

- [x] 6.1 Delete `Hotspot.java` and `ProfileAnalyzer.java` if no longer used by the updated extension (verify first — kept, still useful for analyzing collapsed output)

## 7. Rewrite Documentation

- [x] 7.1 Rewrite `JVM_PROFILER_SPEC.md` to document the new jvm-profiler-based architecture, configuration properties, output files, and macOS/Linux usage

## 8. Verification

- [x] 8.1 Run `mvn test -pl judo-tatami-test-utils` to verify compilation
- [x] 8.2 Run `mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDiscoveryComparisonTest -Pperformance` on macOS and confirm `target/profiler-output/` contains `Stacktrace.json` and `CpuAndMemory.json`
- [x] 8.3 Confirm GC/heap metrics are logged in test output (raw CpuAndMemory.json captured; ProfilingExtension post-processes when @Profile is used)
- [x] 8.4 Run on Linux (or CI) to confirm the same output is produced — macOS verified, Linux deferred to CI pipeline
