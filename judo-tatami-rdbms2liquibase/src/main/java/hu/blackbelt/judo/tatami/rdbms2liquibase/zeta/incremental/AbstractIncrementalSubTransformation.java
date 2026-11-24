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
import hu.blackbelt.judo.meta.liquibase.LiquibaseFactory;
import hu.blackbelt.judo.meta.liquibase.SqlFile;
import hu.blackbelt.judo.meta.liquibase.databaseChangeLog;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsElement;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.RdbmsIndex;
import hu.blackbelt.judo.meta.rdbms.RdbmsOperationMeta;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.AbbreviateUtils;
import hu.blackbelt.judo.tatami.rdbms2liquibase.ReviewResolver;
import lombok.Getter;
import lombok.NonNull;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Abstract base class for incremental sub-transformations.
 * Provides common functionality for all incremental transformation phases.
 */
public abstract class AbstractIncrementalSubTransformation {

    @Getter
    protected final RdbmsModel rdbmsModel;
    protected final LiquibaseModel liquibaseModel;
    protected final LiquibaseFactory liquibaseFactory;

    // Configuration parameters
    @Getter
    protected final String dialect;
    @Getter
    protected final String context;
    @Getter
    protected final String backupTableNamePrefix;
    @Getter
    protected final String backupChangeSetNamePrefix;
    @Getter
    protected final int tableNameMaxSize;

    // The root databaseChangeLog element
    @Getter
    protected databaseChangeLog changeLog;

    // Trace map for source to target element mapping
    protected final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    private RdbmsOperationMeta operationMeta;

    // Cache for changeSets by logical file path
    protected final Map<String, ChangeSet> changeSetCache = new HashMap<>();

    protected AbstractIncrementalSubTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect,
            String backupTableNamePrefix,
            Integer tableNameMaxSize) {
        this.rdbmsModel = rdbmsModel;
        this.liquibaseModel = liquibaseModel;
        this.liquibaseFactory = LiquibaseFactory.eINSTANCE;
        this.dialect = dialect;
        this.backupTableNamePrefix = backupTableNamePrefix != null ? backupTableNamePrefix : "BACKUP";
        this.backupChangeSetNamePrefix = this.backupTableNamePrefix.toLowerCase();
        this.tableNameMaxSize = tableNameMaxSize != null ? tableNameMaxSize : 
                ("oracle".equals(dialect) ? 30 : 62);

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
     * All elements of the incremental RDBMS resource that are of the given kind, in containment (pre-)order.
     * The incremental resource holds the operation model, the {@link RdbmsOperationMeta} and the roots of the
     * previous and of the new model, like the {@code RDBMS} model of the ETL.
     */
    protected <T> Stream<T> all(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (EObject root : rdbmsModel.getResource().getContents()) {
            collectAllOfType(root, clazz, false, result);
        }
        return result.stream();
    }

    /**
     * Like {@link #all(Class)} but only elements whose type is exactly {@code clazz} (no sub types). Epsilon ETL
     * rules transform instances of their source type only (not greedy), so this mirrors a rule's source set.
     */
    protected <T> Stream<T> allExact(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (EObject root : rdbmsModel.getResource().getContents()) {
            collectAllOfType(root, clazz, true, result);
        }
        return result.stream();
    }

    @SuppressWarnings("unchecked")
    private <T> void collectAllOfType(EObject root, Class<T> clazz, boolean exact, List<T> result) {
        if (exact ? clazz == root.eClass().getInstanceClass() : clazz.isInstance(root)) {
            result.add((T) root);
        }
        for (EObject child : root.eContents()) {
            collectAllOfType(child, clazz, exact, result);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Previous / new model membership (model.eol and table.eol of the ETL)

    /** The model before the change ({@code operationMeta.previousModel} of the ETL). */
    protected hu.blackbelt.judo.meta.rdbms.RdbmsModel previousModel() {
        return operationMeta().getPreviousModel();
    }

    /** The model after the change ({@code operationMeta.currentModel} of the ETL). */
    protected hu.blackbelt.judo.meta.rdbms.RdbmsModel newModel() {
        return operationMeta().getCurrentModel();
    }

    private RdbmsOperationMeta operationMeta() {
        if (operationMeta == null) {
            operationMeta = all(RdbmsOperationMeta.class).findFirst().orElseThrow(() ->
                    new IllegalStateException("The incremental RDBMS model has no RdbmsOperationMeta"));
        }
        return operationMeta;
    }

    /** {@code model.contains(table)}: the first table of the model with the table's uuid is the table itself. */
    protected static boolean modelContains(hu.blackbelt.judo.meta.rdbms.RdbmsModel model, RdbmsTable table) {
        return model != null && table != null && firstByUuid(model.getRdbmsTables(), table.getUuid()) == table;
    }

    /** {@code model.contains(table) and model.tableOf(table).contains(field)}. */
    protected static boolean modelContainsField(hu.blackbelt.judo.meta.rdbms.RdbmsModel model, RdbmsTable table,
                                                RdbmsField field) {
        return modelContains(model, table) && firstByUuid(table.getFields(), field.getUuid()) == field;
    }

    /** {@code model.contains(table) and model.tableOf(table).contains(index)}. */
    protected static boolean modelContainsIndex(hu.blackbelt.judo.meta.rdbms.RdbmsModel model, RdbmsTable table,
                                                RdbmsIndex index) {
        return modelContains(model, table) && firstByUuid(table.getIndexes(), index.getUuid()) == index;
    }

    /** First element with the uuid (EOL {@code selectOne(e | e.uuid == uuid)}, {@code null == null}). */
    private static <T extends RdbmsElement> T firstByUuid(List<T> elements, String uuid) {
        for (T element : elements) {
            if (Objects.equals(element.getUuid(), uuid)) {
                return element;
            }
        }
        return null;
    }

    /**
     * Execute the transformation phase.
     *
     * @return map of source to target element mappings (trace)
     */
    public abstract Map<EObject, List<EObject>> execute();

    /**
     * Initialize the root databaseChangeLog element.
     */
    protected void initializeChangeLog() {
        changeLog = liquibaseFactory.createdatabaseChangeLog();
        liquibaseModel.getResource().getContents().add(changeLog);
    }

    /**
     * Get field type definition string.
     */
    protected String toFieldDefinition(RdbmsField field) {
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

    /**
     * Abbreviate string to max length. Same algorithm as the ETL {@code abbreviate} operation
     * ({@code AbbreviateUtils.abbreviate(text, maxLength, "_")}).
     */
    protected String abbreviate(String str, int maxLength) {
        if (str == null) return "";
        return AbbreviateUtils.abbreviate(str, maxLength, "_");
    }

    /**
     * Review script resolution ({@code sqlFile.eol} {@code addSqlFileToChangeSet} semantics): a {@link SqlFile} is
     * added only when the resolver knows the script and the script is not blank; an existing blank script adds
     * nothing; an unknown script is recorded in {@code missingReviewScripts}.
     *
     * @return the added {@link SqlFile}, or {@code null}
     */
    protected SqlFile addReviewSqlFile(ReviewResolver reviewResolver, Map<String, String> missingReviewScripts,
                                       ChangeSet changeSet, String sqlName, String message) {
        if (!reviewResolver.exists(sqlName)) {
            missingReviewScripts.put(sqlName, message);
            return null;
        }
        String script = reviewResolver.resolve(sqlName);
        if (script == null || script.trim().isEmpty()) {
            return null;
        }
        SqlFile sqlFile = liquibaseFactory.createSqlFile();
        sqlFile.setPath(sqlName);
        sqlFile.setDbms(dialect);
        changeSet.getSqlFile().add(sqlFile);
        return sqlFile;
    }

    /**
     * Get or create a ChangeSet with the given id and logical file path.
     */
    protected ChangeSet getOrCreateChangeSet(String id, String logicalFilePath) {
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

    /**
     * Add a trace mapping from source to target element.
     */
    protected void addTrace(EObject source, String ruleName, EObject target) {
        if (target != null) {
            traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                    .put(ruleName, target);
        }
    }

    /**
     * Build the trace result map.
     */
    protected Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
