package hu.blackbelt.judo.tatami.asm2keycloak.perf;

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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.util.AbstractExternalModelTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
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
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unified external model test for ASM2Keycloak transformation.
 *
 * <p>Collects models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}).
 */
@Slf4j
@Tag("comparison")
@Tag("performance")
public class Asm2KeycloakDiscoveryComparisonTest extends AbstractExternalModelTest {

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

        loadModelConfigs(Asm2KeycloakDiscoveryComparisonTest.class)
                .forEach(config -> models.put(config.modelName(), config));

        Assumptions.assumeTrue(!models.isEmpty(),
                "No models found (set '" + BASEDIR_PROPERTY + "' or configure external-model-tests.properties)");

        clearResults();
        List<DynamicTest> tests = models.values().stream()
                .map(config -> DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)))
                .collect(Collectors.toList());
        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary("ASM2Keycloak");
            writeJsonResults("asm2keycloak", Paths.get("target"));
        }));
        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("ASM2Keycloak", config);

        Path modelFile = config.getModelFile("asm");
        assertTrue(modelFile.toFile().exists(),
                "ASM model file not found: " + modelFile);

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);

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
            executeTransformation(warmupEtl, TransformationMode.ETL);
            AsmModel warmupZeta = loadAsmModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA);
            log.info("Warmup complete");
        }

        long etlTotalTime = 0;
        int etlClients = 0;
        KeycloakModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            AsmModel etlSource = loadAsmModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlSource, TransformationMode.ETL);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlClients = countClients(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();

        long zetaTotalTime = 0;
        int zetaClients = 0;
        KeycloakModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            AsmModel zetaSource = loadAsmModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaSource, TransformationMode.ZETA);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaClients = countClients(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();

        printResults(config.modelName() + " ASM2Keycloak", classifierCount,
                etlTime, zetaTime, etlClients, zetaClients, "Clients");

        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison skipped (disabled via system property)");
            recordResult(config.modelName(), etlTime, zetaTime, etlClients, zetaClients, "Clients", "SKIPPED", 0);
            return;
        }

        log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());

        boolean etlEmpty = etlResult.getResourceSet().getResources().isEmpty()
                || etlResult.getResourceSet().getResources().get(0).getContents().isEmpty();
        boolean zetaEmpty = zetaResult.getResourceSet().getResources().isEmpty()
                || zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty();

        if (etlEmpty && zetaEmpty) {
            log.info("SUCCESS: {} - Both ETL and Zeta produced empty models (no actors)", config.modelName());
            recordResult(config.modelName(), etlTime, zetaTime, etlClients, zetaClients, "Clients", "EQUIVALENT", 0);
            return;
        }
        if (etlEmpty != zetaEmpty) {
            recordResult(config.modelName(), etlTime, zetaTime, etlClients, zetaClients, "Clients", "FAILED", 1);
            fail("Model mismatch for " + config.modelName() + ": ETL empty=" + etlEmpty + ", Zeta empty=" + zetaEmpty);
        }

        // Resource-level comparison (Keycloak models have multiple Realm root elements)
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: {} - ETL and Zeta models are EQUIVALENT", config.modelName());
            recordResult(config.modelName(), etlTime, zetaTime, etlClients, zetaClients, "Clients", "EQUIVALENT", 0);
        } else {
            log.error("{} - Models have {} difference(s):\n{}",
                    config.modelName(), result.getDifferenceList().size(), result.getSummary());
            recordResult(config.modelName(), etlTime, zetaTime, etlClients, zetaClients, "Clients", "FAILED", result.getDifferenceList().size());
            fail("ETL and Zeta models are not equivalent for " + config.modelName() + ":\n" + result.getDetailedReport());
        }
    }

    private AsmModel loadAsmModel(Path modelFile, String name) throws Exception {
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-asm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private KeycloakModel executeTransformation(AsmModel asmModel, TransformationMode mode) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("discovery-keycloak")
                .build();

        if (mode.isZeta()) {
            Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel)
                    .build();
            transformation.execute();
        } else {
            executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel));
        }

        return keycloakModel;
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

    private int countClients(KeycloakModel keycloakModel) {
        int count = 0;
        for (var resource : keycloakModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var obj = iterator.next();
                if (obj instanceof hu.blackbelt.judo.meta.keycloak.Client) {
                    count++;
                }
            }
        }
        return count;
    }
}
