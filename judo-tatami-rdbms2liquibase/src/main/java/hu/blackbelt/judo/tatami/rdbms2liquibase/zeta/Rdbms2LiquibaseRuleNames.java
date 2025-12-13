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

/**
 * Rule name constants for RDBMS to Liquibase transformation.
 * These correspond to the ETL rules in rdbmsToLiquibase.etl and related modules.
 */
public final class Rdbms2LiquibaseRuleNames {

    private Rdbms2LiquibaseRuleNames() {
        // Private constructor to prevent instantiation
    }

    // Table transformation rules
    public static final String TABLE_TO_CREATE_TABLE = "TableToCreateTable";
    public static final String TABLE_TO_CREATE_TABLE_CHANGESET = "TableToCreateTableChangeSet";
    public static final String TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET = "TableToCreateForeignKeysChangeSet";
    public static final String TABLE_TO_ADD_NOT_NULL_CHANGESET = "TableToAddNotNullChangeSet";

    // Field transformation rules
    public static final String IDENTIFIER_FIELD_TO_COLUMN = "IdentifierFieldToCreateTableColumn";
    public static final String IDENTIFIER_FIELD_TO_PK_CONSTRAINT = "IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint";
    public static final String VALUE_FIELD_TO_COLUMN = "ValueFieldToCreateTableColumn";
    public static final String FOREIGN_KEY_FIELD_TO_COLUMN = "ForeignKeyFieldToCreateTableColumn";
    public static final String FOREIGN_KEY_TO_ADD_FK_CONSTRAINT = "ForeignKeyFieldToCreateTableAddForeignKeyConstraint";
    public static final String FIELD_TO_ADD_NOT_NULL_CONSTRAINT = "FieldToCreateTableAddNotNullConstraint";

    // Index transformation rules
    public static final String INDEX_TO_CREATE_INDEX = "IndexToCreateIndex";

    // Unique constraint transformation rules
    public static final String UNIQUE_CONSTRAINT_TO_ADD_UNIQUE = "AddUniqueConstraints";

    // =========================================================================
    // FIELD RULES (field.etl) - additional rules
    // =========================================================================
    
    /** Transform RdbmsField to Column (abstract base). */
    public static final String FIELD_TO_COLUMN = "FieldToColumn";
    
    /** Transform RdbmsForeignKey to AddForeignKeyConstraint (abstract). */
    public static final String FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT = "ForeignKeyFieldToAddForeignKeyConstraint";
    
    /** Transform mandatory RdbmsField to AddNotNullConstraint. */
    public static final String FIELD_TO_ADD_NOT_NULL = "FieldToAddNotNullConstraint";

    // =========================================================================
    // INCREMENTAL RULES (incremental.etl)
    // =========================================================================
    
    /** Transform RdbmsDeleteTableOperation to DropTable. */
    public static final String DROP_TABLES = "DropTables";
    
    /** Transform RdbmsCreateTableOperation to CreateTable. */
    public static final String CREATE_TABLES = "CreateTables";
    
    /** Transform RdbmsModifyTableOperation to RenameTable. */
    public static final String RENAME_TABLES = "RanameTables";
    
    /** Transform RdbmsModifyFieldOperation to RenameColumn. */
    public static final String RENAME_COLUMNS = "RenameColumns";
    
    /** Transform RdbmsDeleteFieldOperation to DropColumn. */
    public static final String DROP_COLUMNS = "DropColumns";
    
    /** Transform RdbmsCreateFieldOperation to AddColumnDef. */
    public static final String ADD_COLUMN_DEFS = "AddColumnDefs";
    
    /** Transform RdbmsModifyFieldOperation to ModifyDataType. */
    public static final String MODIFY_DATA_TYPES = "ModifyDataTypes";

    // =========================================================================
    // BEFORE INCREMENTAL RULES (beforeIncremental.etl)
    // =========================================================================
    
    /** Drop indexes before incremental changes. */
    public static final String DROP_INDEXES = "DropIndexes";
    
    /** Drop unique constraints before incremental changes. */
    public static final String DROP_UNIQUE_CONSTRAINTS = "DropUniqueConstraints";
    
    /** Drop not null constraints (abstract). */
    public static final String DROP_NOT_NULL_CONSTRAINTS = "DropNotNullConstraints";
    
    /** Drop not null constraints from value fields. */
    public static final String DROP_NOT_NULL_CONSTRAINTS_FROM_VALUE_FIELDS = "DropNotNullConstraintsFromValueFields";
    
    /** Drop not null constraints from foreign keys. */
    public static final String DROP_NOT_NULL_CONSTRAINTS_FROM_FOREIGN_KEYS = "DropNotNullConstraintsFromForeignKeys";
    
    /** Drop foreign key constraints before incremental changes. */
    public static final String DROP_FOREIGN_KEY_CONSTRAINTS = "DropForeignKeyConstraints";

    // =========================================================================
    // AFTER INCREMENTAL RULES (afterIncremental.etl)
    // =========================================================================
    
    /** Add foreign key constraints after incremental changes. */
    public static final String ADD_FOREIGN_KEY_CONSTRAINTS = "AddForeignKeyConstraints";
    
    /** Add not null constraints (abstract). */
    public static final String ADD_NOT_NULL_CONSTRAINTS = "AddNotNullConstraints";
    
    /** Add not null constraints to value fields. */
    public static final String ADD_NOT_NULL_CONSTRAINTS_TO_VALUE_FIELDS = "AddNotNullConstraintsToValueFields";
    
    /** Add not null constraints to foreign keys. */
    public static final String ADD_NOT_NULL_CONSTRAINTS_TO_FOREIGN_KEYS = "AddNotNullConstraintsToForeignKeys";
    
    /** Add unique constraints after incremental changes. */
    public static final String ADD_UNIQUE_CONSTRAINTS_AFTER = "AddUniqueConstraints";
    
    /** Add indexes after incremental changes. */
    public static final String ADD_INDEXES = "AddIndexes";

    // =========================================================================
    // DATA UPDATE BEFORE INCREMENTAL RULES (dataUpdateBeforeIncremental.etl)
    // =========================================================================
    
    /** Create SQL file for changing to foreign key before. */
    public static final String CREATE_SQL_FILE_FOR_CHANGING_TO_FK_BEFORE = "CreateSqlFileForChangingToForeignKeyBefore";
    
    /** Create SQL file for changing to value field before. */
    public static final String CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_BEFORE = "CreateSqlFileForChangingToValueFieldBefore";
    
    /** Create SQL file for size change. */
    public static final String CREATE_SQL_FILE_FOR_SIZE_CHANGE = "CreateSqlFileForSizeChange";
    
    /** Create SQL file for type change before. */
    public static final String CREATE_SQL_FILE_FOR_TYPE_CHANGE_BEFORE = "CreateSqlFileForTypeChangeBefore";

    // =========================================================================
    // DATA UPDATE AFTER INCREMENTAL RULES (dataUpdateAfterIncremental.etl)
    // =========================================================================
    
    /** Create SQL file for mandatory review. */
    public static final String CREATE_SQL_FILE_FOR_MANDATORY_REVIEW = "CreateSqlFileForMandatoryReview";
    
    /** Create SQL file for create field review. */
    public static final String CREATE_SQL_FILE_FOR_CREATE_FIELD_REVIEW = "CreateSqlFileForCreateFieldReview";
    
    /** Create SQL file for changing to foreign key after. */
    public static final String CREATE_SQL_FILE_FOR_CHANGING_TO_FK_AFTER = "CreateSqlFileForChangingToForeignKeyAfter";
    
    /** Create SQL file for changing to value field after. */
    public static final String CREATE_SQL_FILE_FOR_CHANGING_TO_VALUE_FIELD_AFTER = "CreateSqlFileForChangingToValueFieldAfter";
    
    /** Create SQL file for type change after. */
    public static final String CREATE_SQL_FILE_FOR_TYPE_CHANGE_AFTER = "CreateSqlFileForTypeChangeAfter";

    // =========================================================================
    // DB BACKUP RULES (dbBackup.etl)
    // =========================================================================
    
    /** Backup tables (abstract). */
    public static final String BACKUP_TABLES = "BackupTables";
    
    /** Backup deleted tables. */
    public static final String BACKUP_DELETED_TABLES = "BackupDeletedTables";
    
    /** Backup modified tables. */
    public static final String BACKUP_MODIFIED_TABLES = "BackupModifiedTables";

    // =========================================================================
    // DB CHECKUP RULES (dbCheckup.etl)
    // =========================================================================
    
    /** Check tables. */
    public static final String CHECK_TABLES = "CheckTables";
    
    /** Check junction tables. */
    public static final String CHECK_JUNCTION_TABLES = "CheckJunctionTables";
    
    /** Pre-check backup tables (abstract). */
    public static final String PRE_CHECK_BACKUP_TABLES = "PreCheckBackupTables";
    
    /** Pre-check backup deleted tables. */
    public static final String PRE_CHECK_BACKUP_DELETED_TABLES = "PreCheckBackupDeletedTables";
    
    /** Pre-check backup modified tables. */
    public static final String PRE_CHECK_BACKUP_MODIFIED_TABLES = "PreCheckBackupModifiedTables";
    
    /** Check fields. */
    public static final String CHECK_FIELDS = "CheckFields";
    
    /** Check value fields. */
    public static final String CHECK_VALUE_FIELDS = "CheckValueFields";
    
    /** Check identifier fields. */
    public static final String CHECK_IDENTIFIER_FIELDS = "CheckIdentifierFields";
    
    /** Check foreign keys. */
    public static final String CHECK_FOREIGN_KEYS = "CheckForeignKeys";
    
    /** Check foreign key constraints. */
    public static final String CHECK_FOREIGN_KEY_CONSTRAINTS = "CheckForeignKeyConstraints";
    
    /** Check indexes. */
    public static final String CHECK_INDEXES = "CheckIndexes";
    
    /** Check unique constraints. */
    public static final String CHECK_UNIQUE_CONSTRAINTS = "CheckUniqueConstraints";

    // =========================================================================
    // DB DROP BACKUP RULES (dbDropBackup.etl)
    // =========================================================================
    
    /** Post-check backup tables (abstract). */
    public static final String POST_CHECK_BACKUP_TABLES = "PostCheckBackupTables";
    
    /** Post-check backup deleted tables. */
    public static final String POST_CHECK_BACKUP_DELETED_TABLES = "PostCheckBackupDeletedTables";
    
    /** Post-check backup modified tables. */
    public static final String POST_CHECK_BACKUP_MODIFIED_TABLES = "PostCheckBackupModifiedTables";
    
    /** Delete backup tables (abstract). */
    public static final String DELETE_BACKUP_TABLES = "DeleteBackupTables";
    
    /** Delete backup deleted tables. */
    public static final String DELETE_BACKUP_DELETED_TABLES = "DeleteBackupDeletedTables";
    
    /** Delete backup modified tables. */
    public static final String DELETE_BACKUP_MODIFIED_TABLES = "DeleteBackupModifiedTables";
}
