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
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import hu.blackbelt.judo.tatami.test.util.AbstractExternalModelTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unified external model test for RDBMS2Liquibase transformation.
 *
 * <p>Collects models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}).
 *
 * <p>Note: RDBMS model files use dialect suffix: {@code <model-name>-rdbms_<dialect>.model}.
 */
@Slf4j
@Tag("comparison")
@Tag("performance")
public class Rdbms2LiquibaseDiscoveryComparisonTest extends AbstractExternalModelTest {

    private static final String BASEDIR_PROPERTY = "judo.test.discovery.basedir";
    private static final String MODEL_CONVENTION_PATH = "application/model/target/generated-resources/model";
    private static final String DEFAULT_DIALECT = "hsqldb";

    @TestFactory
    Collection<DynamicTest> compareEtlAndZetaForExternalModels() {
        Map<String, ExternalModelConfig> models = new LinkedHashMap<>();

        String baseDirValue = System.getProperty(BASEDIR_PROPERTY);
        if (baseDirValue != null && !baseDirValue.isEmpty()) {
            Path baseDir = Paths.get(baseDirValue).toAbsolutePath().normalize();
            if (baseDir.toFile().isDirectory()) {
                log.info("Discovering RDBMS models in: {}", baseDir);
                discoverModels(baseDir, "rdbms_" + DEFAULT_DIALECT, MODEL_CONVENTION_PATH)
                        .forEach(config -> models.put(config.modelName(), config));
            }
        }

        loadModelConfigs(Rdbms2LiquibaseDiscoveryComparisonTest.class)
                .forEach(config -> models.put(config.modelName(), config));

        Assumptions.assumeTrue(!models.isEmpty(),
                "No models found (set '" + BASEDIR_PROPERTY + "' or configure external-model-tests.properties)");

        clearResults();
        List<DynamicTest> tests = models.values().stream()
                .map(config -> DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)))
                .collect(Collectors.toList());
        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary("RDBMS2Liquibase");
            writeJsonResults("rdbms2liquibase", Paths.get("target"));
        }));
        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("RDBMS2Liquibase", config);

        String dialect = config.getDialect();
        // RDBMS models have dialect suffix: {name}-rdbms_hsqldb.model
        Path modelFile = config.modelDirectory().resolve(config.modelName() + "-rdbms_" + dialect + ".model");
        assertTrue(modelFile.toFile().exists(),
                "RDBMS model file not found: " + modelFile);

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);
        log.info("Dialect: {}", dialect);

        RdbmsModel countModel;
        try {
            countModel = loadRdbmsModel(modelFile, "count");
        } catch (Exception e) {
            log.warn("Skipping {} - model cannot be loaded: {}", config.modelName(), e.getMessage());
            Assumptions.assumeTrue(false, "Model cannot be loaded (metamodel incompatibility): " + e.getMessage());
            return;
        }
        int tableCount = countTables(countModel);
        int fieldCount = countFields(countModel);
        log.info("Tables: {}, Fields: {}", tableCount, fieldCount);

        if (config.isWarmupEnabled()) {
            log.info("--- Warmup ---");
            RdbmsModel warmupEtl = loadRdbmsModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL, dialect);
            RdbmsModel warmupZeta = loadRdbmsModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA, dialect);
            log.info("Warmup complete");
        }

        long etlTotalTime = 0;
        int etlChangeSets = 0;
        LiquibaseModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            RdbmsModel etlSource = loadRdbmsModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlSource, TransformationMode.ETL, dialect);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlChangeSets = countChangeSets(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();

        long zetaTotalTime = 0;
        int zetaChangeSets = 0;
        LiquibaseModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            RdbmsModel zetaSource = loadRdbmsModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaSource, TransformationMode.ZETA, dialect);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaChangeSets = countChangeSets(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();

        printResults(config.modelName() + " RDBMS2Liquibase", tableCount + fieldCount,
                etlTime, zetaTime, etlChangeSets, zetaChangeSets, "ChangeSets");

        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison skipped (disabled via system property)");
            recordResult(config.modelName(), etlTime, zetaTime, etlChangeSets, zetaChangeSets, "ChangeSets", "SKIPPED", 0);
            return;
        }

        log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: {} - ETL and Zeta models are EQUIVALENT", config.modelName());
            recordResult(config.modelName(), etlTime, zetaTime, etlChangeSets, zetaChangeSets, "ChangeSets", "EQUIVALENT", 0);
        } else {
            log.error("{} - Models have {} difference(s):\n{}",
                    config.modelName(), result.getDifferenceList().size(), result.getSummary());
            recordResult(config.modelName(), etlTime, zetaTime, etlChangeSets, zetaChangeSets, "ChangeSets", "FAILED", result.getDifferenceList().size());
            fail("ETL and Zeta models are not equivalent for " + config.modelName() + ":\n" + result.getDetailedReport());
        }
    }

    private RdbmsModel loadRdbmsModel(Path modelFile, String name) throws Exception {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        return RdbmsModel.loadRdbmsModel(rdbmsLoadArgumentsBuilder()
                .resourceSet(rdbmsModel.getResourceSet())
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-rdbms.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private LiquibaseModel executeTransformation(RdbmsModel rdbmsModel, TransformationMode mode, String dialect) throws Exception {
        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name("discovery-liquibase")
                .build();

        if (mode.isZeta()) {
            Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect(dialect)
                    .build();
            transformation.execute();
        } else {
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect(dialect));
        }

        return liquibaseModel;
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

    private int countFields(RdbmsModel rdbmsModel) {
        int count = 0;
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof hu.blackbelt.judo.meta.rdbms.RdbmsModel) {
                    for (var table : ((hu.blackbelt.judo.meta.rdbms.RdbmsModel) content).getRdbmsTables()) {
                        count += table.getFields().size();
                    }
                }
            }
        }
        return count;
    }

    private int countChangeSets(LiquibaseModel liquibaseModel) {
        int count = 0;
        for (var resource : liquibaseModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof hu.blackbelt.judo.meta.liquibase.databaseChangeLog) {
                    count += ((hu.blackbelt.judo.meta.liquibase.databaseChangeLog) content).getChangeSet().size();
                }
            }
        }
        return count;
    }
}
