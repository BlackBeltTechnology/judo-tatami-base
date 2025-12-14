package hu.blackbelt.judo.tatami.psm2asm;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;

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
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.asm.runtime.AsmUtils.*;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2AsmTransformationTrace.fromModelsAndTrace;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class Psm2AsmTest {

    public static final String DEMO = "demo";
    public static final String NORTHWIND_PSM_MODEL = "northwind-psm.model";
    public static final String NORTHWIND_ASM_MODEL = "northwind-asm.model";
    public static final String NORTHWIND_PSM_2_ASM_MODEL = "northwind-psm2asm.model";

    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;
    AsmModel asmModel;

    @BeforeEach
    public void setUp() throws Exception {
        psmModel = new Demo().fullDemo();

        // When model is invalid the loader have to throw exception. This checks that invalid model cannot valid -if
        // the loading check does not run caused by some reason
        assertTrue(psmModel.isValid());

        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        // Create empty ASM model
        asmModel = buildAsmModel()
                .build();
    }

    @ParameterizedTest(name = "testPsm2AsmTransformation with {0}")
    @EnumSource(TransformationType.class)
    public void testPsm2AsmTransformation(TransformationType transformationType) throws Exception {

        // Make transformation which returns the trace with the serialized URI's
        Psm2AsmTransformationTrace psm2AsmTransformationTrace;
        if (transformationType == TransformationType.ZETA) {
            log.info("Running Zeta transformation");
            Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName(DEMO)
                    .build();
            transformation.execute();
            // For Zeta transformation, trace is not available - create empty trace for compatibility
            psm2AsmTransformationTrace = null;
        } else {
            log.info("Running ETL transformation");
            psm2AsmTransformationTrace = executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel));
        }

        // Trace operations only for ETL transformation
        if (psm2AsmTransformationTrace != null) {
            psm2AsmTransformationTrace.save(new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_2_ASM_MODEL));

            Psm2AsmTransformationTrace psm2AsmTransformationTraceLoaded = fromModelsAndTrace(
                    DEMO,
                    psmModel,
                    asmModel,
                    new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_2_ASM_MODEL));

            // Resolve serialized URI's as EObject map
            Map<EObject, List<EObject>> resolvedTrace = psm2AsmTransformationTraceLoaded.getTransformationTrace();

            // Printing trace
            for (EObject e : resolvedTrace.keySet()) {
                for (EObject t : resolvedTrace.get(e)) {
                    log.trace(e.toString() + " -> " + t.toString());
                }
            }
        }

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                        .outputStream(new FileOutputStream(new File(TARGET_TEST_CLASSES, NORTHWIND_ASM_MODEL))));

        AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
        Optional<EClass> orderInfo = asmUtils.getClassByFQName("demo.services.OrderInfo");
        assertTrue(orderInfo.isPresent());

        final Optional<EReference> itemsOfOrderInfo = orderInfo.get().getEAllReferences().stream().filter(r -> "items".equals(r.getName())).findAny();
        assertTrue(itemsOfOrderInfo.isPresent());

        assertTrue(isAllowedToCreateEmbeddedObject(itemsOfOrderInfo.get()));
        assertTrue(isAllowedToUpdateEmbeddedObject(itemsOfOrderInfo.get()));
        assertTrue(isAllowedToDeleteEmbeddedObject(itemsOfOrderInfo.get()));
    }

    /**
     * Test that ETL and Zeta transformations produce equivalent ASM models.
     * This test runs both transformations and compares the output models.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL transformation
        log.info("Running ETL transformation for equivalence test...");
        PsmModel psmModelEtl = new Demo().fullDemo();
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(etlResult));

        // Run Zeta transformation
        log.info("Running Zeta transformation for equivalence test...");
        PsmModel psmModelZeta = new Demo().fullDemo();
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModelZeta)
                .asmModel(zetaResult)
                .modelName(DEMO)
                .build();
        zetaTransformation.execute();

        // Compare models
        log.info("Comparing ETL and Zeta output models...");
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
