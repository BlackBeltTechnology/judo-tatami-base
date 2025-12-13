package hu.blackbelt.judo.tatami.psm2asm;

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

import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformationV2;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newOperationBodyBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.newUnboundOperationBuilder;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.newUnmappedTransferObjectTypeBuilder;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class OperationTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;
    AsmModel asmModel;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .build();

        // Create empty ASM model
        asmModel = buildAsmModel()
                .build();
    }

    private void transform(final String testName, final TransformationType transformationType) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());

        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        if (transformationType == TransformationType.ZETA) {
            log.info("Running Zeta transformation for test: {}", testName);
            Psm2AsmZetaTransformationV2 transformation = Psm2AsmZetaTransformationV2.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName(MODEL_NAME)
                    .build();
            transformation.execute();
        } else {
            log.info("Running ETL transformation for test: {}", testName);
            executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel));
        }

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }

    /**
     * Runs both ETL and Zeta transformations and compares their outputs.
     * This should be called at the end of parameterized tests to verify equivalence.
     */
    private void compareTransformations(final String testName) throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL fresh
        AsmModel etlModel = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlModel));

        // Run Zeta fresh - use psmModel.getName() to match what ETL uses
        AsmModel zetaModel = buildAsmModel().build();
        Psm2AsmZetaTransformationV2 zetaTransformation = Psm2AsmZetaTransformationV2.builder()
                .psmModel(psmModel)
                .asmModel(zetaModel)
                .modelName(psmModel.getName())
                .build();
        zetaTransformation.execute();

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlModel.getResourceSet().getResources().get(0).getContents().get(0),
                zetaModel.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models for {}", testName);
        } else {
            log.warn("Models have differences for {}:\n{}", testName, result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + testName + ":\n" + result.getDetailedReport());
        }
    }

    @ParameterizedTest(name = "testInitializerAnnotation with {0}")
    @EnumSource(TransformationType.class)
    void testInitializerAnnotation(TransformationType transformationType) throws Exception {
        final Model model = newModelBuilder()
                .withName("Model")
                .withElements(newUnmappedTransferObjectTypeBuilder()
                        .withName("Initializer")
                        .withOperations(newUnboundOperationBuilder()
                                .withName("auto")
                                .withInitializer(true)
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .withOperations(newUnboundOperationBuilder()
                                .withName("manual")
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .build())
                .build();

        psmModel.addContent(model);

        transform("testInitializerAnnotation", transformationType);

        final AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());

        final List<EOperation> initializers = asmUtils.all(EOperation.class).filter(op -> AsmUtils.annotatedAsTrue(op, "initializer")).collect(Collectors.toList());

        assertEquals(1, initializers.size());

        compareTransformations("testInitializerAnnotation");
    }

    @Test
    void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Build model for ETL
        final Model modelEtl = newModelBuilder()
                .withName("Model")
                .withElements(newUnmappedTransferObjectTypeBuilder()
                        .withName("Initializer")
                        .withOperations(newUnboundOperationBuilder()
                                .withName("auto")
                                .withInitializer(true)
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .withOperations(newUnboundOperationBuilder()
                                .withName("manual")
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .build())
                .build();

        PsmModel psmModelEtl = buildPsmModel().build();
        psmModelEtl.addContent(modelEtl);
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(etlResult));

        // Build model for Zeta
        final Model modelZeta = newModelBuilder()
                .withName("Model")
                .withElements(newUnmappedTransferObjectTypeBuilder()
                        .withName("Initializer")
                        .withOperations(newUnboundOperationBuilder()
                                .withName("auto")
                                .withInitializer(true)
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .withOperations(newUnboundOperationBuilder()
                                .withName("manual")
                                .withImplementation(newOperationBodyBuilder()
                                        .withCustomImplementation(true)
                                        .withStateful(true)
                                        .build())
                                .build())
                        .build())
                .build();

        PsmModel psmModelZeta = buildPsmModel().build();
        psmModelZeta.addContent(modelZeta);
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformationV2 zetaTransformation = Psm2AsmZetaTransformationV2.builder()
                .psmModel(psmModelZeta)
                .asmModel(zetaResult)
                .modelName(psmModelZeta.getName())
                .build();
        zetaTransformation.execute();

        // Debug: Print annotations on operations
        hu.blackbelt.judo.meta.asm.runtime.AsmUtils etlAsmUtils = new hu.blackbelt.judo.meta.asm.runtime.AsmUtils(etlResult.getResourceSet());
        hu.blackbelt.judo.meta.asm.runtime.AsmUtils zetaAsmUtils = new hu.blackbelt.judo.meta.asm.runtime.AsmUtils(zetaResult.getResourceSet());
        etlAsmUtils.all(org.eclipse.emf.ecore.EOperation.class).forEach(op -> {
            log.info("ETL {} annotations:", op.getName());
            for (org.eclipse.emf.ecore.EAnnotation ann : op.getEAnnotations()) {
                log.info("  - {}", ann.getSource());
            }
        });
        zetaAsmUtils.all(org.eclipse.emf.ecore.EOperation.class).forEach(op -> {
            log.info("Zeta {} annotations:", op.getName());
            for (org.eclipse.emf.ecore.EAnnotation ann : op.getEAnnotations()) {
                log.info("  - {}", ann.getSource());
            }
        });

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models");
        } else {
            log.warn("Models have differences:\n{}", result.getSummary());
            fail("ETL and Zeta models are not equivalent:\n" + result.getDetailedReport());
        }
    }
}
