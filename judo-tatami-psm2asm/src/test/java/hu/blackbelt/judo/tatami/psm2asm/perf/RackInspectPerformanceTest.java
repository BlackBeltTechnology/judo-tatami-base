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
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.FileInputStream;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Performance test using the real RackInspect PSM model.
 *
 * Run with: mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final String RACKINSPECT_MODEL_PATH =
            "/Users/robson/Project/rackinspect/application/model/target/generated-resources/model/rackinspect-psm.model";

    static boolean modelExists() {
        return new File(RACKINSPECT_MODEL_PATH).exists();
    }

    @Test
    @EnabledIf("modelExists")
    void testRackInspectPsm2AsmPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("RackInspect PSM2ASM Performance Test");
        log.info("================================================================");

        File modelFile = new File(RACKINSPECT_MODEL_PATH);
        log.info("Loading model from: {}", modelFile.getAbsolutePath());
        log.info("Model file size: {} MB", modelFile.length() / (1024 * 1024));

        // Warmup
        log.info("");
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        PsmModel warmupEtl = loadRackInspectPsmModel("warmup-etl");
        executeTransformation(warmupEtl, TransformationMode.ETL);

        log.info("Warming up ZETA...");
        PsmModel warmupZeta = loadRackInspectPsmModel("warmup-zeta");
        executeTransformation(warmupZeta, TransformationMode.ZETA);
        log.info("Warmup complete");

        // Count elements in the model
        PsmModel countModel = loadRackInspectPsmModel("count");
        int totalElements = countElements(countModel);
        log.info("");
        log.info("Model statistics:");
        log.info("  Total Elements: {}", totalElements);

        // ETL measurement
        log.info("");
        log.info("--- ETL Transformation (measured) ---");
        PsmModel etlModel = loadRackInspectPsmModel("etl");
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = executeTransformation(etlModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlClassifiers = countClassifiers(etlResult);
        log.info("ETL completed in {}ms, produced {} classifiers", etlTime, etlClassifiers);

        // Zeta measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        PsmModel zetaModel = loadRackInspectPsmModel("zeta");
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = executeTransformation(zetaModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaClassifiers = countClassifiers(zetaResult);
        log.info("Zeta completed in {}ms, produced {} classifiers", zetaTime, zetaClassifiers);

        // Results
        printResults(totalElements, etlTime, zetaTime, etlClassifiers, zetaClassifiers);

        // Model comparison
        log.info("");
        log.info("--- Model Comparison ---");
        log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta models are EQUIVALENT");
        } else {
            log.error("Models have {} difference(s):\n{}",
                    result.getDifferenceList().size(), result.getSummary());
            fail("ETL and Zeta models are not equivalent:\n" + result.getDetailedReport());
        }
    }

    private PsmModel loadRackInspectPsmModel(String name) throws Exception {
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI("rackinspect-" + name + "-psm.model"))
                .inputStream(new FileInputStream(RACKINSPECT_MODEL_PATH)));
    }

    private AsmModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("RackInspectTest");
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

    private int countElements(PsmModel psmModel) {
        int count = 0;
        for (var resource : psmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
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

    private void printResults(int totalElements, long etlTime, long zetaTime,
                              int etlClassifiers, int zetaClassifiers) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: RackInspect PSM2ASM ({} elements)", totalElements);
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
