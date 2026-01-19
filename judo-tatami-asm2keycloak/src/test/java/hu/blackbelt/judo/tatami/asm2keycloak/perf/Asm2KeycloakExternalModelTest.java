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
import hu.blackbelt.judo.tatami.test.util.comparison.ComparisonResult;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parametrized external model test for ASM2Keycloak transformation.
 *
 * <p>Configure external models in {@code src/test/resources/external-model-tests.properties}:
 * <pre>
 * # Simple format
 * rackinspect=../../../rackinspect/application/model/target/generated-resources/model
 *
 * # Extended format with warmup and iterations
 * mymodel=/path/to/models;warmup=true;iterations=3
 * </pre>
 *
 * <p>Run with: {@code mvn test -pl judo-tatami-asm2keycloak -Dtest=Asm2KeycloakExternalModelTest -Pperformance}
 */
@Slf4j
@Tag("performance")
public class Asm2KeycloakExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        // Skip if model directory doesn't exist
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("ASM2Keycloak", config);

        Path modelFile = config.getModelFile("asm");
        assertTrue(modelFile.toFile().exists(),
                "ASM model file not found: " + modelFile + "\n" +
                "Expected file: " + config.modelName() + "-asm.model");

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} MB", modelFile.toFile().length() / (1024 * 1024));

        // Optional warmup (only for engines that will run)
        if (config.isWarmupEnabled()) {
            log.info("");
            log.info("--- Warmup ---");
            if (shouldRunEtl()) {
                log.info("Warming up ETL...");
                AsmModel warmupEtl = loadAsmModel(modelFile, "warmup-etl");
                executeTransformation(warmupEtl, TransformationMode.ETL);
            }
            if (shouldRunZeta()) {
                log.info("Warming up ZETA...");
                AsmModel warmupZeta = loadAsmModel(modelFile, "warmup-zeta");
                executeTransformation(warmupZeta, TransformationMode.ZETA);
            }
            log.info("Warmup complete");
        }

        // Count input elements
        AsmModel countModel = loadAsmModel(modelFile, "count");
        int classifierCount = countClassifiers(countModel);
        log.info("");
        log.info("Model statistics:");
        log.info("  Total Classifiers: {}", classifierCount);

        // ETL measurement (if enabled)
        long etlTime = 0;
        int etlClients = 0;
        int etlRealms = 0;
        KeycloakModel etlResult = null;
        if (shouldRunEtl()) {
            log.info("");
            log.info("--- ETL Transformation (measured) ---");
            long etlTotalTime = 0;
            for (int i = 0; i < config.getIterations(); i++) {
                AsmModel etlModel = loadAsmModel(modelFile, "etl-" + i);
                long etlStart = System.currentTimeMillis();
                etlResult = executeTransformation(etlModel, TransformationMode.ETL);
                etlTotalTime += System.currentTimeMillis() - etlStart;
                etlClients = countClients(etlResult);
                etlRealms = countRealms(etlResult);
            }
            etlTime = etlTotalTime / config.getIterations();
            log.info("ETL completed in {}ms (avg of {}), produced {} clients in {} realms",
                    etlTime, config.getIterations(), etlClients, etlRealms);
        } else {
            log.info("");
            log.info("--- ETL Transformation (skipped) ---");
        }

        // ZETA measurement (if enabled)
        long zetaTime = 0;
        int zetaClients = 0;
        int zetaRealms = 0;
        KeycloakModel zetaResult = null;
        if (shouldRunZeta()) {
            log.info("");
            log.info("--- ZETA Transformation (measured) ---");
            long zetaTotalTime = 0;
            for (int i = 0; i < config.getIterations(); i++) {
                AsmModel zetaModel = loadAsmModel(modelFile, "zeta-" + i);
                long zetaStart = System.currentTimeMillis();
                zetaResult = executeTransformation(zetaModel, TransformationMode.ZETA);
                zetaTotalTime += System.currentTimeMillis() - zetaStart;
                zetaClients = countClients(zetaResult);
                zetaRealms = countRealms(zetaResult);
            }
            zetaTime = zetaTotalTime / config.getIterations();
            log.info("Zeta completed in {}ms (avg of {}), produced {} clients in {} realms",
                    zetaTime, config.getIterations(), zetaClients, zetaRealms);
        } else {
            log.info("");
            log.info("--- ZETA Transformation (skipped) ---");
        }

        // Results (only meaningful in DUAL mode)
        if (shouldCompareResults()) {
            printResults(config.modelName() + " ASM2Keycloak", classifierCount,
                    etlTime, zetaTime, etlClients, zetaClients, "Clients");
        } else {
            log.info("");
            log.info("================================================================");
            log.info("RESULTS: {} ({} elements)", config.modelName() + " ASM2Keycloak", classifierCount);
            log.info("================================================================");
            if (shouldRunEtl()) {
                log.info("ETL Time: {}ms, Clients: {}, Realms: {}", etlTime, etlClients, etlRealms);
            }
            if (shouldRunZeta()) {
                log.info("ZETA Time: {}ms, Clients: {}, Realms: {}", zetaTime, zetaClients, zetaRealms);
            }
            log.info("================================================================");
        }

        // Model comparison (only in DUAL mode)
        if (shouldCompareResults()) {
            log.info("");
            log.info("--- Model Comparison ---");

            var etlResource = etlResult.getResourceSet().getResources().get(0);
            var zetaResource = zetaResult.getResourceSet().getResources().get(0);

            // Export model structures if enabled
            exportModelStructures(etlResource, zetaResource, config.modelName() + "-asm2keycloak");

            if (isStructuralComparisonEnabled()) {
                log.info("Using structural comparison (checksum-based)");
                ComparisonResult structuralResult = compareModelsStructural(etlResource, zetaResource);

                if (structuralResult.isMatch()) {
                    log.info("SUCCESS: ETL and Zeta models are STRUCTURALLY EQUIVALENT");
                } else {
                    log.error("Structural comparison found {} difference(s)", structuralResult.getDifferenceCount());
                    fail("ETL and Zeta models are not structurally equivalent: " +
                            structuralResult.getDifferenceCount() + " differences found");
                }
            } else {
                log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());
                ModelComparator.ComparisonResult result = ModelComparator.compare(
                        etlResource.getContents().get(0),
                        zetaResource.getContents().get(0),
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
        } else {
            log.info("");
            log.info("--- Model Comparison (skipped - single engine mode) ---");
        }
    }

    public static Stream<ExternalModelConfig> externalModels() {
        return loadModelConfigs(Asm2KeycloakExternalModelTest.class);
    }

    private AsmModel loadAsmModel(Path modelFile, String name) throws Exception {
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-asm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private KeycloakModel executeTransformation(AsmModel asmModel, TransformationMode mode) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("external-keycloak")
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

    private int countRealms(KeycloakModel keycloakModel) {
        int count = 0;
        for (var resource : keycloakModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var obj = iterator.next();
                if (obj instanceof hu.blackbelt.judo.meta.keycloak.Realm) {
                    count++;
                }
            }
        }
        return count;
    }
}
