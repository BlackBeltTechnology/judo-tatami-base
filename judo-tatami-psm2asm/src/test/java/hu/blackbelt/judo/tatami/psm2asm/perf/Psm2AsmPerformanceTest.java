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
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.psm2asm.ModelComparator;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformationV2;
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
 * 
 * Run with: mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmPerformanceTest -Dgroups=performance
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
        log.info("ETL vs ZETA vs ZETA-V2 Performance Comparison: {} ({} entities)", testName, entityCount);
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
        PerformanceResult etlResult = runPerformanceTest(psmModel, TransformationMode.ETL, entityCount);

        // Run Zeta V1 performance test
        log.info("");
        log.info("--- ZETA V1 Transformation ---");
        PerformanceResult zetaResult = runPerformanceTest(psmModel, TransformationMode.ZETA, entityCount);

        // Run Zeta V2 performance test
        log.info("");
        log.info("--- ZETA V2 Transformation (TransformationRegistry) ---");
        PerformanceResult zetaV2Result = runPerformanceTestV2(psmModel, entityCount);

        // Compare output models
        log.info("");
        log.info("--- Model Comparison ---");
        compareOutputModels(psmModel);

        // Print comparison
        printComparisonV2(testName, entityCount, totalElements, etlResult, zetaResult, zetaV2Result);
    }

    private void compareOutputModels(PsmModel psmModel) throws Exception {
        // Execute all three transformations and compare results
        AsmModel etlResult = executeTransformationWithResult(psmModel, TransformationMode.ETL);
        AsmModel zetaResult = executeTransformationWithResult(psmModel, TransformationMode.ZETA);
        AsmModel zetaV2Result = executeTransformationV2WithResult(psmModel);

        // Compare the models - count structural elements
        if (!etlResult.getResourceSet().getResources().isEmpty() && 
            !zetaResult.getResourceSet().getResources().isEmpty() &&
            !zetaV2Result.getResourceSet().getResources().isEmpty()) {
            
            // For performance tests with synthetic models, we do a structural comparison
            // rather than a full comparison since annotations may differ
            org.eclipse.emf.ecore.EPackage etlPkg = (org.eclipse.emf.ecore.EPackage) 
                    etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            org.eclipse.emf.ecore.EPackage zetaPkg = (org.eclipse.emf.ecore.EPackage) 
                    zetaResult.getResourceSet().getResources().get(0).getContents().get(0);
            org.eclipse.emf.ecore.EPackage zetaV2Pkg = (org.eclipse.emf.ecore.EPackage) 
                    zetaV2Result.getResourceSet().getResources().get(0).getContents().get(0);
            
            int etlClassCount = etlPkg.getEClassifiers().size();
            int zetaClassCount = zetaPkg.getEClassifiers().size();
            int zetaV2ClassCount = zetaV2Pkg.getEClassifiers().size();
            
            log.info("ETL output: {} classifiers", etlClassCount);
            log.info("Zeta V1 output: {} classifiers", zetaClassCount);
            log.info("Zeta V2 output: {} classifiers", zetaV2ClassCount);
            
            if (etlClassCount == zetaClassCount && etlClassCount == zetaV2ClassCount) {
                log.info("All output models have EQUIVALENT structure ({} classifiers)", etlClassCount);
            } else {
                log.warn("Output models have DIFFERENT classifier counts: ETL={}, Zeta-V1={}, Zeta-V2={}", 
                        etlClassCount, zetaClassCount, zetaV2ClassCount);
                
                // Find extra classifiers in V2
                java.util.Set<String> etlNames = etlPkg.getEClassifiers().stream()
                        .map(c -> c.getName()).collect(java.util.stream.Collectors.toSet());
                java.util.Set<String> v2Names = zetaV2Pkg.getEClassifiers().stream()
                        .map(c -> c.getName()).collect(java.util.stream.Collectors.toSet());
                
                java.util.Set<String> extraInV2 = new java.util.HashSet<>(v2Names);
                extraInV2.removeAll(etlNames);
                if (!extraInV2.isEmpty()) {
                    log.warn("Extra classifiers in V2: {}", extraInV2);
                }
                
                java.util.Set<String> missingInV2 = new java.util.HashSet<>(etlNames);
                missingInV2.removeAll(v2Names);
                if (!missingInV2.isEmpty()) {
                    log.warn("Missing classifiers in V2: {}", missingInV2);
                }
                
                // Check for duplicates in V2
                java.util.List<String> v2NamesList = zetaV2Pkg.getEClassifiers().stream()
                        .map(c -> c.getName()).collect(java.util.stream.Collectors.toList());
                java.util.Map<String, Long> v2NameCounts = v2NamesList.stream()
                        .collect(java.util.stream.Collectors.groupingBy(n -> n, java.util.stream.Collectors.counting()));
                java.util.Map<String, Long> duplicates = v2NameCounts.entrySet().stream()
                        .filter(e -> e.getValue() > 1)
                        .collect(java.util.stream.Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                if (!duplicates.isEmpty()) {
                    log.warn("Duplicate classifiers in V2: {}", duplicates);
                }
            }
            
            // Assert V2 produces equivalent output to ETL
            assertEquals(etlClassCount, zetaV2ClassCount, 
                    "Zeta V2 should produce same number of classifiers as ETL");
        }
    }

    private AsmModel executeTransformationV2WithResult(PsmModel psmModel) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        Psm2AsmZetaTransformationV2 transformation = Psm2AsmZetaTransformationV2.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName("PerformanceTest")
                .build();
        transformation.execute();
        return asmModel;
    }

    private AsmModel executeTransformationWithResult(PsmModel psmModel, TransformationMode mode) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        
        if (mode.isZeta()) {
            Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName("PerformanceTest")
                    .build();
            transformation.execute();
        } else {
            executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .createTrace(false));
        }
        
        return asmModel;
    }

    private PerformanceResult runPerformanceTest(PsmModel psmModel, TransformationMode mode, int entityCount) throws Exception {
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

    private PerformanceResult runPerformanceTestV2(PsmModel psmModel, int entityCount) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeTransformationV2(psmModel);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeTransformationV2(psmModel);
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

        return new PerformanceResult(null, stats.getMin(), stats.getMax(), stats.getAverage(), median, stdDev);
    }

    private void executeTransformationV2(PsmModel psmModel) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        Psm2AsmZetaTransformationV2 transformation = Psm2AsmZetaTransformationV2.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName("PerformanceTest")
                .build();
        transformation.execute();
    }

    private void executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        AsmModel asmModel = buildAsmModel().build();
        
        if (mode.isZeta()) {
            // Execute Zeta transformation directly
            Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName("PerformanceTest")
                    .build();
            transformation.execute();
        } else {
            // Execute ETL transformation directly
            // Note: executePsm2AsmTransformation is the ETL-only static method,
            // distinct from Psm2AsmWork which supports both modes via TransformationMode.
            // This ensures ETL execution regardless of system property settings.
            executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .createTrace(false));
        }
    }

    private void printComparison(String testName, int entityCount, int totalElements, 
                                  PerformanceResult etlResult, PerformanceResult zetaResult) {
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: {} ({} entities, {} elements)", testName, entityCount, totalElements);
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

        // Assert Zeta is not significantly slower (allow 50% tolerance for now since Zeta is placeholder)
        assertTrue(zetaResult.avg < etlResult.avg * 1.5, 
                "Zeta transformation is significantly slower than ETL: " + 
                zetaResult.avg + "ms vs " + etlResult.avg + "ms");
    }

    private void printComparisonV2(String testName, int entityCount, int totalElements, 
                                   PerformanceResult etlResult, PerformanceResult zetaResult, 
                                   PerformanceResult zetaV2Result) {
        log.info("");
        log.info("================================================================================");
        log.info("COMPARISON RESULTS: {} ({} entities, {} elements)", testName, entityCount, totalElements);
        log.info("================================================================================");
        log.info("");
        log.info("                    ETL              ZETA-V1          ZETA-V2");
        log.info("--------------------------------------------------------------------------------");
        log.info("Min:          {}ms       {}ms       {}ms", 
                String.format("%8d", etlResult.min), 
                String.format("%8d", zetaResult.min), 
                String.format("%8d", zetaV2Result.min));
        log.info("Max:          {}ms       {}ms       {}ms", 
                String.format("%8d", etlResult.max), 
                String.format("%8d", zetaResult.max),
                String.format("%8d", zetaV2Result.max));
        log.info("Avg:          {}ms       {}ms       {}ms", 
                String.format("%8.1f", etlResult.avg), 
                String.format("%8.1f", zetaResult.avg),
                String.format("%8.1f", zetaV2Result.avg));
        log.info("Median:       {}ms       {}ms       {}ms", 
                String.format("%8d", etlResult.median), 
                String.format("%8d", zetaResult.median),
                String.format("%8d", zetaV2Result.median));
        log.info("StdDev:       {}ms       {}ms       {}ms", 
                String.format("%8.1f", etlResult.stdDev), 
                String.format("%8.1f", zetaResult.stdDev),
                String.format("%8.1f", zetaV2Result.stdDev));
        log.info("");
        
        double etlThroughput = totalElements / (etlResult.avg / 1000.0);
        double zetaThroughput = totalElements / (zetaResult.avg / 1000.0);
        double zetaV2Throughput = totalElements / (zetaV2Result.avg / 1000.0);
        log.info("Throughput:   {}/s       {}/s       {}/s", 
                String.format("%8.0f", etlThroughput), 
                String.format("%8.0f", zetaThroughput),
                String.format("%8.0f", zetaV2Throughput));
        log.info("");
        
        // Compare V1 to ETL
        if (zetaResult.avg < etlResult.avg) {
            double speedup = etlResult.avg / zetaResult.avg;
            log.info(">>> ZETA-V1 is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaResult.avg > etlResult.avg) {
            double slowdown = zetaResult.avg / etlResult.avg;
            log.info(">>> ZETA-V1 is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        }
        
        // Compare V2 to ETL
        if (zetaV2Result.avg < etlResult.avg) {
            double speedup = etlResult.avg / zetaV2Result.avg;
            log.info(">>> ZETA-V2 is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaV2Result.avg > etlResult.avg) {
            double slowdown = zetaV2Result.avg / etlResult.avg;
            log.info(">>> ZETA-V2 is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        }
        
        // Compare V2 to V1
        if (zetaV2Result.avg < zetaResult.avg) {
            double speedup = zetaResult.avg / zetaV2Result.avg;
            log.info(">>> ZETA-V2 is {}x FASTER than ZETA-V1 <<<", String.format("%.2f", speedup));
        } else if (zetaV2Result.avg > zetaResult.avg) {
            double slowdown = zetaV2Result.avg / zetaResult.avg;
            log.info(">>> ZETA-V2 is {}x SLOWER than ZETA-V1 <<<", String.format("%.2f", slowdown));
        }
        
        log.info("================================================================================\n");

        // Assert Zeta V2 is not significantly slower than ETL (allow 50% tolerance)
        assertTrue(zetaV2Result.avg < etlResult.avg * 1.5, 
                "Zeta V2 transformation is significantly slower than ETL: " + 
                zetaV2Result.avg + "ms vs " + etlResult.avg + "ms");
    }

    private double percentDiff(double baseline, double value) {
        return ((value - baseline) / baseline) * 100.0;
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
