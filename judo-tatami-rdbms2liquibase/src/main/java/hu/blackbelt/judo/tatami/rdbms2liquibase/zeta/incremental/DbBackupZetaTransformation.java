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

import hu.blackbelt.judo.meta.liquibase.ChangeSet;
import hu.blackbelt.judo.meta.liquibase.SqlFile;
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
 * DB Backup transformation phase for incremental RDBMS to Liquibase transformation.
 * <p>
 * This phase creates SQL file references for backing up deleted and modified tables.
 * </p>
 */
@Slf4j
public class DbBackupZetaTransformation extends AbstractIncrementalSubTransformation {

    @Builder
    public DbBackupZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing DB Backup transformation");

        initializeChangeLog();

        // Backup deleted tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformBackupDeletedTable);

        // Backup modified tables
        all(RdbmsModifyTableOperation.class).forEach(this::transformBackupModifiedTable);

        log.debug("DB Backup transformation completed");
        return buildTraceResult();
    }

    private void transformBackupDeletedTable(RdbmsDeleteTableOperation op) {
        transformBackupTable(op, op.getTable(), BACKUP_DELETED_TABLES);
    }

    private void transformBackupModifiedTable(RdbmsModifyTableOperation op) {
        transformBackupTable(op, op.getPreviousTable(), BACKUP_MODIFIED_TABLES);
    }

    private void transformBackupTable(RdbmsTableOperation op, RdbmsTable table, String ruleName) {
        log.debug("  Transform backup table: {}", table.getSqlName());

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        sqlFile.setPath(backupChangeSetNamePrefix + "_" + table.getSqlName().toLowerCase() + "_data_" + dialect + ".sql");
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("backup-tables-" + context, "backup-tables");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, ruleName, sqlFile);
        log.debug("SqlFile added: {}", sqlFile.getPath());
    }
}
