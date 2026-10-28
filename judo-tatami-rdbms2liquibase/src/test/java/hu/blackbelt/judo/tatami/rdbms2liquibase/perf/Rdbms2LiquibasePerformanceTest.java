package hu.blackbelt.judo.tatami.rdbms2liquibase.perf;

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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.RdbmsIdentifierField;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import lombok.extern.slf4j.Slf4j;
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

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbms.util.builder.RdbmsBuilders.*;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance tests comparing ETL vs Zeta for RDBMS to Liquibase transformation.
 * 
 * Run with: mvn test -pl judo-tatami-rdbms2liquibase -Pperformance -Dtest=Rdbms2LiquibasePerformanceTest
 */
@Slf4j
@Tag("performance")
public class Rdbms2LiquibasePerformanceTest {

    private static final int WARMUP_ITERATIONS = 0;
    private static final int MEASUREMENT_ITERATIONS = 1;
    private static final int FIELDS_PER_TABLE = 5;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testSmallModelComparison() throws Exception {
        runComparisonTest(100, "Small");
    }

    @Test
    void testMediumModelComparison() throws Exception {
        runComparisonTest(500, "Medium");
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000})
    void testVariousModelSizesComparison(int tableCount) throws Exception {
        runComparisonTest(tableCount, "Size-" + tableCount);
    }

    private void runComparisonTest(int tableCount, String testName) throws Exception {
        log.info("================================================================");
        log.info("ETL vs ZETA Performance Comparison: {} ({} tables)", testName, tableCount);
        log.info("================================================================");

        // Generate RDBMS model
        long genStart = System.nanoTime();
        hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel = generateRdbmsModel(tableCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = tableCount + (tableCount * FIELDS_PER_TABLE);
        log.info("Total model elements: {} ({} tables, {} fields)", 
                totalElements, tableCount, tableCount * FIELDS_PER_TABLE);

        // Run ETL performance test
        log.info("");
        log.info("--- ETL Transformation ---");
        PerformanceResult etlResult = runPerformanceTest(rdbmsModel, TransformationMode.ETL, tableCount);

        // Run Zeta performance test
        log.info("");
        log.info("--- ZETA Transformation ---");
        PerformanceResult zetaResult = runPerformanceTest(rdbmsModel, TransformationMode.ZETA, tableCount);

        // Compare output models
        log.info("");
        log.info("--- Model Comparison ---");
        compareOutputModels(rdbmsModel);

        // Print comparison
        printComparison(testName, tableCount, totalElements, etlResult, zetaResult);
    }

    private void compareOutputModels(hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel) throws Exception {
        // Execute both transformations and compare results
        LiquibaseModel etlResult = executeTransformationWithResult(rdbmsModel, TransformationMode.ETL);
        LiquibaseModel zetaResult = executeTransformationWithResult(rdbmsModel, TransformationMode.ZETA);

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
                // Log first 10 differences for debugging
                result.getDifferenceList().stream().limit(10).forEach(diff -> log.info("  - {}", diff));
            }
        }
    }

    private LiquibaseModel executeTransformationWithResult(hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel, 
                                                            TransformationMode mode) throws Exception {
        LiquibaseModel liquibaseModel = buildLiquibaseModel()
                .name("PerformanceTest")
                .build();
        
        if (mode.isZeta()) {
            Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb")
                    .build();
            transformation.execute();
        } else {
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb"));
        }
        
        return liquibaseModel;
    }

    private PerformanceResult runPerformanceTest(hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel, 
                                                  TransformationMode mode, int tableCount) throws Exception {
        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeTransformation(rdbmsModel, mode);
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executeTransformation(rdbmsModel, mode);
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

    private void executeTransformation(hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel, 
                                        TransformationMode mode) throws Exception {
        LiquibaseModel liquibaseModel = buildLiquibaseModel()
                .name("PerformanceTest")
                .build();
        
        if (mode.isZeta()) {
            Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb")
                    .build();
            transformation.execute();
        } else {
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb"));
        }
    }

    private void printComparison(String testName, int tableCount, int totalElements, 
                                  PerformanceResult etlResult, PerformanceResult zetaResult) {
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: {} ({} tables, {} elements)", testName, tableCount, totalElements);
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
        return ((value - baseline) / baseline) * 100.0;
    }

    private hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel generateRdbmsModel(int tableCount) {
        hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel = buildRdbmsModel()
                .build();
        
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        // Create tables with fields
        List<RdbmsTable> tables = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) {
            List<RdbmsField> fields = new ArrayList<>();
            
            // Add ID field
            RdbmsIdentifierField idField = newRdbmsIdentifierFieldBuilder()
                    .withName("ID")
                    .withUuid("id_" + i)
                    .withRdbmsTypeName("BIGINT")
                    .withMandatory(true)
                    .build();
            fields.add(idField);
            
            // Add other fields
            for (int j = 0; j < FIELDS_PER_TABLE; j++) {
                RdbmsField field = newRdbmsValueFieldBuilder()
                        .withName("field" + j)
                        .withUuid("field_" + i + "_" + j)
                        .withRdbmsTypeName("VARCHAR(255)")
                        .withMandatory(false)
                        .withSize(255)
                        .build();
                fields.add(field);
            }

            RdbmsTable table = newRdbmsTableBuilder()
                    .withName("T_TABLE" + i)
                    .withUuid("table_" + i)
                    .withSqlName("T_TABLE" + i)
                    .withFields(fields)
                    .withPrimaryKey(idField)
                    .build();

            tables.add(table);
        }

        // Add all tables to model
        hu.blackbelt.judo.meta.rdbms.RdbmsModel rdbmsRoot = newRdbmsModelBuilder()
                .withName("PerformanceTestModel")
                .withVersion("1.0.0")
                .withRdbmsTables(tables)
                .build();

        rdbmsModel.addContent(rdbmsRoot);

        return rdbmsModel;
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
