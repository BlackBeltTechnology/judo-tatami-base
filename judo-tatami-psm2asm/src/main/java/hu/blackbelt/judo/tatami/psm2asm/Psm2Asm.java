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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter.programParameterBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.getTransformationTraceFromEtlExecutionContext;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2AsmTransformationTrace.resolvePsm2AsmTrace;

@Slf4j
public class Psm2Asm {

    public static final String HTTP_BLACKBELT_HU_JUDO_META_EXTENDED_METADATA = "http://blackbelt.hu/judo/meta/ExtendedMetadata";
    public static final String SCRIPT_ROOT_TATAMI_PSM_2_ASM = "tatami/psm2asm/transformations/asm/";


    @Builder(builderMethodName = "psm2AsmParameter")
    public static final class Psm2AsmParameter {

        @NonNull
        PsmModel psmModel;

        @NonNull
        AsmModel asmModel;

        @Builder.Default
        java.net.URI scriptUri = calculatePsm2AsmTransformationScriptURI();

        Log log;

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;
    }

    public static Psm2AsmTransformationTrace executePsm2AsmTransformation(Psm2AsmParameter.Psm2AsmParameterBuilder builder) throws Exception {
        return executePsm2AsmTransformation(builder.build());
    }

    public static Psm2AsmTransformationTrace executePsm2AsmTransformation(Psm2AsmParameter parameter) throws Exception {

        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                 () -> {
                                                     loggerToBeClosed.set(true);
                                                     return new BufferedSlf4jLogger(Psm2Asm.log);
                                                 });

        try {
            // Executrion context
            ExecutionContext executionContext = executionContextBuilder()
                    .log(log)
                    .modelContexts(ImmutableList.of(
                            wrappedEmfModelContextBuilder()
                                    .log(log)
                                    .name("JUDOPSM")
                                    .resource(parameter.psmModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .log(log)
                                    .name("ASM")
                                    .resource(parameter.asmModel.getResource())
                                    .build()))
                    .injectContexts(ImmutableMap.of(
                            "asmUtils", new AsmUtils(parameter.asmModel.getResourceSet()),
                            "psmUtils", new PsmUtils()
                    )).build();

            // run the model / metadata loading
            executionContext.load();

            EtlExecutionContext etlExecutionContext = etlExecutionContextBuilder()
                    .source(UriUtil.resolve("psmToAsm.etl", parameter.scriptUri))
                    .parameters(ImmutableList.of(
                            programParameterBuilder().name("modelName").value(parameter.psmModel.getName()).build(),
                            programParameterBuilder().name("nsURI").value("http://blackbelt.hu/judo/" + parameter.psmModel.getName()).build(),
                            programParameterBuilder().name("nsPrefix").value("runtime" + parameter.psmModel.getName()).build(),
                            programParameterBuilder().name("extendedMetadataURI").value(HTTP_BLACKBELT_HU_JUDO_META_EXTENDED_METADATA).build()
                    ))
                    .parallel(parameter.parallel)
                    .build();

            // Transformation script
            executionContext.executeProgram(etlExecutionContext);
            executionContext.commit();
            executionContext.close();

            Map<EObject, List<EObject>> traceMap = new ConcurrentHashMap<>();
            if (parameter.createTrace) {
                List<EObject> traceModel = getTransformationTraceFromEtlExecutionContext(
                        Psm2AsmTransformationTrace.PSM_2_ASM_URI_POSTFIX, etlExecutionContext);
                traceMap = resolvePsm2AsmTrace(traceModel, parameter.psmModel, parameter.asmModel);
            }
            return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                    .asmModel(parameter.asmModel)
                    .psmModel(parameter.psmModel)
                    .trace(traceMap).build();

        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    @SneakyThrows(URISyntaxException.class)
    public static URI calculatePsm2AsmTransformationScriptURI() {
        URI psmRoot = Psm2Asm.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (psmRoot.toString().endsWith(".jar")) {
            psmRoot = new URI("jar:" + psmRoot.toString() + "!/" + SCRIPT_ROOT_TATAMI_PSM_2_ASM);
        } else if (psmRoot.toString().startsWith("jar:bundle:")) {
            psmRoot = new URI(psmRoot.toString().substring(4, psmRoot.toString().indexOf("!")) + SCRIPT_ROOT_TATAMI_PSM_2_ASM);
        } else {
            psmRoot = new URI(psmRoot.toString() + "/" + SCRIPT_ROOT_TATAMI_PSM_2_ASM);
        }
        return psmRoot;
    }

}
