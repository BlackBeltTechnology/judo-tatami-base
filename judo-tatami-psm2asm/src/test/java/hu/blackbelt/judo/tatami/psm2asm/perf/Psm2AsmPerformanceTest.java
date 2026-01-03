package hu.blackbelt.judo.tatami.psm2asm.perf;

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
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.type.NumericType;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
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

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newCardinalityBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newNumericTypeBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newStringTypeBuilder;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests comparing ETL vs Zeta for PSM to ASM transformation.
 * <p>
 * Run with: mvn test -Pperformance -Dtest=Psm2AsmPerformanceTest
 */
@Slf4j
@Tag("performance")
public class Psm2AsmPerformanceTest {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int ATTRIBUTES_PER_ENTITY = 5;
    private static final int RELATIONS_PER_ENTITY = 2;

    private StringType stringType;
    private NumericType intType;

    @BeforeEach
    void setUp() {
        stringType = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        intType = newNumericTypeBuilder().withName("Integer").withPrecision(9).withScale(0).build();
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
    void testVariousModelSizesComparison(int entityCount) throws Exception {
        runComparisonTest(entityCount, "Size-" + entityCount);
    }

    private void runComparisonTest(int entityCount, String testName) throws Exception {
        log.info("================================================================");
        log.info("ETL vs Zeta Performance Comparison: {} ({} entities)", testName, entityCount);
        log.info("================================================================");

        // Generate PSM model
        long genStart = System.nanoTime();
        PsmModel psmModel = generatePsmModel(entityCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = countModelElements(entityCount);
        log.info("Total model elements: {}", totalElements);

        // Run ETL performance test
        log.info("");
        log.info("--- ETL Transformation ---");
        PerformanceResult etlResult = runEtlPerformanceTest(psmModel);

        // Run Zeta performance test
        log.info("");
        log.info("--- Zeta Transformation ---");
        PerformanceResult zetaResult = runZetaPerformanceTest(psmModel);

        // Compare output models
        log.info("");
        log.info("--- Model Comparison ---");
        compareOutputModels(psmModel);

        // Print comparison
        printComparison(testName, entityCount, totalElements, etlResult, zetaResult);
    }

    private void compareOutputModels(PsmModel psmModel) throws Exception {
        AsmModel etlResult = executeEtlTransformation(psmModel);
        AsmModel zetaResult = executeZetaTransformation(psmModel);

        if (!etlResult.getResourceSet().getResources().isEmpty() &&
            !zetaResult.getResourceSet().getResources().isEmpty()) {

            EPackage etlPkg = (EPackage) etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            EPackage zetaPkg = (EPackage) zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

            int etlClassCount = etlPkg.getEClassifiers().size();
            int zetaClassCount = zetaPkg.getEClassifiers().size();

            log.info("ETL output: {} classifiers", etlClassCount);
            log.info("Zeta output: {} classifiers", zetaClassCount);

            if (etlClassCount == zetaClassCount) {
                log.info("Output models have EQUIVALENT structure ({} classifiers)", etlClassCount);
            } else {
                log.warn("Output models have DIFFERENT classifier counts: ETL={}, Zeta={}",
                        etlClassCount, zetaClassCount);
            }

            assertEquals(etlClassCount, zetaClassCount,
                    "Zeta should produce same number of classifiers as ETL");
        }
    }

    private AsmModel executeEtlTransformation(PsmModel psmModel) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .createTrace(false));
        return asmModel;
    }

    private AsmModel executeZetaTransformation(PsmModel psmModel) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName("PerformanceTest")
                .build();
        transformation.execute();
        return asmModel;
    }

    private PerformanceResult runEtlPerformanceTest(PsmModel psmModel) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeEtlTransformation(psmModel);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeEtlTransformation(psmModel);
            long endTime = System.nanoTime();

            long timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            times.add(timeMs);
            log.info("  Iteration {}: {}ms", i + 1, timeMs);
        }

        return calculateResult(times);
    }

    private PerformanceResult runZetaPerformanceTest(PsmModel psmModel) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeZetaTransformation(psmModel);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeZetaTransformation(psmModel);
            long endTime = System.nanoTime();

            long timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            times.add(timeMs);
            log.info("  Iteration {}: {}ms", i + 1, timeMs);
        }

        return calculateResult(times);
    }

    private PerformanceResult calculateResult(List<Long> times) {
        LongSummaryStatistics stats = times.stream().mapToLong(Long::longValue).summaryStatistics();
        Collections.sort(times);
        long median = times.get(times.size() / 2);
        double stdDev = calculateStdDev(times, stats.getAverage());
        return new PerformanceResult(stats.getMin(), stats.getMax(), stats.getAverage(), median, stdDev);
    }

    private void printComparison(String testName, int entityCount, int totalElements,
                                  PerformanceResult etlResult, PerformanceResult zetaResult) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} entities, {} elements)", testName, entityCount, totalElements);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              Zeta           Speedup");
        log.info("----------------------------------------------------------------");
        log.info("Min:          {}ms       {}ms       {}x",
                String.format("%8d", etlResult.min),
                String.format("%8d", zetaResult.min),
                String.format("%6.1f", (double) etlResult.min / zetaResult.min));
        log.info("Max:          {}ms       {}ms       {}x",
                String.format("%8d", etlResult.max),
                String.format("%8d", zetaResult.max),
                String.format("%6.1f", (double) etlResult.max / zetaResult.max));
        log.info("Avg:          {}ms       {}ms       {}x",
                String.format("%8.1f", etlResult.avg),
                String.format("%8.1f", zetaResult.avg),
                String.format("%6.1f", etlResult.avg / zetaResult.avg));
        log.info("Median:       {}ms       {}ms       {}x",
                String.format("%8d", etlResult.median),
                String.format("%8d", zetaResult.median),
                String.format("%6.1f", (double) etlResult.median / zetaResult.median));
        log.info("StdDev:       {}ms       {}ms",
                String.format("%8.1f", etlResult.stdDev),
                String.format("%8.1f", zetaResult.stdDev));
        log.info("");

        double etlThroughput = totalElements / (etlResult.avg / 1000.0);
        double zetaThroughput = totalElements / (zetaResult.avg / 1000.0);
        log.info("Throughput:   {}/s       {}/s",
                String.format("%8.0f", etlThroughput),
                String.format("%8.0f", zetaThroughput));
        log.info("");

        double speedup = etlResult.avg / zetaResult.avg;
        if (speedup > 1) {
            log.info(">>> Zeta is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (speedup < 1) {
            log.info(">>> Zeta is {}x SLOWER than ETL <<<", String.format("%.2f", 1.0 / speedup));
        } else {
            log.info(">>> ETL and Zeta have EQUAL performance <<<");
        }
        log.info("================================================================\n");

        // Assert Zeta is not significantly slower (allow 50% tolerance)
        assertTrue(zetaResult.avg < etlResult.avg * 1.5,
                "Zeta transformation is significantly slower than ETL: " +
                zetaResult.avg + "ms vs " + etlResult.avg + "ms");
    }

    private PsmModel generatePsmModel(int entityCount) {
        PsmModel psmModel = buildPsmModel().build();

        List<EntityType> entities = new ArrayList<>();

        // Create entities with attributes
        for (int i = 0; i < entityCount; i++) {
            EntityType entity = newEntityTypeBuilder()
                    .withName("Entity" + i)
                    .build();

            // Add attributes
            for (int j = 0; j < ATTRIBUTES_PER_ENTITY; j++) {
                Attribute attr;
                if (j % 2 == 0) {
                    attr = newAttributeBuilder()
                            .withName("strAttr" + j)
                            .withDataType(stringType)
                            .withRequired(j == 0)
                            .build();
                } else {
                    attr = newAttributeBuilder()
                            .withName("intAttr" + j)
                            .withDataType(intType)
                            .withRequired(false)
                            .build();
                }
                entity.getAttributes().add(attr);
            }

            entities.add(entity);
        }

        // Add relations between entities
        for (int i = 0; i < entityCount; i++) {
            EntityType source = entities.get(i);
            for (int j = 0; j < RELATIONS_PER_ENTITY; j++) {
                int targetIndex = (i + j + 1) % entityCount;
                EntityType target = entities.get(targetIndex);

                AssociationEnd relation = newAssociationEndBuilder()
                        .withName("rel" + j + "To" + targetIndex)
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(j == 0 ? 1 : -1)
                                .build())
                        .build();
                source.getRelations().add(relation);
            }
        }

        // Build model with all elements
        List<NamespaceElement> elements = new ArrayList<>();
        elements.add(stringType);
        elements.add(intType);
        elements.addAll(entities);

        Model model = newModelBuilder()
                .withName("PerformanceTestModel")
                .withElements(ImmutableList.copyOf(elements))
                .build();

        psmModel.addContent(model);

        return psmModel;
    }

    private int countModelElements(int entityCount) {
        // 2 types + entities + (attributes per entity * entities) + (relations per entity * entities)
        return 2 + entityCount + (ATTRIBUTES_PER_ENTITY * entityCount) + (RELATIONS_PER_ENTITY * entityCount);
    }

    private double calculateStdDev(List<Long> values, double mean) {
        double sumSquaredDiff = 0;
        for (Long value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    private static class PerformanceResult {
        final long min;
        final long max;
        final double avg;
        final long median;
        final double stdDev;

        PerformanceResult(long min, long max, double avg, long median, double stdDev) {
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.median = median;
            this.stdDev = stdDev;
        }
    }
}
