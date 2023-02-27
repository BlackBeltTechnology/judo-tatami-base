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

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private void transform(final String testName) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());

        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }

    @Test
    void testInitializerAnnotation() throws Exception {
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

        transform("testInitializerAnnotation");

        final AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());

        final List<EOperation> initializers = asmUtils.all(EOperation.class).filter(op -> AsmUtils.annotatedAsTrue(op, "initializer")).collect(Collectors.toList());

        assertEquals(1, initializers.size());
    }
}
