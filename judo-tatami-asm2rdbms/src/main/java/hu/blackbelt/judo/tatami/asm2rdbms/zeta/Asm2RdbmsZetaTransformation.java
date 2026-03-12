package hu.blackbelt.judo.tatami.asm2rdbms.zeta;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMapping;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMappings;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMapping;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMappings;
import hu.blackbelt.judo.meta.rdbmsRules.Rules;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.rules.*;
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
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;
import java.util.stream.Stream;

/**
 * ASM to RDBMS transformation using Zeta framework's TransformationRegistry and TransformationExecutor.
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
 *   <li>{@link PackageRules} - package.etl (rootPackegeToModel, rootPackegeToConfiguration)</li>
 *   <li>{@link ClassRules} - class.etl (EClassToRdbmsTable @Primary, system field rules)</li>
 *   <li>{@link AttributeRules} - attribute.etl (EAttributeToRdbmsField @Abstract, EAttributeToTableValueField @Extends)</li>
 *   <li>{@link ReferenceRules} - reference.etl (EReferenceToRdbmsJunctionTable @Lazy, FK rules)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Asm2RdbmsZetaTransformation {

    private final AsmModel asmModel;
    private final hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel;
    private final AsmUtils asmUtils;

    // Configuration parameters
    private final String dialect;
    private final String modelVersion;
    private final int tableNameMaxSize;
    private final int columnNameMaxSize;
    private final int shortNameSize;
    private final int nameSize;
    private final boolean createSimpleName;
    private final String tablePrefix;
    private final String columnPrefix;
    private final String foreignKeyPrefix;
    private final String inverseForeignKeyPrefix;
    private final String junctionTablePrefix;

    // Type mappings from Excel model
    private final Map<String, TypeMapping> typeMappings = new HashMap<>();
    private Rules rules;

    @Builder
    public Asm2RdbmsZetaTransformation(
            @NonNull AsmModel asmModel,
            @NonNull hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel,
            @NonNull String dialect,
            String modelVersion,
            Integer shortNameSize,
            Integer nameSize,
            Integer tableNameMaxSize,
            Integer columnNameMaxSize,
            Boolean createSimpleName,
            String tablePrefix,
            String columnPrefix,
            String foreignKeyPrefix,
            String inverseForeignKeyPrefix,
            String junctionTablePrefix) {
        this.asmModel = asmModel;
        this.rdbmsModel = rdbmsModel;
        this.asmUtils = new AsmUtils(asmModel.getResourceSet());
        this.dialect = dialect;
        this.modelVersion = modelVersion != null ? modelVersion : asmModel.getVersion();

        // Set defaults based on dialect
        boolean isOracle = "oracle".equals(dialect);
        this.tableNameMaxSize = tableNameMaxSize != null && tableNameMaxSize > 0 ? tableNameMaxSize : (isOracle ? 30 : 62);
        this.columnNameMaxSize = columnNameMaxSize != null && columnNameMaxSize > 0 ? columnNameMaxSize : (isOracle ? 30 : 58);
        this.shortNameSize = shortNameSize != null && shortNameSize > 0 ? shortNameSize : (isOracle ? 6 : 16);
        this.nameSize = nameSize != null && nameSize > 0 ? nameSize : (isOracle ? 28 : 60);
        this.createSimpleName = createSimpleName != null ? createSimpleName : false;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "T_";
        this.columnPrefix = columnPrefix != null ? columnPrefix : "C_";
        this.foreignKeyPrefix = foreignKeyPrefix != null ? foreignKeyPrefix : "FK_";
        this.inverseForeignKeyPrefix = inverseForeignKeyPrefix != null ? inverseForeignKeyPrefix : "FK_INV_";
        this.junctionTablePrefix = junctionTablePrefix != null ? junctionTablePrefix : "J_";

        // Load type mappings and rules
        loadTypeMappings();
        loadRules();
    }

    private void loadTypeMappings() {
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(TypeMappings.class::isInstance)
                .map(TypeMappings.class::cast)
                .flatMap(mappings -> mappings.getTypeMappings().stream())
                .forEach(mapping -> typeMappings.put(mapping.getAsmType(), mapping));
    }

    private void loadRules() {
        rules = rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(Rules.class::isInstance)
                .map(Rules.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rules not found in RDBMS model. Make sure mapping model is loaded."));
    }

    /**
     * Execute the transformation using TransformationExecutor.
     *
     * @return Zeta TransformationTrace containing source to target element mappings
     */
    public TransformationTrace execute() {
        log.info("Starting ASM to RDBMS Zeta transformation with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        // Clear helper caches from previous runs
        Asm2RdbmsHelper.clearCaches();

        // Create registry and register all rule classes
        TransformationRegistry registry = createRegistry();

        // Create transformation context
        TransformationContext context = createContext(registry);

        // Create executor - sequential mode required due to EMF thread safety
        // Parallel execution causes NPE in postProcess when accessing model elements
        // on large models (e.g., RackInspect with 1273 elements)
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)
                .build();

        // Execute transformation
        log.debug("Starting executor.transform()");
        TransformationResult result = executor.transform();
        log.debug("Finished executor.transform()");

        // Post-processing: add root elements to resource and apply name mappings
        postProcess(context);

        long duration = System.currentTimeMillis() - startTime;
        log.info("ASM to RDBMS Zeta transformation completed in {}ms", duration);

        return result.getTrace();
    }

    /**
     * Creates and configures the TransformationRegistry with all rule classes.
     * Rule classes are registered in order matching ETL imports.
     */
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();

        // Phase 1: Package rules (must exist first for model/configuration)
        registry.register(PackageRules.class);

        // Phase 2: Class rules (tables must exist before fields)
        registry.register(ClassRules.class);

        // Phase 3: Attribute rules
        registry.register(AttributeRules.class);

        // Phase 4: Reference rules (foreign keys and junction tables)
        registry.register(ReferenceRules.class);

        log.debug("Registered 4 rule classes with TransformationRegistry");
        return registry;
    }

    /**
     * Creates and configures the TransformationContext.
     */
    private TransformationContext createContext(TransformationRegistry registry) {
        ResourceSet sourceResourceSet = asmModel.getResourceSet();
        ResourceSet targetResourceSet = rdbmsModel.getResourceSet();

        // Create model provider
        ModelProvider modelProvider = new Asm2RdbmsModelProvider(asmModel);

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
        context.setTargetPackage(RdbmsPackage.eINSTANCE);
        context.setTransformationRegistry(registry);
        context.setUseStructuredIds(true);

        // Register resources with aliases
        context.registerResource("asm", sourceResourceSet);
        context.registerResource("rdbms", targetResourceSet);
        context.setPreferredSourceAlias("asm");

        // Store configuration in context attributes for rules to access
        context.setAttribute("dialect", dialect);
        context.setAttribute("modelVersion", modelVersion);
        context.setAttribute("tableNameMaxSize", tableNameMaxSize);
        context.setAttribute("columnNameMaxSize", columnNameMaxSize);
        context.setAttribute("shortNameSize", shortNameSize);
        context.setAttribute("nameSize", nameSize);
        context.setAttribute("createSimpleName", createSimpleName);
        context.setAttribute("tablePrefix", tablePrefix);
        context.setAttribute("columnPrefix", columnPrefix);
        context.setAttribute("foreignKeyPrefix", foreignKeyPrefix);
        context.setAttribute("inverseForeignKeyPrefix", inverseForeignKeyPrefix);
        context.setAttribute("junctionTablePrefix", junctionTablePrefix);

        // Store shared instances
        context.setAttribute("asmUtils", asmUtils);
        context.setAttribute("typeMappings", typeMappings);
        context.setAttribute("rules", rules);

        return context;
    }

    /**
     * Post-processing after all rules have executed.
     */
    private void postProcess(TransformationContext context) {
        // 1. Add root elements (RdbmsModel) to the resource
        asmUtils.all(EPackage.class)
                .filter(pkg -> pkg.getESuperPackage() == null)
                .forEach(rootPkg -> {
                    RdbmsModel model = context.equivalent(rootPkg, RdbmsModel.class);
                    if (model != null && !rdbmsModel.getResource().getContents().contains(model)) {
                        context.addToResource(model);
                        log.debug("Added RdbmsModel '{}' to resource", model.getName());
                    }
                });

        // 2. Apply name mappings from the model
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(NameMappings.class::isInstance)
                .map(NameMappings.class::cast)
                .flatMap(mappings -> mappings.getNameMappings().stream())
                .forEach(this::applyNameMapping);
    }

    /**
     * Apply a name mapping to an RDBMS element.
     */
    private void applyNameMapping(NameMapping mapping) {
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(RdbmsModel.class::isInstance)
                .map(RdbmsModel.class::cast)
                .flatMap(model -> getAllRdbmsElements(model).stream())
                .filter(elem -> mapping.getFullyQualifiedName().equals(elem.getUuid()))
                .findFirst()
                .ifPresent(elem -> {
                    log.debug("Replace sqlName in: {} to: {}", elem, mapping.getRdbmsName());
                    elem.setSqlName(mapping.getRdbmsName());
                });
    }

    /**
     * Get all RDBMS elements from a model for name mapping.
     */
    private List<RdbmsElement> getAllRdbmsElements(RdbmsModel model) {
        List<RdbmsElement> elements = new ArrayList<>();
        for (RdbmsTable table : model.getRdbmsTables()) {
            elements.add(table);
            elements.addAll(table.getFields());
            elements.addAll(table.getIndexes());
            elements.addAll(table.getUniqueConstraints());
        }
        return elements;
    }

    /**
     * Model provider for ASM to RDBMS transformation.
     */
    private static class Asm2RdbmsModelProvider implements ModelProvider {
        private final AsmModel asmModel;

        Asm2RdbmsModelProvider(AsmModel asmModel) {
            this.asmModel = asmModel;
        }

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
