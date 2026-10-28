package hu.blackbelt.judo.tatami.test.util;

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

import java.util.concurrent.Callable;

/**
 * Standalone utility for measuring execution time of operations.
 *
 * <p>Provides simple wall-clock timing with {@link TimedResult} that captures
 * both the result and duration. Useful for comparing ETL vs ZETA transformation
 * performance, or any operation that needs timing.
 *
 * <p>Example:
 * <pre>
 * var result = PerformanceMeasurement.measure(() -> transform(model));
 * log.info("Took {}ms, produced {} elements", result.durationMs(), count(result.result()));
 * </pre>
 */
public class PerformanceMeasurement {

    /**
     * Result of a timed execution, capturing both the return value and wall-clock duration.
     *
     * @param result the value returned by the operation
     * @param durationMs wall-clock duration in milliseconds
     * @param <T> type of the result
     */
    public record TimedResult<T>(T result, long durationMs) {}

    /**
     * Measures a single execution of the given action.
     *
     * @param action the operation to measure
     * @param <T> type of the result
     * @return timed result with wall-clock duration
     * @throws Exception if the action throws
     */
    public static <T> TimedResult<T> measure(Callable<T> action) throws Exception {
        long start = System.currentTimeMillis();
        T result = action.call();
        return new TimedResult<>(result, System.currentTimeMillis() - start);
    }

    /**
     * Measures N executions of the given action, returning the last result with average duration.
     *
     * <p>Useful for averaging out JIT warmup effects. The returned result is from the
     * last iteration; the duration is the arithmetic mean of all iterations.
     *
     * @param action the operation to measure (called N times)
     * @param iterations number of executions (must be &gt; 0)
     * @param <T> type of the result
     * @return timed result from the last iteration with average duration
     * @throws Exception if any iteration throws
     * @throws IllegalArgumentException if iterations &lt; 1
     */
    public static <T> TimedResult<T> measureAvg(Callable<T> action, int iterations) throws Exception {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1, got: " + iterations);
        }
        long total = 0;
        T result = null;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            result = action.call();
            total += System.currentTimeMillis() - start;
        }
        return new TimedResult<>(result, total / iterations);
    }

    private PerformanceMeasurement() {
        // utility class
    }
}
