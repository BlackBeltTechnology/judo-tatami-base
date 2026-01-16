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

        assertEquals("target/profiler-output/", config.getOutputPath());
        assertEquals("collapsed", config.getOutputFormat());
        assertEquals(1_000_000L, config.getSamplingInterval());
        assertEquals("cpu", config.getEvent());
        assertEquals(0L, config.getThresholdMs());
        assertFalse(config.isEnabled());
        assertTrue(config.isAutoRegister());
        assertFalse(config.isLlmEnabled());
        assertEquals("openai", config.getLlmProvider());
    }

    @Test
    void testBuilderOverrides() {
        ProfileConfig config = ProfileConfig.builder()
                .outputPath("custom/path/")
                .outputFormat("flamegraph")
                .samplingInterval(5_000_000L)
                .event("wall")
                .thresholdMs(500L)
                .enabled(true)
                .llmEnabled(true)
                .llmProvider("deepseek")
                .build();

        assertEquals("custom/path/", config.getOutputPath());
        assertEquals("flamegraph", config.getOutputFormat());
        assertEquals(5_000_000L, config.getSamplingInterval());
        assertEquals("wall", config.getEvent());
        assertEquals(500L, config.getThresholdMs());
        assertTrue(config.isEnabled());
        assertTrue(config.isLlmEnabled());
        assertEquals("deepseek", config.getLlmProvider());
    }

    @Test
    void testFileExtension() {
        assertEquals(".txt", ProfileConfig.builder().outputFormat("collapsed").build().getFileExtension());
        assertEquals(".svg", ProfileConfig.builder().outputFormat("flamegraph").build().getFileExtension());
        assertEquals(".jfr", ProfileConfig.builder().outputFormat("jfr").build().getFileExtension());
        assertEquals(".txt", ProfileConfig.builder().outputFormat("COLLAPSED").build().getFileExtension());
        assertEquals(".txt", ProfileConfig.builder().outputFormat("unknown").build().getFileExtension());
    }

    @Test
    void testProfilerOutputArg() {
        assertEquals("collapsed", ProfileConfig.builder().outputFormat("collapsed").build().getProfilerOutputArg());
        assertEquals("flamegraph", ProfileConfig.builder().outputFormat("flamegraph").build().getProfilerOutputArg());
        assertEquals("jfr", ProfileConfig.builder().outputFormat("jfr").build().getProfilerOutputArg());
    }

    @Test
    void testFromSystemProperties() {
        // This test verifies the factory method works
        ProfileConfig config = ProfileConfig.fromSystemProperties();
        assertNotNull(config);
    }
}
