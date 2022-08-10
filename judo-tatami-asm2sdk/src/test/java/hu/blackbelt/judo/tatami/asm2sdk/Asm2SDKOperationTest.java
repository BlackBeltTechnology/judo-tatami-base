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

import com.google.common.io.ByteStreams;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.PsmTestModelBuilder;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2asm.Psm2Asm;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.PsmTestModelBuilder.Cardinality.cardinality;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.tatami.asm2sdk.Asm2SDK.Asm2SDKParameter.asm2SDKParameter;
import static hu.blackbelt.judo.tatami.asm2sdk.Asm2SDK.executeAsm2SDKGeneration;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;

@Slf4j
public class Asm2SDKOperationTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";
    public static final String GENERATED_JAVA = "/asm2sdk-operation-test";

    @SuppressWarnings("unused")
	private Log slf4jlog;
    private AsmModel asmModel;
	private PsmModel psmModel;

    @BeforeEach
    public void setUp() throws Exception {
        // Default logger
        slf4jlog = new Slf4jLog(log);

        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .name(MODEL_NAME)
                .build();
        
        // Create empty ASM model
        asmModel = buildAsmModel()
                .name(MODEL_NAME)
                .build();
        
        fillPsmModel();

    }

    @Test
    public void testExecuteAsm2SDKGeneration() throws Exception {
        Asm2SDKBundleStreams bundleStreams = executeAsm2SDKGeneration(asm2SDKParameter()
						.asmModel(asmModel)
						.sourceCodeOutputDir(new File(TARGET_TEST_CLASSES, GENERATED_JAVA)));

        OutputStream sdkOutputStream = new FileOutputStream(new File(TARGET_TEST_CLASSES, String.format("%s-sdk.jar", MODEL_NAME)));
        ByteStreams.copy(bundleStreams.getSdkBundleStream(), sdkOutputStream);
        bundleStreams.getSdkBundleStream();
        OutputStream internalOutputStream = new FileOutputStream(new File(TARGET_TEST_CLASSES, String.format("%s-sdk-internal.jar", MODEL_NAME)));
        ByteStreams.copy(bundleStreams.getInternalBundleStream(), internalOutputStream);
    }

    private void transform(final String testName) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());
		try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
			validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
		}

        executePsm2AsmTransformation(Psm2Asm.Psm2AsmParameter.psm2AsmParameter()
				.psmModel(psmModel)
				.asmModel(asmModel));

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }

    private void fillPsmModel() throws Exception {
    	PsmTestModelBuilder modelBuilder = new PsmTestModelBuilder();
    	modelBuilder.addEntity("InputEntity").withAttribute("String", "name");
    	modelBuilder.addEntity("RelatedEntity").withAttribute("String", "name");
    	modelBuilder.addEntity("OutputEntity").withAttribute("String", "name");
    	modelBuilder.addEntity("Entity").withAttribute("String", "text")
    		.withAggregation("RelatedEntity", "related", cardinality(0, 1))
    		.withAggregation("RelatedEntity", "relateds", cardinality(0, -1))
    		.withAttribute("Country", "country");
		modelBuilder.addMappedTransferObject("EntityInfo", "Entity");
    	modelBuilder.addUnmappedTransferObject("Initializer");
    	modelBuilder
    		.addUnmappedTransferObject("UnmappedWithStatic")
    		.withStaticData("String", "simpleArithmeticExpression", "1+2");
    	
    	// unbound operation
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationVoid");
    	// unbound operation with single entity input
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationWithEntityInputVoid")
    		.withInput("InputEntity", "input", cardinality(0, 1));
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationWithEntityInputSingle")
		.withInput("InputEntity", "input", cardinality(0, 1))
		.withOutput("OutputEntity", cardinality(1,1));
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationWithEntityInputMulti")
		.withInput("InputEntity", "input", cardinality(0, 1))
		.withOutput("OutputEntity", cardinality(0, -1));
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationMulti")		
		.withOutput("OutputEntity", cardinality(0, -1));
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationSingle")		
		.withOutput("OutputEntity", cardinality(1, 1));
    	// unbound operation with multiple entity input
    	modelBuilder.addUnboundOperation("Initializer", "unboundOperationWithEntitiesInputVoid")
    		.withInput("InputEntity", "input", cardinality(0, -1));
    	
    	modelBuilder.addBoundOperation("Entity", "boundOperationVoid");
    	modelBuilder.addBoundOperation("Entity", "boundOperationWithEntityInputVoid")
    		.withInput("InputEntity", "input", cardinality(0, 1));
    	modelBuilder.addBoundOperation("Entity", "boundOperationWithEntityInputSingle")
		.withInput("InputEntity", "input", cardinality(0, 1))
		.withOutput("OutputEntity", cardinality(1,1));
    	modelBuilder.addBoundOperation("Entity", "boundOperationWithEntityInputMulti")
		.withInput("InputEntity", "input", cardinality(0, 1))
		.withOutput("OutputEntity", cardinality(0, -1));
    	modelBuilder.addBoundOperation("Entity", "boundOperationMulti")
		.withOutput("OutputEntity", cardinality(0, -1));
    	modelBuilder.addBoundOperation("Entity", "boundOperationSingle")
		.withOutput("OutputEntity", cardinality(0, 1));    	
    	modelBuilder.addBoundOperation("Entity", "boundOperationWithEntitiesInputVoid")
    		.withInput("InputEntity", "input", cardinality(0, -1));
    	
    	modelBuilder.addUnboundOperation("Initializer", "scriptOperation").withBody("var demo::entities::Entity e");
//    	modelBuilder.addActorType("BoundVoidActor", "Entity");
//    	modelBuilder.addActorType("InitializerActor", "Initializer");
        psmModel.addContent(modelBuilder.build());
        transform(MODEL_NAME);

    }

}
