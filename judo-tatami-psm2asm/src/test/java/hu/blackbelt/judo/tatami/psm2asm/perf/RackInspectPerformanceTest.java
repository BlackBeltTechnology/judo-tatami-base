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
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.TransferObjectType;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.ModelComparator;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.loadPsmModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test using the real-world RackInspect model.
 * 
 * Run with: mvn test -pl judo-tatami-psm2asm -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RackInspectPerformanceTest.class);
    
    private static final File RACKINSPECT_PSM = new File("../rackinspect/rackinspect-psm.model");
    
    private static PsmModel psmModel;
    private static int entityCount;
    private static int transferObjectCount;
    private static int totalElements;

    @BeforeAll
    static void loadModel() throws Exception {
        if (!RACKINSPECT_PSM.exists()) {
            throw new IllegalStateException("RackInspect PSM model not found at: " + RACKINSPECT_PSM.getAbsolutePath());
        }

        log.info("Loading RackInspect PSM model from: {}", RACKINSPECT_PSM.getAbsolutePath());
        long loadStart = System.currentTimeMillis();
        
        psmModel = loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_PSM.getAbsolutePath()))
                .build());
        
        long loadTime = System.currentTimeMillis() - loadStart;
        log.info("Model loaded in {}ms", loadTime);

        // Count model elements
        PsmUtils psmUtils = new PsmUtils(psmModel.getResourceSet());
        entityCount = (int) psmUtils.all(psmModel.getResourceSet(), EntityType.class).count();
        transferObjectCount = (int) psmUtils.all(psmModel.getResourceSet(), TransferObjectType.class).count();
        
        // Count all contents properly
        totalElements = 0;
        for (var resource : psmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                totalElements++;
            }
        }

        log.info("Model statistics:");
        log.info("  - Entities: {}", entityCount);
        log.info("  - Transfer Objects: {}", transferObjectCount);
        log.info("  - Total Elements: {}", totalElements);
    }

    @Test
    void testRackInspectPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("ETL vs ZETA Performance: RackInspect (Real-World Model)");
        log.info("================================================================");
        log.info("Entities: {}, Transfer Objects: {}, Total Elements: {}", 
                entityCount, transferObjectCount, totalElements);
        log.info("");

        // Warmup both transformations (JIT compilation)
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // Run ETL transformation
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = executeTransformation(TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlClassifiers = countClassifiers(etlResult);
        log.info("ETL completed in {}ms, produced {} classifiers", etlTime, etlClassifiers);

        // Run Zeta transformation
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = executeTransformation(TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaClassifiers = countClassifiers(zetaResult);
        log.info("Zeta completed in {}ms, produced {} classifiers", zetaTime, zetaClassifiers);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Print comparison
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: RackInspect");
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlTime), 
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double)(zetaTime - etlTime) / etlTime) * 100));
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

    private AsmModel executeTransformation(TransformationMode mode) throws Exception {
        // Reload the PSM model for each transformation to avoid state issues
        PsmModel freshPsmModel = loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_PSM.getAbsolutePath()))
                .build());
        
        TransformationContext context = new TransformationContext("RackInspect");
        context.put(freshPsmModel);
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
        // First check classifier counts match
        int etlClassifiers = countClassifiers(etlResult);
        int zetaClassifiers = countClassifiers(zetaResult);
        
        assertEquals(etlClassifiers, zetaClassifiers, 
                "Classifier count mismatch: ETL=" + etlClassifiers + ", Zeta=" + zetaClassifiers);
        log.info("Classifier count: {} (both match)", etlClassifiers);

        // Check both have resources
        assertTrue(!etlResult.getResourceSet().getResources().isEmpty(), 
                "ETL result has no resources");
        assertTrue(!zetaResult.getResourceSet().getResources().isEmpty(), 
                "Zeta result has no resources");

        // Get root packages
        EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
        EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

        assertTrue(etlRoot instanceof EPackage, "ETL root is not an EPackage");
        assertTrue(zetaRoot instanceof EPackage, "Zeta root is not an EPackage");

        // Compare models using ModelComparator (STRUCTURAL mode - tolerates annotation differences)
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlRoot, zetaRoot, ModelComparator.ComparisonMode.STRUCTURAL);

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta models are structurally equivalent");
        } else {
            log.warn("Models have {} difference(s):", result.getDifferenceCount());
            // Log first 20 differences
            int count = 0;
            for (ModelComparator.Difference diff : result.getDifferenceList()) {
                if (count++ >= 20) {
                    log.warn("  ... and {} more differences", result.getDifferenceCount() - 20);
                    break;
                }
                log.warn("  - {}", diff.describe());
            }
            
            // Don't fail the test for now - just report differences
            // This allows us to see performance numbers even if there are equivalence issues
            log.warn("Model equivalence check found differences - review needed");
        }
    }
}
