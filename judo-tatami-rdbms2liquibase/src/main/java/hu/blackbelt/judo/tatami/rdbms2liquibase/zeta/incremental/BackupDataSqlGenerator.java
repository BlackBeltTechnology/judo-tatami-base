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

import hu.blackbelt.judo.meta.rdbms.RdbmsDeleteTableOperation;
import hu.blackbelt.judo.meta.rdbms.RdbmsModifyTableOperation;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.AbbreviateUtils;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backup-data SQL generation of the incremental transformation. Java port of
 * {@code generations/sql/main.egl} + {@code BackupTableData.egl} of the ETL add-on; the output is the same, including
 * the table name of a modified table's backup (taken from the operation's current {@code table}, while the file name
 * and the source table come from {@code previousTable}).
 * See change: rdbms-incremental-without-epsilon.
 */
public final class BackupDataSqlGenerator {

    private BackupDataSqlGenerator() {
    }

    /**
     * Generates one script per deleted table, then one per modified table, into {@code backupDataSqlFiles}
     * (file name to script content). A later script with the same file name overwrites the earlier one.
     */
    public static void generate(RdbmsModel rdbmsModel, String dialect, String backupTableNamePrefix,
                                int tableNameMaxSize, Map<String, String> backupDataSqlFiles) {
        final String backupChangeSetNamePrefix = backupTableNamePrefix.toLowerCase();

        for (RdbmsDeleteTableOperation op : allOf(rdbmsModel, RdbmsDeleteTableOperation.class)) {
            put(backupDataSqlFiles, dialect, backupTableNamePrefix, backupChangeSetNamePrefix, tableNameMaxSize,
                    op.getTable(), op.getTable());
        }
        for (RdbmsModifyTableOperation op : allOf(rdbmsModel, RdbmsModifyTableOperation.class)) {
            put(backupDataSqlFiles, dialect, backupTableNamePrefix, backupChangeSetNamePrefix, tableNameMaxSize,
                    op.getTable(), op.getPreviousTable());
        }
    }

    private static void put(Map<String, String> backupDataSqlFiles, String dialect, String backupTableNamePrefix,
                            String backupChangeSetNamePrefix, int tableNameMaxSize,
                            RdbmsTable nameSource, RdbmsTable dataSource) {
        String backupTableName = backupTableNamePrefix + "_" + AbbreviateUtils.abbreviate(
                nameSource.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1, "_").toUpperCase();
        backupDataSqlFiles.put(
                backupChangeSetNamePrefix + "_" + dataSource.getSqlName().toLowerCase() + "_data_" + dialect + ".sql",
                backupTableData(dialect, dataSource.getSqlName(), backupTableName));
    }

    /** Content of {@code BackupTableData.egl}. */
    static String backupTableData(String dialect, String tableSqlName, String backupTableName) {
        return "\nCREATE TABLE " + backupTableName + " AS (SELECT * FROM " + tableSqlName + ")"
                + ("hsqldb".equals(dialect) ? " WITH DATA" : "") + ";\n";
    }

    private static <T extends EObject> List<T> allOf(RdbmsModel rdbmsModel, Class<T> type) {
        List<T> result = new ArrayList<>();
        TreeIterator<Notifier> contents = rdbmsModel.getResourceSet().getAllContents();
        while (contents.hasNext()) {
            Notifier next = contents.next();
            if (type.isInstance(next)) {
                result.add(type.cast(next));
            }
        }
        return result;
    }
}
