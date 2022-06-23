package hu.blackbelt.judo.tatami.asm2sdk;

import com.google.common.io.ByteStreams;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static hu.blackbelt.judo.tatami.asm2sdk.Asm2SDK.executeAsm2SDKGeneration;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;

@Slf4j
public class Asm2SDKTest {

	public static final String MODEL_NAME = "northwind";
	public static final String TARGET_TEST_CLASSES = "target/test-classes";
	public static final String NORTHWIND_ASM_MODEL = "northwind-asm.model";
	public static final String GENERATED_JAVA = "generated/java";

	AsmModel asmModel;

	@BeforeEach
	public void setUp() throws Exception {
		final PsmModel psmModel = new Demo().fullDemo();

		// Create empty ASM model
		asmModel = AsmModel.buildAsmModel().name(MODEL_NAME).build();

		executePsm2AsmTransformation(psm2AsmParameter()
				.psmModel(psmModel)
				.asmModel(asmModel));
	}

	@Test
	public void testExecuteAsm2SDKGeneration() throws Exception {
        Asm2SDKBundleStreams bundleStreams = executeAsm2SDKGeneration(
				Asm2SDK.Asm2SDKParameter.asm2SDKParameter()
						.asmModel(asmModel)
						.sourceCodeOutputDir(new File(TARGET_TEST_CLASSES, GENERATED_JAVA)));

        OutputStream sdkOutputStream = new FileOutputStream(
				new File(TARGET_TEST_CLASSES, String.format("%s-sdk.jar", MODEL_NAME)));
        ByteStreams.copy(bundleStreams.getSdkBundleStream(), sdkOutputStream);
        bundleStreams.getSdkBundleStream();

        OutputStream internalOutputStream = new FileOutputStream(
				new File(TARGET_TEST_CLASSES, String.format("%s-sdk-internal.jar", MODEL_NAME)));
        ByteStreams.copy(bundleStreams.getInternalBundleStream(), internalOutputStream);

		OutputStream guiceOutputStreams = new FileOutputStream(
				new File(TARGET_TEST_CLASSES, String.format("%s-sdk-guice.jar", MODEL_NAME)));
		ByteStreams.copy(bundleStreams.getGuiceBundleStream(), guiceOutputStreams);

		OutputStream springOutputStreams = new FileOutputStream(
				new File(TARGET_TEST_CLASSES, String.format("%s-sdk-spring.jar", MODEL_NAME)));
		ByteStreams.copy(bundleStreams.getSpringBundleStream(), springOutputStreams);

	}
}
