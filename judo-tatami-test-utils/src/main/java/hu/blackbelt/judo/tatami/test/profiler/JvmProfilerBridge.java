package hu.blackbelt.judo.tatami.test.profiler;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utility class for reading and filtering jvm-profiler output files.
 *
 * <p>jvm-profiler (via FileOutputReporter) writes one JSON entry per line to:
 * <ul>
 *   <li>{@code Stacktrace.json} — CPU stacktrace samples</li>
 *   <li>{@code CpuAndMemory.json} — GC, heap, thread metrics</li>
 * </ul>
 *
 * <p>Each line is a self-contained JSON object. This class filters lines by
 * {@code epochMillis} to extract data for a specific test's time window.
 */
public class JvmProfilerBridge {

    private static final Logger LOG = Logger.getLogger(JvmProfilerBridge.class.getName());

    private JvmProfilerBridge() {
        // Utility class
    }

    /**
     * Represents a single stacktrace sample from Stacktrace.json.
     */
    public record StacktraceSample(long epochMillis, String json) {}

    /**
     * Represents a single CPU/memory metric entry from CpuAndMemory.json.
     */
    public record CpuMemorySample(long epochMillis, long heapUsedBytes, long gcTotalCount, long gcTotalTimeMs, String json) {}

    /**
     * Summarized GC and heap delta for a test time window.
     */
    public record JvmMetricsDelta(long heapDeltaMb, long gcCollections, long gcTimeMs) {}

    /**
     * Filters Stacktrace.json entries that fall within the given time window.
     *
     * @param stacktraceFile path to Stacktrace.json
     * @param startMs        window start (inclusive), epoch millis
     * @param endMs          window end (inclusive), epoch millis
     * @return matching lines as raw JSON strings
     */
    public static List<String> filterStacktraces(Path stacktraceFile, long startMs, long endMs) {
        return filterByWindow(stacktraceFile, startMs, endMs);
    }

    /**
     * Reads CpuAndMemory.json and computes heap and GC deltas for the time window.
     *
     * @param cpuMemoryFile path to CpuAndMemory.json
     * @param startMs       window start (inclusive), epoch millis
     * @param endMs         window end (inclusive), epoch millis
     * @return delta metrics, or {@code null} if the file is missing or has no data in range
     */
    public static JvmMetricsDelta computeMetricsDelta(Path cpuMemoryFile, long startMs, long endMs) {
        if (!Files.exists(cpuMemoryFile)) {
            return null;
        }

        List<CpuMemorySample> samples = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(cpuMemoryFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                CpuMemorySample sample = parseCpuMemoryLine(line);
                if (sample != null && sample.epochMillis() >= startMs && sample.epochMillis() <= endMs) {
                    samples.add(sample);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to read CpuAndMemory.json: " + e.getMessage());
            return null;
        }

        if (samples.isEmpty()) {
            return null;
        }

        CpuMemorySample first = samples.get(0);
        CpuMemorySample last = samples.get(samples.size() - 1);

        long heapDeltaBytes = last.heapUsedBytes() - first.heapUsedBytes();
        long gcCountDelta = last.gcTotalCount() - first.gcTotalCount();
        long gcTimeDelta = last.gcTotalTimeMs() - first.gcTotalTimeMs();

        return new JvmMetricsDelta(
                heapDeltaBytes / (1024 * 1024),
                Math.max(0, gcCountDelta),
                Math.max(0, gcTimeDelta)
        );
    }

    /**
     * Extracts the {@code stackcollapse.py} script from classpath resources to a temp file.
     *
     * @return path to the extracted script, or {@code null} if not found
     */
    public static Path extractStackcollapseScript() {
        try {
            InputStream resource = JvmProfilerBridge.class.getResourceAsStream("/scripts/stackcollapse.py");
            if (resource == null) {
                return null;
            }
            Path tempScript = Files.createTempFile("stackcollapse", ".py");
            tempScript.toFile().deleteOnExit();
            Files.copy(resource, tempScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tempScript;
        } catch (IOException e) {
            LOG.warning("Failed to extract stackcollapse.py: " + e.getMessage());
            return null;
        }
    }

    // ---- Private helpers ----

    private static List<String> filterByWindow(Path file, long startMs, long endMs) {
        List<String> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Stacktrace.json uses startEpoch/endEpoch; CpuAndMemory.json uses epochMillis
                if (line.contains("\"startEpoch\"")) {
                    long startEpoch = extractLongField(line, "startEpoch");
                    long endEpoch = extractLongField(line, "endEpoch");
                    // Include sample if its window overlaps with the test window
                    if (startEpoch <= endMs && endEpoch >= startMs) {
                        result.add(line);
                    }
                } else {
                    long epoch = extractEpochMillis(line);
                    if (epoch >= startMs && epoch <= endMs) {
                        result.add(line);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to read " + file + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Extracts {@code epochMillis} from a JSON line using simple string parsing
     * (avoids a JSON library dependency).
     */
    static long extractEpochMillis(String json) {
        int idx = json.indexOf("\"epochMillis\"");
        if (idx < 0) return -1;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return -1;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) return -1;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parses a CpuAndMemory.json line for heap and GC totals.
     * Fields used: {@code epochMillis}, {@code heapMemoryUsed}, {@code gcTime}, {@code gcCount}.
     */
    private static CpuMemorySample parseCpuMemoryLine(String json) {
        long epochMillis = extractEpochMillis(json);
        if (epochMillis < 0) return null;

        long heapUsed = extractLongField(json, "heapMemoryUsed");
        long gcCount = extractLongField(json, "gcCount");
        long gcTime = extractLongField(json, "gcTime");

        return new CpuMemorySample(epochMillis, Math.max(0, heapUsed), Math.max(0, gcCount), Math.max(0, gcTime), json);
    }

    private static long extractLongField(String json, String fieldName) {
        int idx = json.indexOf("\"" + fieldName + "\"");
        if (idx < 0) return 0;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) return 0;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
