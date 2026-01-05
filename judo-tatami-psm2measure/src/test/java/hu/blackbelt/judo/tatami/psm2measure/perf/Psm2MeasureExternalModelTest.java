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

        // Optional warmup
        if (config.isWarmupEnabled()) {
            log.info("");
            log.info("--- Warmup ---");
            log.info("Warming up ETL...");
            PsmModel warmupEtl = loadPsmModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL);

            log.info("Warming up ZETA...");
            PsmModel warmupZeta = loadPsmModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA);
            log.info("Warmup complete");
        }

        // Count input elements
        PsmModel countModel = loadPsmModel(modelFile, "count");
        int totalElements = countElements(countModel.getResourceSet());
        log.info("");
        log.info("Model statistics:");
        log.info("  Total Elements: {}", totalElements);

        // ETL measurement
        log.info("");
        log.info("--- ETL Transformation (measured) ---");
        long etlTotalTime = 0;
        int etlMeasures = 0;
        MeasureModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel etlModel = loadPsmModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlModel, TransformationMode.ETL);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlMeasures = countMeasures(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();
        log.info("ETL completed in {}ms (avg of {}), produced {} measures",
                etlTime, config.getIterations(), etlMeasures);

        // ZETA measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaTotalTime = 0;
        int zetaMeasures = 0;
        MeasureModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel zetaModel = loadPsmModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaModel, TransformationMode.ZETA);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaMeasures = countMeasures(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();
        log.info("Zeta completed in {}ms (avg of {}), produced {} measures",
                zetaTime, config.getIterations(), zetaMeasures);

        // Results
        printResults(config.modelName() + " PSM2Measure", totalElements,
                etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures");

        // Model comparison
        log.info("");
        log.info("--- Model Comparison ---");
        log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());

        // Use Resource-level comparison for order-independent matching of root elements
        // (measure models have multiple root elements - one per measure)
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
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
