package hu.blackbelt.judo.tatami.rdbms2liquibase.etl;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase :: ETL
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

import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseIncrementalEtlEngine;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseIncrementalParameter;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseIncrementalResult;

/**
 * {@link Rdbms2LiquibaseIncrementalEtlEngine} backed by the Epsilon ETL scripts of this artifact: delegates to
 * {@link Rdbms2LiquibaseIncremental}. Discovered via META-INF/services (plain Java) or registered by
 * {@code osgi.Rdbms2LiquibaseIncrementalEtlEngineComponent} (Karaf).
 * See change: rdbms-incremental-without-epsilon.
 */
public class Rdbms2LiquibaseIncrementalEtlEngineImpl implements Rdbms2LiquibaseIncrementalEtlEngine {

    @Override
    public Rdbms2LiquibaseIncrementalResult execute(Rdbms2LiquibaseIncrementalParameter parameter) throws Exception {
        // sqlOutput is written by the core entry point, not here.
        Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalResult etlResult =
                Rdbms2LiquibaseIncremental.executeRdbms2LiquibaseIncrementalTransformation(
                        Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalParameter.rdbms2LiquibaseIncrementalParameter()
                                .incrementalRdbmsModel(parameter.getIncrementalRdbmsModel())
                                .dbCheckupLiquibaseModel(parameter.getDbCheckupLiquibaseModel())
                                .dbBackupLiquibaseModel(parameter.getDbBackupLiquibaseModel())
                                .beforeIncrementalLiquibaseModel(parameter.getBeforeIncrementalLiquibaseModel())
                                .updateDataBeforeIncrementalLiquibaseModel(parameter.getUpdateDataBeforeIncrementalLiquibaseModel())
                                .incrementalLiquibaseModel(parameter.getIncrementalLiquibaseModel())
                                .updateDataAfterIncrementalLiquibaseModel(parameter.getUpdateDataAfterIncrementalLiquibaseModel())
                                .afterIncrementalLiquibaseModel(parameter.getAfterIncrementalLiquibaseModel())
                                .dbDropBackupLiquibaseModel(parameter.getDbDropBackupLiquibaseModel())
                                .dialect(parameter.getDialect())
                                .backupPrefix(parameter.getBackupPrefix())
                                .tableNameMaxSize(parameter.getTableNameMaxSize() != null ? parameter.getTableNameMaxSize() : -1)
                                .reviewResolver(parameter.getReviewResolver())
                                .sqlScriptPath(parameter.getSqlScriptPath())
                                .log(parameter.getLog())
                                .parallel(parameter.getParallel()));
        return Rdbms2LiquibaseIncrementalResult.rdbms2LiquibaseIncrementalResult()
                .backupDataSqlFiles(etlResult.getBackupDataSqlFiles())
                .missingReviewScripts(etlResult.getMissingReviewScripts())
                .build();
    }
}
