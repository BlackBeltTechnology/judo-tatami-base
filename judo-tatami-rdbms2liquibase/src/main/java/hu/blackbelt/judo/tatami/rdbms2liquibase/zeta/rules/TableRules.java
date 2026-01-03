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

import hu.blackbelt.judo.meta.liquibase.ChangeSet;
import hu.blackbelt.judo.meta.liquibase.CreateTable;
import hu.blackbelt.judo.meta.liquibase.databaseChangeLog;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.RdbmsForeignKey;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * Table transformation rules from table.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>TableToCreateTable (@lazy, @greedy) - creates CreateTable element</li>
 *   <li>TableToCreateTableChangeSet (@greedy) - creates ChangeSet with CreateTable</li>
 *   <li>TableToCreateForeignKeysChangeSet (@greedy, guard) - creates FK ChangeSet</li>
 *   <li>TableToAddNotNullChangeSet (@greedy, guard) - creates not-null ChangeSet</li>
 * </ul>
 * </p>
 * <p>
 * ETL equivalents:
 * <pre>
 * @lazy
 * @greedy
 * rule TableToCreateTable
 *     transform s : RDBMS!RdbmsTable
 *     to t : LIQUIBASE!CreateTable {
 *         t.tableName = s.sqlName;
 *         t.remarks = s.uuid;
 *     }
 *
 * @greedy
 * rule TableToCreateTableChangeSet
 *     transform s : RDBMS!RdbmsTable
 *     to t : LIQUIBASE!ChangeSet {
 *         targetModel.changeSet.add(t);
 *         t.id = "create-table-" + s.sqlName;
 *         t.createTable.add(s.equivalent("TableToCreateTable"));
 *         ...
 *     }
 * </pre>
 * </p>
 */
@Slf4j
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = RdbmsTable.class, target = CreateTable.class)
public class TableRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public TableRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: RdbmsTable has foreign key fields.
     * Matches ETL: guard: s.fields.select(f | f.isTypeOf(RDBMS!RdbmsForeignKey)).size() > 0
     */
    public boolean hasForeignKeys(EObject source, TransformationContext ctx) {
        if (source instanceof RdbmsTable) {
            RdbmsTable table = (RdbmsTable) source;
            return table.getFields().stream()
                    .anyMatch(f -> f instanceof RdbmsForeignKey);
        }
        return false;
    }

    /**
     * Guard: RdbmsTable has mandatory fields.
     * Matches ETL: guard: s.fields.select(f | f.mandatory).size() > 0
     */
    public boolean hasMandatoryFields(EObject source, TransformationContext ctx) {
        if (source instanceof RdbmsTable) {
            RdbmsTable table = (RdbmsTable) source;
            return table.getFields().stream()
                    .anyMatch(RdbmsField::isMandatory);
        }
        return false;
    }

    // =========================================================================
    // TRANSFORMATION RULES
    // =========================================================================

    /**
     * @lazy
     * @greedy
     * rule TableToCreateTable
     *     transform s : RDBMS!RdbmsTable
     *     to t : LIQUIBASE!CreateTable {
     *         t.tableName = s.sqlName;
     *         t.remarks = s.uuid;
     *     }
     * <p>
     * This is a lazy rule - only executed when another rule calls ctx.equivalent().
     * </p>
     */
    @TransformRule(name = TABLE_TO_CREATE_TABLE, description = "Transform RdbmsTable to CreateTable (lazy)")
    @Lazy
    @Greedy
    @Transform(type = RdbmsTable.class)
    @To(type = CreateTable.class)
    public TransformFunction<RdbmsTable, CreateTable> tableToCreateTable() {
        return (s, ctx) -> {
            log.debug("  Create CreateTable for: {}", s.getSqlName());

            CreateTable t = ctx.createTarget(CreateTable.class);
            t.setTableName(s.getSqlName());
            t.setRemarks(s.getUuid());

            return t;
        };
    }

    /**
     * @greedy
     * rule TableToCreateTableChangeSet
     *     transform s : RDBMS!RdbmsTable
     *     to t : LIQUIBASE!ChangeSet {
     *         targetModel.changeSet.add(t);
     *         t.id = "create-table-" + s.sqlName;
     *         t.author = "tatami-rdbms2liquibase";
     *         t.dbms = dialect;
     *         t.context = "full and " + RDBMS!RdbmsModel.all.first.version;
     *         t.createTable.add(s.equivalent("TableToCreateTable"));
     *         t.logicalFilePath = "create-tables";
     *     }
     * <p>
     * Key ETL pattern: s.equivalent("TableToCreateTable") triggers lazy rule execution.
     * </p>
     */
    @TransformRule(name = TABLE_TO_CREATE_TABLE_CHANGESET, description = "Transform RdbmsTable to CreateTable ChangeSet")
    @Greedy
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToCreateTableChangeSet() {
        return (s, ctx) -> {
            log.debug("  Create CreateTable ChangeSet for: {}", s.getSqlName());

            String dialect = ctx.getAttribute("dialect");
            String modelVersion = ctx.getAttribute("modelVersion");

            ChangeSet t = ctx.createTarget(ChangeSet.class);
            t.setId("create-table-" + s.getSqlName());
            t.setAuthor("tatami-rdbms2liquibase");
            t.setDbms(dialect);
            t.setContext("full and " + (modelVersion != null ? modelVersion : ""));
            t.setLogicalFilePath("create-tables");

            // Key pattern: use ctx.equivalent() to trigger lazy TableToCreateTable
            CreateTable createTable = ctx.equivalent(s, TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                t.getCreateTable().add(createTable);
            }

            // Add to databaseChangeLog (thread-safe)
            databaseChangeLog changeLog = ctx.getAttribute("changeLog");
            if (changeLog != null) {
                addChangeSetThreadSafe(changeLog, t);
            }

            return t;
        };
    }

    /**
     * @greedy
     * rule TableToCreateForeignKeysChangeSet
     *     transform s : RDBMS!RdbmsTable
     *     to t : LIQUIBASE!ChangeSet {
     *         guard: s.fields.select(f | f.isTypeOf(RDBMS!RdbmsForeignKey)).size() > 0
     *         targetModel.changeSet.add(t);
     *         t.id = "create-foreignkeys-" + s.sqlName;
     *         ...
     *     }
     */
    @TransformRule(name = TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET, description = "Transform RdbmsTable to FK ChangeSet")
    @Greedy
    @Guard(method = "hasForeignKeys")
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToCreateForeignKeysChangeSet() {
        return (s, ctx) -> {
            log.debug("  Create FK ChangeSet for: {}", s.getSqlName());

            String dialect = ctx.getAttribute("dialect");
            String modelVersion = ctx.getAttribute("modelVersion");

            ChangeSet t = ctx.createTarget(ChangeSet.class);
            t.setId("create-foreignkeys-" + s.getSqlName());
            t.setAuthor("tatami-rdbms2liquibase");
            t.setDbms(dialect);
            t.setContext(modelVersion);
            t.setLogicalFilePath("create-foreignkeys");

            // Add to databaseChangeLog (thread-safe)
            databaseChangeLog changeLog = ctx.getAttribute("changeLog");
            if (changeLog != null) {
                addChangeSetThreadSafe(changeLog, t);
            }

            return t;
        };
    }

    /**
     * @greedy
     * rule TableToAddNotNullChangeSet
     *     transform s : RDBMS!RdbmsTable
     *     to t : LIQUIBASE!ChangeSet {
     *         guard: s.fields.select(f | f.mandatory).size() > 0
     *         targetModel.changeSet.add(t);
     *         t.id = "add-not-null-" + s.sqlName;
     *         ...
     *     }
     */
    @TransformRule(name = TABLE_TO_ADD_NOT_NULL_CHANGESET, description = "Transform RdbmsTable to NotNull ChangeSet")
    @Greedy
    @Guard(method = "hasMandatoryFields")
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToAddNotNullChangeSet() {
        return (s, ctx) -> {
            log.debug("  Create NotNull ChangeSet for: {}", s.getSqlName());

            String dialect = ctx.getAttribute("dialect");
            String modelVersion = ctx.getAttribute("modelVersion");

            ChangeSet t = ctx.createTarget(ChangeSet.class);
            t.setId("add-not-null-" + s.getSqlName());
            t.setAuthor("tatami-rdbms2liquibase");
            t.setDbms(dialect);
            t.setContext(modelVersion);
            t.setLogicalFilePath("add-not-null");

            // Add to databaseChangeLog (thread-safe)
            databaseChangeLog changeLog = ctx.getAttribute("changeLog");
            if (changeLog != null) {
                addChangeSetThreadSafe(changeLog, t);
            }

            return t;
        };
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Thread-safe method to add a ChangeSet to the databaseChangeLog.
     * Required because Zeta runs transformation rules in parallel and
     * EMF ELists are not thread-safe.
     *
     * @param changeLog the databaseChangeLog to add to
     * @param changeSet the ChangeSet to add
     */
    private static synchronized void addChangeSetThreadSafe(databaseChangeLog changeLog, ChangeSet changeSet) {
        changeLog.getChangeSet().add(changeSet);
    }
}
