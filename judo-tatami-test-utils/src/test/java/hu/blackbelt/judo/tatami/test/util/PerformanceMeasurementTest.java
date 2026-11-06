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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMeasurementTest {

    @Test
    void measureSingleExecution() throws Exception {
        PerformanceMeasurement.TimedResult<String> result =
                PerformanceMeasurement.measure(() -> "hello");

        assertEquals("hello", result.result());
        assertTrue(result.durationMs() >= 0, "Duration should be non-negative");
    }

    @Test
    void measureCapturesDuration() throws Exception {
        PerformanceMeasurement.TimedResult<String> result =
                PerformanceMeasurement.measure(() -> {
                    Thread.sleep(50);
                    return "done";
                });

        assertEquals("done", result.result());
        assertTrue(result.durationMs() >= 40, "Duration should be at least ~50ms, was: " + result.durationMs());
    }

    @Test
    void measurePropagatesException() {
        assertThrows(RuntimeException.class, () ->
                PerformanceMeasurement.measure(() -> {
                    throw new RuntimeException("test error");
                }));
    }

    @Test
    void measureAvgRunsNTimes() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);

        PerformanceMeasurement.TimedResult<Integer> result =
                PerformanceMeasurement.measureAvg(() -> counter.incrementAndGet(), 5);

        assertEquals(5, counter.get(), "Should have been called 5 times");
        assertEquals(5, result.result(), "Result should be from last iteration");
        assertTrue(result.durationMs() >= 0, "Duration should be non-negative");
    }

    @Test
    void measureAvgReturnsAverageDuration() throws Exception {
        PerformanceMeasurement.TimedResult<String> result =
                PerformanceMeasurement.measureAvg(() -> {
                    Thread.sleep(20);
                    return "avg";
                }, 3);

        assertEquals("avg", result.result());
        // Average of 3 × ~20ms should be roughly 20ms (with some tolerance)
        assertTrue(result.durationMs() >= 10, "Average duration should be at least ~20ms, was: " + result.durationMs());
    }

    @Test
    void measureAvgSingleIteration() throws Exception {
        PerformanceMeasurement.TimedResult<String> result =
                PerformanceMeasurement.measureAvg(() -> "single", 1);

        assertEquals("single", result.result());
    }

    @Test
    void measureAvgRejectsZeroIterations() {
        assertThrows(IllegalArgumentException.class, () ->
                PerformanceMeasurement.measureAvg(() -> "x", 0));
    }

    @Test
    void measureAvgRejectsNegativeIterations() {
        assertThrows(IllegalArgumentException.class, () ->
                PerformanceMeasurement.measureAvg(() -> "x", -1));
    }

    @Test
    void measureAvgPropagatesException() {
        AtomicInteger counter = new AtomicInteger(0);
        assertThrows(RuntimeException.class, () ->
                PerformanceMeasurement.measureAvg(() -> {
                    if (counter.incrementAndGet() == 3) {
                        throw new RuntimeException("fail on 3rd");
                    }
                    return "ok";
                }, 5));
        assertEquals(3, counter.get(), "Should have stopped on the 3rd iteration");
    }
}
