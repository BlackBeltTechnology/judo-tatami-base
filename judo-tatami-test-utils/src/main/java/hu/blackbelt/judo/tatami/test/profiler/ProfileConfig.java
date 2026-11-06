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
     * Output directory for profiler files.
     * System property: judo.test.profiler.outputDir
     */
    @Builder.Default
    String outputDir = System.getProperty(PROPERTY_PREFIX + "outputDir", "target/profiler-output");

    /**
     * Stacktrace sampling interval in milliseconds (matches jvm-profiler sampleInterval).
     * System property: judo.test.profiler.sampleInterval
     */
    @Builder.Default
    long sampleIntervalMs = Long.parseLong(System.getProperty(PROPERTY_PREFIX + "sampleInterval", "100"));

    /**
     * JVM metric collection interval in milliseconds (matches jvm-profiler metricInterval).
     * System property: judo.test.profiler.metricInterval
     */
    @Builder.Default
    long metricIntervalMs = Long.parseLong(System.getProperty(PROPERTY_PREFIX + "metricInterval", "1000"));

    /**
     * Minimum test duration in milliseconds to trigger post-processing.
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

}
