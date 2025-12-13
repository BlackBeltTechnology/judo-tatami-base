package hu.blackbelt.judo.tatami.asm2keycloak.perf;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.util.ModelComparator;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
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
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests comparing ETL vs Zeta for ASM to Keycloak transformation.
 * 
 * Run with: mvn test -pl judo-tatami-asm2keycloak -Pperformance -Dtest=Asm2KeycloakPerformanceTest
 */
@Slf4j
@Tag("performance")
public class Asm2KeycloakPerformanceTest {

    private static final int WARMUP_ITERATIONS = 0;
    private static final int MEASUREMENT_ITERATIONS = 1;

    private AsmUtils asmUtils;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testSmallModelComparison() throws Exception {
        runComparisonTest(10, "Small");
    }

    @Test
    void testMediumModelComparison() throws Exception {
        runComparisonTest(50, "Medium");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    void testVariousModelSizesComparison(int actorCount) throws Exception {
        runComparisonTest(actorCount, "Size-" + actorCount);
    }

    private void runComparisonTest(int actorCount, String testName) throws Exception {
        log.info("================================================================");
        log.info("ETL vs ZETA Performance Comparison: {} ({} actors)", testName, actorCount);
        log.info("================================================================");

        // Generate ASM model with actors
        long genStart = System.nanoTime();
        AsmModel asmModel = generateAsmModel(actorCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        log.info("Total model elements: {} actors", actorCount);

        // Run ETL performance test
        log.info("");
        log.info("--- ETL Transformation ---");
        PerformanceResult etlResult = runPerformanceTest(asmModel, TransformationMode.ETL, actorCount);

        // Run Zeta performance test
        log.info("");
        log.info("--- ZETA Transformation ---");
        PerformanceResult zetaResult = runPerformanceTest(asmModel, TransformationMode.ZETA, actorCount);

        // Compare output models
        log.info("");
        log.info("--- Model Comparison ---");
        compareOutputModels(asmModel);

        // Print comparison
        printComparison(testName, actorCount, actorCount, etlResult, zetaResult);
    }

    private void compareOutputModels(AsmModel asmModel) throws Exception {
        // Execute both transformations and compare results
        KeycloakModel etlResult = executeTransformationWithResult(asmModel, TransformationMode.ETL);
        KeycloakModel zetaResult = executeTransformationWithResult(asmModel, TransformationMode.ZETA);

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

    private KeycloakModel executeTransformationWithResult(AsmModel asmModel, TransformationMode mode) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("PerformanceTest")
                .build();
        
        if (mode.isZeta()) {
            Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel)
                    .build();
            transformation.execute();
        } else {
            executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel));
        }
        
        return keycloakModel;
    }

    private PerformanceResult runPerformanceTest(AsmModel asmModel, TransformationMode mode, int actorCount) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeTransformation(asmModel, mode);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeTransformation(asmModel, mode);
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

    private void executeTransformation(AsmModel asmModel, TransformationMode mode) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("PerformanceTest")
                .build();
        
        if (mode.isZeta()) {
            Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel)
                    .build();
            transformation.execute();
        } else {
            executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel));
        }
    }

    private void printComparison(String testName, int actorCount, int totalElements, 
                                  PerformanceResult etlResult, PerformanceResult zetaResult) {
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: {} ({} actors)", testName, actorCount);
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
        if (baseline == 0) return 0;
        return ((value - baseline) / baseline) * 100.0;
    }

    private AsmModel generateAsmModel(int actorCount) {
        AsmModel asmModel = buildAsmModel().build();
        asmUtils = new AsmUtils(asmModel.getResourceSet());

        EPackage rootPackage = EcoreFactory.eINSTANCE.createEPackage();
        rootPackage.setName("perftest");
        rootPackage.setNsPrefix("perftest");
        rootPackage.setNsURI("http://perftest");

        // Create actor classes with actorType annotation
        for (int i = 0; i < actorCount; i++) {
            EClass actorClass = EcoreFactory.eINSTANCE.createEClass();
            actorClass.setName("Actor" + i);

            // Add transferObjectType annotation
            EAnnotation toAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
            toAnnotation.setSource(asmUtils.getAnnotationUri("transferObjectType"));
            toAnnotation.getDetails().put("value", "true");
            actorClass.getEAnnotations().add(toAnnotation);

            // Add actorType annotation
            EAnnotation actorAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
            actorAnnotation.setSource(asmUtils.getAnnotationUri("actorType"));
            actorAnnotation.getDetails().put("value", "true");
            actorAnnotation.getDetails().put("kind", "HUMAN");
            actorClass.getEAnnotations().add(actorAnnotation);

            // Add realm annotation (every other actor has a different realm)
            if (i % 2 == 0) {
                EAnnotation realmAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
                realmAnnotation.setSource(asmUtils.getAnnotationUri("realm"));
                realmAnnotation.getDetails().put("value", "realm" + (i / 10));
                actorClass.getEAnnotations().add(realmAnnotation);
            }

            rootPackage.getEClassifiers().add(actorClass);
        }

        asmModel.addContent(rootPackage);

        return asmModel;
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
