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
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import hu.blackbelt.judo.tatami.test.util.AbstractExternalModelTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parametrized external model test for RDBMS2Liquibase transformation.
 *
 * <p>Configure external models in {@code src/test/resources/external-model-tests.properties}:
 * <pre>
 * # Simple format (uses default dialect: hsqldb)
 * rackinspect=../../../rackinspect/application/model/target/generated-resources/model
 *
 * # Extended format with dialect and performance options
 * mymodel=/path/to/models;dialect=postgresql;warmup=true;iterations=3
 * </pre>
 *
 * <p>Note: RDBMS model files are expected with dialect suffix: {@code <model-name>-rdbms_<dialect>.model}
 *
 * <p>Run with: {@code mvn test -pl judo-tatami-rdbms2liquibase -Dtest=Rdbms2LiquibaseExternalModelTest -Pperformance}
 */
@Slf4j
@Tag("performance")
public class Rdbms2LiquibaseExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        // Skip if model directory doesn't exist
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("RDBMS2Liquibase", config);

        String dialect = config.getDialect();
        // RDBMS models have dialect suffix: rackinspect-rdbms_hsqldb.model
        Path modelFile = config.modelDirectory().resolve(config.modelName() + "-rdbms_" + dialect + ".model");
        assertTrue(modelFile.toFile().exists(),
                "RDBMS model file not found: " + modelFile + "\n" +
                "Expected file: " + config.modelName() + "-rdbms_" + dialect + ".model");

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);
        log.info("Dialect: {}", dialect);

        // Optional warmup
        if (config.isWarmupEnabled()) {
            log.info("");
            log.info("--- Warmup ---");
            log.info("Warming up ETL...");
            RdbmsModel warmupEtl = loadRdbmsModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL, dialect);

            log.info("Warming up ZETA...");
            RdbmsModel warmupZeta = loadRdbmsModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA, dialect);
            log.info("Warmup complete");
        }

        // Count input elements
        RdbmsModel countModel = loadRdbmsModel(modelFile, "count");
        int tableCount = countTables(countModel);
        int fieldCount = countFields(countModel);
        log.info("");
        log.info("Model statistics:");
        log.info("  Tables: {}", tableCount);
        log.info("  Fields: {}", fieldCount);

        // ETL measurement
        log.info("");
        log.info("--- ETL Transformation (measured) ---");
        long etlTotalTime = 0;
        int etlChangeSets = 0;
        LiquibaseModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            RdbmsModel etlModel = loadRdbmsModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlModel, TransformationMode.ETL, dialect);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlChangeSets = countChangeSets(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();
        log.info("ETL completed in {}ms (avg of {}), produced {} changeSets",
                etlTime, config.getIterations(), etlChangeSets);

        // ZETA measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaTotalTime = 0;
        int zetaChangeSets = 0;
        LiquibaseModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            RdbmsModel zetaModel = loadRdbmsModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaModel, TransformationMode.ZETA, dialect);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaChangeSets = countChangeSets(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();
        log.info("Zeta completed in {}ms (avg of {}), produced {} changeSets",
                zetaTime, config.getIterations(), zetaChangeSets);

        // Results
        printResults(config.modelName() + " RDBMS2Liquibase", tableCount + fieldCount,
                etlTime, zetaTime, etlChangeSets, zetaChangeSets, "ChangeSets");

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

    public static Stream<ExternalModelConfig> externalModels() {
        return loadModelConfigs(Rdbms2LiquibaseExternalModelTest.class);
    }

    private RdbmsModel loadRdbmsModel(Path modelFile, String name) throws Exception {
        // Create an empty model first to register metamodels
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        // Now load the model content
        return RdbmsModel.loadRdbmsModel(rdbmsLoadArgumentsBuilder()
                .resourceSet(rdbmsModel.getResourceSet())
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-rdbms.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private LiquibaseModel executeTransformation(RdbmsModel rdbmsModel, TransformationMode mode, String dialect) throws Exception {
        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name("external-liquibase")
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
