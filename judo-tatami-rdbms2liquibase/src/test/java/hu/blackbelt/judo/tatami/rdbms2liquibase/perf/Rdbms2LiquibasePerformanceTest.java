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
import hu.blackbelt.judo.meta.rdbms.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils;
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
 * Performance tests for RDBMS to Liquibase transformation.
 * 
 * Run with: mvn test -pl judo-tatami-rdbms2liquibase -Dtest=Rdbms2LiquibasePerformanceTest -Dgroups=performance
 */
@Slf4j
@Tag("performance")
public class Rdbms2LiquibasePerformanceTest {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int FIELDS_PER_TABLE = 5;

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
    void testVariousModelSizes(int tableCount) throws Exception {
        runPerformanceTest(tableCount, "Size-" + tableCount);
    }

    private void runPerformanceTest(int tableCount, String testName) throws Exception {
        log.info("========================================");
        log.info("Performance Test: {} ({} tables)", testName, tableCount);
        log.info("========================================");

        // Generate RDBMS model
        long genStart = System.nanoTime();
        hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel = generateRdbmsModel(tableCount);
        long genTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStart);
        log.info("Model generation time: {}ms", genTime);

        int totalElements = tableCount + (tableCount * FIELDS_PER_TABLE);
        log.info("Total model elements: {} ({} tables, {} fields)", 
                totalElements, tableCount, tableCount * FIELDS_PER_TABLE);

        // Warmup
        log.info("Warming up ({} iterations)...", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            LiquibaseModel liquibaseModel = buildLiquibaseModel()
                    .name("PerformanceTest")
                    .build();
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb"));
        }

        // Measurement
        log.info("Measuring ({} iterations)...", MEASUREMENT_ITERATIONS);
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            LiquibaseModel liquibaseModel = buildLiquibaseModel()
                    .name("PerformanceTest")
                    .build();
            
            long startTime = System.nanoTime();
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
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
        log.info("Results for {} ({} tables, {} elements):", testName, tableCount, totalElements);
        log.info("  Min:    {}ms", stats.getMin());
        log.info("  Max:    {}ms", stats.getMax());
        log.info("  Avg:    {}ms", String.format("%.1f", stats.getAverage()));
        log.info("  Median: {}ms", median);
        log.info("  StdDev: {}ms", String.format("%.1f", stdDev));
        log.info("  Throughput: {} elements/sec", String.format("%.0f", elementsPerSecond));
        log.info("========================================\n");

        // Basic sanity check - allow 50ms per table to account for ETL overhead and slow CI machines
        assertTrue(stats.getAverage() < tableCount * 50, 
                "Transformation too slow: " + stats.getAverage() + "ms for " + tableCount + " tables");
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
}
