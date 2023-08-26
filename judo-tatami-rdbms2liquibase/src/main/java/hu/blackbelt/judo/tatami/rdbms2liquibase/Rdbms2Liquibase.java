package hu.blackbelt.judo.tatami.rdbms2liquibase;

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
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.epsilon.common.util.UriUtil;
import org.eclipse.epsilon.emc.emf.EmfModel;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;

@Slf4j
public class Rdbms2Liquibase {

    public static final String SCRIPT_ROOT_TATAMI_RDBMS_2_LIQUIBASE = "tatami/rdbms2liquibase/transformations/";


    @Builder(builderMethodName = "rdbms2LiquibaseParameter")
    public static final class Rdbms2LiquibaseParameter {

        @NonNull
        RdbmsModel rdbmsModel;

        @NonNull
        LiquibaseModel liquibaseModel;

        @NonNull
        String dialect;

        Logger log;

        @Builder.Default
        java.net.URI scriptUri = calculateRdbms2LiquibaseTransformationScriptURI();

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;

        @Builder.Default
        boolean useCache = false;
    }

    public static void executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter.Rdbms2LiquibaseParameterBuilder builder) throws Exception {
        executeRdbms2LiquibaseTransformation(builder.build());
    }

    public static void executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Logger log = Objects.requireNonNullElseGet(parameter.log,
                                                 () -> {
                                                     loggerToBeClosed.set(true);
                                                     return new BufferedSlf4jLogger(Rdbms2Liquibase.log);
                                                 });

        try {
            WrappedEmfModelContext rdbmsModelContext = wrappedEmfModelContextBuilder()
                    .log(log)
                    .name("RDBMS")
                    .resource(parameter.rdbmsModel.getResourceSet().getResource(parameter.rdbmsModel.getUri(), false))
                    .build();

            // Execution context
            ExecutionContext executionContext = executionContextBuilder()
                    .log(log)
                    .resourceSet(parameter.liquibaseModel.getResourceSet())
                    .modelContexts(ImmutableList.of(
                            rdbmsModelContext,
                            wrappedEmfModelContextBuilder()
                                    .log(log)
                                    .name("LIQUIBASE")
                                    .resource(parameter.liquibaseModel.getResource())
                                    .build()))
                    .build();

            // run the model / metadata loading
            executionContext.load();

            // Use cache
            if (parameter.useCache) {
                ((EmfModel) executionContext.getProjectModelRepository()
                        .getModelByName(rdbmsModelContext.getName())).setCachingEnabled(true);
            }

            // Transformation script
            executionContext.executeProgram(
                    etlExecutionContextBuilder()
                            .source(UriUtil.resolve("rdbmsToLiquibase.etl", parameter.scriptUri))
                            .parameters(ImmutableList.of(
                                    ProgramParameter.programParameterBuilder().name("dialect").value(parameter.dialect).build()
                            ))
                            .parallel(parameter.parallel)
                            .build());

            executionContext.commit();
            executionContext.close();
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

    @SneakyThrows(URISyntaxException.class)
    public static URI calculateRdbms2LiquibaseTransformationScriptURI() {
        URI rdbmsRoot = Rdbms2Liquibase.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (rdbmsRoot.toString().endsWith(".jar")) {
            rdbmsRoot = new URI("jar:" + rdbmsRoot.toString() + "!/" + SCRIPT_ROOT_TATAMI_RDBMS_2_LIQUIBASE);
        } else if (rdbmsRoot.toString().startsWith("jar:bundle:")) {
            rdbmsRoot = new URI(rdbmsRoot.toString().substring(4, rdbmsRoot.toString().indexOf("!")) + SCRIPT_ROOT_TATAMI_RDBMS_2_LIQUIBASE);
        } else {
            rdbmsRoot = new URI(rdbmsRoot.toString() + "/" + SCRIPT_ROOT_TATAMI_RDBMS_2_LIQUIBASE);
        }
        return rdbmsRoot;
    }

}
