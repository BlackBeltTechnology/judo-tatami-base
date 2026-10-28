# Profiler Native Bundling Specification

## Purpose

Provide plug-and-play native library loading for async-profiler across all supported platforms without requiring manual installation.

## ADDED Requirements

### Requirement: Cross-Platform Native Library Support

The profiler SHALL support all major platforms without manual native library installation.

#### Scenario: Linux x64 support
Given the JVM is running on Linux x64
When the profiler extension initializes
Then the Linux x64 native library is loaded
And profiling functions correctly

#### Scenario: Linux arm64 support
Given the JVM is running on Linux arm64 (aarch64)
When the profiler extension initializes
Then the Linux arm64 native library is loaded
And profiling functions correctly

#### Scenario: macOS x64 support
Given the JVM is running on macOS x64
When the profiler extension initializes
Then the macOS x64 native library is loaded
And profiling functions correctly

#### Scenario: macOS arm64 (Apple Silicon) support
Given the JVM is running on macOS arm64 (Apple Silicon)
When the profiler extension initializes
Then the macOS arm64 native library is loaded
And profiling functions correctly

#### Scenario: Windows x64 support
Given the JVM is running on Windows x64
When the profiler extension initializes
Then the Windows x64 native library is loaded
And profiling functions correctly

#### Scenario: Unsupported platform handling
Given the JVM is running on an unsupported platform
When the profiler extension initializes
Then an `UnsupportedOperationException` is thrown
And the error message includes OS name and architecture

---

### Requirement: ap-loader Integration

The profiler SHALL use ap-loader for native library management.

#### Scenario: Automatic native loading
Given ap-loader dependency is available
When `AsyncProfilerLoader.load()` is called
Then the correct platform binary is extracted
And the native library is loaded into the JVM
And an `AsyncProfiler` instance is returned

#### Scenario: One-time extraction per JVM
Given multiple test classes use profiling
When the profiler initializes for each test class
Then native library extraction occurs only once
And subsequent calls reuse the extracted library

---

### Requirement: BeforeAllCallback Native Loading

The profiler extension SHALL load native libraries once per test suite.

#### Scenario: Native loading in BeforeAllCallback
Given a test class with `@Profile` annotation
When the test suite starts
Then `BeforeAllCallback.beforeAll()` extracts native library
And `AsyncProfiler.getInstance(path)` is called once
And all test methods share the same profiler instance

#### Scenario: Thread-safe extraction
Given multiple test suites run in parallel
When native extraction is triggered concurrently
Then extraction is synchronized
And no race conditions occur
And each JVM has exactly one extracted library

---

### Requirement: Temporary File Management

The profiler SHALL properly manage temporary native library files.

#### Scenario: Temp file creation
Given native library needs extraction
When extraction occurs
Then a unique temp file is created with `Files.createTempFile()`
And file name includes "async-profiler-" prefix
And file extension matches platform (.so, .dylib, .dll)

#### Scenario: Temp file cleanup
Given a temp native library file exists
When the JVM shuts down
Then the temp file is deleted via `deleteOnExit()`

#### Scenario: Parallel test isolation
Given multiple test processes run simultaneously
When each process extracts the native library
Then each process creates a unique temp file
And no file collisions occur

---

### Requirement: Fallback Native Loader

The profiler SHALL provide a fallback loader for environments without ap-loader.

#### Scenario: Manual native bundling structure
Given natives are bundled at `/natives/<platform>/`
When `NativeLoader.extractAndGetPath()` is called
Then the correct platform library is extracted
And the path to the extracted library is returned

#### Scenario: Platform detection
Given `NativeLoader.extractAndGetPath()` is called
When detecting platform
Then `os.name` system property determines OS
And `os.arch` system property determines architecture
And combined lookup selects correct native library

---

### Requirement: Environment Prerequisites Documentation

The profiler documentation SHALL include platform-specific setup requirements.

#### Scenario: Linux perf_event configuration
Given Linux deployment environment
When profiler documentation is consulted
Then instructions for `perf_event_paranoid` are provided
And required value (1 or lower) is specified
And command `echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid` is documented

#### Scenario: macOS dtrace access
Given macOS deployment environment
When profiler documentation is consulted
Then dtrace access requirements are documented
And elevated privilege workarounds are provided

#### Scenario: CI/CD GitHub Actions configuration
Given GitHub Actions CI environment
When profiler documentation is consulted
Then YAML configuration snippet is provided
And `perf_event_paranoid` setup step is included
And artifact upload for profiles is documented
