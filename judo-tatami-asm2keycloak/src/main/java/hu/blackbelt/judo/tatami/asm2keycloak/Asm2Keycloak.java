package hu.blackbelt.judo.tatami.asm2keycloak;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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
import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;
import org.eclipse.epsilon.emc.emf.EmfModel;

import java.io.Closeable;
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
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakTransformationTrace.resolveAsm2KeycloakTrace;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.getTransformationTraceFromEtlExecutionContext;

@Slf4j
public class Asm2Keycloak {

    public static final String SCRIPT_ROOT_TATAMI_ASM_2_KEYCLOAK = "tatami/asm2keycloak/transformations/";

    private static final String ASM_2_KEYCLOAK_URI_POSTFIX = "asm2keycloak";

    @Builder(builderMethodName = "asm2KeycloakParameter")
    public static final class Asm2KeycloakParameter {

        @NonNull
        AsmModel asmModel;

        @NonNull
        KeycloakModel keycloakModel;

        Logger log;

        @Builder.Default
        java.net.URI scriptUri = calculateAsm2KeycloakTransformationScriptURI();

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;

        @Builder.Default
        boolean useCache = false;
    }

    public static Asm2KeycloakTransformationTrace executeAsm2KeycloakTransformation(Asm2KeycloakParameter.Asm2KeycloakParameterBuilder builder) throws Exception {
        return executeAsm2KeycloakTransformation(builder.build());
    }


    public static Asm2KeycloakTransformationTrace executeAsm2KeycloakTransformation(final Asm2KeycloakParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Logger log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(Asm2Keycloak.log);
                                                });

        try {
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
                                            .name("KEYCLOAK")
                                            .resource(parameter.keycloakModel.getResource())
                                            .build()
                            )
                    )
                    .injectContexts(ImmutableMap.of(
                            "asmUtils", new AsmUtils(parameter.asmModel.getResourceSet())
                    ))
                    .build();

            // run the model / metadata loading
            executionContext.load();

            // Use cache
            if (parameter.useCache) {
                ((EmfModel) executionContext.getProjectModelRepository()
                        .getModelByName(asmModelContext.getName())).setCachingEnabled(true);
            }


            EtlExecutionContext asm2keycloakExecutionContext = etlExecutionContextBuilder()
                    .source(UriUtil.resolve("asmToKeycloak.etl", parameter.scriptUri))
                    .parameters(ImmutableList.of(
                            programParameterBuilder().name("modelVersion").value(parameter.asmModel.getVersion()).build()
                    ))
                    .parallel(parameter.parallel)
                    .build();

            executionContext.executeProgram(asm2keycloakExecutionContext);

            executionContext.commit();
            executionContext.close();

            Map<EObject, List<EObject>> traceMap = new ConcurrentHashMap<>();
            if (parameter.createTrace) {
                List<EObject> traceModel = getTransformationTraceFromEtlExecutionContext(ASM_2_KEYCLOAK_URI_POSTFIX, asm2keycloakExecutionContext);
                traceMap = resolveAsm2KeycloakTrace(traceModel, parameter.asmModel, parameter.keycloakModel);
            }


            return Asm2KeycloakTransformationTrace.asm2KeycloakTransformationTraceBuilder()
                    .asmModel(parameter.asmModel)
                    .keycloakModel(parameter.keycloakModel)
                    .trace(traceMap).build();

        } finally {
            if (loggerToBeClosed.get()) {
                try {
                    if (log instanceof Closeable) {
                        ((Closeable) log).close();
                    }
                } catch (Exception e) {
                    //noinspection ThrowFromFinallyBlock
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static URI calculateAsm2KeycloakTransformationScriptURI() {
        return calculateURI(SCRIPT_ROOT_TATAMI_ASM_2_KEYCLOAK);
    }

    @SneakyThrows(URISyntaxException.class)
    public static URI calculateURI(String path) {
        URI psmRoot = Asm2Keycloak.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (psmRoot.toString().endsWith(".jar")) {
            psmRoot = new URI("jar:" + psmRoot.toString() + "!/" + path);
        } else if (psmRoot.toString().startsWith("jar:bundle:")) {
            psmRoot = new URI(psmRoot.toString().substring(4, psmRoot.toString().indexOf("!")) + path);
        } else {
            psmRoot = new URI(psmRoot.toString() + "/" + path);
        }
        return psmRoot;
    }

    public static class MD5Utils {

        public static String md5(final String string) {
            return Hashing.md5().hashString(string, Charset.forName("UTF-8")).toString();
        }
    }
}
