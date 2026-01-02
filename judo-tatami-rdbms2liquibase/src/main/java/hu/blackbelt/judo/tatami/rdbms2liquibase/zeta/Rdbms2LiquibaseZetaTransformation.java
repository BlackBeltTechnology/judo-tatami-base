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
        createTableChangeSet.setContext("full and " + (context != null ? context : ""));
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

        // Transform identifier fields (primary keys) - exclude foreign keys since they extend RdbmsIdentifierField
        all(RdbmsIdentifierField.class)
                .filter(f -> !(f instanceof RdbmsForeignKey))
                .forEach(this::transformIdentifierField);

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

        // Add not null constraint if mandatory (matches ETL FieldToCreateTableAddNotNullConstraint rule)
        if (field.isMandatory()) {
            createNotNullConstraint(field, table);
        }
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
        column.setType(Rdbms2LiquibaseHelper.toFieldDefinition(field));
        column.setRemarks(field.getUuid());
        return column;
    }

    private void createNotNullConstraint(RdbmsField field, RdbmsTable table) {
        AddNotNullConstraint notNull = liquibaseFactory.createAddNotNullConstraint();
        notNull.setColumnDataType(Rdbms2LiquibaseHelper.toFieldDefinition(field));
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

        AddUniqueConstraint firstAddUnique = null;
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

            // Keep track of first created element for tracing
            if (firstAddUnique == null) {
                firstAddUnique = addUnique;
            }

            log.debug("AddUniqueConstraint added: {} ({})", addUnique.getColumnNames(), addUnique.getTableName());
        }

        // Trace from original constraint to the first created AddUniqueConstraint
        if (firstAddUnique != null) {
            addTrace(constraint, UNIQUE_CONSTRAINT_TO_ADD_UNIQUE, firstAddUnique);
        }
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

    // =========================================================================
    // ZETA ANNOTATED TRANSFORMATION RULES
    // =========================================================================
    // The following methods use Zeta annotations to define transformation rules.
    // These rules are equivalent to the ETL rules in the epsilon scripts.
    // =========================================================================

    // -------------------------------------------------------------------------
    // TABLE RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = TABLE_TO_CREATE_TABLE, description = "Transform RdbmsTable to CreateTable")
    @Transform(type = RdbmsTable.class)
    @To(type = CreateTable.class)
    public TransformFunction<RdbmsTable, CreateTable> tableToCreateTableRule() {
        return (table, ctx) -> {
            CreateTable createTable = liquibaseFactory.createCreateTable();
            createTable.setTableName(table.getSqlName());
            createTable.setRemarks(table.getUuid());
            return createTable;
        };
    }

    @TransformRule(name = TABLE_TO_CREATE_TABLE_CHANGESET, description = "Transform RdbmsTable to CreateTable ChangeSet")
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToCreateTableChangeSetRule() {
        return (table, ctx) -> {
            ChangeSet createTableChangeSet = liquibaseFactory.createChangeSet();
            createTableChangeSet.setId("create-table-" + table.getSqlName());
            createTableChangeSet.setAuthor("tatami-rdbms2liquibase");
            createTableChangeSet.setDbms(dialect);
            createTableChangeSet.setContext("full and " + (context != null ? context : ""));
            createTableChangeSet.setLogicalFilePath("create-tables");
            
            // Add the CreateTable element
            CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                createTableChangeSet.getCreateTable().add(createTable);
            }
            
            changeLog.getChangeSet().add(createTableChangeSet);
            return createTableChangeSet;
        };
    }

    /**
     * Guard: Check if table has foreign key fields.
     */
    public boolean hasForeignKeys(RdbmsTable table) {
        return table.getFields().stream().anyMatch(f -> f instanceof RdbmsForeignKey);
    }

    @TransformRule(name = TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET, description = "Transform RdbmsTable to FK ChangeSet")
    @Guard(method = "hasForeignKeys")
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToFkChangeSetRule() {
        return (table, ctx) -> {
            ChangeSet fkChangeSet = liquibaseFactory.createChangeSet();
            fkChangeSet.setId("create-foreignkeys-" + table.getSqlName());
            fkChangeSet.setAuthor("tatami-rdbms2liquibase");
            fkChangeSet.setDbms(dialect);
            fkChangeSet.setContext(context);
            fkChangeSet.setLogicalFilePath("create-foreignkeys");
            changeLog.getChangeSet().add(fkChangeSet);
            return fkChangeSet;
        };
    }

    /**
     * Guard: Check if table has mandatory fields.
     */
    public boolean hasMandatoryFields(RdbmsTable table) {
        return table.getFields().stream().anyMatch(RdbmsField::isMandatory);
    }

    @TransformRule(name = TABLE_TO_ADD_NOT_NULL_CHANGESET, description = "Transform RdbmsTable to NotNull ChangeSet")
    @Guard(method = "hasMandatoryFields")
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToNotNullChangeSetRule() {
        return (table, ctx) -> {
            ChangeSet notNullChangeSet = liquibaseFactory.createChangeSet();
            notNullChangeSet.setId("add-not-null-" + table.getSqlName());
            notNullChangeSet.setAuthor("tatami-rdbms2liquibase");
            notNullChangeSet.setDbms(dialect);
            notNullChangeSet.setContext(context);
            notNullChangeSet.setLogicalFilePath("add-not-null");
            changeLog.getChangeSet().add(notNullChangeSet);
            return notNullChangeSet;
        };
    }

    // -------------------------------------------------------------------------
    // FIELD RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if field is identifier but not foreign key.
     */
    public boolean isIdentifierNotForeignKey(RdbmsIdentifierField field) {
        return !(field instanceof RdbmsForeignKey);
    }

    @TransformRule(name = IDENTIFIER_FIELD_TO_COLUMN, description = "Transform RdbmsIdentifierField to Column")
    @Guard(method = "isIdentifierNotForeignKey")
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsIdentifierField, Column> identifierFieldToColumnRule() {
        return (field, ctx) -> {
            RdbmsTable table = getTable(field);
            if (table == null) return null;

            Column column = createColumn(field);
            
            // Add primary key constraint
            Constraints constraints = liquibaseFactory.createConstraints();
            constraints.setPrimaryKey(true);
            constraints.setNullable(false);
            column.setConstraints(constraints);

            // Add to CreateTable
            CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                createTable.getColumn().add(column);
            }

            return column;
        };
    }

    @TransformRule(name = IDENTIFIER_FIELD_TO_PK_CONSTRAINT, description = "Transform RdbmsIdentifierField to PK Constraints")
    @Guard(method = "isIdentifierNotForeignKey")
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = Constraints.class)
    public TransformFunction<RdbmsIdentifierField, Constraints> identifierFieldToPkConstraintRule() {
        return (field, ctx) -> {
            Column column = (Column) getEquivalent(field, IDENTIFIER_FIELD_TO_COLUMN);
            if (column == null) return null;

            Constraints constraints = liquibaseFactory.createConstraints();
            constraints.setPrimaryKey(true);
            constraints.setNullable(false);
            column.setConstraints(constraints);

            return constraints;
        };
    }

    @TransformRule(name = VALUE_FIELD_TO_COLUMN, description = "Transform RdbmsValueField to Column")
    @Transform(type = RdbmsValueField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsValueField, Column> valueFieldToColumnRule() {
        return (field, ctx) -> {
            RdbmsTable table = getTable(field);
            if (table == null) return null;

            Column column = createColumn(field);

            // Add to CreateTable
            CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                createTable.getColumn().add(column);
            }

            return column;
        };
    }

    @TransformRule(name = FOREIGN_KEY_FIELD_TO_COLUMN, description = "Transform RdbmsForeignKey to Column")
    @Transform(type = RdbmsForeignKey.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsForeignKey, Column> foreignKeyFieldToColumnRule() {
        return (field, ctx) -> {
            RdbmsTable table = getTable(field);
            if (table == null) return null;

            Column column = createColumn(field);

            // Add to CreateTable
            CreateTable createTable = (CreateTable) getEquivalent(table, TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                createTable.getColumn().add(column);
            }

            return column;
        };
    }

    @TransformRule(name = FOREIGN_KEY_TO_ADD_FK_CONSTRAINT, description = "Transform RdbmsForeignKey to AddForeignKeyConstraint")
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddForeignKeyConstraint> foreignKeyToFkConstraintRule() {
        return (field, ctx) -> {
            RdbmsTable table = getTable(field);
            if (table == null) return null;

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

            return fkConstraint;
        };
    }

    /**
     * Guard: Check if field is mandatory.
     */
    public boolean isMandatoryField(RdbmsField field) {
        return field.isMandatory();
    }

    @TransformRule(name = FIELD_TO_ADD_NOT_NULL_CONSTRAINT, description = "Transform mandatory RdbmsField to AddNotNullConstraint")
    @Guard(method = "isMandatoryField")
    @Transform(type = RdbmsField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsField, AddNotNullConstraint> fieldToNotNullConstraintRule() {
        return (field, ctx) -> {
            RdbmsTable table = getTable(field);
            if (table == null) return null;

            AddNotNullConstraint notNull = liquibaseFactory.createAddNotNullConstraint();
            notNull.setColumnDataType(Rdbms2LiquibaseHelper.toFieldDefinition(field));
            notNull.setColumnName(field.getSqlName());
            notNull.setTableName(table.getSqlName());

            ChangeSet notNullChangeSet = (ChangeSet) getEquivalent(table, TABLE_TO_ADD_NOT_NULL_CHANGESET);
            if (notNullChangeSet != null) {
                notNullChangeSet.getAddNotNullConstraint().add(notNull);
            }

            return notNull;
        };
    }

    // -------------------------------------------------------------------------
    // INDEX RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = INDEX_TO_CREATE_INDEX, description = "Transform RdbmsIndex to CreateIndex")
    @Transform(type = RdbmsIndex.class)
    @To(type = CreateIndex.class)
    public TransformFunction<RdbmsIndex, CreateIndex> indexToCreateIndexRule() {
        return (index, ctx) -> {
            RdbmsTable table = (RdbmsTable) index.eContainer();
            if (table == null) return null;

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

            return createIndex;
        };
    }

    // -------------------------------------------------------------------------
    // UNIQUE CONSTRAINT RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = UNIQUE_CONSTRAINT_TO_ADD_UNIQUE, description = "Transform RdbmsUniqueConstraint to AddUniqueConstraint")
    @Transform(type = RdbmsUniqueConstraint.class)
    @To(type = AddUniqueConstraint.class)
    public TransformFunction<RdbmsUniqueConstraint, AddUniqueConstraint> uniqueConstraintToAddUniqueRule() {
        return (constraint, ctx) -> {
            RdbmsTable table = (RdbmsTable) constraint.eContainer();
            if (table == null) return null;

            AddUniqueConstraint firstAddUnique = null;
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

                if (firstAddUnique == null) {
                    firstAddUnique = addUnique;
                }
            }

            return firstAddUnique;
        };
    }

    // =========================================================================
    // ABSTRACT BASE RULES
    // =========================================================================
    // These @Abstract rules are base rules invoked only via @Extends inheritance.

    /**
     * @abstract
     * rule FieldToColumn
     *     transform s : RDBMS!RdbmsField
     *     to t : LIQUIBASE!Column
     * Base rule for field-to-column transformations.
     * Extended by concrete field type rules.
     */
    @TransformRule(name = FIELD_TO_COLUMN, description = "Abstract base rule for field to column transformation")
    @Abstract
    @Transform(type = RdbmsField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsField, Column> fieldToColumnRule() {
        return (field, ctx) -> {
            Column column = liquibaseFactory.createColumn();
            column.setName(field.getSqlName());
            return column;
        };
    }

    /**
     * @abstract
     * rule FieldToAddNotNull
     *     transform s : RDBMS!RdbmsField
     *     to t : LIQUIBASE!AddNotNullConstraint
     * Base rule for adding not-null constraints to fields.
     */
    @TransformRule(name = FIELD_TO_ADD_NOT_NULL, description = "Abstract base rule for field not-null constraint")
    @Abstract
    @Guard(method = "isMandatoryField")
    @Transform(type = RdbmsField.class)
    @To(type = AddNotNullConstraint.class)
    public TransformFunction<RdbmsField, AddNotNullConstraint> fieldToAddNotNullRule() {
        return (field, ctx) -> {
            AddNotNullConstraint addNotNull = liquibaseFactory.createAddNotNullConstraint();
            addNotNull.setColumnName(field.getSqlName());
            RdbmsTable table = (RdbmsTable) field.eContainer();
            if (table != null) {
                addNotNull.setTableName(table.getSqlName());
            }
            return addNotNull;
        };
    }

    /**
     * @abstract
     * rule ForeignKeyFieldToAddForeignKeyConstraint
     *     transform s : RDBMS!RdbmsForeignKey
     *     to t : LIQUIBASE!AddForeignKeyConstraint
     * Base rule for adding foreign key constraints.
     */
    @TransformRule(name = FOREIGN_KEY_FIELD_TO_ADD_FK_CONSTRAINT, description = "Abstract base rule for foreign key constraint")
    @Abstract
    @Transform(type = RdbmsForeignKey.class)
    @To(type = AddForeignKeyConstraint.class)
    public TransformFunction<RdbmsForeignKey, AddForeignKeyConstraint> foreignKeyFieldToAddFkConstraintRule() {
        return (fk, ctx) -> {
            AddForeignKeyConstraint addFk = liquibaseFactory.createAddForeignKeyConstraint();
            addFk.setConstraintName(fk.getForeignKeySqlName());
            addFk.setBaseColumnNames(fk.getSqlName());
            RdbmsTable baseTable = (RdbmsTable) fk.eContainer();
            if (baseTable != null) {
                addFk.setBaseTableName(baseTable.getSqlName());
            }
            if (fk.getReferenceKey() != null) {
                RdbmsTable refTable = (RdbmsTable) fk.getReferenceKey().eContainer();
                if (refTable != null) {
                    addFk.setReferencedTableName(refTable.getSqlName());
                    addFk.setReferencedColumnNames(fk.getReferenceKey().getSqlName());
                }
            }
            return addFk;
        };
    }

    /**
     * rule CheckUniqueConstraints
     *     transform s : RDBMS!RdbmsUniqueConstraint
     *     to t : DBCHECKUP!UniqueConstraintExists
     * Creates unique constraint check in dbCheckup changelog.
     * Note: This is for incremental transformation - dbCheckup phase.
     */
    @TransformRule(name = CHECK_UNIQUE_CONSTRAINTS, description = "Create unique constraint check in dbCheckup")
    @Transform(type = RdbmsUniqueConstraint.class)
    @To(type = AddUniqueConstraint.class)
    public TransformFunction<RdbmsUniqueConstraint, AddUniqueConstraint> checkUniqueConstraintsRule() {
        return (constraint, ctx) -> {
            // This rule is for incremental transformation - dbCheckup phase
            // Returns null as actual implementation is in incremental transformation
            return null;
        };
    }

    // -------------------------------------------------------------------------
    // POST-EXECUTION HOOK
    // -------------------------------------------------------------------------

    @PostExecution
    public void postExecutionHook(TransformationContext ctx) {
        log.debug("Post-execution: Liquibase changelog created with {} changeSets", 
                changeLog != null ? changeLog.getChangeSet().size() : 0);
    }
}
