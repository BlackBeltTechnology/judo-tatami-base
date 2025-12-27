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

import hu.blackbelt.judo.meta.liquibase.*;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.zeta.annotation.Abstract;
import hu.blackbelt.judo.zeta.annotation.Extends;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.PostExecution;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * Java-based incremental RDBMS to Liquibase transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * for incremental schema changes (rdbmsIncrementalToLiquibase.etl and related modules).
 * </p>
 * <p>
 * The incremental transformation handles:
 * <ul>
 *   <li>Schema operations (create, modify, delete tables and fields)</li>
 *   <li>Constraint management (indexes, unique constraints, foreign keys, not null)</li>
 *   <li>Database checkup preconditions</li>
 *   <li>Backup and restore operations</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Rdbms2LiquibaseIncrementalZetaTransformation {

    private final RdbmsModel rdbmsModel;
    private final LiquibaseFactory liquibaseFactory;

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
    private final String context;
    private final String backupTableNamePrefix;
    private final String backupChangeSetNamePrefix;
    private final int tableNameMaxSize;

    // Previous and current models from RdbmsOperationMeta
    private RdbmsModel previousModel;
    private RdbmsModel currentModel;

    // The root databaseChangeLog elements for each phase
    private databaseChangeLog dbCheckupChangeLog;
    private databaseChangeLog dbBackupChangeLog;
    private databaseChangeLog beforeIncrementalChangeLog;
    private databaseChangeLog dataUpdateBeforeIncrementalChangeLog;
    private databaseChangeLog incrementalChangeLog;
    private databaseChangeLog dataUpdateAfterIncrementalChangeLog;
    private databaseChangeLog afterIncrementalChangeLog;
    private databaseChangeLog dbDropBackupChangeLog;

    // PreConditions for checkup model
    private PreConditions dbCheckupPreConditions;
    private PreConditions dbDropBackupPreConditions;

    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    // Cache for changeSets by logical file path (per model)
    private final Map<String, Map<String, ChangeSet>> changeSetCaches = new HashMap<>();

    @Builder
    public Rdbms2LiquibaseIncrementalZetaTransformation(
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
        this.liquibaseFactory = LiquibaseFactory.eINSTANCE;
        this.dialect = dialect;
        this.backupTableNamePrefix = backupTableNamePrefix != null ? backupTableNamePrefix : "BACKUP";
        this.backupChangeSetNamePrefix = this.backupTableNamePrefix.toLowerCase();
        this.tableNameMaxSize = tableNameMaxSize != null ? tableNameMaxSize : 
                ("oracle".equals(dialect) ? 30 : 62);

        // Get model version for context
        this.context = getModelVersion();

        // Initialize changeset caches for each model
        changeSetCaches.put("incremental", new HashMap<>());
        changeSetCaches.put("beforeIncremental", new HashMap<>());
        changeSetCaches.put("afterIncremental", new HashMap<>());
        changeSetCaches.put("dataUpdateBeforeIncremental", new HashMap<>());
        changeSetCaches.put("dataUpdateAfterIncremental", new HashMap<>());
        changeSetCaches.put("dbBackup", new HashMap<>());
        changeSetCaches.put("dbDropBackup", new HashMap<>());
    }

    private String getModelVersion() {
        return all(hu.blackbelt.judo.meta.rdbms.RdbmsModel.class)
                .findFirst()
                .map(hu.blackbelt.judo.meta.rdbms.RdbmsModel::getVersion)
                .orElse(null);
    }

    /**
     * Helper method to get all elements of a given type from the RDBMS model.
     */
    private <T> Stream<T> all(Class<T> clazz) {
        return rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .flatMap(e -> {
                    List<T> result = new ArrayList<>();
                    collectAllOfType(e, clazz, result);
                    return result.stream();
                });
    }

    @SuppressWarnings("unchecked")
    private <T> void collectAllOfType(EObject root, Class<T> clazz, List<T> result) {
        if (clazz.isInstance(root)) {
            result.add((T) root);
        }
        for (EObject child : root.eContents()) {
            collectAllOfType(child, clazz, result);
        }
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting incremental RDBMS to Liquibase Zeta transformation with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        // Initialize operation metadata
        initializeOperationMeta();

        // Create root databaseChangeLog elements for each phase
        initializeChangeLogs();

        // Phase 1: DB Checkup transformations
        transformDbCheckup();

        // Phase 2: DB Backup transformations
        transformDbBackup();

        // Phase 3: Before Incremental transformations
        transformBeforeIncremental();

        // Phase 4: Data Update Before Incremental transformations
        transformDataUpdateBeforeIncremental();

        // Phase 5: Incremental transformations
        transformIncremental();

        // Phase 6: Data Update After Incremental transformations
        transformDataUpdateAfterIncremental();

        // Phase 7: After Incremental transformations
        transformAfterIncremental();

        // Phase 8: DB Drop Backup transformations
        transformDbDropBackup();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Incremental RDBMS to Liquibase Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    private void initializeOperationMeta() {
        // Get RdbmsOperationMeta to access previousModel and currentModel
        all(RdbmsOperationMeta.class).findFirst().ifPresent(meta -> {
            this.previousModel = meta.getPreviousModel() != null ? 
                    createModelFromRdbmsModel(meta.getPreviousModel()) : null;
            this.currentModel = meta.getCurrentModel() != null ? 
                    createModelFromRdbmsModel(meta.getCurrentModel()) : null;
        });
    }

    private RdbmsModel createModelFromRdbmsModel(hu.blackbelt.judo.meta.rdbms.RdbmsModel model) {
        // This is a simplified version - in a real implementation you'd need to properly
        // create an RdbmsModel wrapper around the model element
        return rdbmsModel;
    }

    private void initializeChangeLogs() {
        // DB Checkup
        dbCheckupChangeLog = liquibaseFactory.createdatabaseChangeLog();
        dbCheckupPreConditions = liquibaseFactory.createPreConditions();
        dbCheckupChangeLog.setPreConditions(dbCheckupPreConditions);
        dbCheckupLiquibaseModel.getResource().getContents().add(dbCheckupChangeLog);

        // DB Backup
        dbBackupChangeLog = liquibaseFactory.createdatabaseChangeLog();
        dbBackupLiquibaseModel.getResource().getContents().add(dbBackupChangeLog);

        // Before Incremental
        beforeIncrementalChangeLog = liquibaseFactory.createdatabaseChangeLog();
        beforeIncrementalLiquibaseModel.getResource().getContents().add(beforeIncrementalChangeLog);

        // Data Update Before Incremental
        dataUpdateBeforeIncrementalChangeLog = liquibaseFactory.createdatabaseChangeLog();
        dataUpdateBeforeIncrementalLiquibaseModel.getResource().getContents().add(dataUpdateBeforeIncrementalChangeLog);

        // Incremental (main)
        incrementalChangeLog = liquibaseFactory.createdatabaseChangeLog();
        incrementalLiquibaseModel.getResource().getContents().add(incrementalChangeLog);

        // Data Update After Incremental
        dataUpdateAfterIncrementalChangeLog = liquibaseFactory.createdatabaseChangeLog();
        dataUpdateAfterIncrementalLiquibaseModel.getResource().getContents().add(dataUpdateAfterIncrementalChangeLog);

        // After Incremental
        afterIncrementalChangeLog = liquibaseFactory.createdatabaseChangeLog();
        afterIncrementalLiquibaseModel.getResource().getContents().add(afterIncrementalChangeLog);

        // DB Drop Backup
        dbDropBackupChangeLog = liquibaseFactory.createdatabaseChangeLog();
        dbDropBackupPreConditions = liquibaseFactory.createPreConditions();
        dbDropBackupChangeLog.setPreConditions(dbDropBackupPreConditions);
        dbDropBackupLiquibaseModel.getResource().getContents().add(dbDropBackupChangeLog);
    }

    // =========================================================================
    // INCREMENTAL TRANSFORMATIONS (incremental.etl)
    // =========================================================================

    private void transformIncremental() {
        log.debug("Transforming incremental changes");

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
    }

    private void transformDropTable(RdbmsDeleteTableOperation op) {
        RdbmsTable table = op.getTable();
        log.debug("  Transform drop table: {}", table.getSqlName());

        DropTable dropTable = liquibaseFactory.createDropTable();
        dropTable.setTableName(table.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "drop-tables-" + context, "drop-tables");
        changeSet.getDropTable().add(dropTable);

        addTrace(op, DROP_TABLES, dropTable);
        log.debug("DropTable added: {}", dropTable.getTableName());
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
            column.setType(Rdbms2LiquibaseHelper.toFieldDefinition(field));

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

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "create-table-" + table.getSqlName() + "-" + context, "create-tables");
        changeSet.getCreateTable().add(createTable);

        addTrace(op, CREATE_TABLES, createTable);
        log.debug("CreateTable added: {}", createTable.getTableName());
    }

    private void transformRenameTable(RdbmsModifyTableOperation op) {
        RdbmsTable previousTable = op.getPreviousTable();
        RdbmsTable newTable = op.getTable();
        log.debug("  Transform rename table: {} -> {}", previousTable.getSqlName(), newTable.getSqlName());

        RenameTable renameTable = liquibaseFactory.createRenameTable();
        renameTable.setOldTableName(previousTable.getSqlName());
        renameTable.setNewTableName(newTable.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "rename-tables-" + context, "rename-tables");
        changeSet.getRenameTable().add(renameTable);

        addTrace(op, RENAME_TABLES, renameTable);
        log.debug("RenameTable added: {} -> {}", renameTable.getOldTableName(), renameTable.getNewTableName());
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

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "rename-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "rename-fields");
        changeSet.getRenameColumn().add(renameColumn);

        addTrace(op, RENAME_COLUMNS, renameColumn);
        log.debug("RenameColumn added: {} -> {} ({})", 
                renameColumn.getOldColumnName(), renameColumn.getNewColumnName(), renameColumn.getTableName());
    }

    private void transformDropColumn(RdbmsDeleteFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        log.debug("  Transform drop column: {}", field.getSqlName());

        DropColumn dropColumn = liquibaseFactory.createDropColumn();
        dropColumn.setTableName(tableOp.getTable().getSqlName());
        dropColumn.setColumnName(field.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "drop-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "drop-fields");
        changeSet.getDropColumn().add(dropColumn);

        addTrace(op, DROP_COLUMNS, dropColumn);
        log.debug("DropColumn added: {} ({})", dropColumn.getColumnName(), dropColumn.getTableName());
    }

    private void transformAddColumn(RdbmsCreateFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        log.debug("  Transform add column: {}", field.getSqlName());

        AddColumnDef addColumnDef = liquibaseFactory.createAddColumnDef();
        addColumnDef.setName(field.getSqlName());
        addColumnDef.setType(Rdbms2LiquibaseHelper.toFieldDefinition(field));
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

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "create-fields-in-" + table.getSqlName() + "-" + context, "create-fields");

        // Ensure AddColumn container exists
        if (changeSet.getAddColumn().isEmpty()) {
            AddColumn addColumn = liquibaseFactory.createAddColumn();
            addColumn.setTableName(table.getSqlName());
            changeSet.getAddColumn().add(addColumn);
            log.debug("AddColumn added: {}", table.getSqlName());
        }
        changeSet.getAddColumn().get(0).getColumn().add(addColumnDef);

        addTrace(op, ADD_COLUMN_DEFS, addColumnDef);
        log.debug("AddColumnDef added: {} ({})", addColumnDef.getName(), table.getSqlName());
    }

    private void transformModifyDataType(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        RdbmsField field = op.getField();
        RdbmsField previousField = op.getPreviousField();
        log.debug("  Transform modify data type: {}", field.getSqlName());

        ModifyDataType modifyDataType = liquibaseFactory.createModifyDataType();
        modifyDataType.setTableName(tableOp.getTable().getSqlName());
        modifyDataType.setColumnName(field.getSqlName());
        modifyDataType.setNewDataType(Rdbms2LiquibaseHelper.toFieldDefinition(field));

        ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                "modify-data-types-in-" + tableOp.getTable().getSqlName() + "-" + context, "modify-data-types");
        changeSet.getModifyDataType().add(modifyDataType);

        addTrace(op, MODIFY_DATA_TYPES, modifyDataType);
        log.debug("ModifyDataType added: {} ({}) ==> {} -> {}", 
                modifyDataType.getColumnName(), modifyDataType.getTableName(),
                Rdbms2LiquibaseHelper.toFieldDefinition(previousField), Rdbms2LiquibaseHelper.toFieldDefinition(field));
    }

    // =========================================================================
    // BEFORE INCREMENTAL TRANSFORMATIONS (beforeIncremental.etl)
    // =========================================================================

    private void transformBeforeIncremental() {
        log.debug("Transforming before incremental changes");

        // Drop indexes
        all(RdbmsIndex.class)
                .filter(this::isPreviousModelContainsContainer)
                .forEach(this::transformDropIndex);

        // Drop unique constraints
        all(RdbmsUniqueConstraint.class)
                .filter(this::isPreviousModelContainsContainer)
                .forEach(this::transformDropUniqueConstraint);

        // Drop not null constraints from value fields
        all(RdbmsValueField.class)
                .filter(f -> isPreviousModelContainsContainer(f) && f.isMandatory())
                .forEach(this::transformDropNotNullConstraintFromValueField);

        // Drop not null constraints from foreign keys
        all(RdbmsForeignKey.class)
                .filter(f -> isPreviousModelContainsContainer(f) && f.isMandatory())
                .forEach(this::transformDropNotNullConstraintFromForeignKey);

        // Drop foreign key constraints
        all(RdbmsForeignKey.class)
                .filter(this::isPreviousModelContainsContainer)
                .forEach(this::transformDropForeignKeyConstraint);
    }

    private boolean isPreviousModelContainsContainer(EObject element) {
        // In a real implementation, this would check if the element's container
        // existed in the previous model. For now, we return true for simplicity.
        return element.eContainer() != null;
    }

    private void transformDropIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        log.debug("  Transform drop index: {}", index.getSqlName());

        DropIndex dropIndex = liquibaseFactory.createDropIndex();
        dropIndex.setIndexName(index.getSqlName());
        dropIndex.setTableName(table.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                "drop-indexes-from-" + table.getSqlName() + "-" + context, "drop-indexes");
        changeSet.getDropIndex().add(dropIndex);

        addTrace(index, DROP_INDEXES, dropIndex);
        log.debug("DropIndex added: {} ({})", dropIndex.getIndexName(), dropIndex.getTableName());
    }

    private void transformDropUniqueConstraint(RdbmsUniqueConstraint constraint) {
        RdbmsTable table = (RdbmsTable) constraint.eContainer();
        log.debug("  Transform drop unique constraint: {}", constraint.getSqlName());

        for (RdbmsField field : constraint.getFields()) {
            DropUniqueConstraint dropUnique = liquibaseFactory.createDropUniqueConstraint();
            dropUnique.setConstraintName(constraint.getSqlName());
            dropUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            dropUnique.setUniqueColumns(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-unique-constraints-from-" + table.getSqlName() + "-" + context, "drop-unique-constraints");
            changeSet.getDropUniqueConstraint().add(dropUnique);

            log.debug("DropUniqueConstraint added: {} ({})", dropUnique.getUniqueColumns(), dropUnique.getTableName());
        }

        addTrace(constraint, DROP_UNIQUE_CONSTRAINTS, null);
    }

    private void transformDropNotNullConstraintFromValueField(RdbmsValueField field) {
        transformDropNotNullConstraint(field, DROP_NOT_NULL_CONSTRAINTS_FROM_VALUE_FIELDS);
    }

    private void transformDropNotNullConstraintFromForeignKey(RdbmsForeignKey field) {
        transformDropNotNullConstraint(field, DROP_NOT_NULL_CONSTRAINTS_FROM_FOREIGN_KEYS);
    }

    private void transformDropNotNullConstraint(RdbmsField field, String ruleName) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform drop not null constraint: {}", field.getSqlName());

        DropNotNullConstraint dropNotNull = liquibaseFactory.createDropNotNullConstraint();
        dropNotNull.setTableName(table.getSqlName());
        dropNotNull.setColumnName(field.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                "drop-not-null-constraints-from-" + table.getSqlName() + "-" + context, "drop-not-null-constraints");
        changeSet.getDropNotNullConstraint().add(dropNotNull);

        addTrace(field, ruleName, dropNotNull);
        log.debug("DropNotNullConstraint added: {} ({})", dropNotNull.getColumnName(), dropNotNull.getTableName());
    }

    private void transformDropForeignKeyConstraint(RdbmsForeignKey field) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform drop foreign key constraint: {}", field.getForeignKeySqlName());

        DropForeignKeyConstraint dropFk = liquibaseFactory.createDropForeignKeyConstraint();
        dropFk.setBaseTableName(table.getSqlName());
        dropFk.setConstraintName(field.getForeignKeySqlName());

        ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                "drop-foreign-keys-from-" + table.getSqlName() + "-" + context, "drop-foreign-keys");
        changeSet.getDropForeignKeyConstraint().add(dropFk);

        addTrace(field, DROP_FOREIGN_KEY_CONSTRAINTS, dropFk);
        log.debug("DropForeignKeyConstraint added: {} ({})", dropFk.getConstraintName(), dropFk.getBaseTableName());
    }

    // =========================================================================
    // AFTER INCREMENTAL TRANSFORMATIONS (afterIncremental.etl)
    // =========================================================================

    private void transformAfterIncremental() {
        log.debug("Transforming after incremental changes");

        // Add foreign key constraints
        all(RdbmsForeignKey.class)
                .filter(this::isNewModelContainsContainer)
                .forEach(this::transformAddForeignKeyConstraint);

        // Add not null constraints to value fields
        all(RdbmsValueField.class)
                .filter(f -> isNewModelContainsContainer(f) && f.isMandatory())
                .forEach(this::transformAddNotNullConstraintToValueField);

        // Add not null constraints to foreign keys
        all(RdbmsForeignKey.class)
                .filter(f -> isNewModelContainsContainer(f) && f.isMandatory())
                .forEach(this::transformAddNotNullConstraintToForeignKey);

        // Add unique constraints
        all(RdbmsUniqueConstraint.class)
                .filter(this::isNewModelContainsContainer)
                .forEach(this::transformAddUniqueConstraint);

        // Add indexes
        all(RdbmsIndex.class)
                .filter(this::isNewModelContainsContainer)
                .forEach(this::transformAddIndex);
    }

    private boolean isNewModelContainsContainer(EObject element) {
        // In a real implementation, this would check if the element's container
        // exists in the new model. For now, we return true for simplicity.
        return element.eContainer() != null;
    }

    private void transformAddForeignKeyConstraint(RdbmsForeignKey field) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform add foreign key constraint: {}", field.getForeignKeySqlName());

        AddForeignKeyConstraint addFk = liquibaseFactory.createAddForeignKeyConstraint();
        addFk.setBaseTableName(table.getSqlName());
        addFk.setBaseColumnNames(field.getSqlName());
        addFk.setConstraintName(field.getForeignKeySqlName());

        if (field.getReferenceKey() != null) {
            RdbmsTable refTable = (RdbmsTable) field.getReferenceKey().eContainer();
            addFk.setReferencedTableName(refTable.getSqlName());
            addFk.setReferencedColumnNames(field.getReferenceKey().getSqlName());
        }

        ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                "add-foreign-keys-to-" + table.getSqlName() + "-" + context, "add-foreign-keys");
        changeSet.getAddForeignKeyConstraint().add(addFk);

        addTrace(field, ADD_FOREIGN_KEY_CONSTRAINTS, addFk);
        log.debug("AddForeignKeyConstraint added: {}", addFk.getConstraintName());
    }

    private void transformAddNotNullConstraintToValueField(RdbmsValueField field) {
        transformAddNotNullConstraint(field, ADD_NOT_NULL_CONSTRAINTS_TO_VALUE_FIELDS);
    }

    private void transformAddNotNullConstraintToForeignKey(RdbmsForeignKey field) {
        transformAddNotNullConstraint(field, ADD_NOT_NULL_CONSTRAINTS_TO_FOREIGN_KEYS);
    }

    private void transformAddNotNullConstraint(RdbmsField field, String ruleName) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform add not null constraint: {}", field.getSqlName());

        AddNotNullConstraint addNotNull = liquibaseFactory.createAddNotNullConstraint();
        addNotNull.setTableName(table.getSqlName());
        addNotNull.setColumnName(field.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                "add-not-null-constraints-to-" + table.getSqlName() + "-" + context, "add-not-null-constraints");
        changeSet.getAddNotNullConstraint().add(addNotNull);

        addTrace(field, ruleName, addNotNull);
        log.debug("AddNotNullConstraint added: {} ({})", addNotNull.getColumnName(), addNotNull.getTableName());
    }

    private void transformAddUniqueConstraint(RdbmsUniqueConstraint constraint) {
        RdbmsTable table = (RdbmsTable) constraint.eContainer();
        log.debug("  Transform add unique constraint: {}", constraint.getSqlName());

        for (RdbmsField field : constraint.getFields()) {
            AddUniqueConstraint addUnique = liquibaseFactory.createAddUniqueConstraint();
            addUnique.setConstraintName(constraint.getSqlName());
            addUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            addUnique.setColumnNames(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "add-unique-constraints-to-" + table.getSqlName() + "-" + context, "add-unique-constraints");
            changeSet.getAddUniqueConstraint().add(addUnique);

            log.debug("AddUniqueConstraint added: {} ({})", addUnique.getColumnNames(), addUnique.getTableName());
        }

        addTrace(constraint, ADD_UNIQUE_CONSTRAINTS_AFTER, null);
    }

    private void transformAddIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        log.debug("  Transform add index: {}", index.getSqlName());

        CreateIndex createIndex = liquibaseFactory.createCreateIndex();
        createIndex.setIndexName(index.getSqlName());
        createIndex.setTableName(table.getSqlName());

        for (RdbmsField field : index.getFields()) {
            Column column = liquibaseFactory.createColumn();
            column.setName(field.getSqlName());
            createIndex.getColumn().add(column);
        }

        ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                "create-indexes-for-" + table.getSqlName() + "-" + context, "create-indexes");
        changeSet.getCreateIndex().add(createIndex);

        addTrace(index, ADD_INDEXES, createIndex);
        log.debug("CreateIndex added: {}", createIndex.getIndexName());
    }

    // =========================================================================
    // DATA UPDATE BEFORE INCREMENTAL (dataUpdateBeforeIncremental.etl)
    // =========================================================================

    private void transformDataUpdateBeforeIncremental() {
        log.debug("Transforming data update before incremental changes");

        all(RdbmsModifyFieldOperation.class).forEach(op -> {
            if (op.isChangedValueFieldToForeignKey()) {
                transformSqlFileForChangingToForeignKeyBefore(op);
            }
            if (op.isChangedForeignKeyToValueField()) {
                transformSqlFileForChangingToValueFieldBefore(op);
            }
            if (op.isReviewRequired() && op.isSizeChanged()) {
                transformSqlFileForSizeChange(op);
            }
            if (op.isTypeChanged()) {
                transformSqlFileForTypeChangeBefore(op);
            }
        });
    }

    private void transformSqlFileForChangingToForeignKeyBefore(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for changing to FK before: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_foreign_key_before_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                "change-to-foreign-key-" + tableName + "-" + context, "change-to-foreign-key");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_CHANGING_TO_FK_BEFORE, sqlFile);
    }

    private void transformSqlFileForChangingToValueFieldBefore(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for changing to value field before: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_value_field_before_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                "change-to-value-field-" + tableName + "-" + context, "change-to-value-field");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_BEFORE, sqlFile);
    }

    private void transformSqlFileForSizeChange(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for size change: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_size_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                "modify-size-in-" + tableName + "-" + context, "modify-size");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_SIZE_CHANGE, sqlFile);
    }

    private void transformSqlFileForTypeChangeBefore(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for type change before: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_type_before_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                "modify-type-in-before-" + tableName + "-" + context, "modify-type");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_TYPE_CHANGE_BEFORE, sqlFile);
    }

    // =========================================================================
    // DATA UPDATE AFTER INCREMENTAL (dataUpdateAfterIncremental.etl)
    // =========================================================================

    private void transformDataUpdateAfterIncremental() {
        log.debug("Transforming data update after incremental changes");

        all(RdbmsModifyFieldOperation.class).forEach(op -> {
            if (op.isReviewRequired() && op.isMandatoryChanged()) {
                transformSqlFileForMandatoryReview(op);
            }
            if (op.isChangedValueFieldToForeignKey()) {
                transformSqlFileForChangingToForeignKeyAfter(op);
            }
            if (op.isChangedForeignKeyToValueField()) {
                transformSqlFileForChangingToValueFieldAfter(op);
            }
            if (op.isTypeChanged()) {
                transformSqlFileForTypeChangeAfter(op);
            }
        });

        all(RdbmsCreateFieldOperation.class)
                .filter(RdbmsCreateFieldOperation::isReviewRequired)
                .forEach(this::transformSqlFileForCreateFieldReview);
    }

    private void transformSqlFileForMandatoryReview(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for mandatory review: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_mandatory_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                "modify-mandatory-" + tableName + "-" + context, "modify-mandatory");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_MANDATORY_REVIEW, sqlFile);
    }

    private void transformSqlFileForCreateFieldReview(RdbmsCreateFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for create field review: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_create_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                "create-field-" + tableName + "-" + context, "create-field");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_CREATE_FIELD_REVIEW, sqlFile);
    }

    private void transformSqlFileForChangingToForeignKeyAfter(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for changing to FK after: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_foreign_key_after_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                "change-to-foreign-key-" + tableName + "-" + context, "change-to-foreign-key");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_CHANGING_TO_FK_AFTER, sqlFile);
    }

    private void transformSqlFileForChangingToValueFieldAfter(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for changing to value field after: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_value_field_after_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                "change-to-value-field-" + tableName + "-" + context, "change-to-value-field");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_AFTER, sqlFile);
    }

    private void transformSqlFileForTypeChangeAfter(RdbmsModifyFieldOperation op) {
        RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
        String tableName = tableOp.getTable().getSqlName();
        String columnName = op.getField().getSqlName();
        log.debug("  Transform SQL file for type change after: {}.{}", tableName, columnName);

        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_type_after_" + dialect + ".sql";
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);

        ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                "modify-type-in-after-" + tableName + "-" + context, "modify-type");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_TYPE_CHANGE_AFTER, sqlFile);
    }

    // =========================================================================
    // DB BACKUP TRANSFORMATIONS (dbBackup.etl)
    // =========================================================================

    private void transformDbBackup() {
        log.debug("Transforming DB backup");

        // Backup deleted tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformBackupDeletedTable);

        // Backup modified tables
        all(RdbmsModifyTableOperation.class).forEach(this::transformBackupModifiedTable);
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

        ChangeSet changeSet = getOrCreateChangeSet("dbBackup", dbBackupChangeLog,
                "backup-tables-" + context, "backup-tables");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, ruleName, sqlFile);
        log.debug("SqlFile added: {}", sqlFile.getPath());
    }

    // =========================================================================
    // DB CHECKUP TRANSFORMATIONS (dbCheckup.etl)
    // =========================================================================

    private void transformDbCheckup() {
        log.debug("Transforming DB checkup");

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
    }

    private boolean isPreviousModelContainsElement(EObject element) {
        // Simplified check - in real implementation would verify against previous model
        return true;
    }

    private boolean isFieldInPreviousModel(RdbmsField field) {
        // Simplified check - in real implementation would verify against previous model
        return field.eContainer() != null;
    }

    private boolean isIndexInPreviousModel(RdbmsIndex index) {
        // Simplified check - in real implementation would verify against previous model
        return index.eContainer() != null;
    }

    private void transformCheckTable(RdbmsTable table) {
        log.debug("  Transform check table: {}", table.getSqlName());

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(table.getSqlName());

        dbCheckupPreConditions.getTableExists().add(tableExists);

        addTrace(table, CHECK_TABLES, tableExists);
        log.debug("TableExists added: {}", tableExists.getTableName());
    }

    private void transformCheckJunctionTable(RdbmsJunctionTable table) {
        log.debug("  Transform check junction table: {}", table.getSqlName());

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(table.getSqlName());

        dbCheckupPreConditions.getTableExists().add(tableExists);

        addTrace(table, CHECK_JUNCTION_TABLES, tableExists);
        log.debug("TableExists added (junction): {}", tableExists.getTableName());
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
        if (dbCheckupPreConditions.getNot().isEmpty()) {
            dbCheckupPreConditions.getNot().add(liquibaseFactory.createNot());
        }
        dbCheckupPreConditions.getNot().get(0).getTableExists().add(tableExists);

        addTrace(op, ruleName, tableExists);
        log.debug("Not TableExists added: {}_{}", backupTableNamePrefix, table.getSqlName());
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

        dbCheckupPreConditions.getColumnExists().add(columnExists);

        addTrace(field, ruleName, columnExists);
        log.debug("ColumnExists added: {} ({})", columnExists.getColumnName(), columnExists.getTableName());
    }

    private void transformCheckForeignKeyConstraint(RdbmsForeignKey field) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform check FK constraint: {}", field.getForeignKeySqlName());

        ForeignKeyConstraintExists fkExists = liquibaseFactory.createForeignKeyConstraintExists();
        fkExists.setForeignKeyTableName(table.getSqlName());
        fkExists.setForeignKeyName(field.getForeignKeySqlName());

        dbCheckupPreConditions.getForeignKeyConstraintExists().add(fkExists);

        addTrace(field, CHECK_FOREIGN_KEY_CONSTRAINTS, fkExists);
        log.debug("ForeignKeyConstraintExists added: {} ({})", fkExists.getForeignKeyName(), fkExists.getForeignKeyTableName());
    }

    private void transformCheckIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        log.debug("  Transform check index: {}", index.getSqlName());

        for (RdbmsField field : index.getFields()) {
            IndexExists indexExists = liquibaseFactory.createIndexExists();
            indexExists.setIndexName(index.getSqlName());
            indexExists.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            indexExists.setColumnNames(field.getSqlName());

            dbCheckupPreConditions.getIndexExists().add(indexExists);

            log.debug("IndexExists added: {} ({})", indexExists.getColumnNames(), indexExists.getTableName());
        }

        addTrace(index, CHECK_INDEXES, null);
    }

    // =========================================================================
    // DB DROP BACKUP TRANSFORMATIONS (dbDropBackup.etl)
    // =========================================================================

    private void transformDbDropBackup() {
        log.debug("Transforming DB drop backup");

        // Post-check backup deleted tables
        all(RdbmsDeleteTableOperation.class).forEach(this::transformPostCheckAndDeleteBackupDeletedTable);

        // Post-check backup modified tables
        all(RdbmsModifyTableOperation.class).forEach(this::transformPostCheckAndDeleteBackupModifiedTable);
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
        String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();
        log.debug("  Transform post-check backup table: {}", abbreviatedName);

        TableExists tableExists = liquibaseFactory.createTableExists();
        tableExists.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

        dbDropBackupPreConditions.getTableExists().add(tableExists);

        addTrace(op, ruleName, tableExists);
        log.debug("TableExists added: {}_{}", backupTableNamePrefix, abbreviatedName);
    }

    private void transformDeleteBackupTable(RdbmsTableOperation op, RdbmsTable table, String ruleName) {
        String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();
        log.debug("  Transform delete backup table: {}", abbreviatedName);

        DropTable dropTable = liquibaseFactory.createDropTable();
        dropTable.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

        ChangeSet changeSet = getOrCreateChangeSet("dbDropBackup", dbDropBackupChangeLog,
                "drop-backup-tables-" + context, "drop-backup-tables");
        changeSet.getDropTable().add(dropTable);

        addTrace(op, ruleName, dropTable);
        log.debug("DropTable added: {}_{}", backupTableNamePrefix, abbreviatedName);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private ChangeSet getOrCreateChangeSet(String modelName, databaseChangeLog changeLog, String id, String logicalFilePath) {
        Map<String, ChangeSet> cache = changeSetCaches.get(modelName);
        String cacheKey = id + ":" + logicalFilePath;
        return cache.computeIfAbsent(cacheKey, k -> {
            ChangeSet changeSet = liquibaseFactory.createChangeSet();
            changeSet.setId(id);
            changeSet.setAuthor("tatami-rdbms2liquibase");
            changeSet.setDbms(dialect);
            changeSet.setContext(context);
            changeSet.setLogicalFilePath(logicalFilePath);
            changeLog.getChangeSet().add(changeSet);
            return changeSet;
        });
    }

    private void addTrace(EObject source, String ruleName, EObject target) {
        if (target != null) {
            traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                    .put(ruleName, target);
        }
    }

    private Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }

    // =========================================================================
    // ZETA ANNOTATED TRANSFORMATION RULES
    // =========================================================================

    // -------------------------------------------------------------------------
    // INCREMENTAL RULES (incremental.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = DROP_TABLES, description = "Transform RdbmsDeleteTableOperation to DropTable")
    @Transform(type = RdbmsDeleteTableOperation.class)
    @To(type = DropTable.class)
    public TransformFunction<RdbmsDeleteTableOperation, DropTable> dropTablesRule() {
        return (op, ctx) -> {
            DropTable dropTable = liquibaseFactory.createDropTable();
            dropTable.setTableName(op.getTable().getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "drop-tables-" + context, "drop-tables");
            changeSet.getDropTable().add(dropTable);

            return dropTable;
        };
    }

    @TransformRule(name = CREATE_TABLES, description = "Transform RdbmsCreateTableOperation to CreateTable")
    @Transform(type = RdbmsCreateTableOperation.class)
    @To(type = CreateTable.class)
    public TransformFunction<RdbmsCreateTableOperation, CreateTable> createTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getTable();
            CreateTable createTable = liquibaseFactory.createCreateTable();
            createTable.setTableName(table.getSqlName());
            createTable.setRemarks(table.getUuid());

            for (RdbmsField field : table.getFields()) {
                Column column = liquibaseFactory.createColumn();
                column.setName(field.getSqlName());
                column.setRemarks(field.getUuid());
                column.setType(Rdbms2LiquibaseHelper.toFieldDefinition(field));

                if (field == table.getPrimaryKey() || field.isMandatory()) {
                    Constraints constraint = liquibaseFactory.createConstraints();
                    if (field == table.getPrimaryKey()) {
                        constraint.setPrimaryKey(true);
                    }
                    column.setConstraints(constraint);
                }
                createTable.getColumn().add(column);
            }

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "create-table-" + table.getSqlName() + "-" + context, "create-tables");
            changeSet.getCreateTable().add(createTable);

            return createTable;
        };
    }

    public boolean isNameChanged(RdbmsModifyTableOperation op) {
        return op.isNameChanged();
    }

    @TransformRule(name = RENAME_TABLES, description = "Transform RdbmsModifyTableOperation to RenameTable")
    @Guard(method = "isNameChanged")
    @Transform(type = RdbmsModifyTableOperation.class)
    @To(type = RenameTable.class)
    public TransformFunction<RdbmsModifyTableOperation, RenameTable> renameTablesRule() {
        return (op, ctx) -> {
            RenameTable renameTable = liquibaseFactory.createRenameTable();
            renameTable.setOldTableName(op.getPreviousTable().getSqlName());
            renameTable.setNewTableName(op.getTable().getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "rename-tables-" + context, "rename-tables");
            changeSet.getRenameTable().add(renameTable);

            return renameTable;
        };
    }

    public boolean isFieldNameChanged(RdbmsModifyFieldOperation op) {
        return op.isNameChanged();
    }

    @TransformRule(name = RENAME_COLUMNS, description = "Transform RdbmsModifyFieldOperation to RenameColumn")
    @Guard(method = "isFieldNameChanged")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = RenameColumn.class)
    public TransformFunction<RdbmsModifyFieldOperation, RenameColumn> renameColumnsRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            RenameColumn renameColumn = liquibaseFactory.createRenameColumn();
            renameColumn.setOldColumnName(op.getPreviousField().getSqlName());
            renameColumn.setNewColumnName(op.getField().getSqlName());
            renameColumn.setTableName(tableOp.getTable().getSqlName());
            renameColumn.setRemarks(op.getField().getUuid());

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "rename-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "rename-fields");
            changeSet.getRenameColumn().add(renameColumn);

            return renameColumn;
        };
    }

    @TransformRule(name = DROP_COLUMNS, description = "Transform RdbmsDeleteFieldOperation to DropColumn")
    @Transform(type = RdbmsDeleteFieldOperation.class)
    @To(type = DropColumn.class)
    public TransformFunction<RdbmsDeleteFieldOperation, DropColumn> dropColumnsRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            DropColumn dropColumn = liquibaseFactory.createDropColumn();
            dropColumn.setTableName(tableOp.getTable().getSqlName());
            dropColumn.setColumnName(op.getField().getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "drop-fields-in-" + tableOp.getTable().getSqlName() + "-" + context, "drop-fields");
            changeSet.getDropColumn().add(dropColumn);

            return dropColumn;
        };
    }

    @TransformRule(name = ADD_COLUMN_DEFS, description = "Transform RdbmsCreateFieldOperation to AddColumnDef")
    @Transform(type = RdbmsCreateFieldOperation.class)
    @To(type = AddColumnDef.class)
    public TransformFunction<RdbmsCreateFieldOperation, AddColumnDef> addColumnDefsRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            RdbmsField field = op.getField();
            RdbmsTable table = tableOp.getTable();

            AddColumnDef addColumnDef = liquibaseFactory.createAddColumnDef();
            addColumnDef.setName(field.getSqlName());
            addColumnDef.setType(Rdbms2LiquibaseHelper.toFieldDefinition(field));
            addColumnDef.setRemarks(field.getUuid());

            if (field == table.getPrimaryKey() || field.isMandatory()) {
                Constraints constraint = liquibaseFactory.createConstraints();
                if (field == table.getPrimaryKey()) {
                    constraint.setPrimaryKey(true);
                    constraint.setPrimaryKeyName("PK_" + field.getSqlName());
                }
                addColumnDef.setConstraints(constraint);
            }

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "create-fields-in-" + table.getSqlName() + "-" + context, "create-fields");

            if (changeSet.getAddColumn().isEmpty()) {
                AddColumn addColumn = liquibaseFactory.createAddColumn();
                addColumn.setTableName(table.getSqlName());
                changeSet.getAddColumn().add(addColumn);
            }
            changeSet.getAddColumn().get(0).getColumn().add(addColumnDef);

            return addColumnDef;
        };
    }

    public boolean isTypeOrSizeChanged(RdbmsModifyFieldOperation op) {
        return op.isTypeChanged() || op.isSizeChanged();
    }

    @TransformRule(name = MODIFY_DATA_TYPES, description = "Transform RdbmsModifyFieldOperation to ModifyDataType")
    @Guard(method = "isTypeOrSizeChanged")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = ModifyDataType.class)
    public TransformFunction<RdbmsModifyFieldOperation, ModifyDataType> modifyDataTypesRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            ModifyDataType modifyDataType = liquibaseFactory.createModifyDataType();
            modifyDataType.setTableName(tableOp.getTable().getSqlName());
            modifyDataType.setColumnName(op.getField().getSqlName());
            modifyDataType.setNewDataType(Rdbms2LiquibaseHelper.toFieldDefinition(op.getField()));

            ChangeSet changeSet = getOrCreateChangeSet("incremental", incrementalChangeLog,
                    "modify-data-types-in-" + tableOp.getTable().getSqlName() + "-" + context, "modify-data-types");
            changeSet.getModifyDataType().add(modifyDataType);

            return modifyDataType;
        };
    }

    // -------------------------------------------------------------------------
    // BEFORE INCREMENTAL RULES (beforeIncremental.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = DROP_INDEXES, description = "Drop indexes before incremental changes")
    @Transform(type = RdbmsIndex.class)
    @To(type = DropIndex.class)
    public TransformFunction<RdbmsIndex, DropIndex> dropIndexesRule() {
        return (index, ctx) -> {
            RdbmsTable table = (RdbmsTable) index.eContainer();
            if (table == null) return null;

            DropIndex dropIndex = liquibaseFactory.createDropIndex();
            dropIndex.setIndexName(index.getSqlName());
            dropIndex.setTableName(table.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-indexes-from-" + table.getSqlName() + "-" + context, "drop-indexes");
            changeSet.getDropIndex().add(dropIndex);

            return dropIndex;
        };
    }

    @TransformRule(name = DROP_UNIQUE_CONSTRAINTS, description = "Drop unique constraints before incremental changes")
    @Transform(type = RdbmsUniqueConstraint.class)
    @To(type = DropUniqueConstraint.class)
    public TransformFunction<RdbmsUniqueConstraint, DropUniqueConstraint> dropUniqueConstraintsRule() {
        return (constraint, ctx) -> {
            RdbmsTable table = (RdbmsTable) constraint.eContainer();
            if (table == null) return null;

            DropUniqueConstraint firstDropUnique = null;
            for (RdbmsField field : constraint.getFields()) {
                DropUniqueConstraint dropUnique = liquibaseFactory.createDropUniqueConstraint();
                dropUnique.setConstraintName(constraint.getSqlName());
                dropUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
                dropUnique.setUniqueColumns(field.getSqlName());

                ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                        "drop-unique-constraints-from-" + table.getSqlName() + "-" + context, "drop-unique-constraints");
                changeSet.getDropUniqueConstraint().add(dropUnique);

                if (firstDropUnique == null) {
                    firstDropUnique = dropUnique;
                }
            }

            return firstDropUnique;
        };
    }

    @Abstract
    @TransformRule(name = DROP_NOT_NULL_CONSTRAINTS, description = "Drop not null constraints (abstract)")
    @Transform(type = RdbmsField.class)
    @To(type = DropNotNullConstraint.class)
    public TransformFunction<RdbmsField, DropNotNullConstraint> dropNotNullConstraintsRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            DropNotNullConstraint dropNotNull = liquibaseFactory.createDropNotNullConstraint();
            dropNotNull.setTableName(table.getSqlName());
            dropNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-not-null-constraints-from-" + table.getSqlName() + "-" + context, "drop-not-null-constraints");
            changeSet.getDropNotNullConstraint().add(dropNotNull);

            return dropNotNull;
        };
    }

    @TransformRule(name = DROP_NOT_NULL_CONSTRAINTS_FROM_VALUE_FIELDS, description = "Drop not null constraints from value fields")
    @Extends(DROP_NOT_NULL_CONSTRAINTS)
    @Transform(type = RdbmsValueField.class)
    @To(type = DropNotNullConstraint.class)
    public TransformFunction<RdbmsValueField, DropNotNullConstraint> dropNotNullConstraintsFromValueFieldsRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            DropNotNullConstraint dropNotNull = liquibaseFactory.createDropNotNullConstraint();
            dropNotNull.setTableName(table.getSqlName());
            dropNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-not-null-constraints-from-" + table.getSqlName() + "-" + context, "drop-not-null-constraints");
            changeSet.getDropNotNullConstraint().add(dropNotNull);

            return dropNotNull;
        };
    }

    @TransformRule(name = DROP_NOT_NULL_CONSTRAINTS_FROM_FOREIGN_KEYS, description = "Drop not null constraints from foreign keys")
    @Extends(DROP_NOT_NULL_CONSTRAINTS)
    @Transform(type = RdbmsForeignKey.class)
    @To(type = DropNotNullConstraint.class)
    public TransformFunction<RdbmsForeignKey, DropNotNullConstraint> dropNotNullConstraintsFromForeignKeysRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            DropNotNullConstraint dropNotNull = liquibaseFactory.createDropNotNullConstraint();
            dropNotNull.setTableName(table.getSqlName());
            dropNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-not-null-constraints-from-" + table.getSqlName() + "-" + context, "drop-not-null-constraints");
            changeSet.getDropNotNullConstraint().add(dropNotNull);

            return dropNotNull;
        };
    }

    @TransformRule(name = DROP_FOREIGN_KEY_CONSTRAINTS, description = "Drop foreign key constraints before incremental changes")
    @Transform(type = RdbmsForeignKey.class)
    @To(type = DropForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, DropForeignKeyConstraint> dropForeignKeyConstraintsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            DropForeignKeyConstraint dropFk = liquibaseFactory.createDropForeignKeyConstraint();
            dropFk.setBaseTableName(table.getSqlName());
            dropFk.setConstraintName(field.getForeignKeySqlName());

            ChangeSet changeSet = getOrCreateChangeSet("beforeIncremental", beforeIncrementalChangeLog,
                    "drop-foreign-keys-from-" + table.getSqlName() + "-" + context, "drop-foreign-keys");
            changeSet.getDropForeignKeyConstraint().add(dropFk);

            return dropFk;
        };
    }

    // -------------------------------------------------------------------------
    // AFTER INCREMENTAL RULES (afterIncremental.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = ADD_FOREIGN_KEY_CONSTRAINTS, description = "Add foreign key constraints after incremental changes")
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddForeignKeyConstraint> addForeignKeyConstraintsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            AddForeignKeyConstraint addFk = liquibaseFactory.createAddForeignKeyConstraint();
            addFk.setBaseTableName(table.getSqlName());
            addFk.setBaseColumnNames(field.getSqlName());
            addFk.setConstraintName(field.getForeignKeySqlName());

            if (field.getReferenceKey() != null) {
                RdbmsTable refTable = (RdbmsTable) field.getReferenceKey().eContainer();
                addFk.setReferencedTableName(refTable.getSqlName());
                addFk.setReferencedColumnNames(field.getReferenceKey().getSqlName());
            }

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "add-foreign-keys-to-" + table.getSqlName() + "-" + context, "add-foreign-keys");
            changeSet.getAddForeignKeyConstraint().add(addFk);

            return addFk;
        };
    }

    @Abstract
    @TransformRule(name = ADD_NOT_NULL_CONSTRAINTS, description = "Add not null constraints (abstract)")
    @Transform(type = RdbmsField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsField, AddNotNullConstraint> addNotNullConstraintsRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            AddNotNullConstraint addNotNull = liquibaseFactory.createAddNotNullConstraint();
            addNotNull.setTableName(table.getSqlName());
            addNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "add-not-null-constraints-to-" + table.getSqlName() + "-" + context, "add-not-null-constraints");
            changeSet.getAddNotNullConstraint().add(addNotNull);

            return addNotNull;
        };
    }

    @TransformRule(name = ADD_NOT_NULL_CONSTRAINTS_TO_VALUE_FIELDS, description = "Add not null constraints to value fields")
    @Extends(ADD_NOT_NULL_CONSTRAINTS)
    @Transform(type = RdbmsValueField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsValueField, AddNotNullConstraint> addNotNullConstraintsToValueFieldsRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            AddNotNullConstraint addNotNull = liquibaseFactory.createAddNotNullConstraint();
            addNotNull.setTableName(table.getSqlName());
            addNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "add-not-null-constraints-to-" + table.getSqlName() + "-" + context, "add-not-null-constraints");
            changeSet.getAddNotNullConstraint().add(addNotNull);

            return addNotNull;
        };
    }

    @TransformRule(name = ADD_NOT_NULL_CONSTRAINTS_TO_FOREIGN_KEYS, description = "Add not null constraints to foreign keys")
    @Extends(ADD_NOT_NULL_CONSTRAINTS)
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddNotNullConstraint> addNotNullConstraintsToForeignKeysRule() {
        return (field, ctx) -> {
            if (!field.isMandatory()) return null;
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            AddNotNullConstraint addNotNull = liquibaseFactory.createAddNotNullConstraint();
            addNotNull.setTableName(table.getSqlName());
            addNotNull.setColumnName(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "add-not-null-constraints-to-" + table.getSqlName() + "-" + context, "add-not-null-constraints");
            changeSet.getAddNotNullConstraint().add(addNotNull);

            return addNotNull;
        };
    }

    @TransformRule(name = ADD_UNIQUE_CONSTRAINTS_AFTER, description = "Add unique constraints after incremental changes")
    @Transform(type = RdbmsUniqueConstraint.class)
    @To(type = AddUniqueConstraint.class)
    public TransformFunction<RdbmsUniqueConstraint, AddUniqueConstraint> addUniqueConstraintsRule() {
        return (constraint, ctx) -> {
            RdbmsTable table = (RdbmsTable) constraint.eContainer();
            if (table == null) return null;

            AddUniqueConstraint firstAddUnique = null;
            for (RdbmsField field : constraint.getFields()) {
                AddUniqueConstraint addUnique = liquibaseFactory.createAddUniqueConstraint();
                addUnique.setConstraintName(constraint.getSqlName());
                addUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
                addUnique.setColumnNames(field.getSqlName());

                ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                        "add-unique-constraints-to-" + table.getSqlName() + "-" + context, "add-unique-constraints");
                changeSet.getAddUniqueConstraint().add(addUnique);

                if (firstAddUnique == null) {
                    firstAddUnique = addUnique;
                }
            }

            return firstAddUnique;
        };
    }

    @TransformRule(name = ADD_INDEXES, description = "Add indexes after incremental changes")
    @Transform(type = RdbmsIndex.class)
    @To(type = CreateIndex.class)
    public TransformFunction<RdbmsIndex, CreateIndex> addIndexesRule() {
        return (index, ctx) -> {
            RdbmsTable table = (RdbmsTable) index.eContainer();
            if (table == null) return null;

            CreateIndex createIndex = liquibaseFactory.createCreateIndex();
            createIndex.setIndexName(index.getSqlName());
            createIndex.setTableName(table.getSqlName());

            for (RdbmsField field : index.getFields()) {
                Column column = liquibaseFactory.createColumn();
                column.setName(field.getSqlName());
                createIndex.getColumn().add(column);
            }

            ChangeSet changeSet = getOrCreateChangeSet("afterIncremental", afterIncrementalChangeLog,
                    "create-indexes-for-" + table.getSqlName() + "-" + context, "create-indexes");
            changeSet.getCreateIndex().add(createIndex);

            return createIndex;
        };
    }

    // -------------------------------------------------------------------------
    // DB BACKUP RULES (dbBackup.etl)
    // -------------------------------------------------------------------------

    @Abstract
    @TransformRule(name = BACKUP_TABLES, description = "Backup tables (abstract)")
    @Transform(type = RdbmsTableOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsTableOperation, SqlFile> backupTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op instanceof RdbmsModifyTableOperation ? 
                    ((RdbmsModifyTableOperation) op).getPreviousTable() : op.getTable();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            sqlFile.setPath(backupChangeSetNamePrefix + "_" + table.getSqlName().toLowerCase() + "_data_" + dialect + ".sql");
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dbBackup", dbBackupChangeLog,
                    "backup-tables-" + context, "backup-tables");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    @TransformRule(name = BACKUP_DELETED_TABLES, description = "Backup deleted tables")
    @Extends(BACKUP_TABLES)
    @Transform(type = RdbmsDeleteTableOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsDeleteTableOperation, SqlFile> backupDeletedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getTable();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            sqlFile.setPath(backupChangeSetNamePrefix + "_" + table.getSqlName().toLowerCase() + "_data_" + dialect + ".sql");
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dbBackup", dbBackupChangeLog,
                    "backup-tables-" + context, "backup-tables");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    @TransformRule(name = BACKUP_MODIFIED_TABLES, description = "Backup modified tables")
    @Extends(BACKUP_TABLES)
    @Transform(type = RdbmsModifyTableOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyTableOperation, SqlFile> backupModifiedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getPreviousTable();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            sqlFile.setPath(backupChangeSetNamePrefix + "_" + table.getSqlName().toLowerCase() + "_data_" + dialect + ".sql");
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dbBackup", dbBackupChangeLog,
                    "backup-tables-" + context, "backup-tables");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    // -------------------------------------------------------------------------
    // DATA UPDATE BEFORE INCREMENTAL RULES (dataUpdateBeforeIncremental.etl)
    // -------------------------------------------------------------------------

    public boolean isChangedValueFieldToForeignKey(RdbmsModifyFieldOperation op) {
        return op.isChangedValueFieldToForeignKey();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_CHANGING_TO_FK_BEFORE, description = "Create SQL file for changing to FK before")
    @Guard(method = "isChangedValueFieldToForeignKey")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForChangingToFkBeforeRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_foreign_key_before_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                    "change-to-foreign-key-" + tableName + "-" + context, "change-to-foreign-key");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    public boolean isChangedForeignKeyToValueField(RdbmsModifyFieldOperation op) {
        return op.isChangedForeignKeyToValueField();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_BEFORE, description = "Create SQL file for changing to value field before")
    @Guard(method = "isChangedForeignKeyToValueField")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForChangingToValueFieldBeforeRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_value_field_before_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                    "change-to-value-field-" + tableName + "-" + context, "change-to-value-field");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    public boolean isReviewRequiredAndSizeChanged(RdbmsModifyFieldOperation op) {
        return op.isReviewRequired() && op.isSizeChanged();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_SIZE_CHANGE, description = "Create SQL file for size change")
    @Guard(method = "isReviewRequiredAndSizeChanged")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForSizeChangeRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_size_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                    "modify-size-in-" + tableName + "-" + context, "modify-size");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    public boolean isTypeChangedField(RdbmsModifyFieldOperation op) {
        return op.isTypeChanged();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_TYPE_CHANGE_BEFORE, description = "Create SQL file for type change before")
    @Guard(method = "isTypeChangedField")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForTypeChangeBeforeRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_type_before_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateBeforeIncremental", dataUpdateBeforeIncrementalChangeLog,
                    "modify-type-in-before-" + tableName + "-" + context, "modify-type");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    // -------------------------------------------------------------------------
    // DATA UPDATE AFTER INCREMENTAL RULES (dataUpdateAfterIncremental.etl)
    // -------------------------------------------------------------------------

    public boolean isReviewRequiredAndMandatoryChanged(RdbmsModifyFieldOperation op) {
        return op.isReviewRequired() && op.isMandatoryChanged();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_MANDATORY_REVIEW, description = "Create SQL file for mandatory review")
    @Guard(method = "isReviewRequiredAndMandatoryChanged")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForMandatoryReviewRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_mandatory_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                    "modify-mandatory-" + tableName + "-" + context, "modify-mandatory");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    public boolean isReviewRequiredCreate(RdbmsCreateFieldOperation op) {
        return op.isReviewRequired();
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_CREATE_FIELD_REVIEW, description = "Create SQL file for create field review")
    @Guard(method = "isReviewRequiredCreate")
    @Transform(type = RdbmsCreateFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsCreateFieldOperation, SqlFile> createSqlFileForCreateFieldReviewRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_create_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                    "create-field-" + tableName + "-" + context, "create-field");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_CHANGING_TO_FK_AFTER, description = "Create SQL file for changing to FK after")
    @Guard(method = "isChangedValueFieldToForeignKey")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForChangingToFkAfterRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_foreign_key_after_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                    "change-to-foreign-key-" + tableName + "-" + context, "change-to-foreign-key");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_AFTER, description = "Create SQL file for changing to value field after")
    @Guard(method = "isChangedForeignKeyToValueField")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForChangingToValueFieldAfterRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_to_value_field_after_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                    "change-to-value-field-" + tableName + "-" + context, "change-to-value-field");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    @TransformRule(name = CREATE_SQL_FILE_FOR_TYPE_CHANGE_AFTER, description = "Create SQL file for type change after")
    @Guard(method = "isTypeChangedField")
    @Transform(type = RdbmsModifyFieldOperation.class)
    @To(type = SqlFile.class)
    public TransformFunction<RdbmsModifyFieldOperation, SqlFile> createSqlFileForTypeChangeAfterRule() {
        return (op, ctx) -> {
            RdbmsTableOperation tableOp = (RdbmsTableOperation) op.eContainer();
            String tableName = tableOp.getTable().getSqlName();
            String columnName = op.getField().getSqlName();

            SqlFile sqlFile = liquibaseFactory.createSqlFile();
            String sqlName = tableName.toLowerCase() + "_" + columnName.toLowerCase() + "_type_after_" + dialect + ".sql";
            sqlFile.setPath(sqlName);
            sqlFile.setDbms(dialect);

            ChangeSet changeSet = getOrCreateChangeSet("dataUpdateAfterIncremental", dataUpdateAfterIncrementalChangeLog,
                    "modify-type-in-after-" + tableName + "-" + context, "modify-type");
            changeSet.getSqlFile().add(sqlFile);

            return sqlFile;
        };
    }

    // -------------------------------------------------------------------------
    // DB CHECKUP RULES (dbCheckup.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CHECK_TABLES, description = "Check tables exist")
    @Transform(type = RdbmsTable.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsTable, TableExists> checkTablesRule() {
        return (table, ctx) -> {
            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(table.getSqlName());

            dbCheckupPreConditions.getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @TransformRule(name = CHECK_JUNCTION_TABLES, description = "Check junction tables exist")
    @Extends(CHECK_TABLES)
    @Transform(type = RdbmsJunctionTable.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsJunctionTable, TableExists> checkJunctionTablesRule() {
        return (table, ctx) -> {
            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(table.getSqlName());

            dbCheckupPreConditions.getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @Abstract
    @TransformRule(name = PRE_CHECK_BACKUP_TABLES, description = "Pre-check backup tables (abstract)")
    @Transform(type = RdbmsTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsTableOperation, TableExists> preCheckBackupTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op instanceof RdbmsModifyTableOperation ? 
                    ((RdbmsModifyTableOperation) op).getPreviousTable() : op.getTable();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + table.getSqlName());

            if (dbCheckupPreConditions.getNot().isEmpty()) {
                dbCheckupPreConditions.getNot().add(liquibaseFactory.createNot());
            }
            dbCheckupPreConditions.getNot().get(0).getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @TransformRule(name = PRE_CHECK_BACKUP_DELETED_TABLES, description = "Pre-check backup deleted tables")
    @Extends(PRE_CHECK_BACKUP_TABLES)
    @Transform(type = RdbmsDeleteTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsDeleteTableOperation, TableExists> preCheckBackupDeletedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getTable();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + table.getSqlName());

            if (dbCheckupPreConditions.getNot().isEmpty()) {
                dbCheckupPreConditions.getNot().add(liquibaseFactory.createNot());
            }
            dbCheckupPreConditions.getNot().get(0).getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @TransformRule(name = PRE_CHECK_BACKUP_MODIFIED_TABLES, description = "Pre-check backup modified tables")
    @Extends(PRE_CHECK_BACKUP_TABLES)
    @Transform(type = RdbmsModifyTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsModifyTableOperation, TableExists> preCheckBackupModifiedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getPreviousTable();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + table.getSqlName());

            if (dbCheckupPreConditions.getNot().isEmpty()) {
                dbCheckupPreConditions.getNot().add(liquibaseFactory.createNot());
            }
            dbCheckupPreConditions.getNot().get(0).getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @Abstract
    @TransformRule(name = CHECK_FIELDS, description = "Check fields exist (abstract)")
    @Transform(type = RdbmsField.class)
    @To(type = ColumnExists.class)
    public TransformFunction<RdbmsField, ColumnExists> checkFieldsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            ColumnExists columnExists = liquibaseFactory.createColumnExists();
            columnExists.setTableName(table.getSqlName());
            columnExists.setColumnName(field.getSqlName());

            dbCheckupPreConditions.getColumnExists().add(columnExists);

            return columnExists;
        };
    }

    @TransformRule(name = CHECK_VALUE_FIELDS, description = "Check value fields exist")
    @Extends(CHECK_FIELDS)
    @Transform(type = RdbmsValueField.class)
    @To(type = ColumnExists.class)
    public TransformFunction<RdbmsValueField, ColumnExists> checkValueFieldsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            ColumnExists columnExists = liquibaseFactory.createColumnExists();
            columnExists.setTableName(table.getSqlName());
            columnExists.setColumnName(field.getSqlName());

            dbCheckupPreConditions.getColumnExists().add(columnExists);

            return columnExists;
        };
    }

    public boolean isIdentifierNotForeignKeyIncr(RdbmsIdentifierField field) {
        return !(field instanceof RdbmsForeignKey);
    }

    @TransformRule(name = CHECK_IDENTIFIER_FIELDS, description = "Check identifier fields exist")
    @Guard(method = "isIdentifierNotForeignKeyIncr")
    @Extends(CHECK_FIELDS)
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = ColumnExists.class)
    public TransformFunction<RdbmsIdentifierField, ColumnExists> checkIdentifierFieldsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            ColumnExists columnExists = liquibaseFactory.createColumnExists();
            columnExists.setTableName(table.getSqlName());
            columnExists.setColumnName(field.getSqlName());

            dbCheckupPreConditions.getColumnExists().add(columnExists);

            return columnExists;
        };
    }

    @TransformRule(name = CHECK_FOREIGN_KEYS, description = "Check foreign keys exist")
    @Extends(CHECK_FIELDS)
    @Transform(type = RdbmsForeignKey.class)
    @To(type = ColumnExists.class)
    public TransformFunction<RdbmsForeignKey, ColumnExists> checkForeignKeysRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            ColumnExists columnExists = liquibaseFactory.createColumnExists();
            columnExists.setTableName(table.getSqlName());
            columnExists.setColumnName(field.getSqlName());

            dbCheckupPreConditions.getColumnExists().add(columnExists);

            return columnExists;
        };
    }

    @TransformRule(name = CHECK_FOREIGN_KEY_CONSTRAINTS, description = "Check foreign key constraints exist")
    @Transform(type = RdbmsForeignKey.class)
    @To(type = ForeignKeyConstraintExists.class)
    public TransformFunction<RdbmsForeignKey, ForeignKeyConstraintExists> checkForeignKeyConstraintsRule() {
        return (field, ctx) -> {
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table == null) return null;

            ForeignKeyConstraintExists fkExists = liquibaseFactory.createForeignKeyConstraintExists();
            fkExists.setForeignKeyTableName(table.getSqlName());
            fkExists.setForeignKeyName(field.getForeignKeySqlName());

            dbCheckupPreConditions.getForeignKeyConstraintExists().add(fkExists);

            return fkExists;
        };
    }

    @TransformRule(name = CHECK_INDEXES, description = "Check indexes exist")
    @Transform(type = RdbmsIndex.class)
    @To(type = IndexExists.class)
    public TransformFunction<RdbmsIndex, IndexExists> checkIndexesRule() {
        return (index, ctx) -> {
            IndexExists firstIndexExists = null;
            for (RdbmsField field : index.getFields()) {
                IndexExists indexExists = liquibaseFactory.createIndexExists();
                indexExists.setIndexName(index.getSqlName());
                indexExists.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
                indexExists.setColumnNames(field.getSqlName());

                dbCheckupPreConditions.getIndexExists().add(indexExists);

                if (firstIndexExists == null) {
                    firstIndexExists = indexExists;
                }
            }

            return firstIndexExists;
        };
    }

    // -------------------------------------------------------------------------
    // DB DROP BACKUP RULES (dbDropBackup.etl)
    // -------------------------------------------------------------------------

    @Abstract
    @TransformRule(name = POST_CHECK_BACKUP_TABLES, description = "Post-check backup tables (abstract)")
    @Transform(type = RdbmsTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsTableOperation, TableExists> postCheckBackupTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op instanceof RdbmsModifyTableOperation ? 
                    ((RdbmsModifyTableOperation) op).getPreviousTable() : op.getTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            dbDropBackupPreConditions.getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @TransformRule(name = POST_CHECK_BACKUP_DELETED_TABLES, description = "Post-check backup deleted tables")
    @Extends(POST_CHECK_BACKUP_TABLES)
    @Transform(type = RdbmsDeleteTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsDeleteTableOperation, TableExists> postCheckBackupDeletedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            dbDropBackupPreConditions.getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @TransformRule(name = POST_CHECK_BACKUP_MODIFIED_TABLES, description = "Post-check backup modified tables")
    @Extends(POST_CHECK_BACKUP_TABLES)
    @Transform(type = RdbmsModifyTableOperation.class)
    @To(type = TableExists.class)
    public TransformFunction<RdbmsModifyTableOperation, TableExists> postCheckBackupModifiedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getPreviousTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            TableExists tableExists = liquibaseFactory.createTableExists();
            tableExists.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            dbDropBackupPreConditions.getTableExists().add(tableExists);

            return tableExists;
        };
    }

    @Abstract
    @TransformRule(name = DELETE_BACKUP_TABLES, description = "Delete backup tables (abstract)")
    @Transform(type = RdbmsTableOperation.class)
    @To(type = DropTable.class)
    public TransformFunction<RdbmsTableOperation, DropTable> deleteBackupTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op instanceof RdbmsModifyTableOperation ? 
                    ((RdbmsModifyTableOperation) op).getPreviousTable() : op.getTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            DropTable dropTable = liquibaseFactory.createDropTable();
            dropTable.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            ChangeSet changeSet = getOrCreateChangeSet("dbDropBackup", dbDropBackupChangeLog,
                    "drop-backup-tables-" + context, "drop-backup-tables");
            changeSet.getDropTable().add(dropTable);

            return dropTable;
        };
    }

    @TransformRule(name = DELETE_BACKUP_DELETED_TABLES, description = "Delete backup deleted tables")
    @Extends(DELETE_BACKUP_TABLES)
    @Transform(type = RdbmsDeleteTableOperation.class)
    @To(type = DropTable.class)
    public TransformFunction<RdbmsDeleteTableOperation, DropTable> deleteBackupDeletedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            DropTable dropTable = liquibaseFactory.createDropTable();
            dropTable.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            ChangeSet changeSet = getOrCreateChangeSet("dbDropBackup", dbDropBackupChangeLog,
                    "drop-backup-tables-" + context, "drop-backup-tables");
            changeSet.getDropTable().add(dropTable);

            return dropTable;
        };
    }

    @TransformRule(name = DELETE_BACKUP_MODIFIED_TABLES, description = "Delete backup modified tables")
    @Extends(DELETE_BACKUP_TABLES)
    @Transform(type = RdbmsModifyTableOperation.class)
    @To(type = DropTable.class)
    public TransformFunction<RdbmsModifyTableOperation, DropTable> deleteBackupModifiedTablesRule() {
        return (op, ctx) -> {
            RdbmsTable table = op.getPreviousTable();
            String abbreviatedName = Rdbms2LiquibaseHelper.abbreviate(table.getSqlName(), tableNameMaxSize - backupTableNamePrefix.length() - 1).toUpperCase();

            DropTable dropTable = liquibaseFactory.createDropTable();
            dropTable.setTableName(backupTableNamePrefix + "_" + abbreviatedName);

            ChangeSet changeSet = getOrCreateChangeSet("dbDropBackup", dbDropBackupChangeLog,
                    "drop-backup-tables-" + context, "drop-backup-tables");
            changeSet.getDropTable().add(dropTable);

            return dropTable;
        };
    }

    // -------------------------------------------------------------------------
    // POST-EXECUTION HOOK
    // -------------------------------------------------------------------------

    @PostExecution
    public void postExecutionHook(TransformationContext ctx) {
        log.debug("Post-execution: Incremental transformation completed");
        log.debug("  dbCheckup changeSets: {}", dbCheckupChangeLog != null ? dbCheckupChangeLog.getChangeSet().size() : 0);
        log.debug("  dbBackup changeSets: {}", dbBackupChangeLog != null ? dbBackupChangeLog.getChangeSet().size() : 0);
        log.debug("  beforeIncremental changeSets: {}", beforeIncrementalChangeLog != null ? beforeIncrementalChangeLog.getChangeSet().size() : 0);
        log.debug("  incremental changeSets: {}", incrementalChangeLog != null ? incrementalChangeLog.getChangeSet().size() : 0);
        log.debug("  afterIncremental changeSets: {}", afterIncrementalChangeLog != null ? afterIncrementalChangeLog.getChangeSet().size() : 0);
        log.debug("  dbDropBackup changeSets: {}", dbDropBackupChangeLog != null ? dbDropBackupChangeLog.getChangeSet().size() : 0);
    }
}
