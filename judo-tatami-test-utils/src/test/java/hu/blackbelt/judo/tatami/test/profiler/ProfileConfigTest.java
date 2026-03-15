package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProfileConfig}.
 */
class ProfileConfigTest {

    @Test
    void testDefaultValues() {
        ProfileConfig config = ProfileConfig.builder().build();

        assertEquals("target/profiler-output", config.getOutputDir());
        assertEquals(100L, config.getSampleIntervalMs());
        assertEquals(1000L, config.getMetricIntervalMs());
        assertEquals(0L, config.getThresholdMs());
        assertFalse(config.isEnabled());
        assertFalse(config.isLlmEnabled());
        assertEquals("openai", config.getLlmProvider());
    }

    @Test
    void testBuilderOverrides() {
        ProfileConfig config = ProfileConfig.builder()
                .outputDir("custom/path")
                .sampleIntervalMs(50L)
                .metricIntervalMs(500L)
                .thresholdMs(500L)
                .enabled(true)
                .llmEnabled(true)
                .llmProvider("deepseek")
                .build();

        assertEquals("custom/path", config.getOutputDir());
        assertEquals(50L, config.getSampleIntervalMs());
        assertEquals(500L, config.getMetricIntervalMs());
        assertEquals(500L, config.getThresholdMs());
        assertTrue(config.isEnabled());
        assertTrue(config.isLlmEnabled());
        assertEquals("deepseek", config.getLlmProvider());
    }

    @Test
    void testFromSystemProperties() {
        ProfileConfig config = ProfileConfig.fromSystemProperties();
        assertNotNull(config);
    }
}
