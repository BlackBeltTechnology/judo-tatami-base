package hu.blackbelt.judo.tatami.psm2asm.zeta;

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
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.TransferObjectRelation;
import hu.blackbelt.judo.meta.psm.service.TransferObjectType;
import hu.blackbelt.judo.meta.psm.service.TransferOperationBehaviourType;
import hu.blackbelt.judo.meta.psm.service.UnboundOperation;
import hu.blackbelt.judo.tatami.psm2asm.zeta.rules.*;
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
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

import java.util.*;

/**
 * PSM to ASM transformation using Zeta framework's TransformationRegistry and TransformationExecutor.
 * <p>
 * This class uses the Zeta framework's declarative rule-based transformation approach:
 * <ul>
 *   <li>Rules are declared in separate classes with @TransformRule annotations</li>
 *   <li>TransformationRegistry scans and registers all rules</li>
 *   <li>TransformationExecutor executes rules in proper order</li>
 *   <li>Supports parallel execution for large models</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Psm2AsmZetaTransformation {

    private final PsmModel psmModel;
    private final AsmModel asmModel;
    private final String modelName;
    private final String nsURI;
    private final String nsPrefix;

    @Builder
    public Psm2AsmZetaTransformation(
            @NonNull PsmModel psmModel,
            @NonNull AsmModel asmModel,
            @NonNull String modelName,
            String nsURI,
            String nsPrefix) {
        this.psmModel = psmModel;
        this.asmModel = asmModel;
        this.modelName = modelName;
        this.nsURI = nsURI != null ? nsURI : "http://blackbelt.hu/judo/" + modelName;
        this.nsPrefix = nsPrefix != null ? nsPrefix : "runtime" + modelName;
    }

    /**
     * Execute the transformation using TransformationExecutor.
     *
     * @return Zeta TransformationTrace containing source to target element mappings
     */
    public TransformationTrace execute() {
        log.info("Starting PSM to ASM Zeta transformation  for model: {}", modelName);
        long startTime = System.currentTimeMillis();

        // Clear helper caches from previous runs
        Psm2AsmHelper.clearCaches();

        // Create registry and register all rule classes
        TransformationRegistry registry = createRegistry();

        // Create transformation context
        TransformationContext context = createContext(registry);

        // Create executor - disable parallel execution to avoid recursive update issues
        // The transformation has complex dependencies between rules that require sequential execution
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)  // Sequential execution for complex rule dependencies
                .build();

        // Execute transformation
        log.info("Starting executor.transform()");
        TransformationResult result = executor.transform();
        log.info("Finished executor.transform()");

        // Post-processing: set up inheritance relationships that require all classes to exist
        postProcess(context);

        long duration = System.currentTimeMillis() - startTime;
        log.info("PSM to ASM Zeta transformation  completed in {}ms", duration);

        // Return native Zeta trace
        return result.getTrace();
    }

    /**
     * Creates and configures the TransformationRegistry with all rule classes.
     */
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();

        // Register rule classes in transformation order
        // Phase 1: Namespace rules (packages must exist first)
        registry.register(NamespaceRules.class);

        // Phase 2: Type rules (types must exist before entities)
        registry.register(TypeRules.class);

        // Phase 3: Data rules (entities, attributes, relations)
        registry.register(DataRules.class);

        // Phase 4: Transfer object rules
        registry.register(TransferObjectRules.class);

        // Phase 5: Derived property rules
        registry.register(DerivedRules.class);

        // Phase 6: Static data rules
        registry.register(StaticRules.class);

        // Phase 7: Operation rules
        registry.register(OperationRules.class);

        // Phase 8: Actor rules
        registry.register(ActorRules.class);

        log.debug("Registered {} rule classes with TransformationRegistry", 7);
        return registry;
    }

    /**
     * Creates and configures the TransformationContext.
     */
    private TransformationContext createContext(TransformationRegistry registry) {
        ResourceSet sourceResourceSet = psmModel.getResourceSet();
        ResourceSet targetResourceSet = asmModel.getResourceSet();

        // Create model provider
        ModelProvider modelProvider = new Psm2AsmModelProvider(psmModel, asmModel);

        // Create extension method registry (empty for now, can add extensions later)
        ExtensionMethodRegistry extensionRegistry = new ExtensionMethodRegistry();

        // Create context
        TransformationContext context = new TransformationContext(
                modelProvider,
                sourceResourceSet,
                targetResourceSet,
                extensionRegistry
        );

        // Configure context
        context.setTargetPackage(EcorePackage.eINSTANCE);
        context.setTransformationRegistry(registry);
        context.setUseStructuredIds(true);
        // Note: autoAddRootElements is intentionally disabled (default: false)
        // We add root elements in postProcess after all rules complete, so that
        // addToResource can recursively apply pending XMI IDs to all children

        // Register resources with aliases
        context.registerResource("psm", sourceResourceSet);
        context.registerResource("asm", targetResourceSet);

        // Use "psm" alias in structured XMI IDs to match ETL format
        context.setPreferredSourceAlias("psm");

        // Store configuration in context attributes for rules to access
        context.setAttribute("modelName", modelName);
        context.setAttribute("nsURI", nsURI);
        context.setAttribute("nsPrefix", nsPrefix);

        // Pre-compute getRangeInputTypes set for TransferObjectRules
        Set<TransferObjectType> getRangeInputTypes = computeGetRangeInputTypes();
        context.setAttribute("getRangeInputTypes", getRangeInputTypes);
        log.debug("Pre-computed {} getRangeInput types", getRangeInputTypes.size());

        return context;
    }

    /**
     * Post-processing after all rules have executed.
     * Handles cross-references that require all elements to exist.
     */
    private void postProcess(TransformationContext context) {
        hu.blackbelt.judo.meta.psm.PsmUtils psmUtils = new hu.blackbelt.judo.meta.psm.PsmUtils(psmModel.getResourceSet());

        // 1. Add root packages to the ASM model resource and apply pending XMI IDs
        // Note: addToResource only sets the ID for the root element when staging is disabled,
        // so we need to manually apply pending IDs to all children recursively
        XMIResource xmiResource = asmModel.getResource() instanceof XMIResource
                ? (XMIResource) asmModel.getResource() : null;

        psmUtils.all(psmModel.getResourceSet(), Model.class).forEach(model -> {
            EPackage rootPkg = context.equivalent(model, EPackage.class);
            if (rootPkg != null && !asmModel.getResource().getContents().contains(rootPkg)) {
                context.addToResource(rootPkg);
                // Apply pending XMI IDs to all children recursively
                applyPendingXmiIds(rootPkg, context, xmiResource);
                log.debug("Added root package '{}' to ASM resource", rootPkg.getName());
            }
        });
        
        // 2. Set EOpposite for bidirectional associations
        // This is done in post-processing to avoid recursive update issues
        psmUtils.all(psmModel.getResourceSet(), AssociationEnd.class).forEach(assocEnd -> {
            if (assocEnd.getPartner() != null) {
                EReference ref = context.equivalent(assocEnd, EReference.class);
                EReference partnerRef = context.equivalent(assocEnd.getPartner(), EReference.class);
                if (ref != null && partnerRef != null && ref.getEOpposite() == null) {
                    ref.setEOpposite(partnerRef);
                    log.trace("Set EOpposite for '{}' -> '{}'", ref.getName(), partnerRef.getName());
                }
            }
        });
        
        // 3. Set target types for TransferObjectRelations
        // This is done in post-processing to avoid recursive update issues
        psmUtils.all(psmModel.getResourceSet(), TransferObjectRelation.class).forEach(relation -> {
            if (relation.getTarget() != null) {
                EReference ref = context.equivalent(relation, EReference.class);
                EClass targetClass = context.equivalent(relation.getTarget(), EClass.class);
                if (ref != null && targetClass != null && ref.getEType() == null) {
                    ref.setEType(targetClass);
                    log.trace("Set target type for relation '{}' -> '{}'", ref.getName(), targetClass.getName());
                }
            }
        });
        
        // 4. Set up inheritance for Reference classes (Entity__Reference extends Parent__Reference)
        // This must be done in post-processing to ensure all reference classes exist first
        psmUtils.all(psmModel.getResourceSet(), EntityType.class).forEach(entityType -> {
            if (!entityType.getSuperEntityTypes().isEmpty()) {
                // Find the reference class for this entity by looking at equivalents
                String refClassName = entityType.getName() + "__Reference";
                EClass refClass = null;
                for (EClass candidate : context.equivalents(entityType, EClass.class)) {
                    if (refClassName.equals(candidate.getName())) {
                        refClass = candidate;
                        break;
                    }
                }
                
                if (refClass != null) {
                    // Set up inheritance from each super type's reference class
                    for (EntityType superType : entityType.getSuperEntityTypes()) {
                        String superRefClassName = superType.getName() + "__Reference";
                        for (EClass candidate : context.equivalents(superType, EClass.class)) {
                            if (superRefClassName.equals(candidate.getName())) {
                                if (!refClass.getESuperTypes().contains(candidate)) {
                                    refClass.getESuperTypes().add(candidate);
                                    log.trace("Set reference class inheritance: {} extends {}", 
                                            refClassName, superRefClassName);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        });
        
        // 5. Enrich model with annotations (exposedBy, etc.)
        // This is the same post-processing step that ETL calls
        AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
        asmUtils.enrichWithAnnotations();
        log.debug("Enriched ASM model with annotations");
    }

    /**
     * Recursively apply pending XMI IDs to an element and all its children.
     * This is needed because TransformationContext.addToResource only sets the ID for the
     * direct element when staging is disabled, not for its children.
     *
     * @param element     the element to apply IDs to
     * @param context     the transformation context containing pending IDs
     * @param xmiResource the XMI resource to set IDs on
     */
    private void applyPendingXmiIds(EObject element, TransformationContext context, XMIResource xmiResource) {
        if (xmiResource == null) {
            return;
        }

        // Iterate through all contained children
        for (EObject child : element.eContents()) {
            // Get and apply the pending XMI ID for this child
            String pendingId = context.getPendingXmiId(child);
            if (pendingId != null) {
                xmiResource.setID(child, pendingId);
                log.trace("Applied pending XMI ID '{}' to element {}", pendingId, child.eClass().getName());
            }
            // Recursively process this child's children
            applyPendingXmiIds(child, context, xmiResource);
        }
    }

    /**
     * Pre-compute the set of TransferObjectTypes that are used as input for GET_RANGE operations.
     * A transfer object is a get range input type if it's the input type of a GET_RANGE operation.
     */
    private Set<TransferObjectType> computeGetRangeInputTypes() {
        Set<TransferObjectType> result = new HashSet<>();
        hu.blackbelt.judo.meta.psm.PsmUtils psmUtils = new hu.blackbelt.judo.meta.psm.PsmUtils(psmModel.getResourceSet());
        
        psmUtils.all(psmModel.getResourceSet(), UnboundOperation.class).forEach(op -> {
            if (op.getBehaviour() != null 
                    && op.getBehaviour().getBehaviourType() == TransferOperationBehaviourType.GET_RANGE
                    && op.getInput() != null 
                    && op.getInput().getType() != null) {
                result.add(op.getInput().getType());
            }
        });
        
        return result;
    }

    /**
     * ModelProvider implementation for PSM to ASM transformation.
     */
    private static class Psm2AsmModelProvider implements ModelProvider {
        private final PsmModel psmModel;
        private final AsmModel asmModel;
        private final hu.blackbelt.judo.meta.psm.PsmUtils psmUtils;

        public Psm2AsmModelProvider(PsmModel psmModel, AsmModel asmModel) {
            this.psmModel = psmModel;
            this.asmModel = asmModel;
            this.psmUtils = new hu.blackbelt.judo.meta.psm.PsmUtils(psmModel.getResourceSet());
        }

        @Override
        public <T extends EObject> java.util.Collection<T> getAllContents(ResourceSet resourceSet, Class<T> type) {
            return psmUtils.all(resourceSet, type).toList();
        }

        @Override
        public String getName(EObject element) {
            if (element instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement) {
                return ((hu.blackbelt.judo.meta.psm.namespace.NamedElement) element).getName();
            }
            return ModelProvider.super.getName(element);
        }

        @Override
        public String getTypeName(EObject element) {
            return element.eClass().getName();
        }
    }
}
