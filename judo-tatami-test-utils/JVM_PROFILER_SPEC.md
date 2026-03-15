# JVM Profiler Integration for JUnit Tests

This specification describes a reusable profiling library that integrates with JUnit 5 to automatically capture CPU profiles and JVM metrics during test execution. It uses **Uber's jvm-profiler** (pure Java agent) and works on macOS and Linux without native library installation.

## Overview

The profiler records per-test time windows and post-processes the jvm-profiler agent output into per-test collapsed stack files. This enables performance analysis of transformation tests (PSM2ASM, ASM2RDBMS, etc.) without requiring manual instrumentation, and works out of the box on any platform.

## Architecture

```
Maven surefire (performance profile)
  └─ -javaagent:jvm-profiler.jar=FileOutputReporter,outputDir=target/profiler-output
       │
       ├─ Stacktrace.json    (one JSON line per sample, all tests)
       └─ CpuAndMemory.json  (GC, heap, threads per metricInterval)

JUnit 5 ProfilingExtension
  ├─ beforeTestExecution  → record startEpochMs
  ├─ afterTestExecution   → record endEpochMs (if duration >= thresholdMs)
  └─ afterAll             → for each test window:
                              • filter Stacktrace.json → per-test JSON
                              • run stackcollapse.py  → per-test .txt
                              • extract GC/heap delta from CpuAndMemory.json
                              • log: [Profiler] <test> heap: +NMB gc: N collections Nms
```

## Quick Start

```java
import hu.blackbelt.judo.tatami.test.profiler.Profile;

@Profile
class Psm2AsmPerformanceTest {

    @Test
    void testLargeModelTransformation() {
        // This test will be automatically profiled
        Psm2AsmWork work = new Psm2AsmWork(largeModel);
        work.execute();
    }
}
```

Run with:
```bash
mvn test -pl judo-tatami-psm2asm -Dtest=*PerformanceTest -Pperformance
```

Output in `target/profiler-output/`:
```
Psm2AsmPerformanceTest_testLargeModelTransformation.txt   ← collapsed stacks
Stacktrace.json                                            ← raw agent output
CpuAndMemory.json                                          ← GC/heap/thread metrics
```

## Configuration

### System Properties

| Property | Default | Description |
|---|---|---|
| `judo.test.profiler.enabled` | `false` | Enable profiling globally |
| `judo.test.profiler.outputDir` | `target/profiler-output` | Output directory |
| `judo.test.profiler.sampleInterval` | `100` | Stacktrace sampling interval (ms) |
| `judo.test.profiler.metricInterval` | `1000` | JVM metric collection interval (ms) |
| `judo.test.profiler.thresholdMs` | `0` | Minimum test duration to trigger output |
| `judo.test.profiler.llm.enabled` | `false` | Enable integrated LLM analysis |
| `judo.test.profiler.llm.provider` | `openai` | LLM provider name |

### @Profile Annotation

```java
@Profile(thresholdMs = 500)   // only profile tests taking > 500ms
class MyTest { ... }
```

### Maven surefire argLine (performance profile)

The `-javaagent` flag is configured in the `performance` Maven profile in `judo-tatami-test-utils/pom.xml`. Other modules inherit this via surefire configuration.

To override sampling rates:
```bash
mvn test -Pperformance \
  -Djudo.test.profiler.sampleInterval=50 \
  -Djudo.test.profiler.metricInterval=500
```

## How Per-Test Isolation Works

jvm-profiler runs continuously for the entire JVM lifetime. The extension uses **timestamp bridging**:

1. Records `startEpochMs` before each test
2. Records `endEpochMs` after each test
3. In `afterAll`, filters `Stacktrace.json` by `[startMs, endMs]` per test
4. Runs `stackcollapse.py` on the filtered data → per-test `.txt`

**Limitation**: Tests shorter than one `sampleInterval` (default 100ms) may capture zero frames. Use `thresholdMs` to skip short tests.

## Output Files

### Per-test collapsed stack (`.txt`)

Standard collapsed stack format, compatible with flamegraph.pl and LLM analysis:

```
com/example/Foo.bar;com/example/Bar.baz;... 42
com/example/Foo.bar;com/example/Other.method;... 18
```

To generate a flamegraph SVG:
```bash
flamegraph.pl target/profiler-output/MyTest_testFoo.txt > flamegraph.svg
```

### GC/Heap log output

Logged after each profiled test:
```
[Profiler] MyTest_testFoo heap: +128MB gc: 3 collections 45ms
```

## Platform Requirements

| Platform | Requirements |
|---|---|
| macOS | None — pure Java agent |
| Linux | None — pure Java agent |
| ~~Linux (old)~~ | ~~perf_event_paranoid=1~~ — **no longer needed** |

## Dependency

jvm-profiler is resolved from JitPack at build time:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.uber-common</groupId>
    <artifactId>jvm-profiler</artifactId>
    <version>07ecfce5bb</version>  <!-- pinned commit -->
    <scope>test</scope>
</dependency>
```

The version is pinned to a specific commit hash for reproducible builds. To update: find the desired commit on [uber-common/jvm-profiler](https://github.com/uber-common/jvm-profiler) and update the version in `judo-tatami-test-utils/pom.xml`.

## LLM Analysis Workflow

The collapsed format is optimized for external LLM tools:

```bash
# 1. Run profiled tests
mvn test -Dtest=Psm2AsmExternalModelTest -Pperformance

# 2. Ask Claude Code/Cursor/Copilot to analyze:
# "Analyze target/profiler-output/Psm2AsmExternalModelTest_testExternalModel.txt
#  and suggest optimizations"
```

### Optional Integrated LLM Analyzer

For automated analysis during test runs (disabled by default):

```bash
mvn test -Dtest=*PerformanceTest \
    -Djudo.test.profiler.enabled=true \
    -Djudo.test.profiler.llm.enabled=true \
    -Djudo.test.profiler.llm.provider=openrouter
```

Supported providers: `openai`, `anthropic`, `openrouter`, `deepseek`, `minimax`, `groq`, `together`, `ollama`

## GitHub Actions

No special configuration required — jvm-profiler is pure Java:

```yaml
- name: Run profiled tests
  run: mvn test -Pperformance

- name: Upload profiles
  uses: actions/upload-artifact@v4
  with:
    name: profiler-output
    path: '**/target/profiler-output/'
```
