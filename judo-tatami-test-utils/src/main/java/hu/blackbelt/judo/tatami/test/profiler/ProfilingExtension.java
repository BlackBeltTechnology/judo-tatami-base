package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JUnit 5 extension that provides automatic JVM profiling for test methods using
 * Uber's jvm-profiler (pure Java agent, works on macOS and Linux).
 *
 * <p>The jvm-profiler agent must be configured via {@code -javaagent} in the surefire
 * {@code argLine} (see the {@code performance} Maven profile). This extension:
 * <ul>
 *   <li>Records per-test start/end timestamps</li>
 *   <li>After all tests, filters {@code Stacktrace.json} by time window per test</li>
 *   <li>Invokes {@code stackcollapse.py} to produce collapsed stack files</li>
 *   <li>Extracts GC/heap deltas from {@code CpuAndMemory.json} and logs them</li>
 * </ul>
 *
 * <p>Activate via the {@link Profile} annotation on test classes or methods,
 * or via system property {@code -Djudo.test.profiler.enabled=true}.
 */
public class ProfilingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback, AfterAllCallback {

    private static final Logger LOG = Logger.getLogger(ProfilingExtension.class.getName());
    private static final String START_TIME_KEY = "profiler.startTime";

    /** Shared across all instances in a JVM run: testKey → {startMs, endMs} */
    private static final Map<String, long[]> testWindows = new ConcurrentHashMap<>();

    private final ProfileConfig globalConfig;

    public ProfilingExtension() {
        this.globalConfig = ProfileConfig.fromSystemProperties();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (!shouldProfile(context)) {
            return;
        }
        getStore(context).put(START_TIME_KEY, System.currentTimeMillis());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Long startMs = getStore(context).remove(START_TIME_KEY, Long.class);
        if (startMs == null) {
            return;
        }

        long endMs = System.currentTimeMillis();
        long duration = endMs - startMs;

        ProfileConfig config = getEffectiveConfig(context);
        if (duration < config.getThresholdMs()) {
            LOG.fine("Test duration (" + duration + "ms) below threshold (" +
                    config.getThresholdMs() + "ms), skipping profiling for: " + context.getDisplayName());
            return;
        }

        String testKey = buildTestKey(context);
        testWindows.put(testKey, new long[]{startMs, endMs});
        LOG.fine("Recorded profiling window for: " + testKey + " [" + startMs + ", " + endMs + "]");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (testWindows.isEmpty()) {
            return;
        }

        Path outputDir = Paths.get(globalConfig.getOutputDir());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOG.warning("Failed to create profiler output directory: " + e.getMessage());
            return;
        }

        Path stacktraceFile = outputDir.resolve("Stacktrace.json");
        Path cpuMemoryFile = outputDir.resolve("CpuAndMemory.json");
        Path scriptPath = JvmProfilerBridge.extractStackcollapseScript();
        boolean pythonAvailable = isPythonAvailable();

        for (Map.Entry<String, long[]> entry : testWindows.entrySet()) {
            String testKey = entry.getKey();
            long startMs = entry.getValue()[0];
            long endMs = entry.getValue()[1];
            processTestWindow(testKey, startMs, endMs, stacktraceFile, cpuMemoryFile, outputDir, scriptPath, pythonAvailable);
        }

        testWindows.clear();
    }

    private void processTestWindow(String testKey, long startMs, long endMs,
                                    Path stacktraceFile, Path cpuMemoryFile,
                                    Path outputDir, Path scriptPath, boolean pythonAvailable) {
        // Log GC/heap delta
        JvmProfilerBridge.JvmMetricsDelta delta = JvmProfilerBridge.computeMetricsDelta(cpuMemoryFile, startMs, endMs);
        if (delta != null) {
            LOG.info(String.format("[Profiler] %s heap: %+dMB gc: %d collections %dms",
                    testKey, delta.heapDeltaMb(), delta.gcCollections(), delta.gcTimeMs()));
        }

        // Filter stacktraces for this window
        List<String> filteredLines = JvmProfilerBridge.filterStacktraces(stacktraceFile, startMs, endMs);
        if (filteredLines.isEmpty()) {
            LOG.fine("No stacktrace samples in window for: " + testKey);
            return;
        }

        String safeKey = sanitizeFileName(testKey);
        Path filteredJson = outputDir.resolve(safeKey + "_stacktrace.json");
        try {
            Files.write(filteredJson, filteredLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warning("Failed to write filtered stacktrace JSON for " + testKey + ": " + e.getMessage());
            return;
        }

        if (scriptPath != null && pythonAvailable) {
            collapseStacks(scriptPath, filteredJson, outputDir.resolve(safeKey + ".txt"), testKey);
        } else {
            LOG.warning("[Profiler] stackcollapse.py unavailable, raw JSON retained for: " + testKey);
            try {
                Files.move(filteredJson, outputDir.resolve(safeKey + ".json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.fine("Could not rename filtered JSON: " + e.getMessage());
            }
        }
    }

    private void collapseStacks(Path script, Path inputJson, Path outputFile, String testKey) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", script.toAbsolutePath().toString(),
                    "--input", inputJson.toAbsolutePath().toString()
            );
            pb.redirectOutput(outputFile.toFile());

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                LOG.info("[Profiler] Output: " + outputFile);
                Files.deleteIfExists(inputJson);
            } else {
                LOG.warning("[Profiler] stackcollapse.py exited with code " + exitCode + " for: " + testKey);
            }
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.WARNING, "Failed to run stackcollapse.py for: " + testKey, e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private boolean isPythonAvailable() {
        try {
            Process process = new ProcessBuilder("python3", "--version").start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean shouldProfile(ExtensionContext context) {
        Optional<Profile> methodAnnotation = context.getTestMethod()
                .flatMap(m -> Optional.ofNullable(m.getAnnotation(Profile.class)));
        Optional<Profile> classAnnotation = context.getTestClass()
                .flatMap(c -> Optional.ofNullable(c.getAnnotation(Profile.class)));

        boolean hasAnnotation = methodAnnotation.isPresent() || classAnnotation.isPresent();

        if (hasAnnotation) {
            return globalConfig.isEnabled() ||
                    !System.getProperty(ProfileConfig.PROPERTY_PREFIX + "enabled", "").equals("false");
        }

        return globalConfig.isEnabled();
    }

    private ProfileConfig getEffectiveConfig(ExtensionContext context) {
        Optional<Profile> annotation = context.getTestMethod()
                .flatMap(m -> Optional.ofNullable(m.getAnnotation(Profile.class)))
                .or(() -> context.getTestClass()
                        .flatMap(c -> Optional.ofNullable(c.getAnnotation(Profile.class))));

        if (annotation.isPresent()) {
            Profile p = annotation.get();
            return ProfileConfig.builder()
                    .outputDir(globalConfig.getOutputDir())
                    .thresholdMs(p.thresholdMs())
                    .enabled(true)
                    .sampleIntervalMs(globalConfig.getSampleIntervalMs())
                    .metricIntervalMs(globalConfig.getMetricIntervalMs())
                    .llmEnabled(globalConfig.isLlmEnabled())
                    .llmProvider(globalConfig.getLlmProvider())
                    .build();
        }

        return globalConfig;
    }

    private String buildTestKey(ExtensionContext context) {
        String testClass = context.getRequiredTestClass().getSimpleName();
        String testMethod = context.getTestMethod()
                .map(m -> m.getName())
                .orElse(context.getDisplayName());
        return testClass + "_" + testMethod;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(ProfilingExtension.class, context.getUniqueId()));
    }
}
