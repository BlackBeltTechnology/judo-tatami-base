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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unified external model test for PSM2Measure transformation.
 *
 * <p>Collects models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}).
 */
@Slf4j
@Tag("comparison")
@Tag("performance")
public class Psm2MeasureDiscoveryComparisonTest extends AbstractExternalModelTest {

    private static final String BASEDIR_PROPERTY = "judo.test.discovery.basedir";
    private static final String MODEL_CONVENTION_PATH = "application/model/target/generated-resources/model";

    @TestFactory
    Collection<DynamicTest> compareEtlAndZetaForExternalModels() {
        Map<String, ExternalModelConfig> models = new LinkedHashMap<>();

        String baseDirValue = System.getProperty(BASEDIR_PROPERTY);
        if (baseDirValue != null && !baseDirValue.isEmpty()) {
            Path baseDir = Paths.get(baseDirValue).toAbsolutePath().normalize();
            if (baseDir.toFile().isDirectory()) {
                log.info("Discovering PSM models in: {}", baseDir);
                discoverModels(baseDir, "psm", MODEL_CONVENTION_PATH)
                        .forEach(config -> models.put(config.modelName(), config));
            }
        }

        loadModelConfigs(Psm2MeasureDiscoveryComparisonTest.class)
                .forEach(config -> models.put(config.modelName(), config));

        Assumptions.assumeTrue(!models.isEmpty(),
                "No models found (set '" + BASEDIR_PROPERTY + "' or configure external-model-tests.properties)");

        clearResults();
        List<DynamicTest> tests = models.values().stream()
                .map(config -> DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)))
                .collect(Collectors.toList());
        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary("PSM2Measure");
            writeJsonResults("psm2measure", Paths.get("target"));
        }));
        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("PSM2Measure", config);

        Path modelFile = config.getModelFile("psm");
        assertTrue(modelFile.toFile().exists(),
                "PSM model file not found: " + modelFile);

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);

        PsmModel countModel;
        try {
            countModel = loadPsmModel(modelFile, "count");
        } catch (Exception e) {
            log.warn("Skipping {} - model cannot be loaded: {}", config.modelName(), e.getMessage());
            Assumptions.assumeTrue(false, "Model cannot be loaded (metamodel incompatibility): " + e.getMessage());
            return;
        }
        int totalElements = countElements(countModel.getResourceSet());
        log.info("Total Elements: {}", totalElements);

        if (config.isWarmupEnabled()) {
            log.info("--- Warmup ---");
            PsmModel warmupEtl = loadPsmModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL);
            PsmModel warmupZeta = loadPsmModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA);
            log.info("Warmup complete");
        }

        long etlTotalTime = 0;
        int etlMeasures = 0;
        MeasureModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel etlSource = loadPsmModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlSource, TransformationMode.ETL);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlMeasures = countMeasures(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();

        long zetaTotalTime = 0;
        int zetaMeasures = 0;
        MeasureModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel zetaSource = loadPsmModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaSource, TransformationMode.ZETA);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaMeasures = countMeasures(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();

        printResults(config.modelName() + " PSM2Measure", totalElements,
                etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures");

        // Model comparison (Resource-level for measure models with multiple root elements)
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison skipped (disabled via system property)");
            recordResult(config.modelName(), etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures", "SKIPPED", 0);
            return;
        }

        log.info("Comparison mode: {}", ModelComparator.getConfiguredMode());

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: {} - ETL and Zeta models are EQUIVALENT", config.modelName());
            recordResult(config.modelName(), etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures", "EQUIVALENT", 0);
        } else {
            recordResult(config.modelName(), etlTime, zetaTime, etlMeasures, zetaMeasures, "Measures", "FAILED", result.getDifferenceList().size());
            log.error("{} - Models have {} difference(s):\n{}",
                    config.modelName(), result.getDifferenceList().size(), result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + config.modelName() + ":\n" + result.getDetailedReport());
        }
    }

    private PsmModel loadPsmModel(Path modelFile, String name) throws Exception {
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI(name + "-psm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    private MeasureModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("DiscoveryComparisonTest");
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
