# Add JVM Profiler Integration for JUnit Tests

## Summary

Add a reusable JVM profiling library to `judo-tatami-test-utils` that integrates with JUnit 5 to automatically capture CPU profiles during test execution. The profiler outputs data in collapsed stack format optimized for LLM-based analysis.

## Motivation

1. **Performance Analysis Need**: Transformation tests (PSM2ASM, ASM2RDBMS, Zeta vs ETL) require performance profiling to identify bottlenecks
2. **LLM-Assisted Optimization**: Modern development workflows use LLM tools (Claude Code, Cursor, Copilot) that can analyze profiling data and suggest optimizations
3. **Zero-Config Experience**: Developers should be able to profile tests without manual async-profiler setup
4. **Reusability**: A centralized profiling utility benefits all transformation modules

## Approach

### Core Architecture

```
judo-tatami-test-utils/
├── src/main/java/hu/blackbelt/judo/tatami/test/
│   └── profiler/
│       ├── ProfilingExtension.java     # JUnit 5 lifecycle extension
│       ├── Profile.java                # @Profile annotation
│       ├── ProfileConfig.java          # Configuration options
│       ├── ProfileAnalyzer.java        # Local hotspot detection
│       ├── NativeLoader.java           # Platform-specific library loading
│       └── llm/                         # Optional LLM integration
│           ├── LlmProvider.java
│           ├── LlmProviderFactory.java
│           └── OpenAiCompatibleProvider.java
└── src/main/resources/
    └── META-INF/services/
        └── org.junit.jupiter.api.extension.Extension
```

### Key Design Decisions

1. **ap-loader for Native Libraries**: Use `me.bechberger:ap-loader-all` instead of bundling natives manually
2. **Collapsed Format Default**: Token-efficient format ideal for LLM consumption
3. **External LLM Primary**: Outputs designed for external LLM tools (Claude Code, etc.)
4. **Integrated LLM Optional**: Built-in LLM analysis disabled by default, supports 8+ providers
5. **System Property Configuration**: All settings configurable via `-D` flags

## Impact

### Modules Affected
- `judo-tatami-test-utils` - New profiler package added
- All transformation test modules - Can optionally use `@Profile` annotation

### Dependencies Added
- `me.bechberger:ap-loader-all:3.0` (required)
- `org.junit.jupiter:junit-jupiter-api` (required)
- `dev.langchain4j:langchain4j-*` (optional, for integrated LLM)

### Breaking Changes
None. This is an additive change.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Native library compatibility issues | Medium | High | Use ap-loader which handles cross-platform binaries |
| perf_event permissions in CI | High | Medium | Document CI configuration in AGENTS.md |
| LLM API rate limits | Low | Low | Integrated LLM is optional and disabled by default |
| Profile output size | Medium | Low | Configurable thresholds and token management utilities |

## Success Criteria

1. Tests can be profiled with `@Profile` annotation or `-Djudo.test.profiler.enabled=true`
2. Profile output in `target/profiler-output/` is readable by external LLMs
3. Cross-platform support (Linux x64/arm64, macOS x64/arm64, Windows x64)
4. CI/CD integration documented and working in GitHub Actions

## Related Documents

- [JVM_PROFILER_SPEC.md](../../judo-tatami-test-utils/JVM_PROFILER_SPEC.md) - Full technical specification
- [AGENTS.md](../AGENTS.md) - Project documentation (to be updated)
