# AGENTS.md — `judo-tatami-test-utils/src/test/java/hu/blackbelt/judo/tatami/test/profiler`

Unit tests verifying collapsed stack profile analysis, profiler configuration defaults and builders, and JUnit 5 profiling extension annotations.

| File | Purpose |
| --- | --- |
| `ProfileAnalyzerTest.java` | Verifies `ProfileAnalyzer.parseHotspots` and `analyze` over sample collapsed stack traces. Checks sorting by sample count descending, percentage calculation, hotspot limiting, empty input handling, and malformed line tolerance. |
| `ProfileConfigTest.java` | Tests `ProfileConfig` default settings, builder overrides for sampling/thresholds/LLM options, and loading configuration from system properties via `fromSystemProperties()`. |
| `ProfilingExtensionTest.java` | Validates `ProfilingExtension` lifecycle instantiation, consistency with `ProfileConfig` default values, and retention/default attributes of the `@Profile` annotation. |
