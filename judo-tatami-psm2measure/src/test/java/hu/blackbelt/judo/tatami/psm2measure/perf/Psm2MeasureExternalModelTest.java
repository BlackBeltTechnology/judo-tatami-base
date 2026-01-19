package hu.blackbelt.judo.tatami.psm2measure.perf;

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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureWork;
import hu.blackbelt.judo.tatami.test.util.AbstractExternalModelTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import hu.blackbelt.judo.tatami.test.util.comparison.ComparisonResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parametrized external model test for PSM2Measure transformation.
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
 * <p>Run with: {@code mvn test -pl judo-tatami-psm2measure -Dtest=Psm2MeasureExternalModelTest -Pperformance}
 */
@Slf4j
@Tag("performance")
public class Psm2MeasureExternalModelTest extends AbstractExternalModelTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("externalModels")
    void testExternalModel(ExternalModelConfig config) throws Exception {
        // Skip if model directory doesn't exist
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("PSM2Measure", config);

        Path modelFile = config.getModelFile("psm");
        assertTrue(modelFile.toFile().exists(),
                "PSM model file not found: " + modelFile + "\n" +
                "Expected file: " + config.modelName() + "-psm.model");

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} MB", modelFile.toFile().length() / (1024 * 1024));

        // Optional warmup (only for engines that will run)
        if (config.isWarmupEnabled()) {
            log.info("");
            log.info("--- Warmup ---");
            if (shouldRunEtl()) {
                log.info("Warming up ETL...");
                PsmModel warmupEtl = loadPsmModel(modelFile, "warmup-etl");
                executeTransformation(warmupEtl, TransformationMode.ETL);
            }
            if (shouldRunZeta()) {
                log.info("Warming up ZETA...");
                PsmModel warmupZeta = loadPsmModel(modelFile, "warmup-zeta");
                executeTransformation(warmupZeta, TransformationMode.ZETA);
            }
            log.info("Warmup complete");
        }

        // Count input elements
        PsmModel countModel = loadPsmModel(modelFile, "count");
        int totalElements = countElements(countModel.getResourceSet());
        log.info("");
        log.info("Model statistics:");
        log.info("  Total Elements: {}", totalElements);

        // ETL measurement (if enabled)
        long etlTime = 0;
        int etlMeasures = 0;
        MeasureModel etlResult = null;
        if (shouldRunEtl()) {
            log.info("");
            log.info("--- ETL Transformation (measured) ---");
            long etlTotalTime = 0;
            for (int i = 0; i < config.getIterations(); i++) {
                PsmModel etlModel = loadPsmModel(modelFile, "etl-" + i);
                long etlStart = System.currentTimeMillis();
                etlResult = executeTransformation(etlModel, TransformationMode.ETL);
                etlTotalTime += System.currentTimeMillis() - etlStart;
                etlMeasures = countMeasures(etlResult);
            }
            etlTime = etlTotalTime / config.getIterations();
            log.info("ETL completed in {}ms (avg of {}), produced {} measures",
                    etlTime, config.getIterations(), etlMeasures);
        } else {
            log.info("");
            log.info("--- ETL Transformation (skipped) ---");
        }

        // ZETA measurement (if enabled)
        long zetaTime = 0;
        int zetaMeasures = 0;
        MeasureModel zetaResult = null;
        if (shouldRunZeta()) {
            log.info("");
            log.info("--- ZETA Transformation (measured) ---");
            long zetaTotalTime = 0;
            for (int i = 0; i < config.getIterations(); i++) {
                PsmModel zetaModel = loadPsmModel(modelFile, "zeta-" + i);
                long zetaStart = System.currentTimeMillis();
                zetaResult = executeTransformation(zetaModel, TransformationMode.ZETA);
                zetaTotalTime += System.currentTimeMillis() - zetaStart;
                zetaMeasures = countMeasures(zetaResult);
            }
            zetaTime = zetaTotalTime / config.getIterations();
            log.info("Zeta completed in {}ms (avg of {}), produced {} measures",
                    zetaTime, config.getIterations(), zetaMeasures);
        } else {
            log.info("");
            log.info("--- ZETA Transformation (skipped) ---");
        }

        // Results (only meaningful in DUAL mode)
        if (shouldCompareResults()) {
            printResults(config.modelName() + " PSM2Measure", totalElements,
                    etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures");
        } else {
            log.info("");
            log.info("================================================================");
            log.info("RESULTS: {} ({} elements)", config.modelName() + " PSM2Measure", totalElements);
            log.info("================================================================");
            if (shouldRunEtl()) {
                log.info("ETL Time: {}ms, Measures: {}", etlTime, etlMeasures);
            }
            if (shouldRunZeta()) {
                log.info("ZETA Time: {}ms, Measures: {}", zetaTime, zetaMeasures);
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
            exportModelStructures(etlResource, zetaResource, config.modelName() + "-psm2measure");

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
                // Use Resource-level comparison for order-independent matching of root elements
                // (measure models have multiple root elements - one per measure)
                ModelComparator.ComparisonResult result = ModelComparator.compare(
                        etlResource,
                        zetaResource,
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
        return loadModelConfigs(Psm2MeasureExternalModelTest.class);
    }

    private PsmModel loadPsmModel(Path modelFile, String name) throws Exception {
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-psm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private MeasureModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("ExternalModelTest");
        context.put(psmModel);
        context.put(Psm2MeasureWork.Psm2MeasureWorkParameter.psm2MeasureWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .build());

        Psm2MeasureWork work = new Psm2MeasureWork(context);
        work.execute();

        return context.getByClass(MeasureModel.class)
                .orElseThrow(() -> new IllegalStateException("Measure Model not found after transformation"));
    }

    private int countMeasures(MeasureModel measureModel) {
        int count = 0;
        for (var resource : measureModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var obj = iterator.next();
                if (obj instanceof hu.blackbelt.judo.meta.measure.Measure) {
                    count++;
                }
            }
        }
        return count;
    }
}
