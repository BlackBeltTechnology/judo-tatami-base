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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.rules.FieldRules;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.rules.TableRules;
import hu.blackbelt.judo.zeta.common.ExtensionMethodRegistry;
import hu.blackbelt.judo.zeta.common.ModelProvider;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import hu.blackbelt.judo.zeta.transformation.core.TransformationExecutor;
import hu.blackbelt.judo.zeta.transformation.core.TransformationRegistry;
import hu.blackbelt.judo.zeta.transformation.core.TransformationResult;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * RDBMS to Liquibase transformation using Zeta framework's TransformationRegistry and TransformationExecutor.
 * <p>
 * This class uses the Zeta framework's declarative rule-based transformation approach:
 * <ul>
 *   <li>Rules are declared in separate classes with @TransformRule annotations</li>
 *   <li>TransformationRegistry scans and registers all rules</li>
 *   <li>TransformationExecutor executes rules in proper order</li>
 *   <li>Named equivalent lookups match ETL patterns exactly</li>
 * </ul>
 * </p>
 * <p>
 * Rule classes (matching ETL files):
 * <ul>
 *   <li>{@link TableRules} - table.etl (TableToCreateTable @Lazy, ChangeSet rules)</li>
 *   <li>{@link FieldRules} - field.etl (FieldToColumn @Abstract, column rules, FK rules)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Rdbms2LiquibaseZetaTransformation {

    private final RdbmsModel rdbmsModel;
    private final LiquibaseModel liquibaseModel;

    // Configuration parameters
    private final String dialect;
    private final String modelVersion;

    // The root databaseChangeLog element
    private databaseChangeLog changeLog;

    // Cache for changeSets by logical file path (for index/unique constraint rules)
    // Uses ConcurrentHashMap for thread-safety in parallel transformation
    private final Map<String, ChangeSet> changeSetCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Builder
    public Rdbms2LiquibaseZetaTransformation(
            @NonNull RdbmsModel rdbmsModel,
            @NonNull LiquibaseModel liquibaseModel,
            @NonNull String dialect) {
        this.rdbmsModel = rdbmsModel;
        this.liquibaseModel = liquibaseModel;
        this.dialect = dialect;

        // Get model version from RDBMS model
        this.modelVersion = getModelVersion();
    }

    private String getModelVersion() {
        return rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(hu.blackbelt.judo.meta.rdbms.RdbmsModel.class::isInstance)
                .map(hu.blackbelt.judo.meta.rdbms.RdbmsModel.class::cast)
                .findFirst()
                .map(hu.blackbelt.judo.meta.rdbms.RdbmsModel::getVersion)
                .orElse(null);
    }

    /**
     * Execute the transformation using TransformationExecutor.
     *
     * @return Zeta TransformationTrace containing source to target element mappings
     */
    public TransformationTrace execute() {
        log.info("Starting RDBMS to Liquibase Zeta transformation with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        // Create the root databaseChangeLog
        changeLog = LiquibaseFactory.eINSTANCE.createdatabaseChangeLog();
        liquibaseModel.getResource().getContents().add(changeLog);

        // Create registry and register all rule classes
        TransformationRegistry registry = createRegistry();

        // Create transformation context
        TransformationContext context = createContext(registry);

        // Create executor - parallel disabled due to race conditions in changeSet creation
        // causing non-deterministic changeSet counts (see fix-parallel-transformation-issues)
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)
                .build();

        // Execute transformation
        log.debug("Starting executor.transform()");
        TransformationResult result = executor.transform();
        log.debug("Finished executor.transform()");

        long duration = System.currentTimeMillis() - startTime;
        log.info("RDBMS to Liquibase Zeta transformation completed in {}ms", duration);

        return result.getTrace();
    }

    /**
     * Creates and configures the TransformationRegistry with all rule classes.
     * Rule classes are registered in order matching ETL imports.
     */
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();

        // Phase 1: Table rules (ChangeSets must exist first)
        registry.register(TableRules.class);

        // Phase 2: Field rules (columns, FK constraints, not-null)
        registry.register(FieldRules.class);

        log.debug("Registered 2 rule classes with TransformationRegistry");
        return registry;
    }

    /**
     * Creates and configures the TransformationContext.
     */
    private TransformationContext createContext(TransformationRegistry registry) {
        ResourceSet sourceResourceSet = rdbmsModel.getResourceSet();
        ResourceSet targetResourceSet = liquibaseModel.getResourceSet();

        // Create model provider
        ModelProvider modelProvider = new Rdbms2LiquibaseModelProvider();

        // Create extension method registry
        ExtensionMethodRegistry extensionRegistry = new ExtensionMethodRegistry();

        // Create context
        TransformationContext context = new TransformationContext(
                modelProvider,
                sourceResourceSet,
                targetResourceSet,
                extensionRegistry
        );

        // Configure context
        context.setTargetPackage(LiquibasePackage.eINSTANCE);
        context.setTransformationRegistry(registry);
        context.setUseStructuredIds(true);

        // Register resources with aliases
        context.registerResource("rdbms", sourceResourceSet);
        context.registerResource("liquibase", targetResourceSet);
        context.setPreferredSourceAlias("rdbms");

        // Store configuration in context attributes for rules to access
        context.setAttribute("dialect", dialect);
        context.setAttribute("modelVersion", modelVersion);
        context.setAttribute("changeLog", changeLog);

        // Store getOrCreateChangeSet helper as a BiFunction for index/unique constraint rules
        BiFunction<String, String, ChangeSet> getOrCreateChangeSetFn = this::getOrCreateChangeSet;
        context.setAttribute("getOrCreateChangeSet", getOrCreateChangeSetFn);

        return context;
    }

    /**
     * Helper method to get or create a ChangeSet by ID and logical file path.
     * Used by IndexToCreateIndex and AddUniqueConstraints rules.
     * Thread-safe for use in parallel transformations.
     *
     * @param id              the ChangeSet ID
     * @param logicalFilePath the logical file path
     * @return the existing or newly created ChangeSet
     */
    private ChangeSet getOrCreateChangeSet(String id, String logicalFilePath) {
        String cacheKey = id + ":" + logicalFilePath;
        return changeSetCache.computeIfAbsent(cacheKey, k -> {
            ChangeSet changeSet = LiquibaseFactory.eINSTANCE.createChangeSet();
            changeSet.setId(id);
            changeSet.setAuthor("tatami-rdbms2liquibase");
            changeSet.setDbms(dialect);
            changeSet.setContext(modelVersion);
            changeSet.setLogicalFilePath(logicalFilePath);
            // Thread-safe add to changeLog
            addChangeSetThreadSafe(changeLog, changeSet);
            return changeSet;
        });
    }

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

    /**
     * Model provider for RDBMS to Liquibase transformation.
     */
    private class Rdbms2LiquibaseModelProvider implements ModelProvider {

        @Override
        public <T extends EObject> Collection<T> getAllContents(ResourceSet resourceSet, Class<T> type) {
            List<T> result = new ArrayList<>();
            for (org.eclipse.emf.ecore.resource.Resource r : resourceSet.getResources()) {
                r.getAllContents().forEachRemaining(obj -> {
                    if (type.isInstance(obj)) {
                        result.add(type.cast(obj));
                    }
                });
            }
            return result;
        }
    }
}
