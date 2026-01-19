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

import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import one.profiler.Events;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Programmatic API for JVM profiling using async-profiler.
 * <p>
 * Use this class to profile specific code sections:
 * <pre>
 * Profiler.start("cpu");
 * // ... code to profile ...
 * Profiler.stopAndSave("my-profile.txt");
 * </pre>
 * <p>
 * Supported event types:
 * <ul>
 *   <li>"cpu" - CPU profiling (default)</li>
 *   <li>"wall" - Wall-clock profiling</li>
 *   <li>"alloc" - Allocation profiling</li>
 *   <li>"lock" - Lock contention profiling</li>
 * </ul>
 */
public class Profiler {

    private static final Logger LOG = Logger.getLogger(Profiler.class.getName());
    private static final String DEFAULT_OUTPUT_DIR = "target/profiler-output";
    private static final long DEFAULT_SAMPLING_INTERVAL = 1_000_000; // 1ms in nanoseconds

    private static volatile AsyncProfiler profiler;
    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean running = false;

    private Profiler() {
        // Utility class
    }

    /**
     * Initialize the profiler. Called automatically on first use.
     *
     * @return true if profiler is available
     */
    public static synchronized boolean initialize() {
        if (initialized) {
            return available;
        }
        initialized = true;

        try {
            profiler = AsyncProfilerLoader.load();
            available = true;
            LOG.info("Async-profiler initialized successfully");
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Failed to initialize async-profiler: " + t.getMessage());
            available = false;
        }

        return available;
    }

    /**
     * Check if profiler is available.
     *
     * @return true if profiler can be used
     */
    public static boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }

    /**
     * Start CPU profiling with default settings.
     *
     * @return true if profiling started successfully
     */
    public static boolean start() {
        return start("cpu");
    }

    /**
     * Start profiling with the specified event type.
     *
     * @param event event type: "cpu", "wall", "alloc", or "lock"
     * @return true if profiling started successfully
     */
    public static boolean start(String event) {
        return start(event, DEFAULT_SAMPLING_INTERVAL);
    }

    /**
     * Start profiling with the specified event type and sampling interval.
     *
     * @param event event type: "cpu", "wall", "alloc", or "lock"
     * @param intervalNanos sampling interval in nanoseconds
     * @return true if profiling started successfully
     */
    public static synchronized boolean start(String event, long intervalNanos) {
        if (!initialize()) {
            LOG.warning("Profiler not available, cannot start profiling");
            return false;
        }

        if (running) {
            LOG.warning("Profiler already running, stop it first");
            return false;
        }

        try {
            String mappedEvent = mapEventType(event);
            profiler.start(mappedEvent, intervalNanos);
            running = true;
            LOG.fine("Started profiling with event: " + event);
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to start profiling: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Stop profiling without saving output.
     *
     * @return true if profiling stopped successfully
     */
    public static synchronized boolean stop() {
        if (!running) {
            return true;
        }

        try {
            profiler.stop();
            running = false;
            LOG.fine("Stopped profiling");
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to stop profiling: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Stop profiling and save output to the specified file.
     *
     * @param filename output filename (placed in target/profiler-output/)
     * @return the path to the saved file, or null if failed
     */
    public static Path stopAndSave(String filename) {
        return stopAndSave(filename, "collapsed");
    }

    /**
     * Stop profiling and save output to the specified file with format.
     *
     * @param filename output filename (placed in target/profiler-output/)
     * @param format output format: "collapsed", "flat", "tree", or "flamegraph"
     * @return the path to the saved file, or null if failed
     */
    public static synchronized Path stopAndSave(String filename, String format) {
        if (!running) {
            LOG.warning("Profiler not running, nothing to save");
            return null;
        }

        try {
            profiler.stop();
            running = false;

            Path outputDir = Paths.get(DEFAULT_OUTPUT_DIR);
            Files.createDirectories(outputDir);

            Path outputFile = outputDir.resolve(sanitizeFileName(filename));
            String command = "file=" + outputFile.toAbsolutePath() + ",output=" + format;

            profiler.execute(command);
            LOG.info("[Profiler] Output saved: " + outputFile);
            return outputFile;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save profile: " + e.getMessage(), e);
            running = false;
            return null;
        }
    }

    /**
     * Check if profiler is currently running.
     *
     * @return true if profiling is active
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Profile a code block and save the result.
     *
     * @param filename output filename
     * @param code the code to profile
     * @param <T> return type
     * @return the result of the code execution
     * @throws Exception if the code throws an exception
     */
    public static <T> T profile(String filename, ProfiledCode<T> code) throws Exception {
        return profile("cpu", filename, code);
    }

    /**
     * Profile a code block with specified event type and save the result.
     *
     * @param event event type: "cpu", "wall", "alloc", or "lock"
     * @param filename output filename
     * @param code the code to profile
     * @param <T> return type
     * @return the result of the code execution
     * @throws Exception if the code throws an exception
     */
    public static <T> T profile(String event, String filename, ProfiledCode<T> code) throws Exception {
        start(event);
        try {
            return code.execute();
        } finally {
            stopAndSave(filename);
        }
    }

    /**
     * Functional interface for code to be profiled.
     *
     * @param <T> return type
     */
    @FunctionalInterface
    public interface ProfiledCode<T> {
        T execute() throws Exception;
    }

    private static String mapEventType(String event) {
        return switch (event.toLowerCase()) {
            case "wall" -> Events.WALL;
            case "alloc" -> Events.ALLOC;
            case "lock" -> Events.LOCK;
            default -> Events.CPU;
        };
    }

    private static String sanitizeFileName(String name) {
        if (!name.contains(".")) {
            name = name + ".txt";
        }
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
