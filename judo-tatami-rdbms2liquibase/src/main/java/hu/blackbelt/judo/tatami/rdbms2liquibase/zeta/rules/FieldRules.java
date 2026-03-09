package hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.rules;

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
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseHelper;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.Map;
import java.util.function.BiFunction;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * Field transformation rules from field.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>FieldToColumn (@abstract, @greedy) - base column creation</li>
 *   <li>IdentifierFieldToCreateTableColumn (@greedy, extends) - identifier field columns</li>
 *   <li>IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint - PK constraint</li>
 *   <li>ValueFieldToCreateTableColumn (extends) - value field columns</li>
 *   <li>ForeignKeyFieldToAddForeignKeyConstraint (@abstract) - base FK constraint</li>
 *   <li>ForeignKeyFieldToCreateTableAddForeignKeyConstraint (extends) - FK constraint in ChangeSet</li>
 *   <li>FieldToAddNotNullConstraint (@abstract, @greedy) - base not-null constraint</li>
 *   <li>FieldToCreateTableAddNotNullConstraint (@greedy, extends, guard) - not-null in ChangeSet</li>
 *   <li>IndexToCreateIndex - create index</li>
 *   <li>AddUniqueConstraints - add unique constraints</li>
 * </ul>
 * </p>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = RdbmsField.class, target = Column.class)
public class FieldRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public FieldRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: Field is mandatory.
     * Matches ETL: guard: s.mandatory
     */
    public TransformGuard isMandatoryField() {
        return (source, ctx) -> {
            if (!(source instanceof RdbmsField s)) return false;
            return s.isMandatory();
        };
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Get the containing table for a field.
     */
    private RdbmsTable getTable(RdbmsField field) {
        if (field.eContainer() instanceof RdbmsTable) {
            return (RdbmsTable) field.eContainer();
        }
        return null;
    }

    // =========================================================================
    // ABSTRACT RULES
    // =========================================================================

    /**
     * @abstract
     * @greedy
     * rule FieldToColumn
     *     transform s : RDBMS!RdbmsField
     *     to t : LIQUIBASE!Column {
     *         t.name = s.sqlName;
     *         t.type = s.toFieldDefinition();
     *         t.remarks = s.uuid;
     *     }
     * <p>
     * Base rule for field-to-column transformations. Extended by concrete field type rules.
     * </p>
     */
    @TransformRule(name = FIELD_TO_COLUMN, description = "Abstract base rule for field to column transformation")
    @Abstract
    @Greedy
    @Transform(type = RdbmsField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsField, Column> fieldToColumn() {
        return (s, ctx) -> {
            Column t = ctx.createTarget(Column.class);
            t.setName(s.getSqlName());
            t.setType(Rdbms2LiquibaseHelper.toFieldDefinition(s));
            t.setRemarks(s.getUuid());
            return t;
        };
    }

    /**
     * @abstract
     * rule ForeignKeyFieldToAddForeignKeyConstraint
     *     transform s : RDBMS!RdbmsForeignKey
     *     to t : LIQUIBASE!AddForeignKeyConstraint {
     *         t.baseTableName = s.table().sqlName;
     *         t.baseColumnNames = s.sqlName;
     *         t.constraintName = s.foreignKeySqlName;
     *         t.referencedColumnNames = s.referenceKey.table().primaryKey.sqlName;
     *         t.referencedTableName = s.referenceKey.table().sqlName;
     *     }
     * <p>
     * Base rule for FK constraint creation.
     * </p>
     */
    @TransformRule(name = FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT, description = "Abstract base rule for FK constraint")
    @Abstract
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddForeignKeyConstraint> foreignKeyFieldToAddFkConstraint() {
        return (s, ctx) -> {
            RdbmsTable table = getTable(s);
            if (table == null) return null;

            AddForeignKeyConstraint t = ctx.createTarget(AddForeignKeyConstraint.class);
            t.setBaseTableName(table.getSqlName());
            t.setBaseColumnNames(s.getSqlName());
            t.setConstraintName(s.getForeignKeySqlName());

            if (s.getReferenceKey() != null) {
                RdbmsTable refTable = getTable(s.getReferenceKey());
                if (refTable != null) {
                    t.setReferencedTableName(refTable.getSqlName());
                    if (refTable.getPrimaryKey() != null) {
                        t.setReferencedColumnNames(refTable.getPrimaryKey().getSqlName());
                    }
                }
            }

            return t;
        };
    }

    /**
     * @abstract
     * @greedy
     * rule FieldToAddNotNullConstraint
     *     transform s : RDBMS!RdbmsField
     *     to t : LIQUIBASE!AddNotNullConstraint {
     *         t.columnDataType = s.toFieldDefinition();
     *         t.columnName = s.sqlName;
     *         t.tableName = s.table().sqlName;
     *     }
     * <p>
     * Base rule for not-null constraint creation.
     * </p>
     */
    @TransformRule(name = FIELD_TO_ADD_NOT_NULL, description = "Abstract base rule for not-null constraint")
    @Abstract
    @Greedy
    @Transform(type = RdbmsField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsField, AddNotNullConstraint> fieldToAddNotNullConstraint() {
        return (s, ctx) -> {
            RdbmsTable table = getTable(s);
            if (table == null) return null;

            AddNotNullConstraint t = ctx.createTarget(AddNotNullConstraint.class);
            t.setColumnDataType(Rdbms2LiquibaseHelper.toFieldDefinition(s));
            t.setColumnName(s.getSqlName());
            t.setTableName(table.getSqlName());

            return t;
        };
    }

    // =========================================================================
    // CONCRETE RULES - EXTENDS ABSTRACT RULES
    // =========================================================================

    /**
     * @greedy
     * rule IdentifierFieldToCreateTableColumn
     *     transform s : RDBMS!RdbmsIdentifierField
     *     to t : LIQUIBASE!Column
     *     extends FieldToColumn {
     *         s.table().equivalent("TableToCreateTable").column.add(t);
     *     }
     * <p>
     * Key ETL pattern: uses ctx.executeParentRule() and ctx.equivalent() for table lookup.
     * </p>
     */
    @TransformRule(name = IDENTIFIER_FIELD_TO_COLUMN, description = "Transform RdbmsIdentifierField to Column")
    @Greedy
    @Extends({FIELD_TO_COLUMN})
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsIdentifierField, Column> identifierFieldToColumn() {
        return (s, ctx) -> {
            log.debug("    Add identifier column: {}", s.getSqlName());

            // Execute parent rule to get base column setup
            Column t = ctx.executeParentRule(FIELD_TO_COLUMN, s);

            // Add to CreateTable using named equivalent lookup
            RdbmsTable table = getTable(s);
            if (table != null) {
                CreateTable createTable = ctx.equivalent(table, TABLE_TO_CREATE_TABLE);
                if (createTable != null) {
                    createTable.getColumn().add(t);
                }
            }

            return t;
        };
    }

    /**
     * rule IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint
     *     transform s : RDBMS!RdbmsIdentifierField
     *     to t : LIQUIBASE!Constraints {
     *         t.primaryKey = true;
     *         t.nullable = false;
     *         s.equivalent("IdentifierFieldToCreateTableColumn").setConstraints(t);
     *     }
     */
    @TransformRule(name = IDENTIFIER_FIELD_TO_PK_CONSTRAINT, description = "Add PK constraint to identifier column")
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = Constraints.class)
    public TransformFunction<RdbmsIdentifierField, Constraints> identifierFieldToPkConstraint() {
        return (s, ctx) -> {
            log.debug("    Add PK constraint for: {}", s.getSqlName());

            Constraints t = ctx.createTarget(Constraints.class);
            t.setPrimaryKey(true);
            t.setNullable(false);

            // Get the column from equivalent lookup and set constraints
            Column column = ctx.equivalent(s, IDENTIFIER_FIELD_TO_COLUMN);
            if (column != null) {
                column.setConstraints(t);
            }

            return t;
        };
    }

    /**
     * rule ValueFieldToCreateTableColumn
     *     transform s : RDBMS!RdbmsValueField
     *     to t : LIQUIBASE!Column
     *     extends FieldToColumn {
     *         s.table().equivalent("TableToCreateTable").column.add(t);
     *     }
     */
    @TransformRule(name = VALUE_FIELD_TO_COLUMN, description = "Transform RdbmsValueField to Column")
    @Extends({FIELD_TO_COLUMN})
    @Transform(type = RdbmsValueField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsValueField, Column> valueFieldToColumn() {
        return (s, ctx) -> {
            log.debug("    Add value column: {}", s.getSqlName());

            // Execute parent rule to get base column setup
            Column t = ctx.executeParentRule(FIELD_TO_COLUMN, s);

            // Add to CreateTable using named equivalent lookup
            RdbmsTable table = getTable(s);
            if (table != null) {
                CreateTable createTable = ctx.equivalent(table, TABLE_TO_CREATE_TABLE);
                if (createTable != null) {
                    createTable.getColumn().add(t);
                }
            }

            return t;
        };
    }

    /**
     * rule ForeignKeyFieldToCreateTableAddForeignKeyConstraint
     *     transform s : RDBMS!RdbmsForeignKey
     *     to t : LIQUIBASE!AddForeignKeyConstraint
     *     extends ForeignKeyFieldToAddForeignKeyConstraint {
     *         s.table().equivalent("TableToCreateForeignKeysChangeSet").addForeignKeyConstraint.add(t);
     *     }
     * <p>
     * Note: Column for FK field is created by IdentifierFieldToCreateTableColumn
     * since RdbmsForeignKey extends RdbmsIdentifierField.
     * </p>
     */
    @TransformRule(name = FOREIGN_KEY_TO_ADD_FK_CONSTRAINT, description = "Transform RdbmsForeignKey to AddForeignKeyConstraint")
    @Extends({FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT})
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddForeignKeyConstraint> foreignKeyFieldToCreateTableAddFkConstraint() {
        return (s, ctx) -> {
            log.debug("    Add FK constraint: {}", s.getSqlName());

            // Execute parent rule to get base FK constraint setup
            AddForeignKeyConstraint t = ctx.executeParentRule(FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT, s);

            // Add to FK ChangeSet using named equivalent lookup (thread-safe)
            // Note: Column is already added by IdentifierFieldToCreateTableColumn (RdbmsForeignKey extends RdbmsIdentifierField)
            RdbmsTable table = getTable(s);
            if (table != null) {
                ChangeSet fkChangeSet = ctx.equivalent(table, TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET);
                if (fkChangeSet != null) {
                    addForeignKeyConstraintThreadSafe(fkChangeSet, t);
                }
            }

            return t;
        };
    }

    /**
     * @greedy
     * rule FieldToCreateTableAddNotNullConstraint
     *     transform s : RDBMS!RdbmsField
     *     to t : LIQUIBASE!AddNotNullConstraint
     *     extends FieldToAddNotNullConstraint {
     *         guard: s.mandatory
     *         s.table().equivalent("TableToAddNotNullChangeSet").addNotNullConstraint.add(t);
     *     }
     */
    @TransformRule(name = FIELD_TO_ADD_NOT_NULL_CONSTRAINT, description = "Add not-null constraint to ChangeSet")
    @Greedy
    @Extends({FIELD_TO_ADD_NOT_NULL})
    @Guard(method = "isMandatoryField")
    @Transform(type = RdbmsField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsField, AddNotNullConstraint> fieldToCreateTableAddNotNullConstraint() {
        return (s, ctx) -> {
            log.debug("    Add not-null constraint: {}", s.getSqlName());

            // Execute parent rule to get base not-null constraint setup
            AddNotNullConstraint t = ctx.executeParentRule(FIELD_TO_ADD_NOT_NULL, s);

            // Add to NotNull ChangeSet using named equivalent lookup (thread-safe)
            RdbmsTable table = getTable(s);
            if (table != null) {
                ChangeSet notNullChangeSet = ctx.equivalent(table, TABLE_TO_ADD_NOT_NULL_CHANGESET);
                if (notNullChangeSet != null) {
                    addNotNullConstraintThreadSafe(notNullChangeSet, t);
                }
            }

            return t;
        };
    }

    // =========================================================================
    // INDEX AND UNIQUE CONSTRAINT RULES
    // =========================================================================

    /**
     * rule IndexToCreateIndex
     *     transform s : RDBMS!RdbmsIndex
     *     to t : LIQUIBASE!CreateIndex {
     *         t.tableName = s.eContainer.sqlName;
     *         t.indexName = s.sqlName;
     *         for (field in s.fields) {
     *             t.column.add(new LIQUIBASE!Column(name = field.sqlName));
     *         }
     *         targetModel.getOrCreateChangeSet(...).createIndex.add(t);
     *     }
     * <p>
     * Uses getOrCreateChangeSet helper from context.
     * </p>
     */
    @TransformRule(name = INDEX_TO_CREATE_INDEX, description = "Transform RdbmsIndex to CreateIndex")
    @Transform(type = RdbmsIndex.class)
    @To(type = CreateIndex.class)
    public TransformFunction<RdbmsIndex, CreateIndex> indexToCreateIndex() {
        return (s, ctx) -> {
            RdbmsTable table = (RdbmsTable) s.eContainer();
            if (table == null) return null;

            log.debug("    Create index: {}", s.getSqlName());

            CreateIndex t = ctx.createTarget(CreateIndex.class);
            t.setTableName(table.getSqlName());
            t.setIndexName(s.getSqlName());

            for (RdbmsField field : s.getFields()) {
                Column column = ctx.createTarget(Column.class);
                column.setName(field.getSqlName());
                t.getColumn().add(column);
            }

            // Get or create ChangeSet using helper from context (thread-safe)
            @SuppressWarnings("unchecked")
            BiFunction<String, String, ChangeSet> getOrCreateChangeSet = ctx.getAttribute("getOrCreateChangeSet");
            if (getOrCreateChangeSet != null) {
                ChangeSet changeSet = getOrCreateChangeSet.apply(
                        "create-indexes-in-" + table.getSqlName(),
                        "create-indexes");
                addCreateIndexThreadSafe(changeSet, t);
            }

            return t;
        };
    }

    /**
     * rule AddUniqueConstraints
     *     transform s : RDBMS!RdbmsUniqueConstraint
     *     to t : LIQUIBASE!AddUniqueConstraint {
     *         for (field in s.fields) {
     *             var addUniqueConstraint = new LIQUIBASE!AddUniqueConstraint();
     *             addUniqueConstraint.constraintName = s.sqlName;
     *             addUniqueConstraint.tableName = field.eContainer.sqlName;
     *             addUniqueConstraint.columnNames = field.sqlName;
     *             targetModel.getOrCreateChangeSet(...).addUniqueConstraint.add(addUniqueConstraint);
     *         }
     *     }
     * <p>
     * Uses getOrCreateChangeSet helper from context.
     * </p>
     */
    @TransformRule(name = UNIQUE_CONSTRAINT_TO_ADD_UNIQUE, description = "Transform RdbmsUniqueConstraint to AddUniqueConstraint")
    @Transform(type = RdbmsUniqueConstraint.class)
    @To(type = AddUniqueConstraint.class)
    public TransformFunction<RdbmsUniqueConstraint, AddUniqueConstraint> addUniqueConstraints() {
        return (s, ctx) -> {
            RdbmsTable table = (RdbmsTable) s.eContainer();
            if (table == null) return null;

            log.debug("    Add unique constraint: {}", s.getSqlName());

            AddUniqueConstraint firstAddUnique = null;

            // Get or create ChangeSet using helper from context
            @SuppressWarnings("unchecked")
            BiFunction<String, String, ChangeSet> getOrCreateChangeSet = ctx.getAttribute("getOrCreateChangeSet");

            for (RdbmsField field : s.getFields()) {
                AddUniqueConstraint addUnique = ctx.createTarget(AddUniqueConstraint.class);
                addUnique.setConstraintName(s.getSqlName());
                addUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
                addUnique.setColumnNames(field.getSqlName());

                if (getOrCreateChangeSet != null) {
                    ChangeSet changeSet = getOrCreateChangeSet.apply(
                            "add-unique-constraints-to-" + table.getSqlName(),
                            "add-unique-constraints");
                    addUniqueConstraintThreadSafe(changeSet, addUnique);
                }

                if (firstAddUnique == null) {
                    firstAddUnique = addUnique;
                }

                log.debug("      AddUniqueConstraint: {} ({})", addUnique.getColumnNames(), addUnique.getTableName());
            }

            return firstAddUnique;
        };
    }

    // =========================================================================
    // THREAD-SAFETY HELPER METHODS
    // =========================================================================

    /**
     * Thread-safe method to add a FK constraint to a ChangeSet.
     * Required because Zeta runs transformation rules in parallel and
     * EMF ELists are not thread-safe.
     */
    private static synchronized void addForeignKeyConstraintThreadSafe(ChangeSet changeSet, AddForeignKeyConstraint constraint) {
        changeSet.getAddForeignKeyConstraint().add(constraint);
    }

    /**
     * Thread-safe method to add a not-null constraint to a ChangeSet.
     * Required because Zeta runs transformation rules in parallel and
     * EMF ELists are not thread-safe.
     */
    private static synchronized void addNotNullConstraintThreadSafe(ChangeSet changeSet, AddNotNullConstraint constraint) {
        changeSet.getAddNotNullConstraint().add(constraint);
    }

    /**
     * Thread-safe method to add a create index to a ChangeSet.
     * Required because Zeta runs transformation rules in parallel and
     * EMF ELists are not thread-safe.
     */
    private static synchronized void addCreateIndexThreadSafe(ChangeSet changeSet, CreateIndex index) {
        changeSet.getCreateIndex().add(index);
    }

    /**
     * Thread-safe method to add a unique constraint to a ChangeSet.
     * Required because Zeta runs transformation rules in parallel and
     * EMF ELists are not thread-safe.
     */
    private static synchronized void addUniqueConstraintThreadSafe(ChangeSet changeSet, AddUniqueConstraint constraint) {
        changeSet.getAddUniqueConstraint().add(constraint);
    }
}
