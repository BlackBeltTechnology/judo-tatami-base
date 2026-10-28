package hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.incremental;

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

import hu.blackbelt.judo.meta.liquibase.*;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.List;
import java.util.Map;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * DB Drop Backup transformation phase for incremental RDBMS to Liquibase transformation.
 * <p>
 * This phase creates preconditions to check backup tables exist and drops them after
 * successful incremental migration.
 * </p>
 */
@Slf4j
public class DbDropBackupZetaTransformation extends AbstractIncrementalSubTransformation {

    private PreConditions preConditions;

    @Builder
    public DbDropBackupZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing DB Drop Backup transformation");

        initializeChangeLog();

        // Create preconditions container
        preConditions = liquibaseFactory.createPreConditions();
        changeLog.setPreConditions(preConditions);

        // Post-check and delete backup deleted tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformPostCheckAndDeleteBackupDeletedTable);

        // Post-check and delete backup modified tables
        all(RdbmsModifyTableOperation.class).forEach(this::transformPostCheckAndDeleteBackupModifiedTable);

        log.debug("DB Drop Backup transformation completed");
        return buildTraceResult();
    }

    private void transformPostCheckAndDeleteBackupDeletedTable(RdbmsDeleteTableOperation op) {
        transformPostCheckBackupTable(op, op.getTable(), POST_CHECK_BACKUP_DELETED_TABLES);
        transformDeleteBackupTable(op, op.getTable(), DELETE_BACKUP_DELETED_TABLES);
    }

    private void transformPostCheckAndDeleteBackupModifiedTable(RdbmsModifyTableOperation op) {
        transformPostCheckBackupTable(op, op.getPreviousTable(), POST_CHECK_BACKUP_MODIFIED_TABLES);
        transformDeleteBackupTable(op, op.getPreviousTable(), DELETE_BACKUP_MODIFIED_TABLES);
    }

    private void transformPostCheckBackupTable(RdbmsTableOperation op, RdbmsTable table, String ruleName) {
        String abbreviatedName = abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();
        log.debug("  Transform post-check backup table: {}", abbreviatedName);

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

        preConditions.getTableExists().add(tableExists);

        addTrace(op, ruleName, tableExists);
    }

    private void transformDeleteBackupTable(RdbmsTableOperation op, RdbmsTable table, String ruleName) {
        String abbreviatedName = abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();
        log.debug("  Transform delete backup table: {}", abbreviatedName);

        DropTable dropTable = liquibaseFactory.createDropTable();
        dropTable.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

        ChangeSet changeSet = getOrCreateChangeSet("drop-backup-tables-" + context, "drop-backup-tables");
        changeSet.getDropTable().add(dropTable);

        addTrace(op, ruleName, dropTable);
    }
}
