package hu.blackbelt.judo.tatami.test.profiler;

import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import one.profiler.Events;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JUnit 5 extension that provides automatic JVM profiling for test methods.
 * <p>
 * This extension hooks into the JUnit lifecycle to:
 * <ul>
 *   <li>Load the async-profiler native library once per test suite</li>
 *   <li>Start profiling before each test method</li>
 *   <li>Stop profiling and write output after each test method</li>
 * </ul>
 * <p>
 * The extension can be activated via:
 * <ul>
 *   <li>The {@link Profile} annotation on test classes or methods</li>
 *   <li>System property: -Djudo.test.profiler.enabled=true</li>
 * </ul>
 * <p>
 * Output is written to {@code target/profiler-output/} by default in collapsed stack format,
 * which is optimized for LLM analysis.
 */
public class ProfilingExtension implements BeforeAllCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Logger LOG = Logger.getLogger(ProfilingExtension.class.getName());
    private static final String START_TIME_KEY = "profiler.startTime";
    private static final String PROFILER_ACTIVE_KEY = "profiler.active";

    private static volatile AsyncProfiler profiler;
    private static volatile boolean profilerAvailable = false;
    private static volatile boolean initAttempted = false;

    private final ProfileConfig globalConfig;

    public ProfilingExtension() {
        this.globalConfig = ProfileConfig.fromSystemProperties();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        initializeProfiler();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        if (!shouldProfile(context)) {
            return;
        }

        if (!profilerAvailable) {
            LOG.fine("Profiler not available, skipping profiling for: " + context.getDisplayName());
            return;
        }

        try {
            ProfileConfig config = getEffectiveConfig(context);
            String event = mapEventType(config.getEvent());

            profiler.start(event, config.getSamplingInterval());

            // Store start time for threshold checking
            getStore(context).put(START_TIME_KEY, System.currentTimeMillis());
            getStore(context).put(PROFILER_ACTIVE_KEY, true);

            LOG.fine("Started profiling for: " + context.getDisplayName());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to start profiling: " + e.getMessage(), e);
            getStore(context).put(PROFILER_ACTIVE_KEY, false);
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        Boolean active = getStore(context).remove(PROFILER_ACTIVE_KEY, Boolean.class);
        if (active == null || !active) {
            return;
        }

        try {
            profiler.stop();

            Long startTime = getStore(context).remove(START_TIME_KEY, Long.class);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

            ProfileConfig config = getEffectiveConfig(context);

            // Check threshold
            if (duration < config.getThresholdMs()) {
                LOG.fine("Test duration (" + duration + "ms) below threshold (" +
                        config.getThresholdMs() + "ms), skipping profile output");
                return;
            }

            // Write profile output
            writeProfileOutput(context, config);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to stop profiling: " + e.getMessage(), e);
        }
    }

    private synchronized void initializeProfiler() {
        if (initAttempted) {
            return;
        }
        initAttempted = true;

        try {
            // Use ap-loader to automatically extract and load the native library
            profiler = AsyncProfilerLoader.load();
            profilerAvailable = true;
            LOG.info("Async-profiler initialized successfully via ap-loader");
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Failed to initialize async-profiler. Profiling will be disabled. " +
                    "On Linux, ensure perf_event_paranoid is set correctly: " +
                    "echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid. " +
                    "On macOS, ensure SIP allows profiling.", t);
            profilerAvailable = false;
        }
    }

    private boolean shouldProfile(ExtensionContext context) {
        // Check if @Profile annotation is present
        Optional<Profile> methodAnnotation = context.getTestMethod()
                .flatMap(m -> Optional.ofNullable(m.getAnnotation(Profile.class)));
        Optional<Profile> classAnnotation = context.getTestClass()
                .flatMap(c -> Optional.ofNullable(c.getAnnotation(Profile.class)));

        boolean hasAnnotation = methodAnnotation.isPresent() || classAnnotation.isPresent();

        // If annotation present, always profile (unless globally disabled)
        if (hasAnnotation) {
            return globalConfig.isEnabled() ||
                    !System.getProperty(ProfileConfig.PROPERTY_PREFIX + "enabled", "").equals("false");
        }

        // If no annotation, only profile if globally enabled
        return globalConfig.isEnabled();
    }

    private ProfileConfig getEffectiveConfig(ExtensionContext context) {
        // Get annotation settings (method takes precedence over class)
        Optional<Profile> annotation = context.getTestMethod()
                .flatMap(m -> Optional.ofNullable(m.getAnnotation(Profile.class)))
                .or(() -> context.getTestClass()
                        .flatMap(c -> Optional.ofNullable(c.getAnnotation(Profile.class))));

        if (annotation.isPresent()) {
            Profile p = annotation.get();
            return ProfileConfig.builder()
                    .outputPath(globalConfig.getOutputPath())
                    .outputFormat(p.format())
                    .event(p.event())
                    .thresholdMs(p.thresholdMs())
                    .enabled(true)
                    .samplingInterval(globalConfig.getSamplingInterval())
                    .llmEnabled(globalConfig.isLlmEnabled())
                    .llmProvider(globalConfig.getLlmProvider())
                    .build();
        }

        return globalConfig;
    }

    private void writeProfileOutput(ExtensionContext context, ProfileConfig config) throws IOException {
        Path outputDir = Paths.get(config.getOutputPath());
        Files.createDirectories(outputDir);

        String testClass = context.getRequiredTestClass().getSimpleName();
        String testMethod = context.getTestMethod()
                .map(m -> m.getName())
                .orElse(context.getDisplayName());

        // Sanitize filename
        String fileName = sanitizeFileName(testClass + "_" + testMethod) + config.getFileExtension();
        Path outputFile = outputDir.resolve(fileName);

        String outputArg = config.getProfilerOutputArg();
        String command = "file=" + outputFile.toAbsolutePath() + ",output=" + outputArg;

        try {
            profiler.execute(command);
            LOG.info("[Profiler] Output: " + outputFile);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write profile output to: " + outputFile, e);
        }
    }

    private String mapEventType(String event) {
        return switch (event.toLowerCase()) {
            case "wall" -> Events.WALL;
            case "alloc" -> Events.ALLOC;
            case "lock" -> Events.LOCK;
            default -> Events.CPU;
        };
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(ProfilingExtension.class, context.getUniqueId()));
    }
}
