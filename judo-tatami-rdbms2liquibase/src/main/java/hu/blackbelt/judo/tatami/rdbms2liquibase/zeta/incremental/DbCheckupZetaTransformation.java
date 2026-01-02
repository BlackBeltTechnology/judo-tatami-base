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
 * DB Checkup transformation phase for incremental RDBMS to Liquibase transformation.
 * <p>
 * This phase creates preconditions to verify that the database is in the expected state
 * before applying incremental changes.
 * </p>
 */
@Slf4j
public class DbCheckupZetaTransformation extends AbstractIncrementalSubTransformation {

    private PreConditions preConditions;

    @Builder
    public DbCheckupZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing DB Checkup transformation");

        initializeChangeLog();

        // Create preconditions container
        preConditions = liquibaseFactory.createPreConditions();
        changeLog.setPreConditions(preConditions);

        // Check tables
        all(RdbmsTable.class)
                .filter(this::isPreviousModelContainsElement)
                .forEach(this::transformCheckTable);

        // Check junction tables
        all(RdbmsJunctionTable.class)
                .filter(this::isPreviousModelContainsElement)
                .forEach(this::transformCheckJunctionTable);

        // Pre-check backup deleted tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformPreCheckBackupDeletedTable);

        // Pre-check backup modified tables
        all(RdbmsModifyTableOperation.class).forEach(this::transformPreCheckBackupModifiedTable);

        // Check fields
        all(RdbmsValueField.class)
                .filter(this::isFieldInPreviousModel)
                .forEach(this::transformCheckValueField);

        all(RdbmsIdentifierField.class)
                .filter(f -> !(f instanceof RdbmsForeignKey))
                .filter(this::isFieldInPreviousModel)
                .forEach(this::transformCheckIdentifierField);

        all(RdbmsForeignKey.class)
                .filter(this::isFieldInPreviousModel)
                .forEach(this::transformCheckForeignKey);

        // Check foreign key constraints
        all(RdbmsForeignKey.class)
                .filter(this::isFieldInPreviousModel)
                .forEach(this::transformCheckForeignKeyConstraint);

        // Check indexes
        all(RdbmsIndex.class)
                .filter(this::isIndexInPreviousModel)
                .forEach(this::transformCheckIndex);

        log.debug("DB Checkup transformation completed");
        return buildTraceResult();
    }

    private boolean isPreviousModelContainsElement(EObject element) {
        return true;
    }

    private boolean isFieldInPreviousModel(RdbmsField field) {
        return field.eContainer() != null;
    }

    private boolean isIndexInPreviousModel(RdbmsIndex index) {
        return index.eContainer() != null;
    }

    private void transformCheckTable(RdbmsTable table) {
        log.debug("  Transform check table: {}", table.getSqlName());

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(table.getSqlName());

        preConditions.getTableExists().add(tableExists);

        addTrace(table, CHECK_TABLES, tableExists);
    }

    private void transformCheckJunctionTable(RdbmsJunctionTable table) {
        log.debug("  Transform check junction table: {}", table.getSqlName());

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(table.getSqlName());

        preConditions.getTableExists().add(tableExists);

        addTrace(table, CHECK_JUNCTION_TABLES, tableExists);
    }

    private void transformPreCheckBackupDeletedTable(RdbmsDeleteTableOperation op) {
        transformPreCheckBackupTable(op, op.getTable(), PRE_CHECK_BACKUP_DELETED_TABLES);
    }

    private void transformPreCheckBackupModifiedTable(RdbmsModifyTableOperation op) {
        transformPreCheckBackupTable(op, op.getPreviousTable(), PRE_CHECK_BACKUP_MODIFIED_TABLES);
    }

    private void transformPreCheckBackupTable(RdbmsTableOperation op, RdbmsTable table, String ruleName) {
        log.debug("  Transform pre-check backup table: {}", table.getSqlName());

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(backupTableNamePrefix + "_" + table.getSqlName());

        // Add to Not precondition
        if (preConditions.getNot().isEmpty()) {
            preConditions.getNot().add(liquibaseFactory.createNot());
        }
        preConditions.getNot().get(0).getTableExists().add(tableExists);

        addTrace(op, ruleName, tableExists);
    }

    private void transformCheckValueField(RdbmsValueField field) {
        transformCheckField(field, CHECK_VALUE_FIELDS);
    }

    private void transformCheckIdentifierField(RdbmsIdentifierField field) {
        transformCheckField(field, CHECK_IDENTIFIER_FIELDS);
    }

    private void transformCheckForeignKey(RdbmsForeignKey field) {
        transformCheckField(field, CHECK_FOREIGN_KEYS);
    }

    private void transformCheckField(RdbmsField field, String ruleName) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform check field: {}.{}", table.getSqlName(), field.getSqlName());

        ColumnExists columnExists = liquibaseFactory.createColumnExists();
        columnExists.setTableName(table.getSqlName());
        columnExists.setColumnName(field.getSqlName());

        preConditions.getColumnExists().add(columnExists);

        addTrace(field, ruleName, columnExists);
    }

    private void transformCheckForeignKeyConstraint(RdbmsForeignKey field) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform check FK constraint: {}", field.getForeignKeySqlName());

        ForeignKeyConstraintExists fkExists = liquibaseFactory.createForeignKeyConstraintExists();
        fkExists.setForeignKeyTableName(table.getSqlName());
        fkExists.setForeignKeyName(field.getForeignKeySqlName());

        preConditions.getForeignKeyConstraintExists().add(fkExists);

        addTrace(field, CHECK_FOREIGN_KEY_CONSTRAINTS, fkExists);
    }

    private void transformCheckIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        log.debug("  Transform check index: {}", index.getSqlName());

        for (RdbmsField field : index.getFields()) {
            IndexExists indexExists = liquibaseFactory.createIndexExists();
            indexExists.setIndexName(index.getSqlName());
            indexExists.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            indexExists.setColumnNames(field.getSqlName());

            preConditions.getIndexExists().add(indexExists);
        }

        addTrace(index, CHECK_INDEXES, null);
    }
}
