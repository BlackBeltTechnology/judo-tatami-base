package hu.blackbelt.judo.tatami.test.profiler;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration options for the JVM profiler.
 * Settings can be overridden via system properties with the prefix "judo.test.profiler."
 */
@Value
@Builder
public class ProfileConfig {

    public static final String PROPERTY_PREFIX = "judo.test.profiler.";

    /**
     * Output directory for profile files.
     * System property: judo.test.profiler.outputPath
     */
    @Builder.Default
    String outputPath = System.getProperty(PROPERTY_PREFIX + "outputPath", "target/profiler-output/");

    /**
     * Output format: collapsed, flamegraph, or jfr.
     * System property: judo.test.profiler.format
     */
    @Builder.Default
    String outputFormat = System.getProperty(PROPERTY_PREFIX + "format", "collapsed");

    /**
     * Sampling interval in nanoseconds. Default is 1ms (1,000,000 ns).
     * System property: judo.test.profiler.interval
     */
    @Builder.Default
    long samplingInterval = Long.parseLong(System.getProperty(PROPERTY_PREFIX + "interval", "1000000"));

    /**
     * Profiling event type: cpu, wall, alloc, or lock.
     * System property: judo.test.profiler.event
     */
    @Builder.Default
    String event = System.getProperty(PROPERTY_PREFIX + "event", "cpu");

    /**
     * Minimum test duration in milliseconds to trigger profiling.
     * Tests completing faster than this threshold will not generate profiles.
     * System property: judo.test.profiler.thresholdMs
     */
    @Builder.Default
    long thresholdMs = Long.parseLong(System.getProperty(PROPERTY_PREFIX + "thresholdMs", "0"));

    /**
     * Global enable/disable flag for profiling.
     * System property: judo.test.profiler.enabled
     */
    @Builder.Default
    boolean enabled = Boolean.parseBoolean(System.getProperty(PROPERTY_PREFIX + "enabled", "false"));

    /**
     * Enable auto-registration via Service Loader.
     * System property: judo.test.profiler.autoRegister
     */
    @Builder.Default
    boolean autoRegister = Boolean.parseBoolean(System.getProperty(PROPERTY_PREFIX + "autoRegister", "true"));

    /**
     * Enable integrated LLM analysis (disabled by default).
     * System property: judo.test.profiler.llm.enabled
     */
    @Builder.Default
    boolean llmEnabled = Boolean.parseBoolean(System.getProperty(PROPERTY_PREFIX + "llm.enabled", "false"));

    /**
     * LLM provider name: openai, anthropic, openrouter, deepseek, minimax, groq, together, ollama.
     * System property: judo.test.profiler.llm.provider
     */
    @Builder.Default
    String llmProvider = System.getProperty(PROPERTY_PREFIX + "llm.provider", "openai");

    /**
     * Creates a default configuration from system properties.
     */
    public static ProfileConfig fromSystemProperties() {
        return ProfileConfig.builder().build();
    }

    /**
     * Returns the file extension for the configured output format.
     */
    public String getFileExtension() {
        return switch (outputFormat.toLowerCase()) {
            case "flamegraph" -> ".svg";
            case "jfr" -> ".jfr";
            default -> ".txt";
        };
    }

    /**
     * Returns the async-profiler output argument for the configured format.
     */
    public String getProfilerOutputArg() {
        return switch (outputFormat.toLowerCase()) {
            case "flamegraph" -> "flamegraph";
            case "jfr" -> "jfr";
            default -> "collapsed";
        };
    }
}
