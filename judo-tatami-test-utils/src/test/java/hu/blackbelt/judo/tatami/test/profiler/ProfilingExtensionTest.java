package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProfilingExtension} JUnit lifecycle integration.
 * These tests verify behavior when native async-profiler is not available
 * (which is the common case in CI environments without native library permissions).
 */
class ProfilingExtensionTest {

    @Test
    void testExtensionInstantiates() {
        // ProfilingExtension should instantiate without errors even if native lib is unavailable
        ProfilingExtension extension = new ProfilingExtension();
        assertNotNull(extension);
    }

    @Test
    void testProfileConfigDefaultsAreConsistentWithExtension() {
        // Verify that the config used by the extension matches expected defaults
        ProfileConfig config = ProfileConfig.fromSystemProperties();

        // Default: profiling disabled unless explicitly enabled
        assertFalse(config.isEnabled(),
                "Profiling should be disabled by default to avoid slowing down normal test runs");
        assertEquals("collapsed", config.getOutputFormat(),
                "Default format should be collapsed for LLM analysis");
        assertEquals("cpu", config.getEvent(),
                "Default event should be cpu");
    }

    @Test
    void testProfileAnnotationDefaultValues() {
        // Verify @Profile annotation defaults on a sample class
        Profile annotation = SampleProfiledClass.class.getAnnotation(Profile.class);
        assertNotNull(annotation);
        assertEquals(0L, annotation.thresholdMs(), "Default threshold should be 0 (always profile)");
        assertEquals("collapsed", annotation.format(), "Default format should be collapsed");
        assertEquals("cpu", annotation.event(), "Default event should be cpu");
    }

    @Test
    void testProfileAnnotationCustomValues() {
        Profile annotation = SampleProfiledClassCustom.class.getAnnotation(Profile.class);
        assertNotNull(annotation);
        assertEquals(500L, annotation.thresholdMs());
        assertEquals("flamegraph", annotation.format());
        assertEquals("wall", annotation.event());
    }

    @Test
    void testFileExtensionForFormats() {
        assertEquals(".txt", ProfileConfig.builder().outputFormat("collapsed").build().getFileExtension());
        assertEquals(".svg", ProfileConfig.builder().outputFormat("flamegraph").build().getFileExtension());
        assertEquals(".jfr", ProfileConfig.builder().outputFormat("jfr").build().getFileExtension());
    }

    @Test
    void testProfilerOutputArgForFormats() {
        assertEquals("collapsed", ProfileConfig.builder().outputFormat("collapsed").build().getProfilerOutputArg());
        assertEquals("flamegraph", ProfileConfig.builder().outputFormat("flamegraph").build().getProfilerOutputArg());
        assertEquals("jfr", ProfileConfig.builder().outputFormat("jfr").build().getProfilerOutputArg());
    }

    // Sample classes for annotation testing
    @Profile
    static class SampleProfiledClass {}

    @Profile(thresholdMs = 500, format = "flamegraph", event = "wall")
    static class SampleProfiledClassCustom {}
}
