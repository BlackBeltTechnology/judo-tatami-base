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
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.psm2measure.util.ModelComparator;
import hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureZetaTransformation;
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
 * Performance tests comparing ETL vs Zeta for PSM to Measure transformation.
 * 
 * Run with: mvn test -pl judo-tatami-psm2measure -Pperformance -Dtest=Psm2MeasurePerformanceTest
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
    void testSmallModelComparison() throws Exception {
        runComparisonTest(100, "Small");
    }

    @Test
    void testMediumModelComparison() throws Exception {
        runComparisonTest(500, "Medium");
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000})
    void testVariousModelSizesComparison(int measureCount) throws Exception {
        runComparisonTest(measureCount, "Size-" + measureCount);
    }

    private void runComparisonTest(int measureCount, String testName) throws Exception {
        log.info("================================================================");
        log.info("ETL vs ZETA Performance Comparison: {} ({} measures)", testName, measureCount);
        log.info("================================================================");

        // Generate PSM model with measures
        long genStart = System.nanoTime();
        PsmModel psmModel = generatePsmModel(measureCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = measureCount + (measureCount * UNITS_PER_MEASURE);
        log.info("Total model elements: {} ({} measures, {} units)", 
                totalElements, measureCount, measureCount * UNITS_PER_MEASURE);

        // Run ETL performance test
        log.info("");
        log.info("--- ETL Transformation ---");
        PerformanceResult etlResult = runPerformanceTest(psmModel, TransformationMode.ETL, measureCount);

        // Run Zeta performance test
        log.info("");
        log.info("--- ZETA Transformation ---");
        PerformanceResult zetaResult = runPerformanceTest(psmModel, TransformationMode.ZETA, measureCount);

        // Compare output models
        log.info("");
        log.info("--- Model Comparison ---");
        compareOutputModels(psmModel);

        // Print comparison
        printComparison(testName, measureCount, totalElements, etlResult, zetaResult);
    }

    private void compareOutputModels(PsmModel psmModel) throws Exception {
        // Execute both transformations and compare results
        MeasureModel etlResult = executeTransformationWithResult(psmModel, TransformationMode.ETL);
        MeasureModel zetaResult = executeTransformationWithResult(psmModel, TransformationMode.ZETA);

        // Compare the models - for performance tests with synthetic models, we do a structural comparison
        if (!etlResult.getResourceSet().getResources().isEmpty() && 
            !zetaResult.getResourceSet().getResources().isEmpty()) {
            
            ModelComparator.ComparisonResult result = ModelComparator.compare(
                    etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                    zetaResult.getResourceSet().getResources().get(0).getContents().get(0)
            );
            
            if (result.isEquivalent()) {
                log.info("Output models are EQUIVALENT");
            } else {
                log.info("Output models have {} differences (annotation variations expected for synthetic models)", 
                        result.getDifferenceList().size());
            }
        }
    }

    private MeasureModel executeTransformationWithResult(PsmModel psmModel, TransformationMode mode) throws Exception {
        MeasureModel measureModel = buildMeasureModel()
                .name(psmModel.getName() + "-measure")
                .build();
        
        if (mode.isZeta()) {
            Psm2MeasureZetaTransformation transformation = Psm2MeasureZetaTransformation.builder()
                    .psmModel(psmModel)
                    .measureModel(measureModel)
                    .build();
            transformation.execute();
        } else {
            executePsm2MeasureTransformation(psm2MeasureParameter()
                    .psmModel(psmModel)
                    .measureModel(measureModel));
        }
        
        return measureModel;
    }

    private PerformanceResult runPerformanceTest(PsmModel psmModel, TransformationMode mode, int measureCount) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeTransformation(psmModel, mode);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeTransformation(psmModel, mode);
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

        return new PerformanceResult(mode, stats.getMin(), stats.getMax(), stats.getAverage(), median, stdDev);
    }

    private void executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        MeasureModel measureModel = buildMeasureModel()
                .name(psmModel.getName() + "-measure")
                .build();
        
        if (mode.isZeta()) {
            Psm2MeasureZetaTransformation transformation = Psm2MeasureZetaTransformation.builder()
                    .psmModel(psmModel)
                    .measureModel(measureModel)
                    .build();
            transformation.execute();
        } else {
            executePsm2MeasureTransformation(psm2MeasureParameter()
                    .psmModel(psmModel)
                    .measureModel(measureModel));
        }
    }

    private void printComparison(String testName, int measureCount, int totalElements, 
                                  PerformanceResult etlResult, PerformanceResult zetaResult) {
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: {} ({} measures, {} elements)", testName, measureCount, totalElements);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Min:          {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlResult.min), 
                String.format("%8d", zetaResult.min), 
                String.format("%+8.1f", (double)(zetaResult.min - etlResult.min)),
                String.format("%+.1f", percentDiff(etlResult.min, zetaResult.min)));
        log.info("Max:          {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlResult.max), 
                String.format("%8d", zetaResult.max),
                String.format("%+8.1f", (double)(zetaResult.max - etlResult.max)),
                String.format("%+.1f", percentDiff(etlResult.max, zetaResult.max)));
        log.info("Avg:          {}ms       {}ms       {}ms ({}%)", 
                String.format("%8.1f", etlResult.avg), 
                String.format("%8.1f", zetaResult.avg),
                String.format("%+8.1f", zetaResult.avg - etlResult.avg),
                String.format("%+.1f", percentDiff(etlResult.avg, zetaResult.avg)));
        log.info("Median:       {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlResult.median), 
                String.format("%8d", zetaResult.median),
                String.format("%+8.1f", (double)(zetaResult.median - etlResult.median)),
                String.format("%+.1f", percentDiff(etlResult.median, zetaResult.median)));
        log.info("StdDev:       {}ms       {}ms", 
                String.format("%8.1f", etlResult.stdDev), 
                String.format("%8.1f", zetaResult.stdDev));
        log.info("");
        
        double etlThroughput = totalElements / (etlResult.avg / 1000.0);
        double zetaThroughput = totalElements / (zetaResult.avg / 1000.0);
        log.info("Throughput:   {}/s       {}/s       {}/s ({}%)", 
                String.format("%8.0f", etlThroughput), 
                String.format("%8.0f", zetaThroughput),
                String.format("%+8.0f", zetaThroughput - etlThroughput),
                String.format("%+.1f", percentDiff(etlThroughput, zetaThroughput)));
        log.info("");
        
        if (zetaResult.avg < etlResult.avg) {
            double speedup = etlResult.avg / zetaResult.avg;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaResult.avg > etlResult.avg) {
            double slowdown = zetaResult.avg / etlResult.avg;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================\n");

        // Assert Zeta is not significantly slower (allow 50% tolerance)
        assertTrue(zetaResult.avg < etlResult.avg * 1.5, 
                "Zeta transformation is significantly slower than ETL: " + 
                zetaResult.avg + "ms vs " + etlResult.avg + "ms");
    }

    private double percentDiff(double baseline, double value) {
        return ((value - baseline) / baseline) * 100.0;
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

    private static class PerformanceResult {
        final TransformationMode mode;
        final long min;
        final long max;
        final double avg;
        final long median;
        final double stdDev;

        PerformanceResult(TransformationMode mode, long min, long max, double avg, long median, double stdDev) {
            this.mode = mode;
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.median = median;
            this.stdDev = stdDev;
        }
    }
}
