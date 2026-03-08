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
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.util.AbstractExternalModelTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unified external model test for ASM2RDBMS transformation.
 *
 * <p>Collects models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}).
 */
@Slf4j
@Tag("comparison")
@Tag("performance")
public class Asm2RdbmsDiscoveryComparisonTest extends AbstractExternalModelTest {

    private static final String BASEDIR_PROPERTY = "judo.test.discovery.basedir";
    private static final String MODEL_CONVENTION_PATH = "application/model/target/generated-resources/model";

    @TestFactory
    Collection<DynamicTest> compareEtlAndZetaForExternalModels() {
        Map<String, ExternalModelConfig> models = new LinkedHashMap<>();

        String baseDirValue = System.getProperty(BASEDIR_PROPERTY);
        if (baseDirValue != null && !baseDirValue.isEmpty()) {
            Path baseDir = Paths.get(baseDirValue).toAbsolutePath().normalize();
            if (baseDir.toFile().isDirectory()) {
                log.info("Discovering ASM models in: {}", baseDir);
                discoverModels(baseDir, "asm", MODEL_CONVENTION_PATH)
                        .forEach(config -> models.put(config.modelName(), config));
            }
        }

        loadModelConfigs(Asm2RdbmsDiscoveryComparisonTest.class)
                .forEach(config -> models.put(config.modelName(), config));

        Assumptions.assumeTrue(!models.isEmpty(),
                "No models found (set '" + BASEDIR_PROPERTY + "' or configure external-model-tests.properties)");

        clearResults();
        List<DynamicTest> tests = models.values().stream()
                .map(config -> DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)))
                .collect(Collectors.toList());
        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary("ASM2RDBMS");
            writeJsonResults("asm2rdbms", Paths.get("target"));
        }));
        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("ASM2RDBMS", config);

        Path modelFile = config.getModelFile("asm");
        assertTrue(modelFile.toFile().exists(),
                "ASM model file not found: " + modelFile);

        String dialect = config.getDialect();
        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);
        log.info("Dialect: {}", dialect);

        AsmModel countModel;
        try {
            countModel = loadAsmModel(modelFile, "count");
        } catch (Exception e) {
            log.warn("Skipping {} - model cannot be loaded: {}", config.modelName(), e.getMessage());
            Assumptions.assumeTrue(false, "Model cannot be loaded (metamodel incompatibility): " + e.getMessage());
            return;
        }
        int classifierCount = countClassifiers(countModel);
        log.info("Total Classifiers: {}", classifierCount);

        if (config.isWarmupEnabled()) {
            log.info("--- Warmup ---");
            AsmModel warmupEtl = loadAsmModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL, dialect);
            AsmModel warmupZeta = loadAsmModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA, dialect);
            log.info("Warmup complete");
        }

        long etlTotalTime = 0;
        int etlTables = 0;
        RdbmsModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            AsmModel etlSource = loadAsmModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlSource, TransformationMode.ETL, dialect);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlTables = countTables(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();

        long zetaTotalTime = 0;
        int zetaTables = 0;
        RdbmsModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            AsmModel zetaSource = loadAsmModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaSource, TransformationMode.ZETA, dialect);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaTables = countTables(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();

        printResults(config.modelName() + " ASM2RDBMS", classifierCount,
                etlTime, zetaTime, etlTables, zetaTables, "Tables");

        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison skipped (disabled via system property)");
            recordResult(config.modelName(), etlTime, zetaTime, etlTables, zetaTables, "Tables", "SKIPPED", 0);
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
            recordResult(config.modelName(), etlTime, zetaTime, etlTables, zetaTables, "Tables", "EQUIVALENT", 0);
        } else {
            recordResult(config.modelName(), etlTime, zetaTime, etlTables, zetaTables, "Tables", "FAILED", result.getDifferenceList().size());
            log.error("{} - Models have {} difference(s):\n{}",
                    config.modelName(), result.getDifferenceList().size(), result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + config.modelName() + ":\n" + result.getDetailedReport());
        }
    }

    private AsmModel loadAsmModel(Path modelFile, String name) throws Exception {
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-asm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private RdbmsModel executeTransformation(AsmModel asmModel, TransformationMode mode, String dialect) throws Exception {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        if (mode.isZeta()) {
            java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
            RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                    rdbmsLoadArgumentsBuilder()
                            .validateModel(false)
                            .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms"))
                            .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                    .toURL()
                                    .openStream()));
            rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());

            Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect(dialect)
                    .build();
            transformation.execute();
        } else {
            executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect(dialect));
        }

        return rdbmsModel;
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
}
