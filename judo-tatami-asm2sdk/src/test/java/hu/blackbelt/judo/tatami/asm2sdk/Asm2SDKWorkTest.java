package hu.blackbelt.judo.tatami.asm2sdk;

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

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.SaveArguments.psmSaveArgumentsBuilder;
import static hu.blackbelt.judo.tatami.asm2sdk.Asm2SDK.calculateAsm2SDKTemplateScriptURI;
import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel.AsmValidationException;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel.PsmValidationException;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;

@Slf4j
public class Asm2SDKWorkTest {
    public static final String NORTHWIND = "northwind";
    public static final String NORTHWIND_ASM_MODEL = "asm2sdk_work_northwind-asm.model";
    public static final String NORTHWIND_PSM_MODEL = "asm2sdk_work_northwind-psm.model";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    Asm2SDKWork asm2SDKWork;
    TransformationContext transformationContext;

    @BeforeEach
    void setUp() throws Exception {

        final PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        AsmModel asmModel = AsmModel.buildAsmModel()
                .build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        transformationContext = new TransformationContext(NORTHWIND);
        transformationContext.put(asmModel);

        asm2SDKWork = new Asm2SDKWork(transformationContext, calculateAsm2SDKTemplateScriptURI());


        transformationContext.getByClass(AsmModel.class)
            .orElseThrow(() -> new IllegalArgumentException("Could not get ASM Model"))
            .saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_ASM_MODEL))
                .build());



        psmModel.savePsmModel(psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_MODEL))
                .build());

    }

    @Test
    void testSimpleWorkflow() throws IllegalArgumentException, IOException, AsmValidationException, PsmValidationException {
        WorkFlow workflow = aNewSequentialFlow().execute(asm2SDKWork).build();

        WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
        WorkReport workReport = workFlowEngine.run(workflow);

        log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
        assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));

    }
}
