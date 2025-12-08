package hu.blackbelt.judo.tatami.psm2measure.perf;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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

import com.google.common.collect.ImmutableList;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.measure.Measure;
import hu.blackbelt.judo.meta.psm.measure.Unit;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;

import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.newMeasureBuilder;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.newUnitBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests for PSM to Measure transformation.
 * 
 * Run with: mvn test -pl judo-tatami-psm2measure -Dtest=Psm2MeasurePerformanceTest -Dgroups=performance
 */
@Slf4j
@Tag("performance")
public class Psm2MeasurePerformanceTest {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int UNITS_PER_MEASURE = 3;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testSmallModel() throws Exception {
        runPerformanceTest(100, "Small");
    }

    @Test
    void testMediumModel() throws Exception {
        runPerformanceTest(500, "Medium");
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000})
    void testVariousModelSizes(int measureCount) throws Exception {
        runPerformanceTest(measureCount, "Size-" + measureCount);
    }

    private void runPerformanceTest(int measureCount, String testName) throws Exception {
        log.info("========================================");
        log.info("Performance Test: {} ({} measures)", testName, measureCount);
        log.info("========================================");

        // Generate PSM model with measures
        long genStart = System.nanoTime();
        PsmModel psmModel = generatePsmModel(measureCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = measureCount + (measureCount * UNITS_PER_MEASURE);
        log.info("Total model elements: {} ({} measures, {} units)", 
                totalElements, measureCount, measureCount * UNITS_PER_MEASURE);

        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            MeasureModel measureModel = buildMeasureModel()
                    .name(psmModel.getName() + "-measure")
                    .build();
            executePsm2MeasureTransformation(psm2MeasureParameter()
                    .psmModel(psmModel)
                    .measureModel(measureModel));
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            MeasureModel measureModel = buildMeasureModel()
                    .name(psmModel.getName() + "-measure")
                    .build();
            
            long startTime = System.nanoTime();
            executePsm2MeasureTransformation(psm2MeasureParameter()
                    .psmModel(psmModel)
                    .measureModel(measureModel));
            long endTime = System.nanoTime();
            
            long timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            times.add(timeMs);
            log.info("  Iteration {}: {}ms", i + 1, timeMs);
        }

        // Calculate statistics
        LongSummaryStatistics stats = times.stream().mapToLong(Long::longValue).summaryStatistics();
        Collections.sort(times);
        long median = times.get(times.size() / 2);
        
        double stdDev = calculateStdDev(times, stats.getAverage());
        double elementsPerSecond = totalElements / (stats.getAverage() / 1000.0);

        log.info("----------------------------------------");
        log.info("Results for {} ({} measures, {} elements):", testName, measureCount, totalElements);
        log.info("  Min:    {}ms", stats.getMin());
        log.info("  Max:    {}ms", stats.getMax());
        log.info("  Avg:    {}ms", String.format("%.1f", stats.getAverage()));
        log.info("  Median: {}ms", median);
        log.info("  StdDev: {}ms", String.format("%.1f", stdDev));
        log.info("  Throughput: {} elements/sec", String.format("%.0f", elementsPerSecond));
        log.info("========================================\n");

        // Basic sanity check - allow 15ms per measure to account for ETL overhead
        assertTrue(stats.getAverage() < measureCount * 15, 
                "Transformation too slow: " + stats.getAverage() + "ms for " + measureCount + " measures");
    }

    private PsmModel generatePsmModel(int measureCount) {
        PsmModel psmModel = buildPsmModel().build();

        List<NamespaceElement> elements = new ArrayList<>();

        // Create measures with units
        for (int i = 0; i < measureCount; i++) {
            List<Unit> units = new ArrayList<>();
            for (int j = 0; j < UNITS_PER_MEASURE; j++) {
                Unit unit = newUnitBuilder()
                        .withName("unit" + i + "_" + j)
                        .withSymbol("u" + i + j)
                        .build();
                units.add(unit);
            }

            Measure measure = newMeasureBuilder()
                    .withName("Measure" + i)
                    .withUnits(units)
                    .build();

            elements.add(measure);
        }

        Model model = newModelBuilder()
                .withName("PerformanceTestModel")
                .withElements(ImmutableList.copyOf(elements))
                .build();

        psmModel.addContent(model);

        return psmModel;
    }

    private double calculateStdDev(List<Long> values, double mean) {
        double sumSquaredDiff = 0;
        for (Long value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquaredDiff / values.size());
    }
}
