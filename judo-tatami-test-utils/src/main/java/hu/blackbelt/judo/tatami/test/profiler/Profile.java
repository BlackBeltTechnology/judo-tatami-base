package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable JVM profiling for test classes or methods.
 * <p>
 * When applied to a test class, all test methods will be profiled.
 * When applied to a test method, only that method will be profiled (overriding class-level settings).
 * <p>
 * Example usage:
 * <pre>{@code
 * @Profile
 * class MyPerformanceTest {
 *     @Test
 *     void testPerformance() {
 *         // This test will be profiled
 *     }
 * }
 * }</pre>
 * <p>
 * With configuration:
 * <pre>{@code
 * @Profile(thresholdMs = 500, format = "flamegraph", event = "wall")
 * class MyPerformanceTest {
 *     // ...
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ProfilingExtension.class)
public @interface Profile {

    /**
     * Minimum test duration in milliseconds to trigger profiling.
     * Tests completing faster than this threshold will not generate profiles.
     * Default: 0 (always profile)
     */
    long thresholdMs() default 0;

    /**
     * Output format for the profile data.
     * Supported values: collapsed, flamegraph, jfr
     * Default: collapsed (most LLM-friendly)
     */
    String format() default "collapsed";

    /**
     * Profiling event type.
     * Supported values: cpu, wall, alloc, lock
     * Default: cpu
     */
    String event() default "cpu";
}
