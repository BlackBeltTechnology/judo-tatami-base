package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProfilingExtension} JUnit lifecycle integration.
 */
class ProfilingExtensionTest {

    @Test
    void testExtensionInstantiates() {
        ProfilingExtension extension = new ProfilingExtension();
        assertNotNull(extension);
    }

    @Test
    void testProfileConfigDefaultsAreConsistentWithExtension() {
        ProfileConfig config = ProfileConfig.fromSystemProperties();

        assertFalse(config.isEnabled(),
                "Profiling should be disabled by default to avoid slowing down normal test runs");
        assertEquals("target/profiler-output", config.getOutputDir(),
                "Default output directory should be target/profiler-output");
        assertEquals(100L, config.getSampleIntervalMs(),
                "Default sample interval should be 100ms");
    }

    @Test
    void testProfileAnnotationDefaultValues() {
        Profile annotation = SampleProfiledClass.class.getAnnotation(Profile.class);
        assertNotNull(annotation);
        assertEquals(0L, annotation.thresholdMs(), "Default threshold should be 0 (always profile)");
    }

    @Test
    void testProfileAnnotationCustomValues() {
        Profile annotation = SampleProfiledClassCustom.class.getAnnotation(Profile.class);
        assertNotNull(annotation);
        assertEquals(500L, annotation.thresholdMs());
    }

    // Sample classes for annotation testing
    @Profile
    static class SampleProfiledClass {}

    @Profile(thresholdMs = 500)
    static class SampleProfiledClassCustom {}
}
