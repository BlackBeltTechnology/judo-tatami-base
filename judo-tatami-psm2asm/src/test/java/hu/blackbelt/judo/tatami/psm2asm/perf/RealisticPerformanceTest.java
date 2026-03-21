package hu.blackbelt.judo.tatami.psm2asm.perf;

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
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import hu.blackbelt.judo.tatami.test.RealisticPsmModelGenerator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Realistic performance test that generates a synthetic model with characteristics
 * similar to the RackInspect real-world model using the shared RealisticPsmModelGenerator.
 *
 * Run with: mvn test -pl judo-tatami-psm2asm -Dtest=RealisticPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    private final RealisticPsmModelGenerator generator = new RealisticPsmModelGenerator();

    @Test
    void testRealisticModelPerformance() throws Exception {
        // Generate model with ~70 entities (similar to RackInspect)
        runPerformanceTest(70, "RackInspect-like");
    }

    @Test
    void testSmallRealisticModel() throws Exception {
        runPerformanceTest(20, "Small");
    }

    @Test
    void testLargeRealisticModel() throws Exception {
        runPerformanceTest(100, "Large");
    }

    private void runPerformanceTest(int entityCount, String testName) throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("Realistic Performance Test: {} ({} entities)", testName, entityCount);
        log.info("================================================================");

        // Generate model using shared generator
        long genStart = System.currentTimeMillis();
        PsmModel psmModel = generator.generate(entityCount);
        long genTime = System.currentTimeMillis() - genStart;

        // Count elements
        int totalElements = RealisticPsmModelGenerator.countElements(psmModel);
        int expectedTOs = entityCount * (RealisticPsmModelGenerator.MAPPED_TOS_PER_ENTITY
                + RealisticPsmModelGenerator.UNMAPPED_TOS_PER_ENTITY);

        log.info("Model generated in {}ms", genTime);
        log.info("  Entities: {}", entityCount);
        log.info("  Expected Transfer Objects: ~{}", expectedTOs);
        log.info("  Total Elements: {}", totalElements);
        log.info("");

        // Warmup - use fresh models to avoid parallel execution state issues
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(generator.generate(entityCount), TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(generator.generate(entityCount), TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // ETL measurement - use fresh model for each run to avoid parallel execution state issues
        log.info("--- ETL Transformation (measured) ---");
        PsmModel etlPsmModel = generator.generate(entityCount);
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = executeTransformation(etlPsmModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlClassifiers = countClassifiers(etlResult);
        log.info("ETL completed in {}ms, produced {} classifiers", etlTime, etlClassifiers);

        // Zeta measurement - use fresh model for each run to avoid parallel execution state issues
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        PsmModel zetaPsmModel = generator.generate(entityCount);
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = executeTransformation(zetaPsmModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaClassifiers = countClassifiers(zetaResult);
        log.info("Zeta completed in {}ms, produced {} classifiers", zetaTime, zetaClassifiers);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Results
        printResults(testName, entityCount, totalElements, etlTime, zetaTime, etlClassifiers, zetaClassifiers);
    }

    private AsmModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("RealisticTest");
        context.put(psmModel);
        context.put(Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .build());

        Psm2AsmWork work = new Psm2AsmWork(context);
        work.execute();

        return context.getByClass(AsmModel.class)
                .orElseThrow(() -> new IllegalStateException("ASM Model not found after transformation"));
    }

    private int countClassifiers(AsmModel asmModel) {
        int count = 0;
        for (var resource : asmModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof EPackage) {
                    count += countClassifiersRecursive((EPackage) content);
                }
            }
        }
        return count;
    }

    private int countClassifiersRecursive(EPackage pkg) {
        int count = pkg.getEClassifiers().size();
        for (EPackage subPkg : pkg.getESubpackages()) {
            count += countClassifiersRecursive(subPkg);
        }
        return count;
    }

    private void compareModels(AsmModel etlResult, AsmModel zetaResult) {
        int etlClassifiers = countClassifiers(etlResult);
        int zetaClassifiers = countClassifiers(zetaResult);

        if (etlClassifiers != zetaClassifiers) {
            log.warn("Classifier count mismatch: ETL={}, Zeta={}", etlClassifiers, zetaClassifiers);
        } else {
            log.info("Classifier count: {} (both match)", etlClassifiers);
        }

        // Structural comparison
        if (!etlResult.getResourceSet().getResources().isEmpty() &&
            !zetaResult.getResourceSet().getResources().isEmpty()) {

            EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

            ModelComparator.ComparisonResult result = ModelComparator.compare(
                    etlRoot, zetaRoot, ModelComparator.ComparisonMode.SKELETON);

            if (result.isEquivalent()) {
                log.info("SUCCESS: ETL and Zeta models are structurally equivalent");
            } else {
                log.warn("Models have {} difference(s)", result.getDifferenceCount());
                int count = 0;
                for (ModelComparator.Difference diff : result.getDifferenceList()) {
                    if (count++ >= 10) {
                        log.warn("  ... and {} more differences", result.getDifferenceCount() - 10);
                        break;
                    }
                    log.warn("  - {}", diff.describe());
                }
            }
        }
    }

    private void printResults(String testName, int entityCount, int totalElements,
                              long etlTime, long zetaTime, int etlClassifiers, int zetaClassifiers) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} entities, {} elements)", testName, entityCount, totalElements);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTime),
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double) (zetaTime - etlTime) / etlTime) * 100));
        log.info("Classifiers:  {}       {}",
                String.format("%8d", etlClassifiers),
                String.format("%8d", zetaClassifiers));
        log.info("");

        double etlThroughput = totalElements / (etlTime / 1000.0);
        double zetaThroughput = totalElements / (zetaTime / 1000.0);
        log.info("Throughput:   {}/s       {}/s",
                String.format("%8.0f", etlThroughput),
                String.format("%8.0f", zetaThroughput));
        log.info("");

        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / zetaTime;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTime > etlTime) {
            double slowdown = (double) zetaTime / etlTime;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");
    }
}
