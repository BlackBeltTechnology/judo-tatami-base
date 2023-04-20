package hu.blackbelt.judo.tatami.psm2sdk;

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
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.SaveArguments.psmSaveArgumentsBuilder;
import static hu.blackbelt.judo.tatami.psm2sdk.Psm2SDK.executePsm2SDKGeneration;

@Slf4j
public class Psm2SDKTest {

    public static final String MODEL_NAME = "northwind";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";
    public static final String NORTHWIND_ASM_MODEL = "asm2sdk_northwind-asm.model";
    public static final String NORTHWIND_PSM_MODEL = "asm2sdk_northwind-psm.model";
    public static final String GENERATED_JAVA = "generated/java";

    PsmModel psmModel;

    @BeforeEach
    public void setUp() throws Exception {
        psmModel = new Demo().fullDemo();

        psmModel.savePsmModel(psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_MODEL))
                .build());
    }

    @Test
    public void testExecutePsm2SDKGeneration() throws Exception {
        Psm2SDKBundleStreams bundleStreams = executePsm2SDKGeneration(
                Psm2SDK.Psm2SDKParameter.psm2SDKParameter()
                        .psmModel(psmModel)
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
