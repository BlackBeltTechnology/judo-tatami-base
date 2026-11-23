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
 * Before Incremental transformation phase for incremental RDBMS to Liquibase transformation.
 * <p>
 * This phase drops indexes, unique constraints, not null constraints, and foreign key constraints
 * before applying schema changes.
 * </p>
 */
@Slf4j
public class BeforeIncrementalZetaTransformation extends AbstractIncrementalSubTransformation {

    @Builder
    public BeforeIncrementalZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        super(rdbmsModel, liquibaseModel, dialect, backupTableNamePrefix, tableNameMaxSize);
    }

    @Override
    public Map<EObject, List<EObject>> execute() {
        log.debug("Executing Before Incremental transformation");

        initializeChangeLog();

        // Rule sources are the elements of exactly the rule's type whose table is part of the previous model
        // (guard: previousModel.contains(s.eContainer)).

        // Drop indexes
        allExact(RdbmsIndex.class)
                .filter(i -> modelContains(previousModel(), (RdbmsTable) i.eContainer()))
                .forEach(this::transformDropIndex);

        // Drop unique constraints
        allExact(RdbmsUniqueConstraint.class)
                .filter(u -> modelContains(previousModel(), (RdbmsTable) u.eContainer()))
                .forEach(this::transformDropUniqueConstraint);

        // Drop not null constraints from value fields
        allExact(RdbmsValueField.class)
                .filter(f -> modelContains(previousModel(), (RdbmsTable) f.eContainer()) && f.isMandatory())
                .forEach(this::transformDropNotNullConstraintFromValueField);

        // Drop not null constraints from foreign keys
        allExact(RdbmsForeignKey.class)
                .filter(f -> modelContains(previousModel(), (RdbmsTable) f.eContainer()) && f.isMandatory())
                .forEach(this::transformDropNotNullConstraintFromForeignKey);

        // Drop foreign key constraints
        allExact(RdbmsForeignKey.class)
                .filter(f -> modelContains(previousModel(), (RdbmsTable) f.eContainer()))
                .forEach(this::transformDropForeignKeyConstraint);

        log.debug("Before Incremental transformation completed");
        return buildTraceResult();
    }

    private void transformDropIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        log.debug("  Transform drop index: {}", index.getSqlName());

        DropIndex dropIndex = liquibaseFactory.createDropIndex();
        dropIndex.setIndexName(index.getSqlName());
        dropIndex.setTableName(table.getSqlName());

        ChangeSet changeSet = getOrCreateChangeSet(
                "drop-indexes-from-" + table.getSqlName() + "-" + context, "drop-indexes");
        changeSet.getDropIndex().add(dropIndex);

        addTrace(index, DROP_INDEXES, dropIndex);
    }

    private void transformDropUniqueConstraint(RdbmsUniqueConstraint constraint) {
        RdbmsTable table = (RdbmsTable) constraint.eContainer();
        log.debug("  Transform drop unique constraint: {}", constraint.getSqlName());

        for (RdbmsField field : constraint.getFields()) {
            DropUniqueConstraint dropUnique = liquibaseFactory.createDropUniqueConstraint();
            dropUnique.setConstraintName(constraint.getSqlName());
            dropUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            dropUnique.setUniqueColumns(field.getSqlName());

            ChangeSet changeSet = getOrCreateChangeSet(
                    "drop-unique-constraints-from-" + table.getSqlName() + "-" + context, "drop-unique-constraints");
            changeSet.getDropUniqueConstraint().add(dropUnique);
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

        ChangeSet changeSet = getOrCreateChangeSet(
                "drop-not-null-constraints-from-" + table.getSqlName() + "-" + context, "drop-not-null-constraints");
        changeSet.getDropNotNullConstraint().add(dropNotNull);

        addTrace(field, ruleName, dropNotNull);
    }

    private void transformDropForeignKeyConstraint(RdbmsForeignKey field) {
        RdbmsTable table = (RdbmsTable) field.eContainer();
        log.debug("  Transform drop foreign key constraint: {}", field.getForeignKeySqlName());

        DropForeignKeyConstraint dropFk = liquibaseFactory.createDropForeignKeyConstraint();
        dropFk.setBaseTableName(table.getSqlName());
        dropFk.setConstraintName(field.getForeignKeySqlName());

        ChangeSet changeSet = getOrCreateChangeSet(
                "drop-foreign-keys-from-" + table.getSqlName() + "-" + context, "drop-foreign-keys");
        changeSet.getDropForeignKeyConstraint().add(dropFk);

        addTrace(field, DROP_FOREIGN_KEY_CONSTRAINTS, dropFk);
    }
}
