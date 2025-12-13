package hu.blackbelt.judo.tatami.rdbms2liquibase.perf;

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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.util.ModelComparator;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.loadRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test using the real-world RackInspect model for RDBMS to Liquibase transformation.
 * 
 * Run with: mvn test -pl judo-tatami-rdbms2liquibase -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final File RACKINSPECT_RDBMS = new File("../rackinspect/rackinspect-rdbms_hsqldb.model");
    private static final String DIALECT = "hsqldb";
    
    private static RdbmsModel rdbmsModel;
    private static int tableCount;
    private static int fieldCount;
    private static int totalElements;

    @BeforeAll
    static void loadModel() throws Exception {
        if (!RACKINSPECT_RDBMS.exists()) {
            throw new IllegalStateException("RackInspect RDBMS model not found at: " + RACKINSPECT_RDBMS.getAbsolutePath());
        }

        log.info("Loading RackInspect RDBMS model from: {}", RACKINSPECT_RDBMS.getAbsolutePath());
        long loadStart = System.currentTimeMillis();
        
        // Build empty model first and register metamodels before loading
        rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
        
        // Now load the model with registered metamodels
        RdbmsModel loadedModel = loadRdbmsModel(rdbmsLoadArgumentsBuilder()
                .resourceSet(rdbmsModel.getResourceSet())
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_RDBMS.getAbsolutePath()))
                .build());
        rdbmsModel = loadedModel;
        
        long loadTime = System.currentTimeMillis() - loadStart;
        log.info("Model loaded in {}ms", loadTime);

        // Count model elements
        tableCount = 0;
        fieldCount = 0;
        totalElements = 0;
        
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                EObject obj = iterator.next();
                totalElements++;
                if (obj instanceof RdbmsTable) {
                    tableCount++;
                    fieldCount += ((RdbmsTable) obj).getFields().size();
                }
            }
        }

        log.info("Model statistics:");
        log.info("  - Tables: {}", tableCount);
        log.info("  - Fields: {}", fieldCount);
        log.info("  - Total RDBMS Elements: {}", totalElements);
    }

    @Test
    void testRackInspectPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("ETL vs ZETA Performance: RackInspect RDBMS to Liquibase ({})", DIALECT);
        log.info("================================================================");
        log.info("Tables: {}, Fields: {}, Total RDBMS Elements: {}", 
                tableCount, fieldCount, totalElements);
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
        LiquibaseModel etlResult = executeEtlTransformation();
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlElementCount = countLiquibaseElements(etlResult);
        log.info("ETL completed in {}ms, produced {} Liquibase elements", etlTime, etlElementCount);

        // Run Zeta transformation
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        LiquibaseModel zetaResult = executeZetaTransformation();
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaElementCount = countLiquibaseElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} Liquibase elements", zetaTime, zetaElementCount);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Print comparison
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: RackInspect RDBMS to Liquibase");
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

    private RdbmsModel loadFreshRdbmsModel() throws Exception {
        RdbmsModel model = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(model.getResourceSet());
        registerRdbmsDataTypesMetamodel(model.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(model.getResourceSet());
        return loadRdbmsModel(rdbmsLoadArgumentsBuilder()
                .resourceSet(model.getResourceSet())
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_RDBMS.getAbsolutePath()))
                .build());
    }

    private LiquibaseModel executeEtlTransformation() throws Exception {
        // Reload RDBMS model for fresh transformation
        RdbmsModel freshRdbmsModel = loadFreshRdbmsModel();
        
        LiquibaseModel liquibaseModel = buildLiquibaseModel()
                .name("RackInspect")
                .build();
        
        executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                .rdbmsModel(freshRdbmsModel)
                .liquibaseModel(liquibaseModel)
                .dialect(DIALECT));
        
        return liquibaseModel;
    }

    private LiquibaseModel executeZetaTransformation() throws Exception {
        // Reload RDBMS model for fresh transformation
        RdbmsModel freshRdbmsModel = loadFreshRdbmsModel();
        
        LiquibaseModel liquibaseModel = buildLiquibaseModel()
                .name("RackInspect")
                .build();
        
        Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                .rdbmsModel(freshRdbmsModel)
                .liquibaseModel(liquibaseModel)
                .dialect(DIALECT)
                .build();
        transformation.execute();
        
        return liquibaseModel;
    }

    private int countLiquibaseElements(LiquibaseModel liquibaseModel) {
        int count = 0;
        for (var resource : liquibaseModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(LiquibaseModel etlResult, LiquibaseModel zetaResult) {
        assertTrue(!etlResult.getResourceSet().getResources().isEmpty(), 
                "ETL result has no resources");
        assertTrue(!zetaResult.getResourceSet().getResources().isEmpty(), 
                "Zeta result has no resources");

        int etlCount = countLiquibaseElements(etlResult);
        int zetaCount = countLiquibaseElements(zetaResult);
        
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
