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
import hu.blackbelt.judo.meta.liquibase.databaseChangeLog;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.Getter;
import lombok.NonNull;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Helper method to get all elements of a given type from the RDBMS model.
     */
    protected <T> Stream<T> all(Class<T> clazz) {
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
     * Abbreviate string to max length.
     */
    protected String abbreviate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength);
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

    /**
     * Simplified check if element was in previous model.
     * In a real implementation, this would verify against the previous model.
     */
    protected boolean isPreviousModelContainsContainer(EObject element) {
        return element.eContainer() != null;
    }

    /**
     * Simplified check if element is in new model.
     * In a real implementation, this would verify against the new model.
     */
    protected boolean isNewModelContainsContainer(EObject element) {
        return element.eContainer() != null;
    }
}
