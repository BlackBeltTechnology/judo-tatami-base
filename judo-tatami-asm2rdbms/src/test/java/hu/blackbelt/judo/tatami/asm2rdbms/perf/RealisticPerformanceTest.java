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
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.RealisticAsmModelGenerator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;

/**
 * Realistic performance test that generates a synthetic ASM model with characteristics
 * similar to the RackInspect real-world model using the shared RealisticAsmModelGenerator.
 *
 * Run with: mvn test -pl judo-tatami-asm2rdbms -Dtest=RealisticPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    private static final String DIALECT = "hsqldb";

    private final RealisticAsmModelGenerator generator = new RealisticAsmModelGenerator();

    @Test
    void testRealisticModelPerformance() throws Exception {
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
        log.info("Realistic Performance Test (Asm2Rdbms): {} ({} entities)", testName, entityCount);
        log.info("================================================================");

        // Generate model using shared generator
        long genStart = System.currentTimeMillis();
        AsmModel asmModel = generator.generate(entityCount);
        long genTime = System.currentTimeMillis() - genStart;

        int classifierCount = RealisticAsmModelGenerator.countClassifiers(asmModel);

        log.info("Model generated in {}ms", genTime);
        log.info("  Entity classes: {}", entityCount);
        log.info("  Total classifiers: {}", classifierCount);
        log.info("  Dialect: {}", DIALECT);
        log.info("");

        // Warmup
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(asmModel, TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(asmModel, TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // ETL measurement
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        RdbmsModel etlResult = executeTransformation(asmModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlTables = countTables(etlResult);
        log.info("ETL completed in {}ms, produced {} tables", etlTime, etlTables);

        // Zeta measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        RdbmsModel zetaResult = executeTransformation(asmModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaTables = countTables(zetaResult);
        log.info("Zeta completed in {}ms, produced {} tables", zetaTime, zetaTables);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Results
        printResults(testName, entityCount, classifierCount, etlTime, zetaTime, etlTables, zetaTables);
    }

    private RdbmsModel executeTransformation(AsmModel asmModel, TransformationMode mode) throws Exception {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        if (mode.isZeta()) {
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
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect(DIALECT)
                    .build();
            transformation.execute();
        } else {
            executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect(DIALECT));
        }

        return rdbmsModel;
    }

    private int countTables(RdbmsModel rdbmsModel) {
        int count = 0;
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof hu.blackbelt.judo.meta.rdbms.RdbmsModel) {
                    count += ((hu.blackbelt.judo.meta.rdbms.RdbmsModel) content).getRdbmsTables().size();
                }
            }
        }
        return count;
    }

    private void compareModels(RdbmsModel etlResult, RdbmsModel zetaResult) {
        int etlTables = countTables(etlResult);
        int zetaTables = countTables(zetaResult);

        if (etlTables != zetaTables) {
            log.warn("Table count mismatch: ETL={}, Zeta={}", etlTables, zetaTables);
        } else {
            log.info("Table count: {} (both match)", etlTables);
        }

        // Structural comparison
        if (!etlResult.getResourceSet().getResources().isEmpty() &&
            !zetaResult.getResourceSet().getResources().isEmpty() &&
            !etlResult.getResourceSet().getResources().get(0).getContents().isEmpty() &&
            !zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty()) {

            EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

            ModelComparator.ComparisonResult result = ModelComparator.compare(etlRoot, zetaRoot);

            if (result.isEquivalent()) {
                log.info("SUCCESS: ETL and Zeta models are structurally equivalent");
            } else {
                log.warn("Models have {} difference(s)", result.getDifferenceList().size());
                int count = 0;
                for (ModelComparator.Difference diff : result.getDifferenceList()) {
                    if (count++ >= 10) {
                        log.warn("  ... and {} more differences", result.getDifferenceList().size() - 10);
                        break;
                    }
                    log.warn("  - {}", diff);
                }
            }
        }
    }

    private void printResults(String testName, int entityCount, int classifierCount,
                              long etlTime, long zetaTime, int etlTables, int zetaTables) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} entities, {} classifiers)", testName, entityCount, classifierCount);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTime),
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double) (zetaTime - etlTime) / Math.max(etlTime, 1)) * 100));
        log.info("Tables:       {}       {}",
                String.format("%8d", etlTables),
                String.format("%8d", zetaTables));
        log.info("");

        double etlThroughput = classifierCount / (Math.max(etlTime, 1) / 1000.0);
        double zetaThroughput = classifierCount / (Math.max(zetaTime, 1) / 1000.0);
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
