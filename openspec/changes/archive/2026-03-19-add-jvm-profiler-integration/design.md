# JVM Profiler Integration - Design Document

## Overview

This document describes the architecture and design decisions for integrating JVM profiling capabilities into the judo-tatami-test-utils module.

## System Context

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Development Environment                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────┐      ┌─────────────────┐      ┌─────────────┐ │
│  │  Test Module    │      │  test-utils     │      │ async-      │ │
│  │  (psm2asm,etc)  │─────▶│  profiler pkg   │─────▶│ profiler    │ │
│  └─────────────────┘      └─────────────────┘      └─────────────┘ │
│         │                        │                       │         │
│         ▼                        ▼                       ▼         │
│  ┌─────────────────┐      ┌─────────────────┐      ┌─────────────┐ │
│  │  @Profile       │      │  target/        │      │  Native     │ │
│  │  annotation     │      │  profiler-      │      │  Libraries  │ │
│  │                 │      │  output/        │      │  (so/dylib) │ │
│  └─────────────────┘      └─────────────────┘      └─────────────┘ │
│                                  │                                  │
│                                  ▼                                  │
│                    ┌─────────────────────────┐                     │
│                    │  External LLM Tools     │                     │
│                    │  (Claude Code, Cursor)  │                     │
│                    └─────────────────────────┘                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Architecture

### Core Components

```
ProfilingExtension
├── BeforeAllCallback          # Extract native library once per suite
├── BeforeTestExecutionCallback # Start profiling
└── AfterTestExecutionCallback  # Stop profiling, write output

ProfileConfig
├── outputPath                  # Where to write profiles
├── outputFormat                # collapsed | flamegraph | jfr
├── samplingInterval            # Nanoseconds between samples
├── event                       # cpu | wall | alloc | lock
├── thresholdMs                 # Minimum test duration to profile
└── enabled                     # Global enable/disable

NativeLoader
├── extractAndGetPath()         # Extract native lib from JAR
├── detectPlatform()            # Determine OS/arch
└── getLibraryName()            # Platform-specific lib name
```

### Optional LLM Components

```
LlmProvider (interface)
└── complete(prompt) → String

LlmProviderFactory
├── PROVIDERS map               # Pre-configured provider configs
└── create() → LlmProvider      # Factory method

OpenAiCompatibleProvider
├── endpoint                    # API URL
├── model                       # Model identifier
├── apiKey                      # From environment variable
└── complete(prompt)            # HTTP POST to API
```

## Design Decisions

### Decision 1: Use ap-loader vs Manual Native Bundling

**Options Considered:**
1. Bundle native libraries manually in `src/main/resources/natives/`
2. Use `ap-loader-all` dependency

**Decision: Use ap-loader**

**Rationale:**
- ap-loader maintains platform binaries for Linux (x64, arm64), macOS (x64, arm64), Windows
- Automatic updates with new async-profiler releases
- Reduces maintenance burden
- Single-line loading: `AsyncProfilerLoader.load()`

**Trade-offs:**
- Additional dependency (~15MB)
- Less control over specific profiler versions

### Decision 2: Output Format

**Options Considered:**
1. Collapsed stack format (text)
2. Flamegraph SVG
3. JFR (Java Flight Recorder)
4. All formats simultaneously

**Decision: Collapsed format as default, others optional**

**Rationale:**
- Collapsed format is most token-efficient for LLM analysis
- Human-readable text file
- Flamegraph can be generated post-hoc from collapsed format
- JFR adds complexity and larger file sizes

### Decision 3: LLM Integration Architecture

**Options Considered:**
1. No LLM integration - output only
2. Integrated LLM only
3. External LLM primary, integrated optional

**Decision: External LLM primary, integrated optional (disabled by default)**

**Rationale:**
- External LLMs (Claude Code, Cursor) have full codebase context
- No API keys required in library
- Integrated option available for automated CI analysis
- Multi-provider support for flexibility

### Decision 4: Configuration Mechanism

**Options Considered:**
1. Java API only (builder pattern)
2. Annotation parameters only
3. System properties only
4. All three

**Decision: All three with precedence**

**Precedence (highest to lowest):**
1. Method-level `@Profile` annotation
2. Class-level `@Profile` annotation
3. System properties (`-Djudo.test.profiler.*`)
4. Defaults in `ProfileConfig`

### Decision 5: Extension Registration

**Options Considered:**
1. Explicit `@ExtendWith(ProfilingExtension.class)` only
2. Service Loader auto-registration only
3. Both with toggle

**Decision: Both with toggle**

**Rationale:**
- Auto-registration provides zero-config experience
- Explicit annotation allows selective profiling
- System property toggle (`-Djudo.test.profiler.autoRegister=false`) for control

## Data Flow

### Test Execution Flow

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. JUnit Discovers Test Suite                                    │
└──────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 2. BeforeAllCallback: NativeLoader.extractAndGetPath()           │
│    - Detect OS/arch                                              │
│    - Extract native lib to temp directory                        │
│    - AsyncProfiler.getInstance(path)                             │
└──────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 3. BeforeTestExecutionCallback: profiler.start(EVENT_CPU, 1ms)   │
└──────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 4. Test Method Executes (profiled)                               │
└──────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 5. AfterTestExecutionCallback:                                   │
│    - profiler.stop()                                             │
│    - profiler.execute("dump,output=collapsed")                   │
│    - Write to target/profiler-output/TestClass_testMethod.txt    │
│    - (Optional) Call integrated LLM for analysis                 │
└──────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 6. External LLM reads output file for analysis                   │
└──────────────────────────────────────────────────────────────────┘
```

### LLM Provider Selection Flow

```
System.getProperty("judo.test.profiler.llm.provider")
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
        "openai"         "openrouter"       "ollama"
              │                │                │
              ▼                ▼                ▼
     ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
     │ OpenAI API  │  │ OpenRouter  │  │ Local       │
     │ gpt-4o-mini │  │ claude-3    │  │ llama3.1    │
     └─────────────┘  └─────────────┘  └─────────────┘
```

## Security Considerations

1. **API Keys**: Never stored in code; read from environment variables only
2. **Profile Data**: Contains method names and stack traces - may reveal internal architecture
3. **LLM Data Transmission**: Only when explicitly enabled; data truncated to limit exposure
4. **Native Libraries**: Extracted to temp directory with `deleteOnExit`

## Performance Considerations

1. **Profiling Overhead**: ~2-5% CPU overhead during profiling
2. **Native Loading**: One-time extraction per JVM (~100ms)
3. **Output Writing**: Synchronous write; fast for collapsed format
4. **LLM Calls**: Async option available; timeout configurable

## Platform Support Matrix

| Platform | Architecture | Native Library | Status |
|----------|--------------|----------------|--------|
| Linux | x64 | libasyncProfiler.so | Supported |
| Linux | arm64 | libasyncProfiler.so | Supported |
| macOS | x64 | libasyncProfiler.dylib | Supported |
| macOS | arm64 | libasyncProfiler.dylib | Supported |
| Windows | x64 | asyncProfiler.dll | Supported |

## Future Considerations

1. **Aggregated Reports**: Combine multiple test profiles into single report
2. **Baseline Comparison**: Compare against previous runs for regression detection
3. **Custom Events**: Support for custom profiling events beyond CPU/wall/alloc/lock
4. **IDE Integration**: IntelliJ/VS Code plugins for inline profile visualization
