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
 * After Incremental transformation phase for RDBMS to Liquibase transformation.
 * <p>
 * This phase adds constraints back after schema changes:
 * <ul>
 *   <li>Add foreign key constraints</li>
 *   <li>Add not null constraints</li>
 *   <li>Add unique constraints</li>
 *   <li>Add indexes</li>
 * </ul>
 * </p>
 */
@Slf4j
public class AfterIncrementalZetaTransformation extends AbstractIncrementalSubTransformation {

    @Builder
    public AfterIncrementalZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing After Incremental transformation");

        initializeChangeLog();

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

        log.debug("After Incremental transformation completed");
        return buildTraceResult();
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

        ChangeSet changeSet = getOrCreateChangeSet(
                "add-foreign-keys-to-" + table.getSqlName() + "-" + context, "add-foreign-keys");
        changeSet.getAddForeignKeyConstraint().add(addFk);

        addTrace(field, ADD_FOREIGN_KEY_CONSTRAINTS, addFk);
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

        ChangeSet changeSet = getOrCreateChangeSet(
                "add-not-null-constraints-to-" + table.getSqlName() + "-" + context, "add-not-null-constraints");
        changeSet.getAddNotNullConstraint().add(addNotNull);

        addTrace(field, ruleName, addNotNull);
    }

    private void transformAddUniqueConstraint(RdbmsUniqueConstraint constraint) {
        RdbmsTable table = (RdbmsTable) constraint.eContainer();
        log.debug("  Transform add unique constraint: {}", constraint.getSqlName());

        for (RdbmsField field : constraint.getFields()) {
            AddUniqueConstraint addUnique = liquibaseFactory.createAddUniqueConstraint();
            addUnique.setConstraintName(constraint.getSqlName());
            addUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            addUnique.setColumnNames(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet(
                    "add-unique-constraints-to-" + table.getSqlName() + "-" + context, "add-unique-constraints");
            changeSet.getAddUniqueConstraint().add(addUnique);
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

        ChangeSet changeSet = getOrCreateChangeSet(
                "create-indexes-for-" + table.getSqlName() + "-" + context, "create-indexes");
        changeSet.getCreateIndex().add(createIndex);

        addTrace(index, ADD_INDEXES, createIndex);
    }
}
