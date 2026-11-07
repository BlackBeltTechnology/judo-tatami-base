package hu.blackbelt.judo.tatami.test.profiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyzes collapsed stack profile data to identify CPU hotspots.
 * <p>
 * This analyzer works with the collapsed stack format produced by async-profiler:
 * <pre>
 * method1;method2;method3 123
 * method1;method4 456
 * </pre>
 * Where the number at the end is the sample count.
 * <p>
 * Example usage:
 * <pre>{@code
 * String analysis = ProfileAnalyzer.analyze(Path.of("target/profiler-output/test.txt"));
 * System.out.println(analysis);
 * }</pre>
 */
public class ProfileAnalyzer {

    private static final int DEFAULT_LIMIT = 10;

    /**
     * Analyzes a collapsed profile file and returns a formatted summary.
     *
     * @param collapsedProfile Path to the collapsed stack format profile
     * @return A formatted string with top hotspots
     * @throws IOException If the file cannot be read
     */
    public static String analyze(Path collapsedProfile) throws IOException {
        return analyze(collapsedProfile, DEFAULT_LIMIT);
    }

    /**
     * Analyzes a collapsed profile file and returns a formatted summary.
     *
     * @param collapsedProfile Path to the collapsed stack format profile
     * @param limit            Maximum number of hotspots to return
     * @return A formatted string with top hotspots
     * @throws IOException If the file cannot be read
     */
    public static String analyze(Path collapsedProfile, int limit) throws IOException {
        String content = Files.readString(collapsedProfile);
        return analyze(content, limit);
    }

    /**
     * Analyzes collapsed profile content and returns a formatted summary.
     *
     * @param collapsedContent The collapsed stack format content
     * @param limit            Maximum number of hotspots to return
     * @return A formatted string with top hotspots
     */
    public static String analyze(String collapsedContent, int limit) {
        List<Hotspot> hotspots = parseHotspots(collapsedContent, limit);

        StringBuilder summary = new StringBuilder();
        summary.append("=== Profile Analysis ===\n");
        summary.append("Top CPU hotspots:\n\n");

        for (int i = 0; i < hotspots.size(); i++) {
            Hotspot h = hotspots.get(i);
            summary.append(String.format("%2d. %s\n", i + 1, h.toDisplayString()));
        }

        return summary.toString();
    }

    /**
     * Parses collapsed profile content and returns the top hotspots.
     *
     * @param collapsedContent The collapsed stack format content
     * @param limit            Maximum number of hotspots to return
     * @return List of hotspots sorted by sample count (descending)
     */
    public static List<Hotspot> parseHotspots(String collapsedContent, int limit) {
        // Parse lines and aggregate by leaf method
        Map<String, Long> methodSamples = collapsedContent.lines()
                .filter(line -> !line.isBlank() && line.contains(" "))
                .map(ProfileAnalyzer::parseLine)
                .filter(entry -> entry != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.summingLong(Map.Entry::getValue)
                ));

        // Calculate total samples
        long totalSamples = methodSamples.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        // Convert to Hotspot records and sort
        return methodSamples.entrySet().stream()
                .map(e -> new Hotspot(
                        e.getKey(),
                        e.getValue(),
                        totalSamples > 0 ? (e.getValue() * 100.0 / totalSamples) : 0.0
                ))
                .sorted(Comparator.comparingLong(Hotspot::samples).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Parses a single line from collapsed format.
     * Format: "method1;method2;method3 123"
     *
     * @param line A line from the collapsed profile
     * @return Entry with leaf method name and sample count, or null if invalid
     */
    private static Map.Entry<String, Long> parseLine(String line) {
        try {
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace <= 0) {
                return null;
            }

            String stack = line.substring(0, lastSpace);
            String countStr = line.substring(lastSpace + 1).trim();

            // Get the leaf method (last in the stack)
            int lastSemicolon = stack.lastIndexOf(';');
            String leafMethod = lastSemicolon >= 0 ? stack.substring(lastSemicolon + 1) : stack;

            long count = Long.parseLong(countStr);

            return Map.entry(leafMethod, count);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Truncates profile content for LLM analysis to limit token usage.
     *
     * @param collapsedContent The full collapsed profile content
     * @param maxLines         Maximum number of lines to keep
     * @return Truncated content
     */
    public static String truncateForLlm(String collapsedContent, int maxLines) {
        return collapsedContent.lines()
                .limit(maxLines)
                .collect(Collectors.joining("\n"));
    }
}
