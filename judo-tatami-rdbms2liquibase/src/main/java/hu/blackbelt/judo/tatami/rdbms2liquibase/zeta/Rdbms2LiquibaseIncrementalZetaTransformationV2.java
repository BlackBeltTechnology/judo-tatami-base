package hu.blackbelt.judo.tatami.rdbms2liquibase.zeta;

/*-
 * #%L
 * JUDO Tatami parent
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

import hu.blackbelt.judo.meta.liquibase.databaseChangeLog;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.incremental.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java-based incremental RDBMS to Liquibase transformation using Zeta framework patterns.
 * <p>
 * This is a refactored version that orchestrates sub-transformations for each phase.
 * Each phase is implemented as a separate transformation class for better modularity,
 * testability, and maintainability.
 * </p>
 * <p>
 * The transformation phases are:
 * <ol>
 *   <li>DB Checkup - Verify database is in expected state</li>
 *   <li>DB Backup - Create backup SQL files for tables being modified/deleted</li>
 *   <li>Before Incremental - Drop constraints before schema changes</li>
 *   <li>Data Update Before - SQL files for data migration before schema changes</li>
 *   <li>Incremental - Main schema changes (create/drop/rename tables and columns)</li>
 *   <li>Data Update After - SQL files for data migration after schema changes</li>
 *   <li>After Incremental - Add constraints back after schema changes</li>
 *   <li>DB Drop Backup - Drop backup tables after successful migration</li>
 * </ol>
 * </p>
 */
@Slf4j
public class Rdbms2LiquibaseIncrementalZetaTransformationV2 {

    private final RdbmsModel rdbmsModel;

    // Multiple target Liquibase models for different transformation phases
    private final LiquibaseModel dbCheckupLiquibaseModel;
    private final LiquibaseModel dbBackupLiquibaseModel;
    private final LiquibaseModel beforeIncrementalLiquibaseModel;
    private final LiquibaseModel dataUpdateBeforeIncrementalLiquibaseModel;
    private final LiquibaseModel incrementalLiquibaseModel;
    private final LiquibaseModel dataUpdateAfterIncrementalLiquibaseModel;
    private final LiquibaseModel afterIncrementalLiquibaseModel;
    private final LiquibaseModel dbDropBackupLiquibaseModel;

    // Configuration parameters
    private final String dialect;
    private final String backupTableNamePrefix;
    private final Integer tableNameMaxSize;

    // Sub-transformation instances
    @Getter
    private DbCheckupZetaTransformation dbCheckupTransformation;
    @Getter
    private DbBackupZetaTransformation dbBackupTransformation;
    @Getter
    private BeforeIncrementalZetaTransformation beforeIncrementalTransformation;
    @Getter
    private DataUpdateBeforeZetaTransformation dataUpdateBeforeTransformation;
    @Getter
    private IncrementalZetaTransformation incrementalTransformation;
    @Getter
    private DataUpdateAfterZetaTransformation dataUpdateAfterTransformation;
    @Getter
    private AfterIncrementalZetaTransformation afterIncrementalTransformation;
    @Getter
    private DbDropBackupZetaTransformation dbDropBackupTransformation;

    @Builder
    public Rdbms2LiquibaseIncrementalZetaTransformationV2(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel dbCheckupLiquibaseModel,
            @NonNull LiquibaseModel dbBackupLiquibaseModel,
            @NonNull LiquibaseModel beforeIncrementalLiquibaseModel,
            @NonNull LiquibaseModel dataUpdateBeforeIncrementalLiquibaseModel,
            @NonNull LiquibaseModel incrementalLiquibaseModel,
            @NonNull LiquibaseModel dataUpdateAfterIncrementalLiquibaseModel,
            @NonNull LiquibaseModel afterIncrementalLiquibaseModel,
            @NonNull LiquibaseModel dbDropBackupLiquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        this.rdbmsModel = rdbmsModel;
        this.dbCheckupLiquibaseModel = dbCheckupLiquibaseModel;
        this.dbBackupLiquibaseModel = dbBackupLiquibaseModel;
        this.beforeIncrementalLiquibaseModel = beforeIncrementalLiquibaseModel;
        this.dataUpdateBeforeIncrementalLiquibaseModel = dataUpdateBeforeIncrementalLiquibaseModel;
        this.incrementalLiquibaseModel = incrementalLiquibaseModel;
        this.dataUpdateAfterIncrementalLiquibaseModel = dataUpdateAfterIncrementalLiquibaseModel;
        this.afterIncrementalLiquibaseModel = afterIncrementalLiquibaseModel;
        this.dbDropBackupLiquibaseModel = dbDropBackupLiquibaseModel;
        this.dialect = dialect;
        this.backupTableNamePrefix = backupTableNamePrefix;
        this.tableNameMaxSize = tableNameMaxSize;

        // Initialize sub-transformations
        initializeSubTransformations();
    }

    private void initializeSubTransformations() {
        dbCheckupTransformation = DbCheckupZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(dbCheckupLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        dbBackupTransformation = DbBackupZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(dbBackupLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        beforeIncrementalTransformation = BeforeIncrementalZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(beforeIncrementalLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        dataUpdateBeforeTransformation = DataUpdateBeforeZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(dataUpdateBeforeIncrementalLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        incrementalTransformation = IncrementalZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(incrementalLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        dataUpdateAfterTransformation = DataUpdateAfterZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(dataUpdateAfterIncrementalLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        afterIncrementalTransformation = AfterIncrementalZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(afterIncrementalLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();

        dbDropBackupTransformation = DbDropBackupZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(dbDropBackupLiquibaseModel)
                .dialect(dialect)
                .backupTableNamePrefix(backupTableNamePrefix)
                .tableNameMaxSize(tableNameMaxSize)
                .build();
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting incremental RDBMS to Liquibase Zeta transformation (V2) with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        Map<EObject, List<EObject>> combinedTrace = new HashMap<>();

        // Phase 1: DB Checkup transformations
        log.debug("Phase 1: DB Checkup");
        mergeTrace(combinedTrace, dbCheckupTransformation.execute());

        // Phase 2: DB Backup transformations
        log.debug("Phase 2: DB Backup");
        mergeTrace(combinedTrace, dbBackupTransformation.execute());

        // Phase 3: Before Incremental transformations
        log.debug("Phase 3: Before Incremental");
        mergeTrace(combinedTrace, beforeIncrementalTransformation.execute());

        // Phase 4: Data Update Before Incremental transformations
        log.debug("Phase 4: Data Update Before Incremental");
        mergeTrace(combinedTrace, dataUpdateBeforeTransformation.execute());

        // Phase 5: Incremental transformations
        log.debug("Phase 5: Incremental");
        mergeTrace(combinedTrace, incrementalTransformation.execute());

        // Phase 6: Data Update After Incremental transformations
        log.debug("Phase 6: Data Update After Incremental");
        mergeTrace(combinedTrace, dataUpdateAfterTransformation.execute());

        // Phase 7: After Incremental transformations
        log.debug("Phase 7: After Incremental");
        mergeTrace(combinedTrace, afterIncrementalTransformation.execute());

        // Phase 8: DB Drop Backup transformations
        log.debug("Phase 8: DB Drop Backup");
        mergeTrace(combinedTrace, dbDropBackupTransformation.execute());

        long duration = System.currentTimeMillis() - startTime;
        log.info("Incremental RDBMS to Liquibase Zeta transformation (V2) completed in {}ms", duration);

        logSummary();

        return combinedTrace;
    }

    private void mergeTrace(Map<EObject, List<EObject>> target, Map<EObject, List<EObject>> source) {
        for (Map.Entry<EObject, List<EObject>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
    }

    private void logSummary() {
        databaseChangeLog dbCheckupLog = dbCheckupTransformation.getChangeLog();
        databaseChangeLog dbBackupLog = dbBackupTransformation.getChangeLog();
        databaseChangeLog beforeIncrLog = beforeIncrementalTransformation.getChangeLog();
        databaseChangeLog incrLog = incrementalTransformation.getChangeLog();
        databaseChangeLog afterIncrLog = afterIncrementalTransformation.getChangeLog();
        databaseChangeLog dbDropBackupLog = dbDropBackupTransformation.getChangeLog();

        log.debug("Transformation summary:");
        log.debug("  dbCheckup changeSets: {}", dbCheckupLog != null ? dbCheckupLog.getChangeSet().size() : 0);
        log.debug("  dbBackup changeSets: {}", dbBackupLog != null ? dbBackupLog.getChangeSet().size() : 0);
        log.debug("  beforeIncremental changeSets: {}", beforeIncrLog != null ? beforeIncrLog.getChangeSet().size() : 0);
        log.debug("  incremental changeSets: {}", incrLog != null ? incrLog.getChangeSet().size() : 0);
        log.debug("  afterIncremental changeSets: {}", afterIncrLog != null ? afterIncrLog.getChangeSet().size() : 0);
        log.debug("  dbDropBackup changeSets: {}", dbDropBackupLog != null ? dbDropBackupLog.getChangeSet().size() : 0);
    }
}
