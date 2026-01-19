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
import hu.blackbelt.judo.meta.psm.service.TransferOperation;
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
import hu.blackbelt.judo.zeta.transformation.core.TransformationMetrics;
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
        long phaseStart;

        // Phase 1: Clear helper caches
        phaseStart = System.currentTimeMillis();
        Psm2AsmHelper.clearCaches();
        log.info("Phase 1 - Clear caches: {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 2: Create registry and register all rule classes
        phaseStart = System.currentTimeMillis();
        TransformationRegistry registry = createRegistry();
        log.info("Phase 2 - Create registry: {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 3: Create transformation context
        phaseStart = System.currentTimeMillis();
        TransformationContext context = createContext(registry);
        log.info("Phase 3 - Create context: {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 4: Create executor
        phaseStart = System.currentTimeMillis();
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)  // Sequential execution - Zeta is faster without parallel overhead
                .build();
        log.info("Phase 4 - Create executor: {}ms", System.currentTimeMillis() - phaseStart);

        // Enable profiling
        TransformationMetrics.reset();
        TransformationMetrics.enable();

        // Phase 5: Execute transformation
        phaseStart = System.currentTimeMillis();
        log.info("Starting executor.transform()");
        TransformationResult result = executor.transform();
        long transformTime = System.currentTimeMillis() - phaseStart;
        log.info("Phase 5 - executor.transform(): {}ms", transformTime);

        // Print metrics report
        log.info("=== TRANSFORMATION METRICS ===\n{}", TransformationMetrics.getReport());
        TransformationMetrics.disable();

        // Phase 6: Post-processing
        phaseStart = System.currentTimeMillis();
        postProcess(context);
        log.info("Phase 6 - postProcess(): {}ms", System.currentTimeMillis() - phaseStart);

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

        // Phase 7b: BoundTransferOperation annotation rules (must be after OperationRules)
        registry.register(BoundTransferOperationAnnotationRules.class);

        // Phase 8: Actor rules
        registry.register(ActorRules.class);

        log.debug("Registered {} rule classes with TransformationRegistry", 8);
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
        context.setEtlCompatibilityMode(true);
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

        // Pre-compute metadataTypes set for TransferObjectRules (O(n²) → O(n) optimization)
        Set<TransferObjectType> metadataTypes = computeMetadataTypes();
        context.setAttribute("metadataTypes", metadataTypes);
        log.debug("Pre-computed {} metadata types", metadataTypes.size());

        return context;
    }

    /**
     * Post-processing after all rules have executed.
     * Handles cross-references that require all elements to exist.
     */
    private void postProcess(TransformationContext context) {
        hu.blackbelt.judo.meta.psm.PsmUtils psmUtils = new hu.blackbelt.judo.meta.psm.PsmUtils(psmModel.getResourceSet());
        long stepStart;

        // 1. Add root packages to the ASM model resource and apply pending XMI IDs
        stepStart = System.currentTimeMillis();
        XMIResource xmiResource = asmModel.getResource() instanceof XMIResource
                ? (XMIResource) asmModel.getResource() : null;

        psmUtils.all(psmModel.getResourceSet(), Model.class).forEach(model -> {
            EPackage rootPkg = context.equivalent(model, EPackage.class);
            if (rootPkg != null && !asmModel.getResource().getContents().contains(rootPkg)) {
                context.addToResource(rootPkg);
                applyPendingXmiIds(rootPkg, context, xmiResource);
                log.debug("Added root package '{}' to ASM resource", rootPkg.getName());
            }
        });
        log.info("  postProcess step 1 (add root packages): {}ms", System.currentTimeMillis() - stepStart);

        // 2. Set EOpposite for bidirectional associations
        stepStart = System.currentTimeMillis();
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
        log.info("  postProcess step 2 (set EOpposite): {}ms", System.currentTimeMillis() - stepStart);

        // 3. Set target types for TransferObjectRelations
        stepStart = System.currentTimeMillis();
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
        log.info("  postProcess step 3 (set TransferObjectRelation types): {}ms", System.currentTimeMillis() - stepStart);

        // 4. Set up inheritance for Reference classes
        stepStart = System.currentTimeMillis();
        psmUtils.all(psmModel.getResourceSet(), EntityType.class).forEach(entityType -> {
            if (!entityType.getSuperEntityTypes().isEmpty()) {
                String refClassName = entityType.getName() + "__Reference";
                EClass refClass = null;
                for (EClass candidate : context.equivalents(entityType, EClass.class)) {
                    if (refClassName.equals(candidate.getName())) {
                        refClass = candidate;
                        break;
                    }
                }

                if (refClass != null) {
                    for (EntityType superType : entityType.getSuperEntityTypes()) {
                        String superRefClassName = superType.getName() + "__Reference";
                        for (EClass candidate : context.equivalents(superType, EClass.class)) {
                            if (superRefClassName.equals(candidate.getName())) {
                                if (!refClass.getESuperTypes().contains(candidate)) {
                                    Psm2AsmHelper.addSuperType(refClass, candidate);
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
        log.info("  postProcess step 4 (Reference class inheritance): {}ms", System.currentTimeMillis() - stepStart);

        // 5. Enrich model with annotations (exposedBy, etc.)
        stepStart = System.currentTimeMillis();
        AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());

        // Debug: Log model state before enrichWithAnnotations
        logModelStateBeforeEnrich(asmModel.getResourceSet(), "Zeta");

        asmUtils.enrichWithAnnotations();

        // Debug: Log model state after enrichWithAnnotations
        logModelStateAfterEnrich(asmModel.getResourceSet(), "Zeta");

        log.info("  postProcess step 5 (enrichWithAnnotations): {}ms", System.currentTimeMillis() - stepStart);

        // 6. Fix null eType for EAttributes (extension transfer object type fixup)
        // This addresses cross-resource type reference issues where ctx.equivalent() returns null
        stepStart = System.currentTimeMillis();
        int fixedCount = fixNullAttributeTypes(context, psmUtils, asmUtils);
        log.info("  postProcess step 6 (fix null eType): {}ms, fixed {} attributes",
                System.currentTimeMillis() - stepStart, fixedCount);
    }

    /**
     * Fix EAttributes with null eType by looking up the type by name.
     * <p>
     * This addresses an issue where extension transfer object types (_default_, _binding_)
     * have attributes whose dataType references don't match the type instances transformed
     * by TypeRules, causing ctx.equivalent() to return null.
     * </p>
     * <p>
     * The approach is to iterate through all EAttributes in the target model, find those
     * with null eType, and use the trace to look up the PSM source and determine the
     * correct type.
     * </p>
     *
     * @param context  the transformation context
     * @param psmUtils PSM utilities for iterating source model
     * @param asmUtils ASM utilities for finding types by name
     * @return the number of attributes fixed
     */
    private int fixNullAttributeTypes(TransformationContext context,
            hu.blackbelt.judo.meta.psm.PsmUtils psmUtils, AsmUtils asmUtils) {
        int fixedCount = 0;

        // Build a map of type names to EClassifiers in the target model
        Map<String, org.eclipse.emf.ecore.EClassifier> typesByName = new HashMap<>();
        for (var resource : asmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var content = iterator.next();
                if (content instanceof org.eclipse.emf.ecore.EDataType) {
                    org.eclipse.emf.ecore.EDataType dataType = (org.eclipse.emf.ecore.EDataType) content;
                    typesByName.put(dataType.getName(), dataType);
                } else if (content instanceof org.eclipse.emf.ecore.EEnum) {
                    org.eclipse.emf.ecore.EEnum enumType = (org.eclipse.emf.ecore.EEnum) content;
                    typesByName.put(enumType.getName(), enumType);
                }
            }
        }
        log.debug("Built type lookup map with {} types", typesByName.size());

        // Build a map of PSM TransferAttribute by attribute name for reverse lookup
        // The ASM extension class name matches the PSM attribute name (for _default_ and _binding_ types)
        Map<String, hu.blackbelt.judo.meta.psm.service.TransferAttribute> psmAttrByName = new HashMap<>();
        for (hu.blackbelt.judo.meta.psm.service.TransferAttribute psmAttr :
                psmUtils.all(psmModel.getResourceSet(), hu.blackbelt.judo.meta.psm.service.TransferAttribute.class).toList()) {
            String attrName = psmAttr.getName();
            if (attrName != null && (attrName.contains("_default_") || attrName.contains("_binding_"))) {
                psmAttrByName.put(attrName, psmAttr);
            }
        }
        log.debug("Built PSM TransferAttribute lookup map with {} _default_/_binding_ attributes", psmAttrByName.size());

        // Iterate through all EAttributes in the target model
        int nullTypeCount = 0;
        for (var resource : asmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var content = iterator.next();
                if (content instanceof org.eclipse.emf.ecore.EAttribute) {
                    org.eclipse.emf.ecore.EAttribute asmAttr = (org.eclipse.emf.ecore.EAttribute) content;
                    if (asmAttr.getEType() == null) {
                        nullTypeCount++;

                        // The ASM extension class name matches the PSM attribute name
                        // For classes like _simpleReviewReport_default_ReviewReportInput,
                        // there's a PSM TransferAttribute with name _simpleReviewReport_default_ReviewReportInput
                        String ownerClassName = asmAttr.getEContainingClass() != null
                                ? asmAttr.getEContainingClass().getName() : null;

                        log.debug("Found EAttribute with null eType: '{}' in '{}', PSM match by class name: {}",
                                asmAttr.getName(), ownerClassName,
                                ownerClassName != null && psmAttrByName.containsKey(ownerClassName));

                        hu.blackbelt.judo.meta.psm.service.TransferAttribute psmAttr = null;
                        if (ownerClassName != null && (ownerClassName.contains("_default_") || ownerClassName.contains("_binding_"))) {
                            // For extension types, look up PSM attribute by the class name
                            psmAttr = psmAttrByName.get(ownerClassName);
                        }

                        if (psmAttr != null && psmAttr.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive) {
                            hu.blackbelt.judo.meta.psm.type.Primitive psmDataType = psmAttr.getDataType();
                            String typeName = getAsmTypeName(psmDataType);
                            org.eclipse.emf.ecore.EClassifier asmType = typesByName.get(typeName);

                            if (asmType != null) {
                                asmAttr.setEType(asmType);
                                fixedCount++;
                                log.info("Fixed eType for attribute '{}' in '{}' -> {}",
                                        asmAttr.getName(), ownerClassName, typeName);
                            } else {
                                log.warn("Could not find type '{}' for attribute '{}' in '{}'",
                                        typeName, asmAttr.getName(), ownerClassName);
                            }
                        } else if (psmAttr != null) {
                            log.info("PSM attribute '{}' has non-Primitive dataType: {}",
                                    psmAttr.getName(), psmAttr.getDataType());
                        } else if (ownerClassName != null && ownerClassName.contains("_binding_")) {
                            // For _binding_ extension types, try to infer type from the containing class
                            // These are typically Boolean values from validation bindings
                            // Search for a PrimitiveAccessor with matching name pattern
                            String typeName = inferTypeForBindingClass(context, psmUtils, ownerClassName);
                            if (typeName != null) {
                                org.eclipse.emf.ecore.EClassifier asmType = typesByName.get(typeName);
                                if (asmType != null) {
                                    asmAttr.setEType(asmType);
                                    fixedCount++;
                                    log.info("Fixed eType for binding attribute '{}' in '{}' -> {} (inferred)",
                                            asmAttr.getName(), ownerClassName, typeName);
                                }
                            } else {
                                log.warn("Could not infer type for binding class '{}' (attr: {})",
                                        ownerClassName, asmAttr.getName());
                            }
                        } else if (ownerClassName != null && ownerClassName.contains("_default_")) {
                            log.warn("Could not find PSM TransferAttribute for class '{}' (attr: {}), map has key: {}",
                                    ownerClassName, asmAttr.getName(), psmAttrByName.containsKey(ownerClassName));
                        }
                    }
                }
            }
        }

        log.info("Found {} EAttributes with null eType, fixed {}", nullTypeCount, fixedCount);
        return fixedCount;
    }

    /**
     * Infer the type for a _binding_ extension class by searching PSM PrimitiveAccessor elements.
     * <p>
     * The naming pattern is _<propertyName>_binding_<TransferObjectType>.
     * We search for PrimitiveAccessor elements that match the property name prefix.
     * </p>
     *
     * @param context  the transformation context
     * @param psmUtils PSM utilities for iterating source model
     * @param className the binding class name like "_falseFlag_binding_ReviewReportInput"
     * @return the ASM type name or null if not found
     */
    private String inferTypeForBindingClass(TransformationContext context,
            hu.blackbelt.judo.meta.psm.PsmUtils psmUtils, String className) {
        // Extract the property name from the pattern _<propertyName>_binding_<type>
        // e.g., "_falseFlag_binding_ReviewReportInput" -> "falseFlag"
        int bindingIdx = className.indexOf("_binding_");
        if (bindingIdx <= 1) {
            return null; // Invalid pattern
        }
        String propertyName = className.substring(1, bindingIdx); // Remove leading _ and get until _binding_

        log.debug("Looking for PrimitiveAccessor with name '{}' for binding class '{}'", propertyName, className);

        // Search PrimitiveAccessor elements for one with matching name
        for (hu.blackbelt.judo.meta.psm.derived.PrimitiveAccessor accessor :
                psmUtils.all(psmModel.getResourceSet(), hu.blackbelt.judo.meta.psm.derived.PrimitiveAccessor.class).toList()) {
            if (propertyName.equals(accessor.getName()) && accessor.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive) {
                hu.blackbelt.judo.meta.psm.type.Primitive dataType = accessor.getDataType();
                String typeName = getAsmTypeName(dataType);
                log.debug("Found PrimitiveAccessor '{}' with dataType '{}' -> '{}'", accessor.getName(), dataType, typeName);
                return typeName;
            }
        }

        // Also check StaticData elements
        for (hu.blackbelt.judo.meta.psm.derived.StaticData staticData :
                psmUtils.all(psmModel.getResourceSet(), hu.blackbelt.judo.meta.psm.derived.StaticData.class).toList()) {
            if (propertyName.equals(staticData.getName()) && staticData.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive) {
                hu.blackbelt.judo.meta.psm.type.Primitive dataType = staticData.getDataType();
                String typeName = getAsmTypeName(dataType);
                log.debug("Found StaticData '{}' with dataType '{}' -> '{}'", staticData.getName(), dataType, typeName);
                return typeName;
            }
        }

        // Also check TransferAttribute elements with binding expressions
        for (hu.blackbelt.judo.meta.psm.service.TransferAttribute attr :
                psmUtils.all(psmModel.getResourceSet(), hu.blackbelt.judo.meta.psm.service.TransferAttribute.class).toList()) {
            if (propertyName.equals(attr.getName()) && attr.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive) {
                hu.blackbelt.judo.meta.psm.type.Primitive dataType = attr.getDataType();
                String typeName = getAsmTypeName(dataType);
                log.debug("Found TransferAttribute '{}' with dataType '{}' -> '{}'", attr.getName(), dataType, typeName);
                return typeName;
            }
        }

        // Also check DataProperty elements (derived properties)
        for (hu.blackbelt.judo.meta.psm.derived.DataProperty dataProp :
                psmUtils.all(psmModel.getResourceSet(), hu.blackbelt.judo.meta.psm.derived.DataProperty.class).toList()) {
            if (propertyName.equals(dataProp.getName()) && dataProp.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive) {
                hu.blackbelt.judo.meta.psm.type.Primitive dataType = dataProp.getDataType();
                String typeName = getAsmTypeName(dataType);
                log.debug("Found DataProperty '{}' with dataType '{}' -> '{}'", dataProp.getName(), dataType, typeName);
                return typeName;
            }
        }

        return null;
    }

    /**
     * Get the ASM type name for a PSM Primitive type.
     * This maps PSM type kinds to their ASM EDataType/EEnum names.
     */
    private String getAsmTypeName(hu.blackbelt.judo.meta.psm.type.Primitive psmType) {
        if (psmType.isString()) {
            return "String";
        } else if (psmType.isInteger()) {
            return "Integer";
        } else if (psmType.isDecimal()) {
            return "Decimal";
        } else if (psmType.isBoolean()) {
            return "Boolean";
        } else if (psmType.isDate()) {
            return "Date";
        } else if (psmType.isTimestamp()) {
            return "Timestamp";
        } else if (psmType.isTime()) {
            return "Time";
        } else if (psmType.isEnumeration()) {
            // For enumerations, use the actual type name
            return psmType.getName();
        } else if (psmType instanceof hu.blackbelt.judo.meta.psm.type.BinaryType) {
            return "Binary";
        } else if (psmType instanceof hu.blackbelt.judo.meta.psm.type.PasswordType) {
            return "Password";
        } else if (psmType instanceof hu.blackbelt.judo.meta.psm.type.XMLType) {
            return "XML";
        } else {
            // Custom type - use the actual name
            return psmType.getName();
        }
    }

    /**
     * Remove duplicate annotations (same source and identical details).
     * This fixes issues where enrichWithAnnotations adds duplicate annotations.
     */
    private int removeDuplicateAnnotations(ResourceSet resourceSet) {
        int totalRemoved = 0;

        for (var resource : resourceSet.getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var content = iterator.next();
                if (content instanceof org.eclipse.emf.ecore.EModelElement) {
                    totalRemoved += removeDuplicateAnnotations((org.eclipse.emf.ecore.EModelElement) content);
                }
            }
        }

        return totalRemoved;
    }

    /**
     * Remove duplicate annotations from a single element.
     */
    private int removeDuplicateAnnotations(org.eclipse.emf.ecore.EModelElement element) {
        var annotations = element.getEAnnotations();
        if (annotations.size() <= 1) {
            return 0;
        }

        // Build a map of unique annotation signatures
        java.util.Map<String, org.eclipse.emf.ecore.EAnnotation> uniqueAnnotations = new java.util.LinkedHashMap<>();

        for (var annotation : annotations) {
            // Build a signature from source and sorted details
            StringBuilder signature = new StringBuilder();
            signature.append(annotation.getSource());
            var sortedDetails = annotation.getDetails().stream()
                    .sorted(java.util.Comparator.comparing(java.util.Map.Entry::getKey))
                    .toList();
            for (var detail : sortedDetails) {
                signature.append("|").append(detail.getKey()).append("=").append(detail.getValue());
            }

            String signatureStr = signature.toString();
            if (!uniqueAnnotations.containsKey(signatureStr)) {
                uniqueAnnotations.put(signatureStr, annotation);
            }
        }

        if (uniqueAnnotations.size() < annotations.size()) {
            annotations.clear();
            annotations.addAll(uniqueAnnotations.values());
            return annotations.size() - uniqueAnnotations.size();
        }
        return 0;
    }

    /**
     * Log the model state before enrichWithAnnotations is called.
     * This helps compare ETL vs Zeta model states.
     */
    private void logModelStateBeforeEnrich(ResourceSet resourceSet, String label) {
        // Count annotations per element type
        int entityAnnotations = 0;
        int operationAnnotations = 0;
        int parameterAnnotations = 0;
        int attributeAnnotations = 0;
        int referenceAnnotations = 0;
        int otherAnnotations = 0;

        // Count annotations by type on operations
        Map<String, Integer> operationAnnotationCounts = new HashMap<>();

        for (var resource : resourceSet.getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var content = iterator.next();
                if (content instanceof org.eclipse.emf.ecore.EModelElement) {
                    var element = (org.eclipse.emf.ecore.EModelElement) content;
                    int count = element.getEAnnotations().size();
                    if (count > 0) {
                        if (content instanceof org.eclipse.emf.ecore.EClass) {
                            entityAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EOperation) {
                            operationAnnotations += count;
                            for (var ann : element.getEAnnotations()) {
                                String source = ann.getSource();
                                operationAnnotationCounts.merge(source, 1, Integer::sum);
                            }
                        } else if (content instanceof org.eclipse.emf.ecore.EParameter) {
                            parameterAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EAttribute) {
                            attributeAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EReference) {
                            referenceAnnotations += count;
                        } else {
                            otherAnnotations += count;
                        }
                    }
                }
            }
        }

        log.info("=== {} MODEL STATE BEFORE enrichWithAnnotations ===", label);
        log.info("  EClass annotations: {}", entityAnnotations);
        log.info("  EOperation annotations: {}", operationAnnotations);
        log.info("  EParameter annotations: {}", parameterAnnotations);
        log.info("  EAttribute annotations: {}", attributeAnnotations);
        log.info("  EReference annotations: {}", referenceAnnotations);
        log.info("  Other annotations: {}", otherAnnotations);
        log.info("  Total: {}", entityAnnotations + operationAnnotations + parameterAnnotations +
                attributeAnnotations + referenceAnnotations + otherAnnotations);
        log.info("  Operation annotation counts: {}", operationAnnotationCounts);
    }

    /**
     * Log the model state after enrichWithAnnotations is called.
     */
    private void logModelStateAfterEnrich(ResourceSet resourceSet, String label) {
        // Count annotations per element type
        int entityAnnotations = 0;
        int operationAnnotations = 0;
        int parameterAnnotations = 0;
        int attributeAnnotations = 0;
        int referenceAnnotations = 0;
        int otherAnnotations = 0;

        for (var resource : resourceSet.getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                var content = iterator.next();
                if (content instanceof org.eclipse.emf.ecore.EModelElement) {
                    var element = (org.eclipse.emf.ecore.EModelElement) content;
                    int count = element.getEAnnotations().size();
                    if (count > 0) {
                        if (content instanceof org.eclipse.emf.ecore.EClass) {
                            entityAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EOperation) {
                            operationAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EParameter) {
                            parameterAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EAttribute) {
                            attributeAnnotations += count;
                        } else if (content instanceof org.eclipse.emf.ecore.EReference) {
                            referenceAnnotations += count;
                        } else {
                            otherAnnotations += count;
                        }
                    }
                }
            }
        }

        log.info("=== {} MODEL STATE AFTER enrichWithAnnotations ===", label);
        log.info("  EClass annotations: {} (added: {})", entityAnnotations,
                entityAnnotations > 0 ? "+" + entityAnnotations : "0");
        log.info("  EOperation annotations: {} (added: {})", operationAnnotations,
                operationAnnotations > 0 ? "+" + operationAnnotations : "0");
        log.info("  EParameter annotations: {} (added: {})", parameterAnnotations,
                parameterAnnotations > 0 ? "+" + parameterAnnotations : "0");
        log.info("  EAttribute annotations: {} (added: {})", attributeAnnotations,
                attributeAnnotations > 0 ? "+" + attributeAnnotations : "0");
        log.info("  EReference annotations: {} (added: {})", referenceAnnotations,
                referenceAnnotations > 0 ? "+" + referenceAnnotations : "0");
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
     * Get a human-readable name for an EObject.
     */
    private String getElementName(EObject element) {
        if (element instanceof org.eclipse.emf.ecore.ENamedElement) {
            return ((org.eclipse.emf.ecore.ENamedElement) element).getName();
        }
        return element.eClass().getName();
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
     * Pre-compute the set of TransferObjectTypes that are metadata types.
     * <p>
     * A transfer object is a metadata type if it's the output type (or a relation target of output type)
     * of a GET_METADATA operation. This pre-computation eliminates O(n²) complexity in the
     * isMetadataType guard method by computing the set once at transformation start.
     * </p>
     * <p>
     * ETL logic: JUDOPSM!TransferOperation.all().exists(o | o.behaviour.isDefined()
     *     and o.behaviour.behaviourType == GET_METADATA
     *     and o.output.isDefined()
     *     and (o.output.type == self or o.output.type.relations.exists(r | r.target == self)))
     * </p>
     */
    private Set<TransferObjectType> computeMetadataTypes() {
        Set<TransferObjectType> result = new HashSet<>();
        hu.blackbelt.judo.meta.psm.PsmUtils psmUtils = new hu.blackbelt.judo.meta.psm.PsmUtils(psmModel.getResourceSet());

        psmUtils.all(psmModel.getResourceSet(), TransferOperation.class).forEach(op -> {
            if (op.getBehaviour() != null
                    && op.getBehaviour().getBehaviourType() == TransferOperationBehaviourType.GET_METADATA
                    && op.getOutput() != null
                    && op.getOutput().getType() != null) {
                // Add the output type itself
                TransferObjectType outputType = op.getOutput().getType();
                result.add(outputType);

                // Add all relation targets of the output type
                if (outputType.getRelations() != null) {
                    for (TransferObjectRelation relation : outputType.getRelations()) {
                        if (relation.getTarget() != null) {
                            result.add(relation.getTarget());
                        }
                    }
                }
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
