# Profiler Core Specification

## Purpose

Provide JUnit 5 integration for async-profiler to automatically capture CPU profiles during test execution with minimal configuration.

## ADDED Requirements

### Requirement: JUnit 5 Profiling Extension

The profiler SHALL provide a JUnit 5 extension that hooks into the test lifecycle to start/stop profiling automatically.

#### Scenario: Basic test profiling with annotation
Given a test class annotated with `@Profile`
When the test method executes
Then profiling starts before test execution
And profiling stops after test execution
And profile data is written to `target/profiler-output/`

#### Scenario: Profiling disabled by default
Given a test class without `@Profile` annotation
And system property `judo.test.profiler.enabled` is not set
When the test method executes
Then no profiling occurs

#### Scenario: Global profiling via system property
Given a test class without `@Profile` annotation
And system property `judo.test.profiler.enabled=true`
When the test method executes
Then profiling occurs for all tests

---

### Requirement: Profile Output Formats

The profiler SHALL support multiple output formats with collapsed stack as the default.

#### Scenario: Collapsed format output (default)
Given profiling is enabled
And no format is specified
When the test completes
Then profile output is in collapsed stack format
And output file has `.txt` extension
And format is `method1;method2;method3 <sample_count>`

#### Scenario: Flamegraph SVG output
Given profiling is enabled with `format=flamegraph`
When the test completes
Then profile output is an SVG flamegraph
And output file has `.svg` extension

#### Scenario: JFR format output
Given profiling is enabled with `format=jfr`
When the test completes
Then profile output is in Java Flight Recorder format
And output file has `.jfr` extension

---

### Requirement: Profiling Event Types

The profiler SHALL support multiple event types for different analysis scenarios.

#### Scenario: CPU event profiling (default)
Given profiling is enabled with `event=cpu`
When the test executes CPU-bound operations
Then CPU cycles are sampled
And hotspots reflect actual CPU usage

#### Scenario: Wall-clock profiling
Given profiling is enabled with `event=wall`
When the test executes I/O-bound operations
Then wall-clock time is sampled
And waiting time is included in profile

#### Scenario: Memory allocation profiling
Given profiling is enabled with `event=alloc`
When the test allocates objects
Then memory allocations are sampled
And allocation hotspots are identified

#### Scenario: Lock contention profiling
Given profiling is enabled with `event=lock`
When the test has concurrent threads
Then lock contention is sampled
And synchronization bottlenecks are identified

---

### Requirement: Threshold-Based Profiling

The profiler SHALL support conditional profiling based on test duration.

#### Scenario: Profile only slow tests
Given profiling is enabled with `thresholdMs=500`
When a test completes in 200ms
Then no profile is generated

#### Scenario: Profile tests exceeding threshold
Given profiling is enabled with `thresholdMs=500`
When a test completes in 800ms
Then profile is generated

---

### Requirement: Configuration via System Properties

The profiler SHALL be configurable via system properties for CI/CD integration.

#### Scenario: Configure output path
Given system property `judo.test.profiler.outputPath=custom/path/`
When profiling completes
Then output is written to `custom/path/`

#### Scenario: Configure sampling interval
Given system property `judo.test.profiler.interval=5000000`
When profiling occurs
Then sampling interval is 5ms (5000000 nanoseconds)

#### Scenario: Disable profiling globally
Given system property `judo.test.profiler.enabled=false`
When tests execute with `@Profile` annotation
Then no profiling occurs

---

### Requirement: Profile Annotation

The profiler SHALL provide an `@Profile` annotation for declarative configuration.

#### Scenario: Class-level annotation
Given `@Profile` annotation on test class
When any test method in the class executes
Then profiling applies to all methods

#### Scenario: Method-level annotation override
Given `@Profile(format="collapsed")` on test class
And `@Profile(format="flamegraph")` on specific method
When that method executes
Then flamegraph format is used (method overrides class)

#### Scenario: Annotation parameters
Given `@Profile(thresholdMs=100, format="collapsed", event="cpu")`
When a matching test executes
Then the specified configuration is applied

---

### Requirement: Local Profile Analysis

The profiler SHALL provide local hotspot detection without requiring external services.

#### Scenario: Top hotspot extraction
Given a collapsed profile file
When `ProfileAnalyzer.analyze(path)` is called
Then top 10 CPU hotspots are returned
And each hotspot includes method name and percentage

#### Scenario: Configurable hotspot limit
Given a collapsed profile file
When `ProfileAnalyzer.analyze(path, limit=5)` is called
Then top 5 CPU hotspots are returned
