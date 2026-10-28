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
 * Data Update Before Incremental transformation phase for RDBMS to Liquibase transformation.
 * <p>
 * This phase creates SQL file references for data updates that need to occur before schema changes:
 * <ul>
 *   <li>Value field to foreign key conversions</li>
 *   <li>Foreign key to value field conversions</li>
 *   <li>Field size changes</li>
 *   <li>Type changes</li>
 * </ul>
 * </p>
 */
@Slf4j
public class DataUpdateBeforeZetaTransformation extends AbstractIncrementalSubTransformation {

    @Builder
    public DataUpdateBeforeZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing Data Update Before Incremental transformation");

        initializeChangeLog();

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

        log.debug("Data Update Before Incremental transformation completed");
        return buildTraceResult();
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

        ChangeSet changeSet = getOrCreateChangeSet(
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

        ChangeSet changeSet = getOrCreateChangeSet(
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

        ChangeSet changeSet = getOrCreateChangeSet(
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

        ChangeSet changeSet = getOrCreateChangeSet(
                "modify-type-in-before-" + tableName + "-" + context, "modify-type");
        changeSet.getSqlFile().add(sqlFile);

        addTrace(op, CREATE_SQL_FILE_FOR_TYPE_CHANGE_BEFORE, sqlFile);
    }
}
