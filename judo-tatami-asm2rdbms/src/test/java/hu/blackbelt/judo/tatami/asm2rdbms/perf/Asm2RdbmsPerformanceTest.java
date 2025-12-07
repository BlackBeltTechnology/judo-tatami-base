package hu.blackbelt.judo.tatami.asm2rdbms.perf;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
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

import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests for ASM to RDBMS transformation.
 * 
 * Run with: mvn test -pl judo-tatami-asm2rdbms -Dtest=Asm2RdbmsPerformanceTest -Dgroups=performance
 */
@Slf4j
@Tag("performance")
public class Asm2RdbmsPerformanceTest {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int ATTRIBUTES_PER_CLASS = 5;

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
    void testVariousModelSizes(int classCount) throws Exception {
        runPerformanceTest(classCount, "Size-" + classCount);
    }

    private void runPerformanceTest(int classCount, String testName) throws Exception {
        log.info("========================================");
        log.info("Performance Test: {} ({} classes)", testName, classCount);
        log.info("========================================");

        // Generate ASM model
        long genStart = System.nanoTime();
        AsmModel asmModel = generateAsmModel(classCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = classCount + (classCount * ATTRIBUTES_PER_CLASS);
        log.info("Total model elements: {} ({} classes, {} attributes)", 
                totalElements, classCount, classCount * ATTRIBUTES_PER_CLASS);

        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            RdbmsModel rdbmsModel = createRdbmsModel();
            executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect("hsqldb"));
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            RdbmsModel rdbmsModel = createRdbmsModel();
            
            long startTime = System.nanoTime();
            executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect("hsqldb"));
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
        log.info("Results for {} ({} classes, {} elements):", testName, classCount, totalElements);
        log.info("  Min:    {}ms", stats.getMin());
        log.info("  Max:    {}ms", stats.getMax());
        log.info("  Avg:    {}ms", String.format("%.1f", stats.getAverage()));
        log.info("  Median: {}ms", median);
        log.info("  StdDev: {}ms", String.format("%.1f", stdDev));
        log.info("  Throughput: {} elements/sec", String.format("%.0f", elementsPerSecond));
        log.info("========================================\n");

        // Basic sanity check
        assertTrue(stats.getAverage() < classCount * 20, 
                "Transformation too slow: " + stats.getAverage() + "ms for " + classCount + " classes");
    }

    private RdbmsModel createRdbmsModel() {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel()
                .build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
        return rdbmsModel;
    }

    private AsmModel generateAsmModel(int classCount) {
        AsmModel asmModel = AsmModel.buildAsmModel()
                .build();

        EPackage rootPackage = EcoreFactory.eINSTANCE.createEPackage();
        rootPackage.setName("perftest");
        rootPackage.setNsPrefix("perftest");
        rootPackage.setNsURI("http://perftest");

        // Create classes with attributes
        for (int i = 0; i < classCount; i++) {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            eClass.setName("Entity" + i);

            // Add entity annotation
            org.eclipse.emf.ecore.EAnnotation entityAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
            entityAnnotation.setSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/entity");
            entityAnnotation.getDetails().put("value", "true");
            eClass.getEAnnotations().add(entityAnnotation);

            // Add attributes
            for (int j = 0; j < ATTRIBUTES_PER_CLASS; j++) {
                EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();
                attr.setName("attr" + j);
                attr.setEType(EcorePackage.Literals.ESTRING);
                
                // Add constraints annotation for string type
                org.eclipse.emf.ecore.EAnnotation constraintAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
                constraintAnnotation.setSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/constraints");
                constraintAnnotation.getDetails().put("maxLength", "255");
                attr.getEAnnotations().add(constraintAnnotation);
                
                eClass.getEStructuralFeatures().add(attr);
            }

            rootPackage.getEClassifiers().add(eClass);
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
}
