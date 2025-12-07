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
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseRuleNames.*;

/**
 * Java-based RDBMS to Liquibase transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * in src/main/epsilon/transformations/, providing a type-safe Java alternative
 * with better IDE support and debugging capabilities.
 * </p>
 */
@Slf4j
public class Rdbms2LiquibaseZetaTransformation {

    private final RdbmsModel rdbmsModel;
    private final LiquibaseModel liquibaseModel;
    private final LiquibaseFactory liquibaseFactory;

    // Configuration parameters
    private final String dialect;
    private final String context;

    // The root databaseChangeLog element
    private databaseChangeLog changeLog;

    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    // Cache for changeSets by logical file path
    private final Map<String, ChangeSet> changeSetCache = new HashMap<>();

    @Builder
    public Rdbms2LiquibaseZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect) {
        this.rdbmsModel = rdbmsModel;
        this.liquibaseModel = liquibaseModel;
        this.liquibaseFactory = LiquibaseFactory.eINSTANCE;
        this.dialect = dialect;

        // Get model version for context
        this.context = getModelVersion();
    }

    private String getModelVersion() {
        return all(hu.blackbelt.judo.meta.rdbms.RdbmsModel.class)
                .findFirst()
                .map(hu.blackbelt.judo.meta.rdbms.RdbmsModel::getVersion)
                .orElse("1.0");
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
        log.info("Starting RDBMS to Liquibase Zeta transformation with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        // Create the root databaseChangeLog
        changeLog = liquibaseFactory.createdatabaseChangeLog();
        liquibaseModel.getResource().getContents().add(changeLog);

        // Phase 1: Transform tables to create table changesets
        transformTables();

        // Phase 2: Transform fields (columns)
        transformFields();

        // Phase 3: Transform indexes
        transformIndexes();

        // Phase 4: Transform unique constraints
        transformUniqueConstraints();

        long duration = System.currentTimeMillis() - startTime;
        log.info("RDBMS to Liquibase Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    // =========================================================================
    // TABLE TRANSFORMATIONS
    // =========================================================================

    private void transformTables() {
        log.debug("Transforming tables");
        all(RdbmsTable.class).forEach(this::transformTable);
    }

    private void transformTable(RdbmsTable table) {
        log.debug("  Transform table: {}", table.getSqlName());

        // Create CreateTable element
        CreateTable createTable = liquibaseFactory.createCreateTable();
        createTable.setTableName(table.getSqlName());
        createTable.setRemarks(table.getUuid());
        addTrace(table, TABLE_TO_CREATE_TABLE, createTable);

        // Create ChangeSet for table creation
        ChangeSet createTableChangeSet = liquibaseFactory.createChangeSet();
        createTableChangeSet.setId("create-table-" + table.getSqlName());
        createTableChangeSet.setAuthor("tatami-rdbms2liquibase");
        createTableChangeSet.setDbms(dialect);
        createTableChangeSet.setContext("full and " + context);
        createTableChangeSet.setLogicalFilePath("create-tables");
        createTableChangeSet.getCreateTable().add(createTable);
        changeLog.getChangeSet().add(createTableChangeSet);
        addTrace(table, TABLE_TO_CREATE_TABLE_CHANGESET, createTableChangeSet);

        // Create ChangeSet for foreign keys if table has foreign keys
        boolean hasForeignKeys = table.getFields().stream()
                .anyMatch(f -> f instanceof RdbmsForeignKey);
        if (hasForeignKeys) {
            ChangeSet fkChangeSet = liquibaseFactory.createChangeSet();
            fkChangeSet.setId("create-foreignkeys-" + table.getSqlName());
            fkChangeSet.setAuthor("tatami-rdbms2liquibase");
            fkChangeSet.setDbms(dialect);
            fkChangeSet.setContext(context);
            fkChangeSet.setLogicalFilePath("create-foreignkeys");
            changeLog.getChangeSet().add(fkChangeSet);
            addTrace(table, TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET, fkChangeSet);
        }

        // Create ChangeSet for not null constraints if table has mandatory fields
        boolean hasMandatoryFields = table.getFields().stream()
                .anyMatch(RdbmsField::isMandatory);
        if (hasMandatoryFields) {
            ChangeSet notNullChangeSet = liquibaseFactory.createChangeSet();
            notNullChangeSet.setId("add-not-null-" + table.getSqlName());
            notNullChangeSet.setAuthor("tatami-rdbms2liquibase");
            notNullChangeSet.setDbms(dialect);
            notNullChangeSet.setContext(context);
            notNullChangeSet.setLogicalFilePath("add-not-null");
            changeLog.getChangeSet().add(notNullChangeSet);
            addTrace(table, TABLE_TO_ADD_NOT_NULL_CHANGESET, notNullChangeSet);
        }
    }

    // =========================================================================
    // FIELD TRANSFORMATIONS
    // =========================================================================

    private void transformFields() {
        log.debug("Transforming fields");

        // Transform identifier fields (primary keys)
        all(RdbmsIdentifierField.class).forEach(this::transformIdentifierField);

        // Transform value fields
        all(RdbmsValueField.class).forEach(this::transformValueField);

        // Transform foreign key fields
        all(RdbmsForeignKey.class).forEach(this::transformForeignKeyField);
    }

    private void transformIdentifierField(RdbmsIdentifierField field) {
        RdbmsTable table = getTable(field);
        if (table == null) return;

        log.debug("    Transform identifier field: {}", field.getSqlName());

        // Create Column
        Column column = createColumn(field);
        addTrace(field, IDENTIFIER_FIELD_TO_COLUMN, column);

        // Add to CreateTable
        CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
        if (createTable != null) {
            createTable.getColumn().add(column);
        }

        // Add primary key constraint
        Constraints constraints = liquibaseFactory.createConstraints();
        constraints.setPrimaryKey(true);
        constraints.setNullable(false);
        column.setConstraints(constraints);
        addTrace(field, IDENTIFIER_FIELD_TO_PK_CONSTRAINT, constraints);
    }

    private void transformValueField(RdbmsValueField field) {
        RdbmsTable table = getTable(field);
        if (table == null) return;

        log.debug("    Transform value field: {}", field.getSqlName());

        // Create Column
        Column column = createColumn(field);
        addTrace(field, VALUE_FIELD_TO_COLUMN, column);

        // Add to CreateTable
        CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
        if (createTable != null) {
            createTable.getColumn().add(column);
        }

        // Add not null constraint if mandatory
        if (field.isMandatory()) {
            createNotNullConstraint(field, table);
        }
    }

    private void transformForeignKeyField(RdbmsForeignKey field) {
        RdbmsTable table = getTable(field);
        if (table == null) return;

        log.debug("    Transform foreign key field: {}", field.getSqlName());

        // Create Column for FK
        Column column = createColumn(field);
        addTrace(field, FOREIGN_KEY_FIELD_TO_COLUMN, column);

        // Add to CreateTable
        CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
        if (createTable != null) {
            createTable.getColumn().add(column);
        }

        // Create AddForeignKeyConstraint
        AddForeignKeyConstraint fkConstraint = liquibaseFactory.createAddForeignKeyConstraint();
        fkConstraint.setBaseTableName(table.getSqlName());
        fkConstraint.setBaseColumnNames(field.getSqlName());
        fkConstraint.setConstraintName(field.getForeignKeySqlName());

        if (field.getReferenceKey() != null) {
            RdbmsTable refTable = getTable(field.getReferenceKey());
            if (refTable != null) {
                fkConstraint.setReferencedTableName(refTable.getSqlName());
                if (refTable.getPrimaryKey() != null) {
                    fkConstraint.setReferencedColumnNames(refTable.getPrimaryKey().getSqlName());
                }
            }
        }

        // Add to FK ChangeSet
        ChangeSet fkChangeSet = (ChangeSet) getEquivalent(table, TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET);
        if (fkChangeSet != null) {
            fkChangeSet.getAddForeignKeyConstraint().add(fkConstraint);
        }
        addTrace(field, FOREIGN_KEY_TO_ADD_FK_CONSTRAINT, fkConstraint);

        // Add not null constraint if mandatory
        if (field.isMandatory()) {
            createNotNullConstraint(field, table);
        }
    }

    private Column createColumn(RdbmsField field) {
        Column column = liquibaseFactory.createColumn();
        column.setName(field.getSqlName());
        column.setType(toFieldDefinition(field));
        column.setRemarks(field.getUuid());
        return column;
    }

    private void createNotNullConstraint(RdbmsField field, RdbmsTable table) {
        AddNotNullConstraint notNull = liquibaseFactory.createAddNotNullConstraint();
        notNull.setColumnDataType(toFieldDefinition(field));
        notNull.setColumnName(field.getSqlName());
        notNull.setTableName(table.getSqlName());

        ChangeSet notNullChangeSet = (ChangeSet) getEquivalent(table, TABLE_TO_ADD_NOT_NULL_CHANGESET);
        if (notNullChangeSet != null) {
            notNullChangeSet.getAddNotNullConstraint().add(notNull);
        }
        addTrace(field, FIELD_TO_ADD_NOT_NULL_CONSTRAINT, notNull);
    }

    // =========================================================================
    // INDEX TRANSFORMATIONS
    // =========================================================================

    private void transformIndexes() {
        log.debug("Transforming indexes");
        all(RdbmsIndex.class).forEach(this::transformIndex);
    }

    private void transformIndex(RdbmsIndex index) {
        RdbmsTable table = (RdbmsTable) index.eContainer();
        if (table == null) return;

        log.debug("    Transform index: {}", index.getSqlName());

        CreateIndex createIndex = liquibaseFactory.createCreateIndex();
        createIndex.setTableName(table.getSqlName());
        createIndex.setIndexName(index.getSqlName());

        for (RdbmsField field : index.getFields()) {
            Column column = liquibaseFactory.createColumn();
            column.setName(field.getSqlName());
            createIndex.getColumn().add(column);
        }

        // Get or create ChangeSet for indexes
        ChangeSet indexChangeSet = getOrCreateChangeSet(
                "create-indexes-in-" + table.getSqlName(),
                "create-indexes");
        indexChangeSet.getCreateIndex().add(createIndex);

        addTrace(index, INDEX_TO_CREATE_INDEX, createIndex);
        log.debug("CreateIndex added: {}", createIndex.getIndexName());
    }

    // =========================================================================
    // UNIQUE CONSTRAINT TRANSFORMATIONS
    // =========================================================================

    private void transformUniqueConstraints() {
        log.debug("Transforming unique constraints");
        all(RdbmsUniqueConstraint.class).forEach(this::transformUniqueConstraint);
    }

    private void transformUniqueConstraint(RdbmsUniqueConstraint constraint) {
        RdbmsTable table = (RdbmsTable) constraint.eContainer();
        if (table == null) return;

        log.debug("    Transform unique constraint: {}", constraint.getSqlName());

        for (RdbmsField field : constraint.getFields()) {
            AddUniqueConstraint addUnique = liquibaseFactory.createAddUniqueConstraint();
            addUnique.setConstraintName(constraint.getSqlName());
            addUnique.setTableName(((RdbmsTable) field.eContainer()).getSqlName());
            addUnique.setColumnNames(field.getSqlName());

            // Get or create ChangeSet for unique constraints
            ChangeSet uniqueChangeSet = getOrCreateChangeSet(
                    "add-unique-constraints-to-" + table.getSqlName(),
                    "add-unique-constraints");
            uniqueChangeSet.getAddUniqueConstraint().add(addUnique);

            log.debug("AddUniqueConstraint added: {} ({})", addUnique.getColumnNames(), addUnique.getTableName());
        }

        addTrace(constraint, UNIQUE_CONSTRAINT_TO_ADD_UNIQUE, constraint);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private RdbmsTable getTable(RdbmsField field) {
        if (field.eContainer() instanceof RdbmsTable) {
            return (RdbmsTable) field.eContainer();
        }
        // Fallback: search all tables
        return all(RdbmsTable.class)
                .filter(t -> t.getFields().contains(field))
                .findFirst()
                .orElse(null);
    }

    private String toFieldDefinition(RdbmsField field) {
        if (field.getRdbmsTypeName() != null) {
            StringBuilder typedef = new StringBuilder(field.getRdbmsTypeName().toUpperCase());
            if (field.getPrecision() > 0) {
                typedef.append("(").append(field.getPrecision());
                if (field.getScale() > 0) {
                    typedef.append(", ").append(field.getScale());
                }
                typedef.append(")");
            } else if (field.getSize() > 0) {
                typedef.append("(").append(field.getSize()).append(")");
            }
            return typedef.toString();
        }
        return "";
    }

    private ChangeSet getOrCreateChangeSet(String id, String logicalFilePath) {
        String cacheKey = id + ":" + logicalFilePath;
        return changeSetCache.computeIfAbsent(cacheKey, k -> {
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
        traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                .put(ruleName, target);
    }

    private EObject getEquivalent(EObject source, String ruleName) {
        Map<String, EObject> rules = traceMap.get(source);
        return rules != null ? rules.get(ruleName) : null;
    }

    private Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
