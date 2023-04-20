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

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static hu.blackbelt.judo.tatami.psm2sdk.Psm2SDK.executePsm2SDKGeneration;

@Slf4j
public class Psm2SDKWork extends AbstractTransformationWork {

    private static final String SDK_OUTPUT = "psm2SDK:output";
    private static final String SDK_OUTPUT_INTERNAL = "psm2SDK:output-internal";

    private static final String SDK_OUTPUT_GUICE = "psm2SDK:output-guice";

    private static final String SDK_OUTPUT_SPRING = "psm2SDK:output-spring";

    @Builder(builderMethodName = "psm2SDKWorkParameter")
    @Getter
    public static final class Psm2SDKWorkParameter {
        @Builder.Default
        File outputDirectory = null;

        @Builder.Default
        Boolean compile = true;

        @Builder.Default
        Boolean createJar = true;

        @Builder.Default
        String packagePrefix = "";

        @Builder.Default
        Boolean addSourceToJar = true;

        @Builder.Default
        Boolean generateSdk = true;

        @Builder.Default
        Boolean generateInternal = true;

        @Builder.Default
        Boolean generateGuice = false;

        @Builder.Default
        Boolean generateSpring = false;

        @Builder.Default
        Boolean generateOptionalTypes = true;

        @Builder.Default
        Boolean generatePayloadValidator = true;


    }

    public Psm2SDKWork(TransformationContext transformationContext) {
        super(transformationContext);
    }


    public static void putSdkStream(TransformationContext transformationContext, InputStream stream) {
        transformationContext.put(SDK_OUTPUT, stream);
    }

    public static Optional<InputStream> getSdkStream(TransformationContext transformationContext) {
        return transformationContext.get(InputStream.class, SDK_OUTPUT);
    }

    public static Boolean verifySdkStream(TransformationContext transformationContext) {
        return transformationContext.transformationContextVerifier.verifyKeyPresent(InputStream.class, SDK_OUTPUT);
    }

    public static void putSdkInternalStream(TransformationContext transformationContext, InputStream stream) {
        transformationContext.put(SDK_OUTPUT_INTERNAL, stream);
    }

    public static Optional<InputStream> getSdkInternalStream(TransformationContext transformationContext) {
        return transformationContext.get(InputStream.class, SDK_OUTPUT_INTERNAL);
    }

    public static Boolean verifySdkInternalStream(TransformationContext transformationContext) {
        return transformationContext.transformationContextVerifier.verifyKeyPresent(InputStream.class, SDK_OUTPUT_INTERNAL);
    }

    public static void putSdkGuiceStream(TransformationContext transformationContext, InputStream stream) {
        transformationContext.put(SDK_OUTPUT_GUICE, stream);
    }

    public static Optional<InputStream> getSdkGuiceStream(TransformationContext transformationContext) {
        return transformationContext.get(InputStream.class, SDK_OUTPUT_GUICE);
    }

    public static Boolean verifySdkGuiceStream(TransformationContext transformationContext) {
        return transformationContext.transformationContextVerifier.verifyKeyPresent(InputStream.class, SDK_OUTPUT_GUICE);
    }

    public static void putSdkSpringStream(TransformationContext transformationContext, InputStream stream) {
        transformationContext.put(SDK_OUTPUT_SPRING, stream);
    }

    public static Optional<InputStream> getSdkSpringStream(TransformationContext transformationContext) {
        return transformationContext.get(InputStream.class, SDK_OUTPUT_SPRING);
    }

    public static Boolean verifySdkSpringStream(TransformationContext transformationContext) {
        return transformationContext.transformationContextVerifier.verifyKeyPresent(InputStream.class, SDK_OUTPUT_SPRING);
    }

    @Override
    public void execute() throws Exception {

        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        Psm2SDKWorkParameter workParameter = getTransformationContext().getByClass(Psm2SDKWorkParameter.class)
                .orElseGet(() -> Psm2SDKWorkParameter.psm2SDKWorkParameter().build());

        File outputDirectory = workParameter.outputDirectory;

        if (outputDirectory == null) {
            outputDirectory = File.createTempFile(Psm2SDK.class.getName(), psmModel.get().getName());
            if (outputDirectory.exists()) {
                outputDirectory.delete();
            }
            outputDirectory.deleteOnExit();
            outputDirectory.mkdir();
        }
        Psm2SDKBundleStreams bundleStreams = executePsm2SDKGeneration(Psm2SDK.Psm2SDKParameter.psm2SDKParameter()
                .psmModel(psmModel.get())
                .log(getTransformationContext().getByClass(Log.class).orElse(null))
                .compile(workParameter.compile)
                .createJar(workParameter.createJar)
                .sourceCodeOutputDir(outputDirectory)
                .packagePrefix(workParameter.packagePrefix)
                .addSourceToJar(workParameter.addSourceToJar)
                .generateSdk(workParameter.generateSdk)
                .generateInternal(workParameter.generateInternal)
                .generateGuice(workParameter.generateGuice)
                .generateSpring(workParameter.generateSpring)
                .metricsCollector(metricsCollector)
                .generateOptionalTypes(workParameter.generateOptionalTypes)
                .generatePayloadValidator(workParameter.generatePayloadValidator));


        if (workParameter.createJar) {
            checkState(bundleStreams != null, "No InputStream created");
            putSdkStream(getTransformationContext(), bundleStreams.getSdkBundleStream());
            putSdkInternalStream(getTransformationContext(), bundleStreams.getInternalBundleStream());
        }
    }
}
