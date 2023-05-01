package hu.blackbelt.judo.tatami.asm2rdbms;

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
import com.google.common.hash.Hashing;
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;
import org.eclipse.epsilon.emc.emf.EmfModel;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter.programParameterBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsTransformationTrace.resolveAsm2RdbmsTrace;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.getTransformationTraceFromEtlExecutionContext;

@Slf4j
public class Asm2Rdbms {

    public static final String SCRIPT_ROOT_TATAMI_ASM_2_RDBMS = "tatami/asm2rdbms/transformations/";
    public static final String MODEL_ROOT_TATAMI_ASM_2_RDBMS = "tatami/asm2rdbms/model/";

    public static final String ASM_2_RDBMS_URI_POSTFIX = "asm2rdbms";

    @Builder(builderMethodName = "asm2RdbmsParameter")
    public static final class Asm2RdbmsParameter {

        @NonNull
        AsmModel asmModel;

        @NonNull
        RdbmsModel rdbmsModel;

        @NonNull
        String dialect;

        Log log;

        @Builder.Default
        java.net.URI scriptUri = calculateAsm2RdbmsTransformationScriptURI();

        @Builder.Default
        java.net.URI excelModelUri = calculateAsm2RdbmsModelURI();

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;

    }

    private static MD5Utils MD5_UTILS = new MD5Utils();

    public static Asm2RdbmsTransformationTrace executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter.Asm2RdbmsParameterBuilder builder) throws Exception {
        return executeAsm2RdbmsTransformation(builder.build());
    }

    public static Asm2RdbmsTransformationTrace executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(Asm2Rdbms.log);
                                                });

        try {
            RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                    rdbmsLoadArgumentsBuilder()
                            .validateModel(false)
                            .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + parameter.dialect + "-rdbms"))
                            .inputStream(UriUtil.resolve("mapping-" + parameter.dialect + "-rdbms.model", parameter.excelModelUri)
                                    .toURL()
                                    .openStream()));
            parameter.rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());

            int shortNameSize = 16;
            int nameSize = 60;

            if (parameter.dialect.equals("oracle")) {
                shortNameSize = 6;
                nameSize = 28;
            }

            WrappedEmfModelContext asmModelContext = wrappedEmfModelContextBuilder()
                    .log(log)
                    .name("ASM")
                    .resource(parameter.asmModel.getResource())
                    .build();

            // Execution context
            ExecutionContext executionContext = executionContextBuilder()
                    .log(log)
                    .modelContexts(ImmutableList.of(
                            asmModelContext,
                            wrappedEmfModelContextBuilder()
                                    .log(log)
                                    .name("RDBMS")
                                    .resource(parameter.rdbmsModel.getResource())
                                    .build()))
                    .injectContexts(
                            new ImmutableMap.Builder<String, Object>()
                                    .put("AbbreviateUtils", new AbbreviateUtils())
                                    .put("MD5Utils", MD5_UTILS)
                                    .put("asmUtils", new AsmUtils(parameter.asmModel.getResourceSet()))
                                    .put("rdbmsUtils", new RdbmsUtils(parameter.rdbmsModel.getResourceSet()))
                                    .put("shortNameSize", shortNameSize)
                                    .put("nameSize", nameSize)
                                    .build())
                    .build();

            // run the model / metadata loading
            executionContext.load();

            // Use cache
            ((EmfModel) executionContext.getProjectModelRepository()
                    .getModelByName(asmModelContext.getName())).setCachingEnabled(true);

            EtlExecutionContext asm2rdbmsExecutionContext = etlExecutionContextBuilder()
                    .source(UriUtil.resolve("asmToRdbms.etl", parameter.scriptUri))
                    .parameters(ImmutableList.of(
                            programParameterBuilder().name("modelVersion").value(parameter.asmModel.getVersion()).build(),
                            programParameterBuilder().name("dialect").value(parameter.dialect).build(),
                            programParameterBuilder().name("extendedMetadataURI")
                                    .value("http://blackbelt.hu/judo/meta/ExtendedMetadata").build()
                    ))
                    .parallel(parameter.parallel)
                    .build();

            // Transformation script
            executionContext.executeProgram(asm2rdbmsExecutionContext);

            executionContext.commit();
            executionContext.close();

            Map<EObject, List<EObject>> traceMap = new ConcurrentHashMap<>();
            if (parameter.createTrace) {
                List<EObject> traceModel = getTransformationTraceFromEtlExecutionContext(ASM_2_RDBMS_URI_POSTFIX, asm2rdbmsExecutionContext);
                traceMap = resolveAsm2RdbmsTrace(traceModel, parameter.asmModel, parameter.rdbmsModel);
            }

            return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                    .asmModel(parameter.asmModel)
                    .rdbmsModel(parameter.rdbmsModel)
                    .trace(traceMap).build();
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    public static URI calculateAsm2RdbmsTransformationScriptURI() {
        return calculateURI(SCRIPT_ROOT_TATAMI_ASM_2_RDBMS);
    }

    public static URI calculateAsm2RdbmsModelURI(){
        return calculateURI(MODEL_ROOT_TATAMI_ASM_2_RDBMS);
    }

    @SneakyThrows(URISyntaxException.class)
    public static URI calculateURI(String path) {
        URI root = Asm2Rdbms.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (root.toString().endsWith(".jar")) {
            root = new URI("jar:" + root.toString() + "!/" + path);
        } else if (root.toString().startsWith("jar:bundle:")) {
            root = new URI(root.toString().substring(4, root.toString().indexOf("!")) + path);
        } else {
            root = new URI(root.toString() + "/" + path);
        }
        return root;
    }

    public static class MD5Utils {

        public static String md5(final String string) {
            return Hashing.md5().hashString(string, Charset.forName("UTF-8")).toString();
        }
    }
}
