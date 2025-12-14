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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.rdbms2liquibase.util.ModelComparator;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import hu.blackbelt.judo.tatami.test.RealisticRdbmsModelGenerator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;

/**
 * Realistic performance test that generates a synthetic RDBMS model with characteristics
 * similar to the RackInspect real-world model using the shared RealisticRdbmsModelGenerator.
 *
 * Run with: mvn test -pl judo-tatami-rdbms2liquibase -Dtest=RealisticPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    private static final String DIALECT = "hsqldb";

    private final RealisticRdbmsModelGenerator generator = new RealisticRdbmsModelGenerator();

    @Test
    void testRealisticModelPerformance() throws Exception {
        runPerformanceTest(100, "RackInspect-like");
    }

    @Test
    void testSmallRealisticModel() throws Exception {
        runPerformanceTest(30, "Small");
    }

    @Test
    void testLargeRealisticModel() throws Exception {
        runPerformanceTest(200, "Large");
    }

    private void runPerformanceTest(int tableCount, String testName) throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("Realistic Performance Test (Rdbms2Liquibase): {} ({} tables)", testName, tableCount);
        log.info("================================================================");

        // Generate model using shared generator
        long genStart = System.currentTimeMillis();
        RdbmsModel rdbmsModel = generator.generate(tableCount);
        long genTime = System.currentTimeMillis() - genStart;

        int totalTables = RealisticRdbmsModelGenerator.countTables(rdbmsModel);
        int totalFields = RealisticRdbmsModelGenerator.countFields(rdbmsModel);

        log.info("Model generated in {}ms", genTime);
        log.info("  Tables: {}", totalTables);
        log.info("  Total fields: {}", totalFields);
        log.info("  Dialect: {}", DIALECT);
        log.info("");

        // Warmup
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(rdbmsModel, TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(rdbmsModel, TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // ETL measurement
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        LiquibaseModel etlResult = executeTransformation(rdbmsModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlElements = countElements(etlResult);
        log.info("ETL completed in {}ms, produced {} elements", etlTime, etlElements);

        // Zeta measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        LiquibaseModel zetaResult = executeTransformation(rdbmsModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaElements = countElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} elements", zetaTime, zetaElements);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Results
        printResults(testName, tableCount, totalFields, etlTime, zetaTime, etlElements, zetaElements);
    }

    private LiquibaseModel executeTransformation(RdbmsModel rdbmsModel, TransformationMode mode) throws Exception {
        LiquibaseModel liquibaseModel = buildLiquibaseModel()
                .name("RealisticTest")
                .build();

        if (mode.isZeta()) {
            Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect(DIALECT)
                    .build();
            transformation.execute();
        } else {
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect(DIALECT));
        }

        return liquibaseModel;
    }

    private int countElements(LiquibaseModel model) {
        int count = 0;
        for (var resource : model.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(LiquibaseModel etlResult, LiquibaseModel zetaResult) {
        int etlElements = countElements(etlResult);
        int zetaElements = countElements(zetaResult);

        if (etlElements != zetaElements) {
            log.warn("Element count mismatch: ETL={}, Zeta={}", etlElements, zetaElements);
        } else {
            log.info("Element count: {} (both match)", etlElements);
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

    private void printResults(String testName, int tableCount, int totalFields,
                              long etlTime, long zetaTime, int etlElements, int zetaElements) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} tables, {} fields)", testName, tableCount, totalFields);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTime),
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double) (zetaTime - etlTime) / Math.max(etlTime, 1)) * 100));
        log.info("Elements:     {}       {}",
                String.format("%8d", etlElements),
                String.format("%8d", zetaElements));
        log.info("");

        int totalInputs = tableCount + totalFields;
        double etlThroughput = totalInputs / (Math.max(etlTime, 1) / 1000.0);
        double zetaThroughput = totalInputs / (Math.max(zetaTime, 1) / 1000.0);
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
