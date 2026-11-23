package hu.blackbelt.judo.tatami.rdbms2liquibase;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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

import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseIncrementalZetaTransformationV2;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Entry point of the incremental RDBMS to Liquibase transformation. {@link hu.blackbelt.judo.tatami.core.TransformationMode#ZETA}
 * (default) runs in this Epsilon-free artifact; {@code ETL} delegates to the optional
 * {@link Rdbms2LiquibaseIncrementalEtlEngine} add-on ({@code judo-tatami-rdbms2liquibase-etl}) and fails with the
 * add-on coordinates when it is absent. See change: rdbms-incremental-without-epsilon.
 */
@Slf4j
public final class Rdbms2LiquibaseIncrementalTransformation {

    private Rdbms2LiquibaseIncrementalTransformation() {
    }

    public static Rdbms2LiquibaseIncrementalResult executeRdbms2LiquibaseIncrementalTransformation(
            Rdbms2LiquibaseIncrementalParameter.Rdbms2LiquibaseIncrementalParameterBuilder builder) throws Exception {
        return executeRdbms2LiquibaseIncrementalTransformation(builder.build());
    }

    public static Rdbms2LiquibaseIncrementalResult executeRdbms2LiquibaseIncrementalTransformation(
            Rdbms2LiquibaseIncrementalParameter parameter) throws Exception {
        final Rdbms2LiquibaseIncrementalResult result;
        if (parameter.getTransformationMode().isZeta()) {
            log.info("Executing incremental RDBMS to Liquibase transformation using Zeta engine for dialect: {}",
                    parameter.getDialect());
            result = executeZeta(parameter);
        } else {
            log.info("Executing incremental RDBMS to Liquibase transformation using ETL engine for dialect: {}",
                    parameter.getDialect());
            result = EtlEngineRegistry.require(Rdbms2LiquibaseIncrementalEtlEngine.class,
                    Rdbms2LiquibaseIncrementalEtlEngine.ARTIFACT_COORDINATES).execute(parameter);
        }
        writeBackupDataSqlFiles(parameter.getSqlOutput(), result);
        return result;
    }

    private static Rdbms2LiquibaseIncrementalResult executeZeta(Rdbms2LiquibaseIncrementalParameter parameter) {
        Rdbms2LiquibaseIncrementalZetaTransformationV2 transformation =
                Rdbms2LiquibaseIncrementalZetaTransformationV2.builder()
                        .rdbmsModel(parameter.getIncrementalRdbmsModel())
                        .dbCheckupLiquibaseModel(parameter.getDbCheckupLiquibaseModel())
                        .dbBackupLiquibaseModel(parameter.getDbBackupLiquibaseModel())
                        .beforeIncrementalLiquibaseModel(parameter.getBeforeIncrementalLiquibaseModel())
                        .dataUpdateBeforeIncrementalLiquibaseModel(parameter.getUpdateDataBeforeIncrementalLiquibaseModel())
                        .incrementalLiquibaseModel(parameter.getIncrementalLiquibaseModel())
                        .dataUpdateAfterIncrementalLiquibaseModel(parameter.getUpdateDataAfterIncrementalLiquibaseModel())
                        .afterIncrementalLiquibaseModel(parameter.getAfterIncrementalLiquibaseModel())
                        .dbDropBackupLiquibaseModel(parameter.getDbDropBackupLiquibaseModel())
                        .dialect(parameter.getDialect())
                        .backupTableNamePrefix(parameter.getBackupPrefix())
                        .tableNameMaxSize(parameter.getTableNameMaxSize())
                        .reviewResolver(parameter.getReviewResolver())
                        .sqlScriptPath(parameter.getSqlScriptPath())
                        .build();
        transformation.execute();

        return Rdbms2LiquibaseIncrementalResult.rdbms2LiquibaseIncrementalResult()
                .backupDataSqlFiles(transformation.getBackupDataSqlFiles())
                .missingReviewScripts(transformation.getMissingReviewScripts())
                .build();
    }

    private static void writeBackupDataSqlFiles(String sqlOutput, Rdbms2LiquibaseIncrementalResult result) {
        if (sqlOutput == null || sqlOutput.trim().isEmpty()) {
            return;
        }
        File out = new File(sqlOutput);
        out.mkdirs();
        result.getBackupDataSqlFiles().forEach((name, content) -> {
            try {
                Files.write(new File(out, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException("Could not create file: " + name, e);
            }
        });
    }
}
