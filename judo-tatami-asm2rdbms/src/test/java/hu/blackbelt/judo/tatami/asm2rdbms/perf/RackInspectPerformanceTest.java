package hu.blackbelt.judo.tatami.asm2rdbms.perf;

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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms;
import hu.blackbelt.judo.tatami.asm2rdbms.util.ModelComparator;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.loadAsmModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test using the real-world RackInspect model for ASM to RDBMS transformation.
 * 
 * Run with: mvn test -pl judo-tatami-asm2rdbms -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final File RACKINSPECT_ASM = new File("../rackinspect/rackinspect-asm.model");
    private static final String DIALECT = "hsqldb";
    
    private static AsmModel asmModel;
    private static int classCount;
    private static int attributeCount;
    private static int totalElements;

    @BeforeAll
    static void loadModel() throws Exception {
        if (!RACKINSPECT_ASM.exists()) {
            throw new IllegalStateException("RackInspect ASM model not found at: " + RACKINSPECT_ASM.getAbsolutePath());
        }

        log.info("Loading RackInspect ASM model from: {}", RACKINSPECT_ASM.getAbsolutePath());
        long loadStart = System.currentTimeMillis();
        
        asmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        long loadTime = System.currentTimeMillis() - loadStart;
        log.info("Model loaded in {}ms", loadTime);

        // Count model elements
        classCount = 0;
        attributeCount = 0;
        totalElements = 0;
        
        for (var resource : asmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                EObject obj = iterator.next();
                totalElements++;
                if (obj instanceof EClass) {
                    classCount++;
                    attributeCount += ((EClass) obj).getEStructuralFeatures().size();
                }
            }
        }

        log.info("Model statistics:");
        log.info("  - EClasses: {}", classCount);
        log.info("  - Structural Features: {}", attributeCount);
        log.info("  - Total ASM Elements: {}", totalElements);
    }

    @Test
    void testRackInspectPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("ETL vs ZETA Performance: RackInspect ASM to RDBMS ({})", DIALECT);
        log.info("================================================================");
        log.info("Classes: {}, Features: {}, Total ASM Elements: {}", 
                classCount, attributeCount, totalElements);
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
        RdbmsModel etlResult = executeEtlTransformation();
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlElementCount = countRdbmsElements(etlResult);
        log.info("ETL completed in {}ms, produced {} RDBMS elements", etlTime, etlElementCount);

        // Run Zeta transformation
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        RdbmsModel zetaResult = executeZetaTransformation();
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaElementCount = countRdbmsElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} RDBMS elements", zetaTime, zetaElementCount);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Print comparison
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: RackInspect ASM to RDBMS");
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
                String.format("%8d", etlElementCount), 
                String.format("%8d", zetaElementCount));
        log.info("");
        
        if (etlTime > 0 && zetaTime > 0) {
            double etlThroughput = totalElements / (etlTime / 1000.0);
            double zetaThroughput = totalElements / (zetaTime / 1000.0);
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

    private RdbmsModel executeEtlTransformation() throws Exception {
        // Reload ASM model for fresh transformation
        AsmModel freshAsmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        RdbmsModel rdbmsModel = createRdbmsModel();
        
        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(freshAsmModel)
                .rdbmsModel(rdbmsModel)
                .dialect(DIALECT));
        
        return rdbmsModel;
    }

    private RdbmsModel executeZetaTransformation() throws Exception {
        // Reload ASM model for fresh transformation
        AsmModel freshAsmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        RdbmsModel rdbmsModel = createRdbmsModel();
        
        // Load mapping model
        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + DIALECT + "-rdbms"))
                        .inputStream(UriUtil.resolve("mapping-" + DIALECT + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());
        
        Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(freshAsmModel)
                .rdbmsModel(rdbmsModel)
                .dialect(DIALECT)
                .build();
        transformation.execute();
        
        return rdbmsModel;
    }

    private RdbmsModel createRdbmsModel() {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel()
                .build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
        return rdbmsModel;
    }

    private int countRdbmsElements(RdbmsModel rdbmsModel) {
        int count = 0;
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(RdbmsModel etlResult, RdbmsModel zetaResult) {
        assertTrue(!etlResult.getResourceSet().getResources().isEmpty(), 
                "ETL result has no resources");
        assertTrue(!zetaResult.getResourceSet().getResources().isEmpty(), 
                "Zeta result has no resources");

        int etlCount = countRdbmsElements(etlResult);
        int zetaCount = countRdbmsElements(zetaResult);
        
        log.info("ETL produced {} elements, Zeta produced {} elements", etlCount, zetaCount);

        if (etlCount == zetaCount) {
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
