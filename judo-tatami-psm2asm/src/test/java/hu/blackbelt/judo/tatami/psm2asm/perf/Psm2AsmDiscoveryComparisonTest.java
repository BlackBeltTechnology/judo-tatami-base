package hu.blackbelt.judo.tatami.psm2asm.perf;

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
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
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

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unified external model test for PSM2ASM transformation.
 *
 * <p>Collects models from two sources:
 * <ul>
 *   <li>Auto-discovery via system property {@code judo.test.discovery.basedir}</li>
 *   <li>Properties file {@code external-model-tests.properties} in classpath</li>
 * </ul>
 *
 * <p>Properties-file models override discovered models with the same name
 * (to allow custom warmup/iterations/dialect settings).
 *
 * <p>Run with:
 * <pre>
 * mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmDiscoveryComparisonTest \
 *     -Djudo.test.discovery.basedir=/path/to/models
 * </pre>
 */
@Slf4j
@Tag("comparison")
@Tag("performance")
public class Psm2AsmDiscoveryComparisonTest extends AbstractExternalModelTest {

    private static final String BASEDIR_PROPERTY = "judo.test.discovery.basedir";
    private static final String MODEL_CONVENTION_PATH = "application/model/target/generated-resources/model";

    @TestFactory
    Collection<DynamicTest> compareEtlAndZetaForExternalModels() {
        Map<String, ExternalModelConfig> models = new LinkedHashMap<>();

        // 1. Auto-discovery
        String baseDirValue = System.getProperty(BASEDIR_PROPERTY);
        if (baseDirValue != null && !baseDirValue.isEmpty()) {
            Path baseDir = Paths.get(baseDirValue).toAbsolutePath().normalize();
            if (baseDir.toFile().isDirectory()) {
                log.info("Discovering PSM models in: {}", baseDir);
                discoverModels(baseDir, "psm", MODEL_CONVENTION_PATH)
                        .forEach(config -> models.put(config.modelName(), config));
            }
        }

        // 2. Properties file (overrides discovery for same name)
        loadModelConfigs(Psm2AsmDiscoveryComparisonTest.class)
                .forEach(config -> models.put(config.modelName(), config));

        Assumptions.assumeTrue(!models.isEmpty(),
                "No models found (set '" + BASEDIR_PROPERTY + "' or configure external-model-tests.properties)");

        clearResults();
        List<DynamicTest> tests = models.values().stream()
                .map(config -> DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)))
                .collect(Collectors.toList());
        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary("PSM2ASM");
            writeJsonResults("psm2asm", Paths.get("target"));
        }));
        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        // Skip if model directory doesn't exist
        Assumptions.assumeTrue(checkModelExists(config),
                "Model directory does not exist: " + config.modelDirectory());

        logTestHeader("PSM2ASM", config);

        Path modelFile = config.getModelFile("psm");
        assertTrue(modelFile.toFile().exists(),
                "PSM model file not found: " + modelFile);

        log.info("Model file: {}", modelFile);
        log.info("Model file size: {} KB", modelFile.toFile().length() / 1024);

        // Count input elements - skip if model can't be loaded (metamodel incompatibility)
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

        // Optional warmup
        if (config.isWarmupEnabled()) {
            log.info("--- Warmup ---");
            PsmModel warmupEtl = loadPsmModel(modelFile, "warmup-etl");
            executeTransformation(warmupEtl, TransformationMode.ETL);
            PsmModel warmupZeta = loadPsmModel(modelFile, "warmup-zeta");
            executeTransformation(warmupZeta, TransformationMode.ZETA);
            log.info("Warmup complete");
        }

        // ETL measurement
        long etlTotalTime = 0;
        int etlClassifiers = 0;
        AsmModel etlResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel etlSource = loadPsmModel(modelFile, "etl-" + i);
            long etlStart = System.currentTimeMillis();
            etlResult = executeTransformation(etlSource, TransformationMode.ETL);
            etlTotalTime += System.currentTimeMillis() - etlStart;
            etlClassifiers = countClassifiers(etlResult);
        }
        long etlTime = etlTotalTime / config.getIterations();

        // Zeta measurement
        long zetaTotalTime = 0;
        int zetaClassifiers = 0;
        AsmModel zetaResult = null;
        for (int i = 0; i < config.getIterations(); i++) {
            PsmModel zetaSource = loadPsmModel(modelFile, "zeta-" + i);
            long zetaStart = System.currentTimeMillis();
            zetaResult = executeTransformation(zetaSource, TransformationMode.ZETA);
            zetaTotalTime += System.currentTimeMillis() - zetaStart;
            zetaClassifiers = countClassifiers(zetaResult);
        }
        long zetaTime = zetaTotalTime / config.getIterations();

        // Performance summary
        printResults(config.modelName() + " PSM2ASM", totalElements,
                etlTime, zetaTime, etlClassifiers, zetaClassifiers, "Classifiers");

        // Model comparison
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison skipped (disabled via system property)");
            recordResult(config.modelName(), etlTime, zetaTime, etlClassifiers, zetaClassifiers, "Classifiers", "SKIPPED", 0);
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
            recordResult(config.modelName(), etlTime, zetaTime, etlClassifiers, zetaClassifiers, "Classifiers", "EQUIVALENT", 0);
        } else {
            recordResult(config.modelName(), etlTime, zetaTime, etlClassifiers, zetaClassifiers, "Classifiers", "FAILED", result.getDifferenceList().size());
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

    private AsmModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("DiscoveryComparisonTest");
        context.put(psmModel);
        context.put(Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .parallel(false)
                .useCache(true)
                .build());

        Psm2AsmWork work = new Psm2AsmWork(context);
        work.execute();

        return context.getByClass(AsmModel.class)
                .orElseThrow(() -> new IllegalStateException("ASM Model not found after transformation"));
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
}
