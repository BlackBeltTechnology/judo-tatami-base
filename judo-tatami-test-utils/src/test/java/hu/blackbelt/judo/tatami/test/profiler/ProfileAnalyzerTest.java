package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProfileAnalyzer}.
 */
class ProfileAnalyzerTest {

    private static final String SAMPLE_COLLAPSED_PROFILE = """
            java/lang/Thread.run;com/example/Service.process;com/example/Dao.query 500
            java/lang/Thread.run;com/example/Service.process;com/example/Dao.save 300
            java/lang/Thread.run;com/example/Service.process 100
            java/lang/Thread.run;com/example/Controller.handle 100
            """;

    @Test
    void testParseHotspots() {
        List<Hotspot> hotspots = ProfileAnalyzer.parseHotspots(SAMPLE_COLLAPSED_PROFILE, 10);

        assertEquals(4, hotspots.size());

        // Verify sorted by sample count descending
        assertEquals("com/example/Dao.query", hotspots.get(0).method());
        assertEquals(500L, hotspots.get(0).samples());
        assertEquals(50.0, hotspots.get(0).percentage(), 0.01);

        assertEquals("com/example/Dao.save", hotspots.get(1).method());
        assertEquals(300L, hotspots.get(1).samples());
        assertEquals(30.0, hotspots.get(1).percentage(), 0.01);
    }

    @Test
    void testParseHotspotsWithLimit() {
        List<Hotspot> hotspots = ProfileAnalyzer.parseHotspots(SAMPLE_COLLAPSED_PROFILE, 2);

        assertEquals(2, hotspots.size());
        assertEquals("com/example/Dao.query", hotspots.get(0).method());
        assertEquals("com/example/Dao.save", hotspots.get(1).method());
    }

    @Test
    void testAnalyzeFormat() {
        String analysis = ProfileAnalyzer.analyze(SAMPLE_COLLAPSED_PROFILE, 3);

        assertTrue(analysis.contains("=== Profile Analysis ==="));
        assertTrue(analysis.contains("Top CPU hotspots:"));
        assertTrue(analysis.contains("com/example/Dao.query"));
        assertTrue(analysis.contains("50.0%"));
    }

    @Test
    void testEmptyProfile() {
        List<Hotspot> hotspots = ProfileAnalyzer.parseHotspots("", 10);
        assertTrue(hotspots.isEmpty());
    }

    @Test
    void testMalformedLines() {
        String malformed = """
                valid/method 100
                no_count_here
                another/method 50
                """;

        List<Hotspot> hotspots = ProfileAnalyzer.parseHotspots(malformed, 10);

        // Should skip malformed lines
        assertEquals(2, hotspots.size());
    }

    @Test
    void testTruncateForLlm() {
        String content = "line1\nline2\nline3\nline4\nline5";
        String truncated = ProfileAnalyzer.truncateForLlm(content, 3);

        assertEquals("line1\nline2\nline3", truncated);
    }

    @Test
    void testHotspotDisplayString() {
        Hotspot hotspot = new Hotspot("com/example/Method.test", 1234, 45.67);
        String display = hotspot.toDisplayString();

        assertTrue(display.contains("45.7%"));
        assertTrue(display.contains("1234 samples"));
        assertTrue(display.contains("com/example/Method.test"));
    }
}
