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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.epsilon.common.util.UriUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EglExecutionContext.eglExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter.programParameterBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.calculateRdbms2LiquibaseTransformationScriptURI;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

@Slf4j
public class Rdbms2LiquibaseIncremental {

    private static final String BACKUP_PREFIX = "BACKUP";


    @Builder(builderMethodName = "rdbms2LiquibaseIncrementalParameter")
    public static final class Rdbms2LiquibaseIncrementalParameter {

        @NonNull
        RdbmsModel incrementalRdbmsModel;

        @NonNull
        LiquibaseModel dbCheckupLiquibaseModel;

        @NonNull
        LiquibaseModel dbBackupLiquibaseModel;

        @NonNull
        LiquibaseModel beforeIncrementalLiquibaseModel;

        @NonNull
        LiquibaseModel updateDataBeforeIncrementalLiquibaseModel;

        @NonNull
        LiquibaseModel incrementalLiquibaseModel;

        @NonNull
        LiquibaseModel updateDataAfterIncrementalLiquibaseModel;

        @NonNull
        LiquibaseModel afterIncrementalLiquibaseModel;

        @NonNull
        LiquibaseModel dbDropBackupLiquibaseModel;

        Log log;

        @Builder.Default
        URI scriptUri = calculateRdbms2LiquibaseTransformationScriptURI();

        @NonNull
        String dialect;

        @Builder.Default
        String backupPrefix = BACKUP_PREFIX;

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;

        ReviewResolver reviewResolver;

        String sqlOutput;

        String sqlScriptPath;

    }

    @Builder(builderMethodName = "rdbms2LiquibaseIncrementalResult")
    @Getter
    public static final class Rdbms2LiquibaseIncrementalResult {
        @Builder.Default
        Map<String, String> backupDataSqlFiles = new HashMap();

        @Builder.Default
        Map<String, String> missingReviewScripts = new HashMap();
    }

    public static Rdbms2LiquibaseIncrementalResult executeRdbms2LiquibaseIncrementalTransformation(Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalParameter.Rdbms2LiquibaseIncrementalParameterBuilder builder) throws Exception {
        return executeRdbms2LiquibaseIncrementalTransformation(builder.build());
    }

    public static Rdbms2LiquibaseIncrementalResult executeRdbms2LiquibaseIncrementalTransformation(Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(Rdbms2LiquibaseIncremental.log);
                                                });

        Rdbms2LiquibaseIncrementalResult result;
        try {
            result = Rdbms2LiquibaseIncrementalResult.rdbms2LiquibaseIncrementalResult().build();
            // Execution context
            ExecutionContext executionContext = executionContextBuilder()
                    .log(log)
                    .resourceSet(parameter.incrementalRdbmsModel.getResourceSet())
                    .injectContexts(ImmutableMap.of(
                            "rdbmsUtils", new RdbmsUtils(),
                            "reviewResolver", ofNullable(parameter.reviewResolver)
                                    .orElseGet(() -> new FileSystemReviewResolver(new File(
                                            ofNullable(parameter.sqlScriptPath)
                                                    .orElseThrow(() -> new IllegalArgumentException(
                                                            "One of ReviewResolver or scriptPath have to be set"))))),
                            "missingReviewScripts", result.missingReviewScripts,
                            "backupDataSqlFiles", result.backupDataSqlFiles))
                    .modelContexts(ImmutableList.of(
                            wrappedEmfModelContextBuilder()
                                    .name("RDBMS")
                                    .resource(parameter.incrementalRdbmsModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("DBCHECKUP")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.dbCheckupLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("DBBACKUP")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.dbBackupLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("BEFORE_INCREMENTAL")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.beforeIncrementalLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("DATA_UPDATE_BEFORE_INCREMENTAL")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.updateDataBeforeIncrementalLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("LIQUIBASE")
                                    .resource(parameter.incrementalLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("AFTER_INCREMENTAL")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.afterIncrementalLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("DATA_UPDATE_AFTER_INCREMENTAL")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.updateDataAfterIncrementalLiquibaseModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("DBDROPBACKUP")
                                    .aliases(singletonList("LIQUIBASE"))
                                    .resource(parameter.dbDropBackupLiquibaseModel.getResource())
                                    .build()))
                    .build();

            // run the model / metadata loading
            executionContext.load();

            final ImmutableList<ProgramParameter> parameters = ImmutableList.of(
                    programParameterBuilder().name("dialect").value(parameter.dialect).build(),
                    programParameterBuilder().name("backupTableNamePrefix").value(parameter.backupPrefix).build(),
                    programParameterBuilder().name("backupChangeSetNamePrefix").value(parameter.backupPrefix.toLowerCase()).build()
            );

            // Transformation script
            executionContext.executeProgram(
                    etlExecutionContextBuilder()
                            .source(UriUtil.resolve("rdbmsIncrementalToLiquibase.etl", parameter.scriptUri))
                            .parameters(parameters)
                            .parallel(parameter.parallel)
                            .build());

            // Generation script
            executionContext.executeProgram(
                    eglExecutionContextBuilder()
                            .source(UriUtil.resolve("../generations/sql/main.egl", parameter.scriptUri))
                            .parameters(parameters)
                            .parallel(parameter.parallel)
                            .build());

            executionContext.commit();
            executionContext.close();
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }

        if (parameter.sqlOutput != null && !"".equals(parameter.sqlOutput.trim())) {
            File out = new File(parameter.sqlOutput);
            out.mkdirs();

            result.backupDataSqlFiles.forEach((name, content) -> {
                try {
                    Files.write(new File(out, name).toPath(), content.getBytes(Charsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException("Could not create file: " + name, e);
                }
            });
        }
        return result;
    }

}
