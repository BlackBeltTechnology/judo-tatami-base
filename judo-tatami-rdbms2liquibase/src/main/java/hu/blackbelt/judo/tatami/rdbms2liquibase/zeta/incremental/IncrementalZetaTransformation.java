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
 * Incremental transformation phase for RDBMS to Liquibase transformation.
 * <p>
 * This phase handles the main schema changes:
 * <ul>
 *   <li>Drop tables</li>
 *   <li>Create tables</li>
 *   <li>Rename tables</li>
 *   <li>Rename columns</li>
 *   <li>Drop columns</li>
 *   <li>Add columns</li>
 *   <li>Modify data types</li>
 * </ul>
 * </p>
 */
@Slf4j
public class IncrementalZetaTransformation extends AbstractIncrementalSubTransformation {

    @Builder
    public IncrementalZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing Incremental transformation");

        initializeChangeLog();

        // Drop tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformDropTable);

        // Create tables
        all(RdbmsCreateTableOperation.class).forEach(this::transformCreateTable);

        // Rename tables
        all(RdbmsModifyTableOperation.class)
                .filter(RdbmsModifyTableOperation::isNameChanged)
                .forEach(this::transformRenameTable);

        // Rename columns
        all(RdbmsModifyFieldOperation.class)
                .filter(RdbmsModifyFieldOperation::isNameChanged)
                .forEach(this::transformRenameColumn);

        // Drop columns
        all(RdbmsDeleteFieldOperation.class).forEach(this::transformDropColumn);

        // Add columns
        all(RdbmsCreateFieldOperation.class).forEach(this::transformAddColumn);

        // Modify data types
        all(RdbmsModifyFieldOperation.class)
                .filter(op -> op.isTypeChanged() || op.isSizeChanged())
                .forEach(this::transformModifyDataType);

        log.debug("Incremental transformation completed");
        return buildTraceResult();
    }

    private void transformDropTable(RdbmsDeleteTableOperation op) {
        RdbmsTable table = op.getTable();
        log.debug("  Transform drop table: {}", table.getSqlName());

        DropTable dropTable = liquibaseFactory.createDropTable();
        dropTable.setTableName(table.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("drop-tables-" + context, "drop-tables");
        changeSet.getDropTable().add(dropTable);

        addTrace(op, DROP_TABLES, dropTable);
    }

    private void transformCreateTable(RdbmsCreateTableOperation op) {
        RdbmsTable table = op.getTable();
        log.debug("  Transform create table: {}", table.getSqlName());

        CreateTable createTable = liquibaseFactory.createCreateTable();
        createTable.setTableName(table.getSqlName());
        createTable.setRemarks(table.getUuid());

        // Add columns for all fields
        for (RdbmsField field : table.getFields()) {
            Column column = liquibaseFactory.createColumn();
            column.setName(field.getSqlName());
            column.setRemarks(field.getUuid());
            column.setType(toFieldDefinition(field));

            // Add constraints for primary key or mandatory fields
            if (field == table.getPrimaryKey() || field.isMandatory()) {
                Constraints constraint = liquibaseFactory.createConstraints();
                if (field == table.getPrimaryKey()) {
                    constraint.setPrimaryKey(true);
                }
                column.setConstraints(constraint);
            }
            createTable.getColumn().add(column);
        }

        ChangeSet changeSet = getOrCreateChangeSet(
                "create-table-" + table.getSqlName() + "-" + context, "create-tables");
        changeSet.getCreateTable().add(createTable);

        addTrace(op, CREATE_TABLES, createTable);
    }

    private void transformRenameTable(RdbmsModifyTableOperation op) {
        RdbmsTable previousTable = op.getPreviousTable();
        RdbmsTable newTable = op.getTable();
        log.debug("  Transform rename table: {} -> {}", previousTable.getSqlName(), newTable.getSqlName());

        RenameTable renameTable = liquibaseFactory.createRenameTable();
        renameTable.setOldTableName(previousTable.getSqlName());
        renameTable.setNewTableName(newTable.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("rename-tables-" + context, "rename-tables");
        changeSet.getRenameTable().add(renameTable);

        addTrace(op, RENAME_TABLES, renameTable);
    }

    private void transformRenameColumn(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField previousField = op.getPreviousField();
        RdbmsField newField = op.getField();
        log.debug("  Transform rename column: {} -> {}", previousField.getSqlName(), newField.getSqlName());

        RenameColumn renameColumn = liquibaseFactory.createRenameColumn();
        renameColumn.setOldColumnName(previousField.getSqlName());
        renameColumn.setNewColumnName(newField.getSqlName());
        renameColumn.setTableName(tableOp.getTable().getSqlName());
        renameColumn.setRemarks(newField.getUuid());

        ChangeSet changeSet = getOrCreateChangeSet(
                "rename-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "rename-fields");
        changeSet.getRenameColumn().add(renameColumn);

        addTrace(op, RENAME_COLUMNS, renameColumn);
    }

    private void transformDropColumn(RdbmsDeleteFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        log.debug("  Transform drop column: {}", field.getSqlName());

        DropColumn dropColumn = liquibaseFactory.createDropColumn();
        dropColumn.setTableName(tableOp.getTable().getSqlName());
        dropColumn.setColumnName(field.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet(
                "drop-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "drop-fields");
        changeSet.getDropColumn().add(dropColumn);

        addTrace(op, DROP_COLUMNS, dropColumn);
    }

    private void transformAddColumn(RdbmsCreateFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        log.debug("  Transform add column: {}", field.getSqlName());

        AddColumnDef addColumnDef = liquibaseFactory.createAddColumnDef();
        addColumnDef.setName(field.getSqlName());
        addColumnDef.setType(toFieldDefinition(field));
        addColumnDef.setRemarks(field.getUuid());

        // Add constraints for primary key or mandatory fields
        RdbmsTable table = tableOp.getTable();
        if (field == table.getPrimaryKey() || field.isMandatory()) {
            Constraints constraint = liquibaseFactory.createConstraints();
            if (field == table.getPrimaryKey()) {
                constraint.setPrimaryKey(true);
                constraint.setPrimaryKeyName("PK_" + field.getSqlName());
            }
            addColumnDef.setConstraints(constraint);
        }

        ChangeSet changeSet = getOrCreateChangeSet(
                "create-fields-in-" + table.getSqlName() + "-" + context, "create-fields");

        // Ensure AddColumn container exists
        if (changeSet.getAddColumn().isEmpty()) {
            AddColumn addColumn = liquibaseFactory.createAddColumn();
            addColumn.setTableName(table.getSqlName());
            changeSet.getAddColumn().add(addColumn);
        }
        changeSet.getAddColumn().get(0).getColumn().add(addColumnDef);

        addTrace(op, ADD_COLUMN_DEFS, addColumnDef);
    }

    private void transformModifyDataType(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        log.debug("  Transform modify data type: {}", field.getSqlName());

        ModifyDataType modifyDataType = liquibaseFactory.createModifyDataType();
        modifyDataType.setTableName(tableOp.getTable().getSqlName());
        modifyDataType.setColumnName(field.getSqlName());
        modifyDataType.setNewDataType(toFieldDefinition(field));

        ChangeSet changeSet = getOrCreateChangeSet(
                "modify-data-types-in-" + tableOp.getTable().getSqlName() + "-" + context, "modify-data-types");
        changeSet.getModifyDataType().add(modifyDataType);

        addTrace(op, MODIFY_DATA_TYPES, modifyDataType);
    }
}
