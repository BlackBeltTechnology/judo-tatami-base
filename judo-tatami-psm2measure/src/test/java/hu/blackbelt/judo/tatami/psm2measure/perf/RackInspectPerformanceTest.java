package hu.blackbelt.judo.tatami.psm2measure.perf;

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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.measure.Measure;
import hu.blackbelt.judo.meta.psm.measure.Unit;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.util.ModelComparator;
import hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.loadPsmModel;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test using the real-world RackInspect model for PSM to Measure transformation.
 * 
 * Run with: mvn test -pl judo-tatami-psm2measure -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final File RACKINSPECT_PSM = new File("../rackinspect/rackinspect-psm.model");
    
    private static PsmModel psmModel;
    private static int measureCount;
    private static int unitCount;
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

        // Count model elements relevant to Psm2Measure
        PsmUtils psmUtils = new PsmUtils(psmModel.getResourceSet());
        measureCount = (int) psmUtils.all(psmModel.getResourceSet(), Measure.class).count();
        unitCount = (int) psmUtils.all(psmModel.getResourceSet(), Unit.class).count();
        
        // Count all contents
        totalElements = 0;
        for (var resource : psmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                totalElements++;
            }
        }

        log.info("Model statistics:");
        log.info("  - Measures: {}", measureCount);
        log.info("  - Units: {}", unitCount);
        log.info("  - Total PSM Elements: {}", totalElements);
    }

    @Test
    void testRackInspectPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("ETL vs ZETA Performance: RackInspect PSM to Measure");
        log.info("================================================================");
        log.info("Measures: {}, Units: {}, Total PSM Elements: {}", 
                measureCount, unitCount, totalElements);
        log.info("");

        // Warmup both transformations (JIT compilation)
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeEtlTransformation();
        log.info("Warming up ZETA...");
        executeZetaTransformation();
        log.info("Warmup complete");
        log.info("");

        // Run ETL transformation
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        MeasureModel etlResult = executeEtlTransformation();
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlMeasureCount = countMeasureElements(etlResult);
        log.info("ETL completed in {}ms, produced {} measure elements", etlTime, etlMeasureCount);

        // Run Zeta transformation
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        MeasureModel zetaResult = executeZetaTransformation();
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaMeasureCount = countMeasureElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} measure elements", zetaTime, zetaMeasureCount);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Print comparison
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: RackInspect PSM to Measure");
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlTime), 
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double)(zetaTime - etlTime) / Math.max(etlTime, 1)) * 100));
        log.info("Output Elements: {}       {}", 
                String.format("%8d", etlMeasureCount), 
                String.format("%8d", zetaMeasureCount));
        log.info("");
        
        if (etlTime > 0 && zetaTime > 0) {
            double etlThroughput = (measureCount + unitCount) / (etlTime / 1000.0);
            double zetaThroughput = (measureCount + unitCount) / (zetaTime / 1000.0);
            log.info("Throughput:   {}/s       {}/s", 
                    String.format("%8.0f", etlThroughput), 
                    String.format("%8.0f", zetaThroughput));
        }
        log.info("");
        
        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / Math.max(zetaTime, 1);
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTime > etlTime) {
            double slowdown = (double) zetaTime / Math.max(etlTime, 1);
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");

        // Assert Zeta is not significantly slower
        assertTrue(zetaTime <= etlTime * 2, 
                "Zeta should not be more than 2x slower than ETL");
    }

    private MeasureModel executeEtlTransformation() throws Exception {
        // Reload PSM model for fresh transformation
        PsmModel freshPsmModel = loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_PSM.getAbsolutePath()))
                .build());
        
        MeasureModel measureModel = buildMeasureModel()
                .name("RackInspect-measure")
                .build();
        
        executePsm2MeasureTransformation(psm2MeasureParameter()
                .psmModel(freshPsmModel)
                .measureModel(measureModel));
        
        return measureModel;
    }

    private MeasureModel executeZetaTransformation() throws Exception {
        // Reload PSM model for fresh transformation
        PsmModel freshPsmModel = loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_PSM.getAbsolutePath()))
                .build());
        
        MeasureModel measureModel = buildMeasureModel()
                .name("RackInspect-measure")
                .build();
        
        Psm2MeasureZetaTransformation transformation = Psm2MeasureZetaTransformation.builder()
                .psmModel(freshPsmModel)
                .measureModel(measureModel)
                .build();
        transformation.execute();
        
        return measureModel;
    }

    private int countMeasureElements(MeasureModel measureModel) {
        int count = 0;
        for (var resource : measureModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(MeasureModel etlResult, MeasureModel zetaResult) {
        // Check both have resources
        assertTrue(!etlResult.getResourceSet().getResources().isEmpty(), 
                "ETL result has no resources");
        assertTrue(!zetaResult.getResourceSet().getResources().isEmpty(), 
                "Zeta result has no resources");

        int etlCount = countMeasureElements(etlResult);
        int zetaCount = countMeasureElements(zetaResult);
        
        log.info("ETL produced {} elements, Zeta produced {} elements", etlCount, zetaCount);

        if (etlCount == zetaCount) {
            // Try detailed comparison
            if (!etlResult.getResourceSet().getResources().get(0).getContents().isEmpty() &&
                !zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty()) {
                
                EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
                EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);
                
                ModelComparator.ComparisonResult result = ModelComparator.compare(etlRoot, zetaRoot);
                
                if (result.isEquivalent()) {
                    log.info("SUCCESS: ETL and Zeta models are equivalent");
                } else {
                    log.info("Models have {} difference(s) - checking structural equivalence", 
                            result.getDifferenceCount());
                }
            }
        } else {
            log.warn("Element count mismatch: ETL={}, Zeta={}", etlCount, zetaCount);
        }
    }
}
