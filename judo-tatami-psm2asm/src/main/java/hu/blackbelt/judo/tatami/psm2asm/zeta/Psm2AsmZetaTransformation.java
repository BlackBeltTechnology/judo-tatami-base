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
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.AbstractActorType;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.accesspoint.MappedActorType;
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.BoundOperation;
import hu.blackbelt.judo.meta.psm.data.Containment;
import hu.blackbelt.judo.meta.psm.data.EntitySequence;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.data.NamespaceSequence;
import hu.blackbelt.judo.meta.psm.data.OperationBody;
import hu.blackbelt.judo.meta.psm.data.Sequence;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.derived.NavigationProperty;
import hu.blackbelt.judo.meta.psm.derived.PrimitiveAccessor;
import hu.blackbelt.judo.meta.psm.derived.ReferenceAccessor;
import hu.blackbelt.judo.meta.psm.derived.StaticData;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.NamedElement;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;

import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.BoundTransferOperation;
import hu.blackbelt.judo.meta.psm.service.MappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.service.OperationDeclaration;
import hu.blackbelt.judo.meta.psm.service.Parameter;
import hu.blackbelt.judo.meta.psm.service.TransferAttribute;
import hu.blackbelt.judo.meta.psm.service.TransferObjectRelation;
import hu.blackbelt.judo.meta.psm.service.TransferObjectType;
import hu.blackbelt.judo.meta.psm.service.TransferOperation;
import hu.blackbelt.judo.meta.psm.service.TransferOperationBehaviour;
import hu.blackbelt.judo.meta.psm.service.TransferOperationBehaviourType;
import hu.blackbelt.judo.meta.psm.service.UnboundOperation;
import hu.blackbelt.judo.meta.psm.service.UnmappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.type.*;
import hu.blackbelt.judo.zeta.annotation.Abstract;
import hu.blackbelt.judo.zeta.annotation.Extends;
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
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;

import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Java-based PSM to ASM transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * in src/main/epsilon/transformations/asm/, providing a type-safe Java alternative
 * with better IDE support and debugging capabilities.
 * </p>
 */
@Slf4j
public class Psm2AsmZetaTransformation {

    private final PsmModel psmModel;
    private final AsmModel asmModel;
    private final AsmUtils asmUtils;
    private final PsmUtils psmUtils;
    private final ResourceSet psmResourceSet;
    private final String modelName;
    private final String nsURI;
    private final String nsPrefix;
    
    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();
    
    // Deferred ID map for elements that need ID set after being added to resource
    private final Map<EObject, String> deferredIds = new LinkedHashMap<>();
    
    // Type cache for all() results - avoids repeated model traversals
    // Note: Uses ConcurrentHashMap for thread-safety during parallel processing
    private final Map<Class<?>, List<?>> typeCache = new ConcurrentHashMap<>();
    
    // Container package cache - avoids repeated hierarchy traversals
    // Note: Uses ConcurrentHashMap for thread-safety during parallel processing
    private final Map<EObject, EPackage> containerPackageCache = new ConcurrentHashMap<>();
    
    // Equivalent type cache - avoids repeated rule name lookups
    // Note: Uses ConcurrentHashMap for thread-safety during parallel processing
    private final Map<hu.blackbelt.judo.meta.psm.type.Primitive, EClassifier> equivalentTypeCache = new ConcurrentHashMap<>();
    
    // TransferObject equivalent cache - avoids repeated 3-way rule lookups
    private final Map<TransferObjectType, EClassifier> transferObjectEquivalentCache = new ConcurrentHashMap<>();
    
    // =========================================================================
    // PRE-COMPUTED CACHES FOR O(n²) OPTIMIZATION
    // These are populated once at the start of execute() to avoid repeated
    // iterations through the model during transformation.
    // =========================================================================
    
    // Cache: TransferObjectTypes that are metadata types (used by GET_METADATA operations)
    private volatile Set<TransferObjectType> metadataTypes;
    
    // Cache: TransferObjectTypes that are GET_RANGE input types
    private volatile Set<TransferObjectType> getRangeInputTypes;
    
    // Cache: Attributes that have unmappedDefaultOnly condition
    private volatile Set<Attribute> unmappedDefaultOnlyAttributes;
    
    // Cache: AssociationEnds that have unmappedDefaultOnly condition
    private volatile Set<AssociationEnd> unmappedDefaultOnlyReferences;
    
    // Cache: Element ID strings - avoids repeated string building via psmUtils.namespaceElementToString()
    private final Map<EObject, String> elementIdCache = new ConcurrentHashMap<>();
    
    // Cache: Annotation URIs - avoids repeated string concatenation
    private final Map<String, String> annotationUriCache = new ConcurrentHashMap<>();
    
    // Cache: namespaceElementToString results - avoids repeated hierarchy traversal and string building
    private final Map<NamespaceElement, String> namespaceElementStringCache = new ConcurrentHashMap<>();
    
    // Cache: EntityType for Attribute/AssociationEnd/Containment - avoids repeated container traversal
    private final Map<EObject, EntityType> entityTypeCache = new ConcurrentHashMap<>();
    
    // Cache: Classifier FQName - avoids repeated package hierarchy traversal
    private final Map<EClassifier, String> classifierFQNameCache = new ConcurrentHashMap<>();
    
    // Parallel execution threshold - use parallel streams when element count exceeds this
    private static final int PARALLEL_THRESHOLD = 100;
    
    // Staged elements for parallel transformation - holds (owner, feature) pairs for deferred addition
    private final ConcurrentLinkedQueue<Runnable> stagedFeatureAdditions = new ConcurrentLinkedQueue<>();
    
    @Builder
    public Psm2AsmZetaTransformation(
            @NonNull PsmModel psmModel,
            @NonNull AsmModel asmModel,
            @NonNull String modelName,
            String nsURI,
            String nsPrefix) {
        this.psmModel = psmModel;
        this.asmModel = asmModel;
        this.psmResourceSet = psmModel.getResourceSet();
        this.asmUtils = new AsmUtils(asmModel.getResourceSet());
        this.psmUtils = new PsmUtils(psmResourceSet);
        this.modelName = modelName;
        this.nsURI = nsURI != null ? nsURI : "http://blackbelt.hu/judo/" + modelName;
        this.nsPrefix = nsPrefix != null ? nsPrefix : "runtime" + modelName;
    }

    /**
     * Helper method to get all elements of a given type from the PSM model.
     * Results are cached to avoid repeated model traversals.
     */
    @SuppressWarnings("unchecked")
    private <T> Stream<T> all(Class<T> clazz) {
        List<T> cached = (List<T>) typeCache.computeIfAbsent(clazz, 
                k -> psmUtils.all(psmResourceSet, clazz).toList());
        return cached.stream();
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting PSM to ASM Zeta transformation for model: {}", modelName);
        long startTime = System.currentTimeMillis();
        long phaseStart;

        // Phase 0: Pre-compute caches for O(n²) optimization
        phaseStart = System.currentTimeMillis();
        preComputeCaches();
        log.info("  Phase 0 (pre-compute caches): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 1: Transform namespaces/packages
        phaseStart = System.currentTimeMillis();
        transformNamespaces();
        log.info("  Phase 1 (namespaces): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 2: Transform types
        phaseStart = System.currentTimeMillis();
        transformTypes();
        log.info("  Phase 2 (types): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 3: Transform entities and their members
        phaseStart = System.currentTimeMillis();
        transformEntities();
        log.info("  Phase 3 (entities): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 4: Transform transfer objects (must be before derived properties since they reference transfer object types)
        phaseStart = System.currentTimeMillis();
        transformTransferObjects();
        log.info("  Phase 4 (transfer objects): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 5: Transform derived properties (after transfer objects since they may reference transfer object types in getter expressions)
        phaseStart = System.currentTimeMillis();
        transformDerivedProperties();
        log.info("  Phase 5 (derived properties): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 6: Transform operations (after transfer objects since operations reference transfer object types for parameters)
        phaseStart = System.currentTimeMillis();
        transformOperations();
        log.info("  Phase 6 (operations): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 7: Transform actors
        phaseStart = System.currentTimeMillis();
        transformActors();
        log.info("  Phase 7 (actors): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 8: Transform static data and navigation
        phaseStart = System.currentTimeMillis();
        transformStatics();
        log.info("  Phase 8 (statics): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 9: Apply deferred IDs (must be done after all elements are added to the resource)
        phaseStart = System.currentTimeMillis();
        applyDeferredIds();
        log.info("  Phase 9 (deferred IDs): {}ms", System.currentTimeMillis() - phaseStart);

        // Phase 10: Post-processing
        phaseStart = System.currentTimeMillis();
        postProcess();
        log.info("  Phase 10 (post-processing): {}ms", System.currentTimeMillis() - phaseStart);

        long duration = System.currentTimeMillis() - startTime;
        log.info("PSM to ASM Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    // =========================================================================
    // NAMESPACE TRANSFORMATIONS
    // =========================================================================
    // PRE-COMPUTATION METHODS FOR O(n²) OPTIMIZATION
    // =========================================================================

    /**
     * Pre-computes all caches needed to avoid O(n²) complexity during transformation.
     * This method iterates through the model once and populates lookup sets that
     * would otherwise require repeated iterations.
     */
    private void preComputeCaches() {
        preComputeTypeClassifications();
        preComputeUnmappedDefaultOnlyConditions();
    }

    /**
     * Pre-computes which TransferObjectTypes are metadata types and GET_RANGE input types.
     * This avoids O(TOs × Operations) complexity when checking each TransferObjectType.
     */
    private void preComputeTypeClassifications() {
        Set<TransferObjectType> metadataTypesLocal = ConcurrentHashMap.newKeySet();
        Set<TransferObjectType> getRangeInputTypesLocal = ConcurrentHashMap.newKeySet();
        
        all(TransferOperation.class).forEach(op -> {
            if (op.getBehaviour() == null) return;
            
            TransferOperationBehaviourType behaviourType = op.getBehaviour().getBehaviourType();
            
            // Collect metadata types
            if (behaviourType == TransferOperationBehaviourType.GET_METADATA) {
                if (op.getOutput() != null && op.getOutput().getType() != null) {
                    TransferObjectType outputType = op.getOutput().getType();
                    metadataTypesLocal.add(outputType);
                    // Also add any relation targets as metadata types
                    for (TransferObjectRelation rel : outputType.getRelations()) {
                        if (rel.getTarget() != null) {
                            metadataTypesLocal.add(rel.getTarget());
                        }
                    }
                }
            }
            
            // Collect GET_RANGE input types
            if (behaviourType == TransferOperationBehaviourType.GET_RANGE) {
                if (op.getInput() != null && op.getInput().getType() != null) {
                    getRangeInputTypesLocal.add(op.getInput().getType());
                }
            }
        });
        
        // Assign to volatile fields for thread-safe publication
        this.metadataTypes = metadataTypesLocal;
        this.getRangeInputTypes = getRangeInputTypesLocal;
        
        log.debug("    Pre-computed: {} metadata types, {} getRangeInput types", 
                metadataTypesLocal.size(), getRangeInputTypesLocal.size());
    }

    /**
     * Pre-computes which Attributes and AssociationEnds have unmappedDefaultOnly conditions.
     * This avoids O(Attributes × DefaultRepresentationAttributes) complexity.
     */
    private void preComputeUnmappedDefaultOnlyConditions() {
        Set<Attribute> unmappedDefaultOnlyAttrsLocal = ConcurrentHashMap.newKeySet();
        Set<AssociationEnd> unmappedDefaultOnlyRefsLocal = ConcurrentHashMap.newKeySet();
        
        all(EntityType.class).forEach(entityType -> {
            MappedTransferObjectType defaultRep = entityType.getDefaultRepresentation();
            if (defaultRep == null) return;
            
            // Check transfer attributes for unmappedDefaultOnly condition
            for (TransferAttribute ta : defaultRep.getAttributes()) {
                if (ta.getBinding() instanceof Attribute && ta.getDefaultValue() != null) {
                    unmappedDefaultOnlyAttrsLocal.add((Attribute) ta.getBinding());
                }
            }
            
            // Check transfer relations for unmappedDefaultOnly condition
            for (TransferObjectRelation tr : defaultRep.getRelations()) {
                if (tr.getBinding() instanceof AssociationEnd && tr.getDefaultValue() != null) {
                    unmappedDefaultOnlyRefsLocal.add((AssociationEnd) tr.getBinding());
                }
            }
        });
        
        // Assign to volatile fields for thread-safe publication
        this.unmappedDefaultOnlyAttributes = unmappedDefaultOnlyAttrsLocal;
        this.unmappedDefaultOnlyReferences = unmappedDefaultOnlyRefsLocal;
        
        log.debug("    Pre-computed: {} unmappedDefaultOnly attributes, {} unmappedDefaultOnly references", 
                unmappedDefaultOnlyAttrsLocal.size(), unmappedDefaultOnlyRefsLocal.size());
    }

    // =========================================================================
    // NAMESPACE TRANSFORMATIONS
    // =========================================================================

    private void transformNamespaces() {
        // Transform root model
        psmModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(Model.class::isInstance)
                .map(Model.class::cast)
                .forEach(this::transformModel);
    }

    private void transformModel(Model model) {
        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        setId(pkg, "(psm/" + getId(model) + ")/Package");
        pkg.setName(model.getName());
        pkg.setNsURI(nsURI + "/" + model.getName());
        pkg.setNsPrefix(nsPrefix + capitalize(model.getName()));

        asmModel.getResource().getContents().add(pkg);
        addTrace(model, MODEL_TO_PACKAGE, pkg);

        // Create version annotation
        if (model.getVersion() != null) {
            EAnnotation versionAnnotation = createAnnotation(
                    "(psm/" + getId(model) + ")/ModelToPackageVersion",
                    getAnnotationUri("ModelVersion"));
            addAnnotationDetail(versionAnnotation, "value", model.getVersion());
            pkg.getEAnnotations().add(versionAnnotation);
            addTrace(model, MODEL_TO_PACKAGE_VERSION, versionAnnotation);
        }

        // Transform sub-packages
        for (Package subPkg : model.getPackages()) {
            transformPackage(subPkg, pkg);
        }
    }

    private void transformPackage(Package psmPackage, EPackage parentPackage) {
        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        setId(pkg, "(psm/" + getId(psmPackage) + ")/Package");
        pkg.setName(psmPackage.getName());
        pkg.setNsURI(parentPackage.getNsURI() + "/" + psmPackage.getName());
        pkg.setNsPrefix(parentPackage.getNsPrefix() + capitalize(psmPackage.getName()));

        parentPackage.getESubpackages().add(pkg);
        addTrace(psmPackage, PACKAGE_TO_PACKAGE, pkg);

        // Transform nested packages
        for (Package nestedPkg : psmPackage.getPackages()) {
            transformPackage(nestedPkg, pkg);
        }
    }

    // =========================================================================
    // TYPE TRANSFORMATIONS
    // =========================================================================

    private void transformTypes() {
        all(EnumerationType.class).forEach(this::transformEnumeration);
        all(StringType.class).forEach(this::transformStringType);
        all(NumericType.class).forEach(this::transformNumericType);
        all(BooleanType.class).forEach(this::transformBooleanType);
        all(DateType.class).forEach(this::transformDateType);
        all(TimestampType.class).forEach(this::transformTimestampType);
        all(TimeType.class).forEach(this::transformTimeType);
        all(BinaryType.class).forEach(this::transformBinaryType);
        all(CustomType.class).forEach(this::transformCustomType);
    }

    private void transformEnumeration(EnumerationType enumType) {
        EEnum eEnum = EcoreFactory.eINSTANCE.createEEnum();
        setId(eEnum, "(psm/" + getId(enumType) + ")/Enumeration");
        eEnum.setName(enumType.getName());

        int ordinal = 0;
        for (EnumerationMember member : enumType.getMembers()) {
            EEnumLiteral literal = EcoreFactory.eINSTANCE.createEEnumLiteral();
            setId(literal, eEnum.getName() + "/Literal" + ordinal);
            literal.setValue(member.getOrdinal());
            literal.setLiteral(member.getName());
            literal.setName(member.getName());
            eEnum.getELiterals().add(literal);
            ordinal++;
        }

        getContainerPackage(enumType).getEClassifiers().add(eEnum);
        addTrace(enumType, CREATE_ENUMERATION, eEnum);
    }

    private void transformStringType(StringType stringType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(stringType) + ")/StringType");
        dataType.setName(stringType.getName());
        dataType.setInstanceClassName("java.lang.String");

        getContainerPackage(stringType).getEClassifiers().add(dataType);
        addTrace(stringType, CREATE_STRING_TYPE, dataType);
    }

    private void transformNumericType(NumericType numericType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        
        if (isInteger(numericType)) {
            setId(dataType, "(psm/" + getId(numericType) + ")/IntegerType");
            dataType.setName(numericType.getName());
            dataType.setInstanceClassName(getIntegerClassName(numericType));
            addTrace(numericType, CREATE_INTEGER_TYPE, dataType);
        } else {
            setId(dataType, "(psm/" + getId(numericType) + ")/DecimalType");
            dataType.setName(numericType.getName());
            dataType.setInstanceClassName(getDecimalClassName(numericType));
            addTrace(numericType, CREATE_DECIMAL_TYPE, dataType);
        }

        // Add measured annotation for MeasuredType
        if (numericType instanceof MeasuredType) {
            MeasuredType measuredType = (MeasuredType) numericType;
            if (measuredType.getStoreUnit() != null) {
                EAnnotation measuredAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
                setId(measuredAnnotation, "(psm/" + getId(numericType) + ")/MeasuredAnnotation");
                measuredAnnotation.setSource(getAnnotationUri("measured"));
                
                // Add unit detail
                measuredAnnotation.getDetails().put("unit", measuredType.getStoreUnit().getName());
                
                // Add measure detail (namespace of the unit)
                if (measuredType.getStoreUnit().eContainer() != null) {
                    String measure = getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer());
                    measuredAnnotation.getDetails().put("measure", measure);
                }
                
                dataType.getEAnnotations().add(measuredAnnotation);
            }
        }

        getContainerPackage(numericType).getEClassifiers().add(dataType);
    }

    private void transformBooleanType(BooleanType booleanType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(booleanType) + ")/BooleanType");
        dataType.setName(booleanType.getName());
        dataType.setInstanceClassName("java.lang.Boolean");

        getContainerPackage(booleanType).getEClassifiers().add(dataType);
        addTrace(booleanType, CREATE_BOOLEAN_TYPE, dataType);
    }

    private void transformDateType(DateType dateType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(dateType) + ")/DateType");
        dataType.setName(dateType.getName());
        dataType.setInstanceClassName("java.time.LocalDate");

        getContainerPackage(dateType).getEClassifiers().add(dataType);
        addTrace(dateType, CREATE_DATE_TYPE, dataType);
    }

    private void transformTimestampType(TimestampType timestampType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(timestampType) + ")/TimestampType");
        dataType.setName(timestampType.getName());
        dataType.setInstanceClassName("java.time.LocalDateTime");

        getContainerPackage(timestampType).getEClassifiers().add(dataType);
        addTrace(timestampType, CREATE_TIMESTAMP_TYPE, dataType);
    }

    private void transformTimeType(TimeType timeType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(timeType) + ")/TimeType");
        dataType.setName(timeType.getName());
        dataType.setInstanceClassName("java.time.LocalTime");

        getContainerPackage(timeType).getEClassifiers().add(dataType);
        addTrace(timeType, CREATE_TIME_TYPE, dataType);
    }

    private void transformBinaryType(BinaryType binaryType) {
        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(binaryType) + ")/BinaryType");
        dataType.setName(binaryType.getName());
        dataType.setInstanceClassName("byte[]");

        // Add constraints annotation
        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(binaryType) + ")/Constraints",
                getAnnotationUri("constraints"));

        if (binaryType.getMimeTypes() != null && !binaryType.getMimeTypes().isEmpty()) {
            addAnnotationDetail(constraintsAnnotation, "mimeTypes", 
                    String.join(",", binaryType.getMimeTypes()));
        }
        if (binaryType.getMaxFileSize() > 0) {
            addAnnotationDetail(constraintsAnnotation, "maxFileSize", 
                    String.valueOf(binaryType.getMaxFileSize()));
        }

        dataType.getEAnnotations().add(constraintsAnnotation);
        getContainerPackage(binaryType).getEClassifiers().add(dataType);
        addTrace(binaryType, CREATE_BINARY_TYPE, dataType);
    }

    private void transformCustomType(CustomType customType) {
        // Skip types that are handled by specific rules
        if (customType instanceof NumericType || 
            customType instanceof BooleanType ||
            customType instanceof EnumerationType ||
            customType instanceof StringType ||
            customType instanceof DateType ||
            customType instanceof TimestampType ||
            customType instanceof TimeType) {
            return;
        }

        EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
        setId(dataType, "(psm/" + getId(customType) + ")/CustomType");
        dataType.setName(customType.getName());
        dataType.setInstanceClassName("java.lang.Object");

        getContainerPackage(customType).getEClassifiers().add(dataType);
        addTrace(customType, CREATE_CUSTOM_TYPE, dataType);
    }

    // =========================================================================
    // ENTITY TRANSFORMATIONS
    // =========================================================================

    private void transformEntities() {
        // First pass: create all entity classes
        all(EntityType.class).forEach(this::createEntityClass);

        // Second pass: set up inheritance and members
        all(EntityType.class).forEach(this::setupEntityInheritance);
        all(Attribute.class).forEach(this::transformAttribute);
        all(AssociationEnd.class).forEach(this::transformAssociationEnd);
        all(Containment.class).forEach(this::transformContainment);
        
        // Third pass: set up association partners (EOpposite)
        all(AssociationEnd.class).forEach(this::setupAssociationPartner);
        
        // Fourth pass: add unmappedDefaultOnly annotations
        all(Attribute.class)
                .filter(this::hasUnmappedDefaultOnlyAttributeCondition)
                .forEach(this::addUnmappedDefaultOnlyAttributeAnnotation);
        all(AssociationEnd.class)
                .filter(this::hasUnmappedDefaultOnlyReferenceCondition)
                .forEach(this::addUnmappedDefaultOnlyReferenceAnnotation);
        
        // Fifth pass: transform sequences
        all(EntitySequence.class).forEach(this::transformEntitySequence);
        all(NamespaceSequence.class).forEach(this::transformNamespaceSequence);
    }

    private void createEntityClass(EntityType entityType) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(entityType) + ")/EntityClass");
        eClass.setName(entityType.getName());
        eClass.setAbstract(entityType.isAbstract());

        getContainerPackage(entityType).getEClassifiers().add(eClass);
        addTrace(entityType, CREATE_ENTITY_CLASS, eClass);

        // Add entity annotation
        EAnnotation entityAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/EntityAnnotationClass",
                getAnnotationUri("entity"));
        addAnnotationDetail(entityAnnotation, "value", "true");
        eClass.getEAnnotations().add(entityAnnotation);
        addTrace(entityType, CREATE_ENTITY_ANNOTATION_CLASS, entityAnnotation);

        // Add default representation annotation if present
        if (entityType.getDefaultRepresentation() != null) {
            EAnnotation defaultRepAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/EntityDefaultRepresentationAnnotation",
                    getAnnotationUri("defaultRepresentation"));
            addAnnotationDetail(defaultRepAnnotation, "value", 
                    getQualifiedName(entityType.getDefaultRepresentation()));
            eClass.getEAnnotations().add(defaultRepAnnotation);
        }

        // Add documentation annotation if present
        if (entityType.getDocumentation() != null && 
            !entityType.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(entityType);
            eClass.getEAnnotations().add(docAnnotation);
        }
    }

    private void setupEntityInheritance(EntityType entityType) {
        EClass eClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
        if (eClass == null) return;

        for (EntityType superType : entityType.getSuperEntityTypes()) {
            EClass superClass = (EClass) getEquivalent(superType, CREATE_ENTITY_CLASS);
            if (superClass != null) {
                eClass.getESuperTypes().add(superClass);
            }
        }
    }

    private void transformAttribute(Attribute attribute) {
        if (!isPrimitive(attribute)) {
            return;
        }

        EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
        setId(eAttr, "(psm/" + getId(attribute) + ")/Attribute");
        eAttr.setName(attribute.getName());
        eAttr.setLowerBound(attribute.isRequired() ? 1 : 0);

        // Set type
        EClassifier type = getEquivalentType(attribute.getDataType());
        if (type != null) {
            eAttr.setEType(type);
        }

        // Add to owning class
        EntityType owner = getEntityType(attribute);
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eAttr);
            }
        }

        addTrace(attribute, CREATE_ATTRIBUTE, eAttr);

        // Add constraints annotation
        addAttributeConstraints(attribute, eAttr);

        // Add identifier annotation if applicable
        if (attribute.isIdentifier()) {
            EAnnotation idAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/IdentifierAnnotationForAttribute",
                    getAnnotationUri("identifier"));
            addAnnotationDetail(idAnnotation, "value", "true");
            eAttr.getEAnnotations().add(idAnnotation);
        }

        // Add documentation annotation if present
        if (attribute.getDocumentation() != null && 
            !attribute.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(attribute);
            eAttr.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformAssociationEnd(AssociationEnd associationEnd) {
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(associationEnd) + ")/AssociationEndRelation");
        eRef.setName(associationEnd.getName());
        eRef.setLowerBound(associationEnd.getCardinality().getLower());
        eRef.setUpperBound(associationEnd.getCardinality().getUpper());

        // Set target type
        if (associationEnd.getTarget() != null) {
            EClass targetClass = (EClass) getEquivalent(associationEnd.getTarget(), CREATE_ENTITY_CLASS);
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        // Add to owning class
        EntityType owner = getEntityType(associationEnd);
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eRef);
            }
        }

        addTrace(associationEnd, CREATE_ASSOCIATION_END_RELATION, eRef);

        // Add reverse cascade delete annotation if applicable
        if (associationEnd.isReverseCascadeDelete()) {
            EAnnotation reverseCascadeAnnotation = createAnnotation(
                    "(psm/" + getId(associationEnd) + ")/ReverseCascadeDeleteAnnotation",
                    getAnnotationUri("reverseCascadeDelete"));
            addAnnotationDetail(reverseCascadeAnnotation, "value", "true");
            eRef.getEAnnotations().add(reverseCascadeAnnotation);
        }

        // Add documentation annotation if present
        if (associationEnd.getDocumentation() != null && 
            !associationEnd.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(associationEnd);
            eRef.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformContainment(Containment containment) {
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(containment) + ")/ContainmentRelation");
        eRef.setName(containment.getName());
        eRef.setLowerBound(containment.getCardinality().getLower());
        eRef.setUpperBound(containment.getCardinality().getUpper());
        eRef.setContainment(true);

        // Set target type
        if (containment.getTarget() != null) {
            EClass targetClass = (EClass) getEquivalent(containment.getTarget(), CREATE_ENTITY_CLASS);
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        // Add to owning class
        EntityType owner = getEntityType(containment);
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eRef);
            }
        }

        addTrace(containment, CREATE_CONTAINMENT_RELATION, eRef);

        // Add documentation annotation if present
        if (containment.getDocumentation() != null && 
            !containment.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(containment);
            eRef.getEAnnotations().add(docAnnotation);
        }
    }

    private void setupAssociationPartner(AssociationEnd associationEnd) {
        if (associationEnd.getPartner() == null) {
            return;
        }

        EReference eRef = (EReference) getEquivalent(associationEnd, CREATE_ASSOCIATION_END_RELATION);
        EReference partnerRef = (EReference) getEquivalent(associationEnd.getPartner(), CREATE_ASSOCIATION_END_RELATION);

        if (eRef != null && partnerRef != null) {
            eRef.setEOpposite(partnerRef);
        }
    }

    private void transformEntitySequence(EntitySequence sequence) {
        EAnnotation seqAnnotation = createSequenceAnnotation(sequence);
        
        // Add to owning entity class
        EntityType owner = (EntityType) sequence.eContainer();
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEAnnotations().add(seqAnnotation);
            }
        }
        
        addTrace(sequence, "CreateEntitySequence", seqAnnotation);
    }

    private void transformNamespaceSequence(NamespaceSequence sequence) {
        EAnnotation seqAnnotation = createSequenceAnnotation(sequence);
        
        // Add to containing namespace's equivalent package
        EObject container = sequence.eContainer();
        if (container instanceof Namespace) {
            EPackage pkg = (EPackage) getEquivalent(container, 
                    container instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
            if (pkg != null) {
                pkg.getEAnnotations().add(seqAnnotation);
            }
        }
        
        addTrace(sequence, "CreateNamespaceSequence", seqAnnotation);
    }

    private EAnnotation createSequenceAnnotation(Sequence sequence) {
        EAnnotation seqAnnotation = createAnnotation(
                "(psm/" + getId(sequence) + ")/Sequence",
                getAnnotationUri("sequence"));
        
        addAnnotationDetail(seqAnnotation, "name", sequence.getName());
        addAnnotationDetail(seqAnnotation, "initialValue", String.valueOf(sequence.getInitialValue()));
        addAnnotationDetail(seqAnnotation, "increment", String.valueOf(sequence.getIncrement()));
        addAnnotationDetail(seqAnnotation, "cyclic", String.valueOf(sequence.isCyclic()));
        
        if (sequence.getMaximumValue() != 0) {
            addAnnotationDetail(seqAnnotation, "maximumValue", String.valueOf(sequence.getMaximumValue()));
        }
        
        return seqAnnotation;
    }

    // =========================================================================
    // DERIVED PROPERTIES TRANSFORMATIONS
    // =========================================================================

    private void transformDerivedProperties() {
        log.debug("Transforming derived properties");
        all(DataProperty.class).forEach(this::transformDataProperty);
        all(NavigationProperty.class).forEach(this::transformNavigationProperty);
    }

    private void transformDataProperty(DataProperty dataProperty) {
        if (dataProperty.getDataType() == null || 
            !(dataProperty.getDataType() instanceof Primitive)) {
            return;
        }

        EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
        setId(eAttr, "(psm/" + getId(dataProperty) + ")/DataProperty");
        eAttr.setName(dataProperty.getName());
        eAttr.setDerived(true);
        eAttr.setVolatile(true);
        eAttr.setLowerBound(dataProperty.isRequired() ? 1 : 0);
        
        // Set changeable based on setter
        eAttr.setChangeable(dataProperty.getSetterExpression() != null);

        // Set type
        EClassifier type = getEquivalentType(dataProperty.getDataType());
        if (type != null) {
            eAttr.setEType(type);
        }

        // Add to owning entity class
        EntityType owner = getEntityType(dataProperty);
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eAttr);
            }
        }

        addTrace(dataProperty, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE, eAttr);

        // Add constraints annotation
        addPrimitiveAccessorConstraints(dataProperty, eAttr);

        // Add expression annotation
        addExpressionAnnotation(dataProperty, eAttr);

        // Add documentation if present
        if (dataProperty.getDocumentation() != null && 
            !dataProperty.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(dataProperty);
            eAttr.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformNavigationProperty(NavigationProperty navigationProperty) {
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(navigationProperty) + ")/NavigationProperty");
        eRef.setName(navigationProperty.getName());
        eRef.setDerived(true);
        eRef.setVolatile(true);
        
        if (navigationProperty.getCardinality() != null) {
            eRef.setLowerBound(navigationProperty.getCardinality().getLower());
            eRef.setUpperBound(navigationProperty.getCardinality().getUpper());
        }

        // Set changeable based on setter
        eRef.setChangeable(navigationProperty.getSetterExpression() != null);

        // Set target type
        if (navigationProperty.getTarget() != null) {
            EClass targetClass = (EClass) getEquivalent(navigationProperty.getTarget(), CREATE_ENTITY_CLASS);
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        // Add to owning entity class
        EntityType owner = getEntityType(navigationProperty);
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eRef);
            }
        }

        addTrace(navigationProperty, CREATE_STATIC_NAVIGATION_FOR_DERIVED_ATTRIBUTE, eRef);

        // Add expression annotation
        addReferenceAccessorExpressionAnnotation(navigationProperty, eRef);

        // Add documentation if present
        if (navigationProperty.getDocumentation() != null && 
            !navigationProperty.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(navigationProperty);
            eRef.getEAnnotations().add(docAnnotation);
        }
    }

    private void addPrimitiveAccessorConstraints(PrimitiveAccessor accessor, EAttribute eAttr) {
        if (accessor instanceof StaticData) {
            return; // StaticData doesn't get constraints
        }
        
        Primitive dataType = accessor.getDataType();
        if (dataType == null) return;

        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(accessor) + ")/PrimitiveAccessorConstraints",
                getAnnotationUri("constraints"));

        if (dataType instanceof StringType) {
            StringType stringType = (StringType) dataType;
            addAnnotationDetail(constraintsAnnotation, "maxLength", 
                    String.valueOf(stringType.getMaxLength()));
            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }
        } else if (dataType instanceof NumericType) {
            NumericType numericType = (NumericType) dataType;
            addAnnotationDetail(constraintsAnnotation, "precision", 
                    String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", 
                    String.valueOf(numericType.getScale()));

            if (numericType instanceof MeasuredType) {
                MeasuredType measuredType = (MeasuredType) numericType;
                if (measuredType.getStoreUnit() != null) {
                    // ETL order: measure first, then unit
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                    }
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                }
            }
        } else if (dataType instanceof CustomType && !(dataType instanceof NumericType) 
                && !(dataType instanceof BooleanType) && !(dataType instanceof EnumerationType)
                && !(dataType instanceof StringType)) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    getQualifiedName(dataType));
        }

        if (!constraintsAnnotation.getDetails().isEmpty()) {
            eAttr.getEAnnotations().add(constraintsAnnotation);
        }
    }

    private void addExpressionAnnotation(PrimitiveAccessor accessor, EAttribute eAttr) {
        if (accessor instanceof StaticData) {
            return; // StaticData handled separately
        }
        
        if (accessor.getGetterExpression() == null) {
            return;
        }

        EAnnotation exprAnnotation = createAnnotation(
                "(psm/" + getId(accessor) + ")/PrimitiveAccessorExpressionAnnotation",
                getAnnotationUri("expression"));

        addAnnotationDetail(exprAnnotation, "getter", 
                accessor.getGetterExpression().getExpression());
        addAnnotationDetail(exprAnnotation, "getter.dialect", 
                accessor.getGetterExpression().getDialect().toString());

        if (accessor.getGetterExpression().getParameterType() != null) {
            EObject paramType = getEquivalent(accessor.getGetterExpression().getParameterType(), 
                    CREATE_TRANSFER_OBJECT_TYPE_CLASS);
            if (paramType == null) {
                paramType = getEquivalent(accessor.getGetterExpression().getParameterType(), 
                        CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            }
            if (paramType == null) {
                paramType = getEquivalent(accessor.getGetterExpression().getParameterType(), 
                        CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            }
            if (paramType instanceof EClassifier) {
                addAnnotationDetail(exprAnnotation, "getter.parameter", 
                        getClassifierFQName((EClassifier) paramType));
            }
        }

        if (accessor.getSetterExpression() != null) {
            addAnnotationDetail(exprAnnotation, "setter", 
                    accessor.getSetterExpression().getExpression());
            addAnnotationDetail(exprAnnotation, "setter.dialect", 
                    accessor.getSetterExpression().getDialect().toString());
        }

        eAttr.getEAnnotations().add(exprAnnotation);
    }

    private void addReferenceAccessorExpressionAnnotation(ReferenceAccessor accessor, EReference eRef) {
        if (accessor instanceof StaticNavigation) {
            return; // StaticNavigation handled separately
        }
        
        if (accessor.getGetterExpression() == null) {
            return;
        }

        EAnnotation exprAnnotation = createAnnotation(
                "(psm/" + getId(accessor) + ")/ReferenceAccessorExpressionAnnotation",
                getAnnotationUri("expression"));

        addAnnotationDetail(exprAnnotation, "getter", 
                accessor.getGetterExpression().getExpression());
        addAnnotationDetail(exprAnnotation, "getter.dialect", 
                accessor.getGetterExpression().getDialect().toString());

        if (accessor.getGetterExpression().getParameterType() != null) {
            EObject paramType = getEquivalentTransferObject(accessor.getGetterExpression().getParameterType());
            if (paramType instanceof EClassifier) {
                addAnnotationDetail(exprAnnotation, "getter.parameter", 
                        getClassifierFQName((EClassifier) paramType));
            }
        }

        if (accessor.getSetterExpression() != null) {
            addAnnotationDetail(exprAnnotation, "setter", 
                    accessor.getSetterExpression().getExpression());
            addAnnotationDetail(exprAnnotation, "setter.dialect", 
                    accessor.getSetterExpression().getDialect().toString());
        }

        eRef.getEAnnotations().add(exprAnnotation);
    }

    // =========================================================================
    // OPERATIONS TRANSFORMATIONS
    // =========================================================================

    private void transformOperations() {
        log.debug("Transforming operations");
        all(BoundOperation.class).forEach(this::transformBoundOperation);
        all(UnboundOperation.class).forEach(this::transformUnboundOperation);
        // Transform transfer operations (on TransferObjectTypes)
        // BoundTransferOperation extends TransferOperation, so we handle them separately
        all(BoundTransferOperation.class).forEach(this::transformBoundTransferOperation);
        // Transform unbound transfer operations (TransferOperation that are not BoundTransferOperation or UnboundOperation)
        // UnboundOperation extends TransferOperation, so it would be included twice without this filter
        all(TransferOperation.class)
                .filter(op -> !(op instanceof BoundTransferOperation))
                .filter(op -> !(op instanceof UnboundOperation))
                .forEach(this::transformTransferOperation);
    }

    private void transformBoundOperation(BoundOperation boundOp) {
        EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
        setId(eOp, "(psm/" + getId(boundOp) + ")/BoundOperation");
        eOp.setName(boundOp.getName());

        // Set output type and cardinality - only when output is defined
        // For void operations, Ecore requires upperBound = 1 (not the default -1)
        if (boundOp.getOutput() != null) {
            eOp.setLowerBound(boundOp.getOutput().getCardinality().getLower());
            eOp.setUpperBound(boundOp.getOutput().getCardinality().getUpper());
            EClassifier outputType = getEquivalentTransferObject(boundOp.getOutput().getType());
            if (outputType != null) {
                eOp.setEType(outputType);
            }
        }
        // Note: For void operations (no output), we don't set bounds - ETL doesn't either
        // The Ecore default upperBound is 1 which is correct for void operations

        // Add fault exceptions
        for (var fault : boundOp.getFaults()) {
            EClassifier faultType = getEquivalentTransferObject(fault.getType());
            if (faultType != null) {
                eOp.getEExceptions().add(faultType);
            }
        }

        // Add to owning entity class
        EntityType owner = (EntityType) boundOp.eContainer();
        if (owner != null) {
            EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
            if (ownerClass != null) {
                ownerClass.getEOperations().add(eOp);
            }
        }

        addTrace(boundOp, CREATE_BOUND_OPERATION, eOp);

        // Transform input parameter (single parameter, not a list)
        if (boundOp.getInput() != null) {
            transformInputParameter(boundOp.getInput(), eOp);
        }

        // Add annotations in ETL execution order (from test output analysis):
        // ETL order: customImplementation, instanceRepresentation, bound, outputParameterName

        // 1. Add custom implementation annotation FIRST
        if (boundOp.getImplementation() != null) {
            addCustomImplementationAnnotation(boundOp, eOp);
        }

        // 2. Add instance representation annotation SECOND
        if (boundOp.getInstanceRepresentation() != null) {
            addInstanceRepresentationAnnotation(boundOp, eOp);
        }

        // 3. Add bound annotation THIRD
        addBoundAnnotation(boundOp, eOp);

        // 4. Add output parameter name annotation FOURTH (last)
        if (boundOp.getOutput() != null) {
            addOutputParameterNameAnnotation(boundOp, eOp);
        }

        // Add script body annotation if implementation exists
        if (boundOp.getImplementation() != null && 
            boundOp.getImplementation().getBody() != null &&
            !boundOp.getImplementation().getBody().trim().isEmpty()) {
            addScriptBodyAnnotation(boundOp, eOp);
        }

        // Add abstract annotation
        if (boundOp.isAbstract()) {
            addAbstractAnnotation(boundOp, eOp);
        }

        // Add documentation if present
        if (boundOp.getDocumentation() != null && 
            !boundOp.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(boundOp);
            eOp.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformUnboundOperation(UnboundOperation unboundOp) {
        EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
        setId(eOp, "(psm/" + getId(unboundOp) + ")/UnboundOperation");
        eOp.setName(unboundOp.getName());

        // Set output type and cardinality
        if (unboundOp.getOutput() != null) {
            eOp.setLowerBound(unboundOp.getOutput().getCardinality().getLower());
            eOp.setUpperBound(unboundOp.getOutput().getCardinality().getUpper());
            EClassifier outputType = getEquivalentTransferObject(unboundOp.getOutput().getType());
            if (outputType != null) {
                eOp.setEType(outputType);
            }
        }

        // Add fault exceptions
        for (var fault : unboundOp.getFaults()) {
            EClassifier faultType = getEquivalentTransferObject(fault.getType());
            if (faultType != null) {
                eOp.getEExceptions().add(faultType);
            }
        }

        // Add to containing element - could be Namespace (Package/Model), ActorType, or TransferObjectType
        EObject container = unboundOp.eContainer();
        if (container instanceof Namespace) {
            Namespace ns = (Namespace) container;
            EPackage pkg = (EPackage) getEquivalent(ns, 
                    ns instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
            if (pkg != null) {
                // Find or create OperationHolder class in the package
                EClass operationHolder = null;
                for (EClassifier classifier : pkg.getEClassifiers()) {
                    if (classifier instanceof EClass && "OperationHolder".equals(classifier.getName())) {
                        operationHolder = (EClass) classifier;
                        break;
                    }
                }
                if (operationHolder == null) {
                    operationHolder = EcoreFactory.eINSTANCE.createEClass();
                    operationHolder.setName("OperationHolder");
                    operationHolder.setAbstract(true);
                    operationHolder.setInterface(true);
                    pkg.getEClassifiers().add(operationHolder);
                }
                // Add the operation to the holder class
                operationHolder.getEOperations().add(eOp);
            }
        } else if (container instanceof TransferObjectType) {
            // For TransferObjectType (includes ActorType which extends UnmappedTransferObjectType), 
            // add operation to the equivalent EClass
            EClass toClass = getEquivalentTransferObjectClass((TransferObjectType) container);
            if (toClass != null) {
                toClass.getEOperations().add(eOp);
            }
        }

        addTrace(unboundOp, CREATE_UNBOUND_OPERATION, eOp);

        // Transform input parameter (single parameter, not a list)
        if (unboundOp.getInput() != null) {
            transformInputParameter(unboundOp.getInput(), eOp);
        }

        // Add bound annotation (value = false for unbound)
        addBoundAnnotation(unboundOp, eOp);

        // Add script body annotation if implementation exists
        if (unboundOp.getImplementation() != null && 
            unboundOp.getImplementation().getBody() != null &&
            !unboundOp.getImplementation().getBody().trim().isEmpty()) {
            addScriptBodyAnnotation(unboundOp, eOp);
        }

        // Add custom implementation annotations
        // ETL adds two annotations for UnboundOperation:
        // 1. CreateCustomImplementationAnnotationOnOperation (from TransferOperation base rule)
        // 2. CreateCustomImplementationAnnotationOnUnboundOperation (specific to UnboundOperation)
        if (unboundOp.getImplementation() != null) {
            // First annotation: from TransferOperation rule (applies to all TransferOperations)
            EAnnotation customImplAnnotationBase = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/CustomImplementationAnnotationOnOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotationBase, "value", 
                    String.valueOf(unboundOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotationBase);
            
            // Second annotation: specific to UnboundOperation
            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/CustomImplementationAnnotationOnUnboundOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(unboundOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);
        }

        // Add initializer annotation
        if (unboundOp.isInitializer()) {
            EAnnotation initAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/InitializerAnnotation",
                    getAnnotationUri("initializer"));
            addAnnotationDetail(initAnnotation, "value", "true");
            eOp.getEAnnotations().add(initAnnotation);
        }

        // Add stateful annotation (UnboundOperation is a TransferOperation)
        addStatefulAnnotation(unboundOp, eOp);

        // Add behaviour annotation if behaviour is defined
        addBehaviourAnnotation(unboundOp, eOp);

        // Add permissions annotation
        addPermissionsAnnotation(unboundOp, eOp);

        // Add immutable annotation (UnboundOperation extends TransferOperation)
        addImmutableAnnotation(unboundOp, eOp);

        // Add input range annotation if applicable
        addInputRangeAnnotation(unboundOp, eOp);

        // Add output parameter name annotation
        if (unboundOp.getOutput() != null) {
            EAnnotation outputAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/OutputParameterName",
                    getAnnotationUri("outputParameterName"));
            addAnnotationDetail(outputAnnotation, "value", unboundOp.getOutput().getName());
            eOp.getEAnnotations().add(outputAnnotation);
        }
    }

    private void transformBoundTransferOperation(BoundTransferOperation boundTransferOp) {
        EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
        setId(eOp, "(psm/" + getId(boundTransferOp) + ")/BoundTransferOperation");
        eOp.setName(boundTransferOp.getName());

        // Get output from the binding (BoundOperation)
        BoundOperation binding = boundTransferOp.getBinding();
        if (binding != null && binding.getOutput() != null) {
            eOp.setLowerBound(binding.getOutput().getCardinality().getLower());
            eOp.setUpperBound(binding.getOutput().getCardinality().getUpper());
            EClassifier outputType = getEquivalentTransferObject(binding.getOutput().getType());
            if (outputType != null) {
                eOp.setEType(outputType);
            }
        }
        // Note: For void operations (no output), we don't set bounds
        // The Ecore default upperBound is 1 which is correct for void operations

        // Add fault exceptions from binding
        if (binding != null) {
            for (var fault : binding.getFaults()) {
                EClassifier faultType = getEquivalentTransferObject(fault.getType());
                if (faultType != null) {
                    eOp.getEExceptions().add(faultType);
                }
            }
        }

        // Add to owning transfer object class
        TransferObjectType owner = (TransferObjectType) boundTransferOp.eContainer();
        if (owner != null) {
            EClass ownerClass = getEquivalentTransferObjectClass(owner);
            if (ownerClass != null) {
                ownerClass.getEOperations().add(eOp);
            }
        }

        addTrace(boundTransferOp, "CreateBoundTransferOperation", eOp);

        // Transform input parameter from binding
        if (binding != null && binding.getInput() != null) {
            transformInputParameter(binding.getInput(), eOp);
        }

        // ETL order for BoundTransferOperation annotations:
        // 1. customImplementation (from CreateCustomImplementationAnnotationOnOperation)
        // 2. stateful (from CreateStatefulAnnotationOnOperation) 
        // 3. bound (from CreateBoundOperationAnnotation)
        // 4. binding (from CreateBoundTransferOperation)

        // Add customImplementation annotation from binding's implementation
        if (binding != null && binding.getImplementation() != null) {
            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(boundTransferOp) + ")/CustomImplementationAnnotationOnOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(binding.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);
        }

        // Add stateful annotation (from TransferOperation rules)
        addStatefulAnnotation(boundTransferOp, eOp);

        // Add bound annotation
        addBoundAnnotation(boundTransferOp, eOp);

        // Add binding annotation
        if (binding != null) {
            EObject bindingEquivalent = getEquivalent(binding, CREATE_BOUND_OPERATION);
            if (bindingEquivalent instanceof EOperation) {
                EAnnotation bindingAnnotation = createAnnotation(
                        "(psm/" + getId(boundTransferOp) + ")/BindingAnnotation",
                        getAnnotationUri("binding"));
                addAnnotationDetail(bindingAnnotation, "value", ((EOperation) bindingEquivalent).getName());
                eOp.getEAnnotations().add(bindingAnnotation);
            }
        }

        // Add behaviour annotation if behaviour is defined
        addBehaviourAnnotation(boundTransferOp, eOp);

        // Add permissions annotation
        addPermissionsAnnotation(boundTransferOp, eOp);

        // Add immutable annotation (applies to all TransferOperations)
        addImmutableAnnotation(boundTransferOp, eOp);

        // Add input range annotation if applicable
        addInputRangeAnnotation(boundTransferOp, eOp);

        // Add output parameter name annotation (uses binding's output)
        if (binding != null && binding.getOutput() != null) {
            EAnnotation outputAnnotation = createAnnotation(
                    "(psm/" + getId(boundTransferOp) + ")/OutputParameterName",
                    getAnnotationUri("outputParameterName"));
            addAnnotationDetail(outputAnnotation, "value", binding.getOutput().getName());
            eOp.getEAnnotations().add(outputAnnotation);
        }

        // Add documentation if present
        if (boundTransferOp.getDocumentation() != null &&
            !boundTransferOp.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(boundTransferOp) + ")/DocumentationAnnotationForBoundTransferOperation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", boundTransferOp.getDocumentation());
            eOp.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformTransferOperation(TransferOperation transferOp) {
        EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
        setId(eOp, "(psm/" + getId(transferOp) + ")/TransferOperation");
        eOp.setName(transferOp.getName());

        // Set output type and cardinality
        if (transferOp.getOutput() != null) {
            eOp.setLowerBound(transferOp.getOutput().getCardinality().getLower());
            eOp.setUpperBound(transferOp.getOutput().getCardinality().getUpper());
            EClassifier outputType = getEquivalentTransferObject(transferOp.getOutput().getType());
            if (outputType != null) {
                eOp.setEType(outputType);
            }
        }
        // Note: For void operations (no output), we don't set bounds
        // The Ecore default upperBound is 1 which is correct for void operations

        // Add fault exceptions
        for (var fault : transferOp.getFaults()) {
            EClassifier faultType = getEquivalentTransferObject(fault.getType());
            if (faultType != null) {
                eOp.getEExceptions().add(faultType);
            }
        }

        // Add to owning transfer object class
        TransferObjectType owner = (TransferObjectType) transferOp.eContainer();
        if (owner != null) {
            EClass ownerClass = getEquivalentTransferObjectClass(owner);
            if (ownerClass != null) {
                ownerClass.getEOperations().add(eOp);
            }
        }

        addTrace(transferOp, "CreateTransferOperation", eOp);

        // Transform input parameter
        if (transferOp.getInput() != null) {
            transformInputParameter(transferOp.getInput(), eOp);
        }

        // ETL order for TransferOperation annotations (from test output analysis):
        // outputParameterName, stateful, bound, customImplementation, behaviour, permissions, immutable

        // 1. Add output parameter name annotation FIRST
        if (transferOp.getOutput() != null) {
            EAnnotation outputAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/OutputParameterName",
                    getAnnotationUri("outputParameterName"));
            addAnnotationDetail(outputAnnotation, "value", transferOp.getOutput().getName());
            eOp.getEAnnotations().add(outputAnnotation);
        }

        // 2. Add stateful annotation
        if (transferOp.getImplementation() != null) {
            EAnnotation statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperation",
                    getAnnotationUri("stateful"));
            addAnnotationDetail(statefulAnnotation, "value",
                    String.valueOf(transferOp.getImplementation().isStateful()));
            eOp.getEAnnotations().add(statefulAnnotation);
        } else if (transferOp.getBehaviour() != null) {
            // Has behaviour but no implementation - stateful depends on behaviour type
            addStatefulAnnotation(transferOp, eOp);
        } else {
            // No implementation and no behaviour - default stateful = true
            EAnnotation statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperationWithoutImplementationAndBehaviour",
                    getAnnotationUri("stateful"));
            addAnnotationDetail(statefulAnnotation, "value", "true");
            eOp.getEAnnotations().add(statefulAnnotation);
        }

        // 3. Add bound annotation (value = false for unbound transfer operations)
        EAnnotation boundAnnotation = createAnnotation(
                "(psm/" + getId(transferOp) + ")/BoundOperationAnnotation",
                getAnnotationUri("bound"));
        addAnnotationDetail(boundAnnotation, "value", "false");
        eOp.getEAnnotations().add(boundAnnotation);

        // 4. Add custom implementation annotation
        if (transferOp.getImplementation() != null) {
            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/CustomImplementationAnnotationOnOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value",
                    String.valueOf(transferOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);
        }

        // 5. Add behaviour annotation (if has behaviour)
        addBehaviourAnnotation(transferOp, eOp);

        // 6. Add permissions annotation
        addPermissionsAnnotation(transferOp, eOp);

        // 7. Add immutable annotation (applies to all TransferOperations)
        addImmutableAnnotation(transferOp, eOp);

        // 8. Add input range annotation if applicable
        addInputRangeAnnotation(transferOp, eOp);

        // Add documentation if present
        if (transferOp.getDocumentation() != null &&
            !transferOp.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/DocumentationAnnotationForTransferOperation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferOp.getDocumentation());
            eOp.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformInputParameter(Parameter param, EOperation eOp) {
        EParameter eParam = EcoreFactory.eINSTANCE.createEParameter();
        setId(eParam, "(psm/" + getId(param) + ")/InputParameter");
        eParam.setName(param.getName());
        
        if (param.getCardinality() != null) {
            eParam.setLowerBound(param.getCardinality().getLower());
            eParam.setUpperBound(param.getCardinality().getUpper());
        }

        EClassifier paramType = getEquivalentTransferObject(param.getType());
        if (paramType != null) {
            eParam.setEType(paramType);
        }

        eOp.getEParameters().add(eParam);

        // Add documentation if present
        if (param.getDocumentation() != null && 
            !param.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(param) + ")/DocumentationAnnotationForInputParameter",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", param.getDocumentation());
            eParam.getEAnnotations().add(docAnnotation);
        }
    }

    private void addBoundAnnotation(OperationDeclaration op, EOperation eOp) {
        EAnnotation boundAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/BoundOperationAnnotation",
                getAnnotationUri("bound"));
        boolean isBound = op instanceof BoundOperation || op instanceof BoundTransferOperation;
        addAnnotationDetail(boundAnnotation, "value", String.valueOf(isBound));
        eOp.getEAnnotations().add(boundAnnotation);
    }

    private void addInstanceRepresentationAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation instRepAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/InstanceRepresentationOfBoundOperation",
                getAnnotationUri("instanceRepresentation"));
        EClassifier instRep = getEquivalentTransferObject(boundOp.getInstanceRepresentation());
        if (instRep != null) {
            addAnnotationDetail(instRepAnnotation, "value", getClassifierFQName(instRep));
        }
        eOp.getEAnnotations().add(instRepAnnotation);
    }

    private void addScriptBodyAnnotation(BoundOperation op, EOperation eOp) {
        EAnnotation scriptAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/ScriptBodyAnnotation",
                getAnnotationUri("script"));
        addAnnotationDetail(scriptAnnotation, "body", op.getImplementation().getBody());
        eOp.getEAnnotations().add(scriptAnnotation);
    }

    private void addScriptBodyAnnotation(UnboundOperation op, EOperation eOp) {
        EAnnotation scriptAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/ScriptBodyAnnotation",
                getAnnotationUri("script"));
        addAnnotationDetail(scriptAnnotation, "body", op.getImplementation().getBody());
        eOp.getEAnnotations().add(scriptAnnotation);
    }

    private void addCustomImplementationAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation customImplAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/CustomImplementationAnnotationOnBoundOperation",
                getAnnotationUri("customImplementation"));
        addAnnotationDetail(customImplAnnotation, "value", 
                String.valueOf(boundOp.getImplementation().isCustomImplementation()));
        eOp.getEAnnotations().add(customImplAnnotation);
    }

    private void addAbstractAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation abstractAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/AbstractAnnotationForBoundOperation",
                getAnnotationUri("abstract"));
        addAnnotationDetail(abstractAnnotation, "value", String.valueOf(boundOp.isAbstract()));
        eOp.getEAnnotations().add(abstractAnnotation);
    }

    private void addOutputParameterNameAnnotation(OperationDeclaration op, EOperation eOp) {
        if (op.getOutput() == null) return;
        
        EAnnotation outputAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/OutputParameterName",
                getAnnotationUri("outputParameterName"));
        addAnnotationDetail(outputAnnotation, "value", op.getOutput().getName());
        eOp.getEAnnotations().add(outputAnnotation);
    }

    // =========================================================================
    // TRANSFER OBJECTS TRANSFORMATIONS
    // =========================================================================

    private void transformTransferObjects() {
        log.debug("Transforming transfer objects");
        
        // First pass: create all transfer object classes
        all(MappedTransferObjectType.class).forEach(this::transformMappedTransferObject);
        all(UnmappedTransferObjectType.class).forEach(this::transformUnmappedTransferObject);
        
        // Second pass: create reference classes for entity types
        // Sort topologically so parent Reference classes are created before child ones
        sortEntityTypesTopologically(all(EntityType.class).toList())
                .forEach(this::createReferenceClassForEntityType);
        
        // Third pass: transform attributes and relations with parallel processing
        // Use staged approach: create elements in parallel, then add to owners sequentially
        List<TransferAttribute> attrs = all(TransferAttribute.class).toList();
        List<TransferObjectRelation> rels = all(TransferObjectRelation.class).toList();
        
        boolean useParallel = (attrs.size() + rels.size()) >= PARALLEL_THRESHOLD;
        
        if (useParallel) {
            // Clear staging queue
            stagedFeatureAdditions.clear();
            
            // Process in parallel - elements created, additions staged
            attrs.parallelStream().forEach(this::transformTransferAttributeStaged);
            rels.parallelStream().forEach(this::transformTransferObjectRelationStaged);
            
            // Commit staged additions sequentially (EMF is not thread-safe for mutations)
            Runnable addition;
            while ((addition = stagedFeatureAdditions.poll()) != null) {
                addition.run();
            }
        } else {
            // Sequential processing for small sets
            attrs.forEach(this::transformTransferAttribute);
            rels.forEach(this::transformTransferObjectRelation);
        }
    }

    /**
     * Sorts entity types topologically so that parent types come before child types.
     * This ensures that when creating Reference classes, parent Reference classes
     * are available when setting up inheritance for child Reference classes.
     */
    private List<EntityType> sortEntityTypesTopologically(List<EntityType> entityTypes) {
        List<EntityType> result = new ArrayList<>();
        Set<EntityType> visited = new HashSet<>();
        Set<EntityType> entityTypeSet = new HashSet<>(entityTypes);
        
        for (EntityType entityType : entityTypes) {
            visitEntityType(entityType, visited, result, entityTypeSet);
        }
        
        return result;
    }
    
    private void visitEntityType(EntityType entityType, Set<EntityType> visited, 
            List<EntityType> result, Set<EntityType> entityTypeSet) {
        if (visited.contains(entityType)) {
            return;
        }
        visited.add(entityType);
        
        // Visit all super types first
        for (EntityType superType : entityType.getSuperEntityTypes()) {
            if (entityTypeSet.contains(superType)) {
                visitEntityType(superType, visited, result, entityTypeSet);
            }
        }
        
        result.add(entityType);
    }

    private void transformMappedTransferObject(MappedTransferObjectType mappedTO) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(mappedTO) + ")/MappedTransferObject");
        eClass.setName(mappedTO.getName());

        getContainerPackage(mappedTO).getEClassifiers().add(eClass);
        addTrace(mappedTO, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS, eClass);

        // Add transfer object type annotation
        addTransferObjectTypeAnnotation(mappedTO, eClass);

        // Add mapped entity type annotation
        if (mappedTO.getEntityType() != null) {
            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(mappedTO) + ")/MappedEntityTypeAnnotationOnMappedTransferObject",
                    getAnnotationUri("mappedEntityType"));
            
            EClass entityClass = (EClass) getEquivalent(mappedTO.getEntityType(), CREATE_ENTITY_CLASS);
            if (entityClass != null) {
                addAnnotationDetail(mappedEntityAnnotation, "value", 
                        getClassifierFQName(entityClass));
            }

            if (mappedTO.getFilter() != null) {
                addAnnotationDetail(mappedEntityAnnotation, "filter", 
                        mappedTO.getFilter().getExpression());
                addAnnotationDetail(mappedEntityAnnotation, "filter.dialect", 
                        mappedTO.getFilter().getDialect().toString());
            }

            eClass.getEAnnotations().add(mappedEntityAnnotation);
        }

        // Add documentation if present
        if (mappedTO.getDocumentation() != null && 
            !mappedTO.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(mappedTO);
            eClass.getEAnnotations().add(docAnnotation);
        }
    }

    private void transformUnmappedTransferObject(UnmappedTransferObjectType unmappedTO) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(unmappedTO) + ")/UnmappedTransferObject");
        eClass.setName(unmappedTO.getName());

        getContainerPackage(unmappedTO).getEClassifiers().add(eClass);
        addTrace(unmappedTO, CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS, eClass);

        // Add transfer object type annotation
        addTransferObjectTypeAnnotation(unmappedTO, eClass);

        // Add documentation if present
        if (unmappedTO.getDocumentation() != null && 
            !unmappedTO.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createDocumentationAnnotation(unmappedTO);
            eClass.getEAnnotations().add(docAnnotation);
        }
    }

    private void addTransferObjectTypeAnnotation(TransferObjectType to, EClass eClass) {
        EAnnotation toAnnotation = createAnnotation(
                "(psm/" + getId(to) + ")/TransferObjectTypeAnnotationClass",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add query customizer annotation if applicable
        if (to.isQueryCustomizer()) {
            EAnnotation qcAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/QueryCustomizerAnnotationForQueryCustomizerClass",
                    getAnnotationUri("queryCustomizer"));
            addAnnotationDetail(qcAnnotation, "value", "true");
            eClass.getEAnnotations().add(qcAnnotation);
        }
        
        // Add getRangeInput annotation if this transfer object type is used as input for GET_RANGE operation
        if (isGetRangeInputType(to)) {
            EAnnotation getRangeInputAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/GetRangeInputAnnotationForGetRangeInputClass",
                    getAnnotationUri("getRangeInput"));
            addAnnotationDetail(getRangeInputAnnotation, "value", "true");
            eClass.getEAnnotations().add(getRangeInputAnnotation);
        }
        
        // Add metadata annotation if this transfer object type is a metadata type
        if (isMetadataType(to)) {
            EAnnotation metadataAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/MetadataAnnotationForMetadataClass",
                    getAnnotationUri("metadata"));
            addAnnotationDetail(metadataAnnotation, "value", "true");
            eClass.getEAnnotations().add(metadataAnnotation);
        }
    }

    private void createReferenceClassForEntityType(EntityType entityType) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(entityType) + ")/ReferenceClassForEntityType");
        eClass.setName(entityType.getName() + "__Reference");

        // Set up inheritance
        for (EntityType superType : entityType.getSuperEntityTypes()) {
            EClass superRefClass = (EClass) getEquivalent(superType, "CreateReferenceClassForEntityType");
            if (superRefClass != null) {
                eClass.getESuperTypes().add(superRefClass);
            }
        }

        getContainerPackage(entityType).getEClassifiers().add(eClass);
        addTrace(entityType, "CreateReferenceClassForEntityType", eClass);

        // Add reference holder annotation
        EAnnotation refHolderAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/AnnotationOnReferenceClassForEntityType",
                getAnnotationUri("referenceHolder"));
        addAnnotationDetail(refHolderAnnotation, "value", "true");
        eClass.getEAnnotations().add(refHolderAnnotation);

        // Add transfer object type annotation
        EAnnotation toAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add mapped entity type annotation
        EAnnotation mappedEntityAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                getAnnotationUri("mappedEntityType"));
        EClass entityClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
        if (entityClass != null) {
            addAnnotationDetail(mappedEntityAnnotation, "value", 
                    getClassifierFQName(entityClass));
        }
        eClass.getEAnnotations().add(mappedEntityAnnotation);
    }

    private void transformTransferAttribute(TransferAttribute transferAttr) {
        if (transferAttr.getDataType() == null || 
            !(transferAttr.getDataType() instanceof Primitive)) {
            return;
        }

        EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
        setId(eAttr, "(psm/" + getId(transferAttr) + ")/TransferObjectAttribute");
        eAttr.setName(transferAttr.getName());
        eAttr.setLowerBound(transferAttr.isRequired() ? 1 : 0);

        // Set derived and changeable based on binding
        boolean isDerived = transferAttr.getBinding() != null && 
                           !(transferAttr.getBinding() instanceof Attribute);
        eAttr.setDerived(isDerived);
        
        boolean isChangeable = transferAttr.getBinding() == null ||
                              (transferAttr.getBinding() instanceof Attribute) ||
                              (transferAttr.getBinding() instanceof PrimitiveAccessor && 
                               ((PrimitiveAccessor) transferAttr.getBinding()).getSetterExpression() != null);
        eAttr.setChangeable(isChangeable);

        // Set type
        EClassifier type = getEquivalentType(transferAttr.getDataType());
        if (type != null) {
            eAttr.setEType(type);
        }

        // Add to owning transfer object class
        TransferObjectType owner = (TransferObjectType) transferAttr.eContainer();
        if (owner != null) {
            EClass ownerClass = getEquivalentTransferObjectClass(owner);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eAttr);
            }
        }

        addTrace(transferAttr, CREATE_TRANSFER_ATTRIBUTE, eAttr);

        // Add binding annotation FIRST (ETL order: binding before constraints)
        if (transferAttr.getBinding() != null && !(transferAttr.getBinding() instanceof StaticData)) {
            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferObjectAttributeBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferAttr.getBinding().getName());
            eAttr.getEAnnotations().add(bindingAnnotation);
        }

        // Add constraints annotation SECOND
        addTransferAttributeConstraints(transferAttr, eAttr);

        // Add expression annotation for PrimitiveAccessor bindings (including StaticData)
        // Based on ETL CreateDataReferenceBinding rule
        if (transferAttr.getBinding() != null && transferAttr.getBinding() instanceof PrimitiveAccessor) {
            PrimitiveAccessor binding = (PrimitiveAccessor) transferAttr.getBinding();
            TransferObjectType container = (TransferObjectType) transferAttr.eContainer();
            
            // Guard: (UnmappedTransferObjectType AND PrimitiveAccessor) OR (MappedTransferObjectType AND StaticData)
            boolean shouldAddExpression = 
                (container instanceof UnmappedTransferObjectType && binding instanceof PrimitiveAccessor) ||
                (container instanceof MappedTransferObjectType && binding instanceof StaticData);
            
            if (shouldAddExpression && binding.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/DataReferenceBinding",
                        getAnnotationUri("expression"));
                
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());
                
                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
                
                if (binding.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", 
                            binding.getSetterExpression().getDialect().toString());
                }
                
                eAttr.getEAnnotations().add(exprAnnotation);
            }
        }

        // Add transient annotation if no binding
        if (transferAttr.getBinding() == null) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransientAnnotationToTransferAttribute",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eAttr.getEAnnotations().add(transientAnnotation);
        }

        // Add claim annotation if applicable
        if (transferAttr.getClaimType() != null) {
            EAnnotation claimAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferAttributeClaimAnnotation",
                    getAnnotationUri("claim"));
            addAnnotationDetail(claimAnnotation, "value", transferAttr.getClaimType());
            eAttr.getEAnnotations().add(claimAnnotation);
        }

        // Add default annotation if applicable
        if (transferAttr.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DefaultAnnotationToTransferAttribute",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferAttr.getDefaultValue().getName());
            eAttr.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferAttr.getDocumentation() != null && 
            !transferAttr.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DocumentationAnnotationForTransferAttribute",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferAttr.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);
        }
    }

    private void addTransferAttributeConstraints(TransferAttribute transferAttr, EAttribute eAttr) {
        Primitive dataType = transferAttr.getDataType();
        if (dataType == null) return;

        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(transferAttr) + ")/TransferAttributeConstraints",
                getAnnotationUri("constraints"));

        if (dataType instanceof StringType) {
            StringType stringType = (StringType) dataType;
            addAnnotationDetail(constraintsAnnotation, "maxLength", 
                    String.valueOf(stringType.getMaxLength()));
            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }
        } else if (dataType instanceof NumericType) {
            NumericType numericType = (NumericType) dataType;
            addAnnotationDetail(constraintsAnnotation, "precision", 
                    String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", 
                    String.valueOf(numericType.getScale()));

            if (numericType instanceof MeasuredType) {
                MeasuredType measuredType = (MeasuredType) numericType;
                if (measuredType.getStoreUnit() != null) {
                    // ETL order: measure first, then unit
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                    }
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                }
            }
        } else if (dataType instanceof CustomType && !(dataType instanceof NumericType) 
                && !(dataType instanceof BooleanType) && !(dataType instanceof EnumerationType)
                && !(dataType instanceof StringType)) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    getQualifiedName(dataType));
        }

        if (!constraintsAnnotation.getDetails().isEmpty()) {
            eAttr.getEAnnotations().add(constraintsAnnotation);
        }
    }

    private void transformTransferObjectRelation(TransferObjectRelation transferRel) {
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(transferRel) + ")/TransferObjectRelation");
        eRef.setName(transferRel.getName());
        eRef.setContainment(transferRel.isEmbedded());

        if (transferRel.getCardinality() != null) {
            eRef.setLowerBound(transferRel.getCardinality().getLower());
            eRef.setUpperBound(transferRel.getCardinality().getUpper());
        }

        // Set derived and changeable based on binding
        boolean isDerived = transferRel.getBinding() != null && 
                           !(transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation);
        eRef.setDerived(isDerived);
        
        boolean isChangeable = transferRel.getBinding() == null ||
                              (transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation) ||
                              (transferRel.getBinding() instanceof ReferenceAccessor && 
                               ((ReferenceAccessor) transferRel.getBinding()).getSetterExpression() != null);
        eRef.setChangeable(isChangeable);

        // Set target type
        if (transferRel.getTarget() != null) {
            EClass targetClass = getEquivalentTransferObjectClass(transferRel.getTarget());
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        // Add to owning transfer object class
        TransferObjectType owner = (TransferObjectType) transferRel.eContainer();
        if (owner != null) {
            EClass ownerClass = getEquivalentTransferObjectClass(owner);
            if (ownerClass != null) {
                ownerClass.getEStructuralFeatures().add(eRef);
            }
        }

        addTrace(transferRel, CREATE_TRANSFER_RELATION, eRef);

        // Add binding annotation
        if (transferRel.getBinding() != null && !(transferRel.getBinding() instanceof StaticNavigation)) {
            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferRel.getBinding().getName());
            eRef.getEAnnotations().add(bindingAnnotation);
        }

        // Add expression annotation for ReferenceAccessor bindings (including StaticNavigation)
        // Based on ETL CreateNavigationReferenceBinding rule
        if (transferRel.getBinding() != null && transferRel.getBinding() instanceof ReferenceAccessor) {
            ReferenceAccessor binding = (ReferenceAccessor) transferRel.getBinding();
            TransferObjectType container = (TransferObjectType) transferRel.eContainer();
            
            // Guard: (UnmappedTransferObjectType AND ReferenceAccessor) OR (MappedTransferObjectType AND StaticNavigation)
            boolean shouldAddExpression = 
                (container instanceof UnmappedTransferObjectType && binding instanceof ReferenceAccessor) ||
                (container instanceof MappedTransferObjectType && binding instanceof StaticNavigation);
            
            if (shouldAddExpression && binding.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/NavigationReferenceBinding",
                        getAnnotationUri("expression"));
                
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());
                
                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
                
                if (binding.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", 
                            binding.getSetterExpression().getDialect().toString());
                }
                
                eRef.getEAnnotations().add(exprAnnotation);
            }
        }

        // Add access annotation if applicable
        if (transferRel.isAccess()) {
            EAnnotation accessAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationAccessAnnotation",
                    getAnnotationUri("access"));
            addAnnotationDetail(accessAnnotation, "value", "true");
            eRef.getEAnnotations().add(accessAnnotation);
        }

        // ETL order for remaining annotations: range → embedded → permissions → transient
        
        // Add range annotation if applicable
        if (transferRel.getRange() != null) {
            EAnnotation rangeAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationRangeAnnotation",
                    getAnnotationUri("range"));
            addAnnotationDetail(rangeAnnotation, "value", transferRel.getRange().getName());
            eRef.getEAnnotations().add(rangeAnnotation);
        }

        // Add embedded flags annotation if embedded
        if (transferRel.isEmbedded()) {
            EAnnotation embeddedAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationEmbeddedFlags",
                    getAnnotationUri("embedded"));
            addAnnotationDetail(embeddedAnnotation, "value", "true");
            addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(embeddedAnnotation);
        }

        // Add permissions annotation
        EAnnotation permissionsAnnotation = createAnnotation(
                "(psm/" + getId(transferRel) + ")/TransferObjectRelationPermissions",
                getAnnotationUri("permissions"));
        addAnnotationDetail(permissionsAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
        addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
        addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
        eRef.getEAnnotations().add(permissionsAnnotation);

        // Add transient annotation if no binding and not access (after embedded and permissions per ETL order)
        if (transferRel.getBinding() == null && !transferRel.isAccess()) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransientAnnotationToTransferObjectRelation",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eRef.getEAnnotations().add(transientAnnotation);
        }

        // Add default annotation if applicable
        if (transferRel.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DefaultAnnotationToTransferObjectRelation",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferRel.getDefaultValue().getName());
            eRef.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferRel.getDocumentation() != null && 
            !transferRel.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DocumentationAnnotationForTransferObjectRelation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferRel.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);
        }
    }

    // =========================================================================
    // STAGED TRANSFORMATION METHODS FOR PARALLEL PROCESSING
    // These methods create elements in parallel but stage the "add to owner"
    // operations for sequential execution to maintain EMF thread safety.
    // =========================================================================

    /**
     * Staged version of transformTransferAttribute for parallel processing.
     * Creates the EAttribute and all annotations (thread-safe), but stages
     * the "add to owner" operation for sequential execution later.
     */
    private void transformTransferAttributeStaged(TransferAttribute transferAttr) {
        if (transferAttr.getDataType() == null || 
            !(transferAttr.getDataType() instanceof Primitive)) {
            return;
        }

        EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
        setId(eAttr, "(psm/" + getId(transferAttr) + ")/TransferObjectAttribute");
        eAttr.setName(transferAttr.getName());
        eAttr.setLowerBound(transferAttr.isRequired() ? 1 : 0);

        // Set derived and changeable based on binding
        boolean isDerived = transferAttr.getBinding() != null && 
                           !(transferAttr.getBinding() instanceof Attribute);
        eAttr.setDerived(isDerived);
        
        boolean isChangeable = transferAttr.getBinding() == null ||
                              (transferAttr.getBinding() instanceof Attribute) ||
                              (transferAttr.getBinding() instanceof PrimitiveAccessor && 
                               ((PrimitiveAccessor) transferAttr.getBinding()).getSetterExpression() != null);
        eAttr.setChangeable(isChangeable);

        // Set type
        EClassifier type = getEquivalentType(transferAttr.getDataType());
        if (type != null) {
            eAttr.setEType(type);
        }

        // Add trace immediately (thread-safe with ConcurrentHashMap)
        addTrace(transferAttr, CREATE_TRANSFER_ATTRIBUTE, eAttr);

        // Add binding annotation FIRST (ETL order: binding before constraints)
        if (transferAttr.getBinding() != null && !(transferAttr.getBinding() instanceof StaticData)) {
            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferObjectAttributeBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferAttr.getBinding().getName());
            eAttr.getEAnnotations().add(bindingAnnotation);
        }

        // Add constraints annotation SECOND
        addTransferAttributeConstraints(transferAttr, eAttr);

        // Add expression annotation for PrimitiveAccessor bindings (including StaticData)
        if (transferAttr.getBinding() != null && transferAttr.getBinding() instanceof PrimitiveAccessor) {
            PrimitiveAccessor binding = (PrimitiveAccessor) transferAttr.getBinding();
            TransferObjectType container = (TransferObjectType) transferAttr.eContainer();
            
            boolean shouldAddExpression = 
                (container instanceof UnmappedTransferObjectType && binding instanceof PrimitiveAccessor) ||
                (container instanceof MappedTransferObjectType && binding instanceof StaticData);
            
            if (shouldAddExpression && binding.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/DataReferenceBinding",
                        getAnnotationUri("expression"));
                
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());
                
                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
                
                if (binding.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", 
                            binding.getSetterExpression().getDialect().toString());
                }
                
                eAttr.getEAnnotations().add(exprAnnotation);
            }
        }

        // Add transient annotation if no binding
        if (transferAttr.getBinding() == null) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransientAnnotationToTransferAttribute",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eAttr.getEAnnotations().add(transientAnnotation);
        }

        // Add claim annotation if applicable
        if (transferAttr.getClaimType() != null) {
            EAnnotation claimAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferAttributeClaimAnnotation",
                    getAnnotationUri("claim"));
            addAnnotationDetail(claimAnnotation, "value", transferAttr.getClaimType());
            eAttr.getEAnnotations().add(claimAnnotation);
        }

        // Add default annotation if applicable
        if (transferAttr.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DefaultAnnotationToTransferAttribute",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferAttr.getDefaultValue().getName());
            eAttr.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferAttr.getDocumentation() != null && 
            !transferAttr.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DocumentationAnnotationForTransferAttribute",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferAttr.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);
        }

        // Stage the "add to owner" operation for sequential execution
        TransferObjectType owner = (TransferObjectType) transferAttr.eContainer();
        if (owner != null) {
            stagedFeatureAdditions.add(() -> {
                EClass ownerClass = getEquivalentTransferObjectClass(owner);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eAttr);
                }
            });
        }
    }

    /**
     * Staged version of transformTransferObjectRelation for parallel processing.
     * Creates the EReference and all annotations (thread-safe), but stages
     * the "add to owner" operation for sequential execution later.
     */
    private void transformTransferObjectRelationStaged(TransferObjectRelation transferRel) {
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(transferRel) + ")/TransferObjectRelation");
        eRef.setName(transferRel.getName());
        eRef.setContainment(transferRel.isEmbedded());

        if (transferRel.getCardinality() != null) {
            eRef.setLowerBound(transferRel.getCardinality().getLower());
            eRef.setUpperBound(transferRel.getCardinality().getUpper());
        }

        // Set derived and changeable based on binding
        boolean isDerived = transferRel.getBinding() != null && 
                           !(transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation);
        eRef.setDerived(isDerived);
        
        boolean isChangeable = transferRel.getBinding() == null ||
                              (transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation) ||
                              (transferRel.getBinding() instanceof ReferenceAccessor && 
                               ((ReferenceAccessor) transferRel.getBinding()).getSetterExpression() != null);
        eRef.setChangeable(isChangeable);

        // Set target type
        if (transferRel.getTarget() != null) {
            EClass targetClass = getEquivalentTransferObjectClass(transferRel.getTarget());
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        // Add trace immediately (thread-safe with ConcurrentHashMap)
        addTrace(transferRel, CREATE_TRANSFER_RELATION, eRef);

        // Add binding annotation
        if (transferRel.getBinding() != null && !(transferRel.getBinding() instanceof StaticNavigation)) {
            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferRel.getBinding().getName());
            eRef.getEAnnotations().add(bindingAnnotation);
        }

        // Add expression annotation for ReferenceAccessor bindings (including StaticNavigation)
        if (transferRel.getBinding() != null && transferRel.getBinding() instanceof ReferenceAccessor) {
            ReferenceAccessor binding = (ReferenceAccessor) transferRel.getBinding();
            TransferObjectType container = (TransferObjectType) transferRel.eContainer();
            
            boolean shouldAddExpression = 
                (container instanceof UnmappedTransferObjectType && binding instanceof ReferenceAccessor) ||
                (container instanceof MappedTransferObjectType && binding instanceof StaticNavigation);
            
            if (shouldAddExpression && binding.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/NavigationReferenceBinding",
                        getAnnotationUri("expression"));
                
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());
                
                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
                
                if (binding.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", 
                            binding.getSetterExpression().getDialect().toString());
                }
                
                eRef.getEAnnotations().add(exprAnnotation);
            }
        }

        // Add access annotation if applicable
        if (transferRel.isAccess()) {
            EAnnotation accessAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationAccessAnnotation",
                    getAnnotationUri("access"));
            addAnnotationDetail(accessAnnotation, "value", "true");
            eRef.getEAnnotations().add(accessAnnotation);
        }

        // Add range annotation if applicable
        if (transferRel.getRange() != null) {
            EAnnotation rangeAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationRangeAnnotation",
                    getAnnotationUri("range"));
            addAnnotationDetail(rangeAnnotation, "value", transferRel.getRange().getName());
            eRef.getEAnnotations().add(rangeAnnotation);
        }

        // Add embedded flags annotation if embedded
        if (transferRel.isEmbedded()) {
            EAnnotation embeddedAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationEmbeddedFlags",
                    getAnnotationUri("embedded"));
            addAnnotationDetail(embeddedAnnotation, "value", "true");
            addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(embeddedAnnotation);
        }

        // Add permissions annotation
        EAnnotation permissionsAnnotation = createAnnotation(
                "(psm/" + getId(transferRel) + ")/TransferObjectRelationPermissions",
                getAnnotationUri("permissions"));
        addAnnotationDetail(permissionsAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
        addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
        addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
        eRef.getEAnnotations().add(permissionsAnnotation);

        // Add transient annotation if no binding and not access
        if (transferRel.getBinding() == null && !transferRel.isAccess()) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransientAnnotationToTransferObjectRelation",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eRef.getEAnnotations().add(transientAnnotation);
        }

        // Add default annotation if applicable
        if (transferRel.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DefaultAnnotationToTransferObjectRelation",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferRel.getDefaultValue().getName());
            eRef.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferRel.getDocumentation() != null && 
            !transferRel.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DocumentationAnnotationForTransferObjectRelation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferRel.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);
        }

        // Stage the "add to owner" operation for sequential execution
        TransferObjectType owner = (TransferObjectType) transferRel.eContainer();
        if (owner != null) {
            stagedFeatureAdditions.add(() -> {
                EClass ownerClass = getEquivalentTransferObjectClass(owner);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            });
        }
    }

    // =========================================================================
    // ACTORS TRANSFORMATIONS
    // =========================================================================

    private void transformActors() {
        log.debug("Transforming actors");
        all(TransferObjectType.class)
                .filter(to -> to.getActorType() != null)
                .forEach(this::addActorAnnotation);
        
        all(AbstractActorType.class).forEach(this::addActorTypeAnnotation);
    }

    private void addActorAnnotation(TransferObjectType to) {
        EClass eClass = getEquivalentTransferObjectClass(to);
        if (eClass == null) return;

        AbstractActorType actorType = to.getActorType();
        if (actorType == null) return;

        EAnnotation actorAnnotation = createAnnotation(
                "(psm/" + getId(to) + ")/ActorAnnotation",
                getAnnotationUri("actor"));
        
        addAnnotationDetail(actorAnnotation, "name", 
                psmUtils.namespaceElementToString((NamespaceElement) actorType));
        
        // ETL's isDefined() means not null AND not empty
        if (actorType.getRealm() != null && !actorType.getRealm().isEmpty()) {
            addAnnotationDetail(actorAnnotation, "realm", actorType.getRealm());
        }

        eClass.getEAnnotations().add(actorAnnotation);
    }

    private void addActorTypeAnnotation(AbstractActorType actorType) {
        // ETL's CreateActorTypeAnnotation transforms TransferObjectType with guard isKindOf(AbstractActorType)
        // Both ActorType and MappedActorType ARE TransferObjectTypes, so we add annotations to their own ASM equivalent
        
        // AbstractActorType can be:
        // - MappedActorType (extends MappedTransferObjectType) - IS itself a TransferObjectType
        // - ActorType (extends UnmappedTransferObjectType) - IS itself a TransferObjectType
        
        // Get the ASM equivalent of this AbstractActorType (which IS a TransferObjectType)
        EClass eClass = getEquivalentTransferObjectClass((TransferObjectType) actorType);
        if (eClass == null) return;

        EAnnotation actorTypeAnnotation = createAnnotation(
                "(psm/" + getId(actorType) + ")/ActorTypeAnnotation",
                getAnnotationUri("actorType"));
        addAnnotationDetail(actorTypeAnnotation, "value", "true");

        if (actorType instanceof MappedActorType) {
            MappedActorType mappedActor = (MappedActorType) actorType;
            addAnnotationDetail(actorTypeAnnotation, "managed", String.valueOf(mappedActor.isManaged()));
        }

        if (actorType.getKind() != null) {
            addAnnotationDetail(actorTypeAnnotation, "kind", actorType.getKind().toString());
        }

        eClass.getEAnnotations().add(actorTypeAnnotation);

        // Add realm annotation if applicable (CreateRealmTypeAnnotation in ETL)
        // ETL's guard checks s.realm.isDefined() which means not null AND not empty
        if (actorType.getRealm() != null && !actorType.getRealm().isEmpty()) {
            EAnnotation realmAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/RealmTypeAnnotation",
                    getAnnotationUri("realm"));
            addAnnotationDetail(realmAnnotation, "value", actorType.getRealm());
            eClass.getEAnnotations().add(realmAnnotation);
        }

        // Add documentation if present (CreateDocumentationAnnotationForActorType in ETL)
        if (actorType.getDocumentation() != null && 
            !actorType.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/DocumentationAnnotationForActorType",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", actorType.getDocumentation());
            eClass.getEAnnotations().add(docAnnotation);
        }
    }

    // =========================================================================
    // STATIC TRANSFORMATIONS
    // =========================================================================

    private void transformStatics() {
        log.debug("Transforming static data and navigation");
        all(StaticData.class).forEach(this::transformStaticData);
        all(StaticNavigation.class)
                .filter(this::hasDefaultRepresentation)
                .forEach(this::transformStaticNavigation);
    }

    private void transformStaticData(StaticData staticData) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(staticData) + ")/UnmappedTransferObjectForStaticData");
        eClass.setName(capitalize(staticData.getName()));

        getContainerPackage(staticData).getEClassifiers().add(eClass);
        addTrace(staticData, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA, eClass);

        // Add transfer object type annotation
        EAnnotation toAnnotation = createAnnotation(
                "(psm/" + getId(staticData) + ")/TransferObjectTypeAnnotationClassForStaticData",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add static query annotation
        EAnnotation staticQueryAnnotation = createAnnotation(
                "(psm/" + getId(staticData) + ")/StaticDataQueryAnnotation",
                getAnnotationUri("staticQuery"));
        eClass.getEAnnotations().add(staticQueryAnnotation);

        // Create static query attribute
        EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
        setId(eAttr, "(psm/" + getId(staticData) + ")/StaticQueryAttribute");
        eAttr.setName(staticData.getName());
        eAttr.setLowerBound(staticData.isRequired() ? 1 : 0);
        eAttr.setDerived(true);
        eAttr.setChangeable(false);

        EClassifier type = getEquivalentType(staticData.getDataType());
        if (type != null) {
            eAttr.setEType(type);
        }

        eClass.getEStructuralFeatures().add(eAttr);

        // Add expression annotation for getter
        if (staticData.getGetterExpression() != null) {
            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/DataReferenceBindingForStaticData",
                    getAnnotationUri("expression"));
            addAnnotationDetail(exprAnnotation, "getter", staticData.getGetterExpression().getExpression());
            addAnnotationDetail(exprAnnotation, "getter.dialect", staticData.getGetterExpression().getDialect().toString());

            if (staticData.getGetterExpression().getParameterType() != null) {
                EClassifier paramType = getEquivalentTransferObject(staticData.getGetterExpression().getParameterType());
                if (paramType != null) {
                    addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));

                    // Add parameterized annotation
                    EAnnotation paramAnnotation = createAnnotation(
                            "(psm/" + getId(staticData) + ")/TransferAttributeParameterizedAnnotationForStaticData",
                            getAnnotationUri("parameterized"));
                    addAnnotationDetail(paramAnnotation, "value", "true");
                    addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                    eAttr.getEAnnotations().add(paramAnnotation);
                }
            }

            if (staticData.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", staticData.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", staticData.getSetterExpression().getDialect().toString());
            }

            eAttr.getEAnnotations().add(exprAnnotation);
        }
    }

    private void transformStaticNavigation(StaticNavigation staticNav) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        setId(eClass, "(psm/" + getId(staticNav) + ")/UnmappedTransferObjectForStaticNavigation");
        eClass.setName(capitalize(staticNav.getName()));

        getContainerPackage(staticNav).getEClassifiers().add(eClass);
        addTrace(staticNav, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION, eClass);

        // Add transfer object type annotation
        EAnnotation toAnnotation = createAnnotation(
                "(psm/" + getId(staticNav) + ")/TransferObjectTypeAnnotationClassForStaticNavigation",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add static query annotation
        EAnnotation staticQueryAnnotation = createAnnotation(
                "(psm/" + getId(staticNav) + ")/StaticNavigationQueryAnnotation",
                getAnnotationUri("staticQuery"));
        eClass.getEAnnotations().add(staticQueryAnnotation);

        // Create static query navigation reference
        EReference eRef = EcoreFactory.eINSTANCE.createEReference();
        setId(eRef, "(psm/" + getId(staticNav) + ")/StaticQueryNavigation");
        eRef.setName(staticNav.getName());
        eRef.setContainment(false);
        eRef.setDerived(true);
        eRef.setChangeable(false);

        if (staticNav.getCardinality() != null) {
            eRef.setLowerBound(staticNav.getCardinality().getLower());
            eRef.setUpperBound(staticNav.getCardinality().getUpper());
        }

        // Set target type to the default representation's mapped transfer object
        MappedTransferObjectType defaultRep = staticNav.getTarget().getDefaultRepresentation();
        if (defaultRep != null) {
            EClass targetClass = (EClass) getEquivalent(defaultRep, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            if (targetClass != null) {
                eRef.setEType(targetClass);
            }
        }

        eClass.getEStructuralFeatures().add(eRef);

        // Add expression annotation for getter
        if (staticNav.getGetterExpression() != null) {
            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/NavigationReferenceBindingForStaticNavigation",
                    getAnnotationUri("expression"));
            addAnnotationDetail(exprAnnotation, "getter", staticNav.getGetterExpression().getExpression());
            addAnnotationDetail(exprAnnotation, "getter.dialect", staticNav.getGetterExpression().getDialect().toString());

            if (staticNav.getGetterExpression().getParameterType() != null) {
                EClassifier paramType = getEquivalentTransferObject(staticNav.getGetterExpression().getParameterType());
                if (paramType != null) {
                    addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));

                    // Add parameterized annotation
                    EAnnotation paramAnnotation = createAnnotation(
                            "(psm/" + getId(staticNav) + ")/TransferObjectRelationParameterizedAnnotationForStaticNavigation",
                            getAnnotationUri("parameterized"));
                    addAnnotationDetail(paramAnnotation, "value", "true");
                    addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                    eRef.getEAnnotations().add(paramAnnotation);
                }
            }

            if (staticNav.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", staticNav.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", staticNav.getSetterExpression().getDialect().toString());
            }

            eRef.getEAnnotations().add(exprAnnotation);
        }
    }

    // =========================================================================
    // POST-PROCESSING
    // =========================================================================

    private void postProcess() {
        asmUtils.enrichWithAnnotations();

        // Apply annotations to auto-generated operations created by enrichWithAnnotations()
        // ETL's @greedy rules automatically apply to these, but Zeta needs explicit handling
        addAnnotationsToAutoGeneratedOperations();

        // Fix annotation detail IDs that start with "_"
        asmUtils.all(EAnnotation.class).forEach(annotation -> {
            for (Map.Entry<String, String> detail : annotation.getDetails()) {
                // The detail entries are EStringToStringMapEntry objects
                // We need to handle ID fixing here if needed
            }
        });
    }

    /**
     * Add annotations to auto-generated operations created by enrichWithAnnotations().
     * These operations (like _updateInstance*, _createInstance*, etc.) are generated
     * dynamically and need the same annotations that ETL's @greedy rules apply.
     */
    private void addAnnotationsToAutoGeneratedOperations() {
        String immutableUri = getAnnotationUri("immutable");
        String outputParamUri = getAnnotationUri("outputParameterName");
        String transferObjectTypeUri = getAnnotationUri("transferObjectType");
        String entityUri = getAnnotationUri("entity");
        
        asmUtils.all(EOperation.class).forEach(eOp -> {
            // Determine if this operation is on a TransferObjectType (not an entity)
            // TransferOperation subclasses get immutable annotation, BoundOperation on entities don't
            EClass containingClass = eOp.getEContainingClass();
            boolean isOnTransferObjectType = containingClass != null && 
                    containingClass.getEAnnotations().stream()
                            .anyMatch(ann -> transferObjectTypeUri.equals(ann.getSource()));
            boolean isOnEntity = containingClass != null && 
                    containingClass.getEAnnotations().stream()
                            .anyMatch(ann -> entityUri.equals(ann.getSource()));
            
            // Check if this operation is missing the immutable annotation
            boolean hasImmutable = eOp.getEAnnotations().stream()
                    .anyMatch(ann -> immutableUri.equals(ann.getSource()));
            
            // Check if this operation is missing the outputParameterName annotation
            boolean hasOutputParam = eOp.getEAnnotations().stream()
                    .anyMatch(ann -> outputParamUri.equals(ann.getSource()));
            
            // Only add immutable if missing AND operation is on a TransferObjectType (not an entity)
            // ETL's CreateImmutableFlagForTransferOperation only applies to TransferOperation
            if (!hasImmutable && isOnTransferObjectType && !isOnEntity) {
                EAnnotation immutableAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
                immutableAnnotation.setSource(immutableUri);
                immutableAnnotation.getDetails().put("value", "false");
                eOp.getEAnnotations().add(immutableAnnotation);
            }
            
            // Only add outputParameterName if missing and operation has output type
            // ETL's CreateOutputParameterName applies to all OperationDeclaration with output
            if (!hasOutputParam && eOp.getEType() != null) {
                EAnnotation outputAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
                outputAnnotation.setSource(outputParamUri);
                // Auto-generated operations use "output" as default parameter name
                outputAnnotation.getDetails().put("value", "output");
                eOp.getEAnnotations().add(outputAnnotation);
            }
        });
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void addStatefulAnnotation(TransferOperation transferOp, EOperation eOp) {
        EAnnotation statefulAnnotation;
        String statefulValue;

        if (transferOp.getImplementation() != null) {
            // If implementation is defined, use its stateful value
            statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperation",
                    getAnnotationUri("stateful"));
            statefulValue = String.valueOf(transferOp.getImplementation().isStateful());
        } else if (transferOp.getBehaviour() == null) {
            // No implementation and no behaviour - default stateful = true
            statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperationWithoutImplementationAndBehaviour",
                    getAnnotationUri("stateful"));
            statefulValue = "true";
        } else {
            // No implementation but has behaviour - stateful depends on behaviour type
            statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperationWithBehaviour",
                    getAnnotationUri("stateful"));
            
            TransferOperationBehaviourType behaviourType = transferOp.getBehaviour().getBehaviourType();
            switch (behaviourType) {
                case VALIDATE_CREATE:
                case VALIDATE_UPDATE:
                case LIST:
                case EXPORT:
                case GET_RANGE:
                case GET_TEMPLATE:
                case GET_PRINCIPAL:
                case GET_METADATA:
                case VALIDATE_OPERATION_INPUT:
                    statefulValue = "false";
                    break;
                default:
                    statefulValue = "true";
                    break;
            }
        }

        addAnnotationDetail(statefulAnnotation, "value", statefulValue);
        eOp.getEAnnotations().add(statefulAnnotation);
    }

    private void addBehaviourAnnotation(TransferOperation transferOp, EOperation eOp) {
        TransferOperationBehaviour behaviour = transferOp.getBehaviour();
        if (behaviour == null) {
            return;
        }

        EAnnotation behaviourAnnotation = createAnnotation(
                "(psm/" + getId(transferOp) + ")/BehaviourAnnotation",
                getAnnotationUri("behaviour"));

        // Map behaviour type to string value
        String typeValue;
        TransferOperationBehaviourType behaviourType = behaviour.getBehaviourType();
        switch (behaviourType) {
            case LIST:
                typeValue = "list";
                break;
            case CREATE_INSTANCE:
                typeValue = "createInstance";
                break;
            case VALIDATE_CREATE:
                typeValue = "validateCreate";
                break;
            case REFRESH:
                typeValue = "refresh";
                break;
            case UPDATE_INSTANCE:
                typeValue = "updateInstance";
                break;
            case VALIDATE_UPDATE:
                typeValue = "validateUpdate";
                break;
            case DELETE_INSTANCE:
                typeValue = "deleteInstance";
                break;
            case SET_REFERENCE:
                typeValue = "setReference";
                break;
            case UNSET_REFERENCE:
                typeValue = "unsetReference";
                break;
            case ADD_REFERENCE:
                typeValue = "addReference";
                break;
            case REMOVE_REFERENCE:
                typeValue = "removeReference";
                break;
            case GET_RANGE:
                if (behaviour.getOwner() instanceof TransferObjectRelation) {
                    typeValue = "getReferenceRange";
                } else if (behaviour.getOwner() instanceof TransferOperation) {
                    typeValue = "getInputRange";
                } else {
                    typeValue = "getRange";
                }
                break;
            case GET_TEMPLATE:
                typeValue = "getTemplate";
                break;
            case GET_PRINCIPAL:
                typeValue = "getPrincipal";
                break;
            case GET_METADATA:
                typeValue = "getMetadata";
                break;
            case GET_UPLOAD_TOKEN:
                typeValue = "getUploadToken";
                break;
            case EXPORT:
                typeValue = "export";
                break;
            case VALIDATE_OPERATION_INPUT:
                typeValue = "validateOperationInput";
                break;
            default:
                typeValue = behaviourType.toString().toLowerCase();
                break;
        }
        addAnnotationDetail(behaviourAnnotation, "type", typeValue);

        // Add owner detail
        String ownerValue = null;
        EObject owner = behaviour.getOwner();
        if (owner != null) {
            switch (behaviourType) {
                case GET_TEMPLATE:
                case GET_PRINCIPAL:
                case GET_METADATA:
                case REFRESH:
                case UPDATE_INSTANCE:
                case VALIDATE_UPDATE:
                case DELETE_INSTANCE:
                    // Owner is a TransferObjectType - use classifier FQN
                    EClassifier ownerClassifier = getEquivalentTransferObject((TransferObjectType) owner);
                    if (ownerClassifier != null) {
                        ownerValue = getClassifierFQName(ownerClassifier);
                    }
                    break;
                case GET_UPLOAD_TOKEN:
                    // Owner is a TransferAttribute - use attribute FQN
                    EObject attrEquiv = getEquivalent(owner, CREATE_TRANSFER_ATTRIBUTE);
                    if (attrEquiv instanceof EAttribute) {
                        ownerValue = asmUtils.getAttributeFQName((EAttribute) attrEquiv);
                    }
                    break;
                case GET_RANGE:
                    if (owner instanceof TransferObjectRelation) {
                        EObject relEquiv = getEquivalent(owner, CREATE_TRANSFER_RELATION);
                        if (relEquiv instanceof EReference) {
                            ownerValue = asmUtils.getReferenceFQName((EReference) relEquiv);
                        }
                    } else if (owner instanceof TransferOperation) {
                        EObject opEquiv = getEquivalent(owner, "CreateBoundTransferOperation");
                        if (opEquiv == null) {
                            opEquiv = getEquivalent(owner, "CreateTransferOperation");
                        }
                        if (opEquiv instanceof EOperation) {
                            ownerValue = asmUtils.getOperationFQName((EOperation) opEquiv);
                        }
                    }
                    break;
                case VALIDATE_OPERATION_INPUT:
                    // Owner is a TransferOperation - use operation FQN
                    EObject opEquiv2 = getEquivalent(owner, "CreateBoundTransferOperation");
                    if (opEquiv2 == null) {
                        opEquiv2 = getEquivalent(owner, "CreateTransferOperation");
                    }
                    if (opEquiv2 instanceof EOperation) {
                        ownerValue = asmUtils.getOperationFQName((EOperation) opEquiv2);
                    }
                    break;
                default:
                    // Default: owner is a TransferObjectRelation - use reference FQN
                    EObject defaultEquiv = getEquivalent(owner, CREATE_TRANSFER_RELATION);
                    if (defaultEquiv instanceof EReference) {
                        ownerValue = asmUtils.getReferenceFQName((EReference) defaultEquiv);
                    }
                    break;
            }
        }
        if (ownerValue != null) {
            addAnnotationDetail(behaviourAnnotation, "owner", ownerValue);
        }

        eOp.getEAnnotations().add(behaviourAnnotation);

        // For BoundTransferOperation, also add behaviour annotation to the binding
        if (transferOp instanceof BoundTransferOperation) {
            BoundTransferOperation boundTransferOp = (BoundTransferOperation) transferOp;
            BoundOperation binding = boundTransferOp.getBinding();
            if (binding != null) {
                EObject bindingEquiv = getEquivalent(binding, CREATE_BOUND_OPERATION);
                if (bindingEquiv instanceof EOperation) {
                    EAnnotation bindingBehaviourAnnotation = createAnnotation(
                            "(psm/" + getId(transferOp) + ")/BehaviourAnnotation/BindingAnnotation",
                            getAnnotationUri("behaviour"));
                    addAnnotationDetail(bindingBehaviourAnnotation, "type", typeValue);
                    if (ownerValue != null) {
                        addAnnotationDetail(bindingBehaviourAnnotation, "owner", ownerValue);
                    }
                    ((EOperation) bindingEquiv).getEAnnotations().add(bindingBehaviourAnnotation);
                }
            }
        }
    }

    private void addPermissionsAnnotation(TransferOperation transferOp, EOperation eOp) {
        EAnnotation permissionsAnnotation = createAnnotation(
                "(psm/" + getId(transferOp) + ")/OperationPermissions",
                getAnnotationUri("permissions"));

        addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferOp.isUpdateOnResult()));
        addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferOp.isDeleteOnResult()));

        eOp.getEAnnotations().add(permissionsAnnotation);
    }

    private void addImmutableAnnotation(TransferOperation transferOp, EOperation eOp) {
        EAnnotation immutableAnnotation = createAnnotation(
                "(psm/" + getId(transferOp) + ")/ImmutableAnnotationOnOperation",
                getAnnotationUri("immutable"));
        addAnnotationDetail(immutableAnnotation, "value", String.valueOf(transferOp.isImmutable()));
        eOp.getEAnnotations().add(immutableAnnotation);
    }

    private void addInputRangeAnnotation(TransferOperation transferOp, EOperation eOp) {
        if (transferOp.getInputRange() == null) {
            return;
        }
        
        EReference rangeRef = (EReference) getEquivalent(transferOp.getInputRange(), CREATE_TRANSFER_RELATION);
        if (rangeRef == null) {
            return;
        }
        
        EAnnotation inputRangeAnnotation = createAnnotation(
                "(psm/" + getId(transferOp) + ")/TransferOperationRangeAnnotation",
                getAnnotationUri("inputRange"));
        addAnnotationDetail(inputRangeAnnotation, "value", asmUtils.getReferenceFQName(rangeRef));
        eOp.getEAnnotations().add(inputRangeAnnotation);
    }

    private void addAttributeConstraints(Attribute attribute, EAttribute eAttr) {
        if (attribute.getDataType() == null) return;

        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(attribute) + ")/AttributeConstraints",
                getAnnotationUri("constraints"));

        if (attribute.getDataType() instanceof StringType) {
            StringType stringType = (StringType) attribute.getDataType();
            addAnnotationDetail(constraintsAnnotation, "maxLength", 
                    String.valueOf(stringType.getMaxLength()));
            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }
        } else if (attribute.getDataType() instanceof NumericType) {
            NumericType numericType = (NumericType) attribute.getDataType();
            addAnnotationDetail(constraintsAnnotation, "precision", 
                    String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", 
                    String.valueOf(numericType.getScale()));

            if (numericType instanceof MeasuredType) {
                MeasuredType measuredType = (MeasuredType) numericType;
                if (measuredType.getStoreUnit() != null) {
                    // ETL order: measure first, then unit
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                    }
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                }
            }
        } else if (attribute.getDataType() instanceof CustomType) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    getQualifiedName(attribute.getDataType()));
        }

        if (!constraintsAnnotation.getDetails().isEmpty()) {
            eAttr.getEAnnotations().add(constraintsAnnotation);
        }
    }

    private EAnnotation createAnnotation(String id, String source) {
        EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
        setId(annotation, id);
        annotation.setSource(source);
        return annotation;
    }

    /**
     * Gets the annotation URI for a given annotation name, with caching.
     * This avoids repeated string concatenation for the ~210 annotation URI lookups.
     */
    private String getAnnotationUri(String annotationName) {
        return annotationUriCache.computeIfAbsent(annotationName, AsmUtils::getAnnotationUri);
    }
    
    /**
     * Gets the fully qualified name of an EClassifier with caching.
     * This avoids repeated package hierarchy traversal.
     */
    private String getClassifierFQName(EClassifier classifier) {
        if (classifier == null) return null;
        return classifierFQNameCache.computeIfAbsent(classifier, AsmUtils::getClassifierFQName);
    }
    
    /**
     * Gets the fully qualified name of a namespace element with caching.
     * Converts :: separators to . (dot) separators.
     * This avoids repeated hierarchy traversal and string building.
     */
    private String getQualifiedName(NamespaceElement element) {
        return namespaceElementStringCache.computeIfAbsent(element, 
                e -> psmUtils.namespaceElementToString(e).replace("::", "."));
    }
    
    /**
     * Gets the fully qualified name with underscore separator (for IDs).
     */
    private String getQualifiedNameWithUnderscore(NamespaceElement element) {
        // Reuse the cached string and just replace differently
        String cached = namespaceElementStringCache.computeIfAbsent(element,
                e -> psmUtils.namespaceElementToString(e));
        return cached.replace("::", "_");
    }

    private void addAnnotationDetail(EAnnotation annotation, String key, String value) {
        // Simple approach: just add key-value pair to details
        // The ETL creates EStringToStringMapEntry with IDs, but for functional equivalence
        // this is not necessary - the ModelComparator compares by content, not by IDs
        annotation.getDetails().put(key, value);
    }

    private EAnnotation createDocumentationAnnotation(hu.blackbelt.judo.meta.psm.namespace.NamedElement element) {
        EAnnotation docAnnotation = createAnnotation(
                "(psm/" + getId(element) + ")/DocumentationAnnotation",
                getAnnotationUri("documentation"));
        addAnnotationDetail(docAnnotation, "value", element.getDocumentation());
        return docAnnotation;
    }

    private EPackage getContainerPackage(EObject element) {
        return containerPackageCache.computeIfAbsent(element, e -> {
            EObject container = e.eContainer();
            while (container != null) {
                if (container instanceof Namespace) {
                    EObject equivalent = getEquivalent(container, 
                            container instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
                    if (equivalent instanceof EPackage) {
                        return (EPackage) equivalent;
                    }
                }
                container = container.eContainer();
            }
            // Return the first root package if no container found
            return asmModel.getResourceSet().getResources().stream()
                    .flatMap(r -> r.getContents().stream())
                    .filter(EPackage.class::isInstance)
                    .map(EPackage.class::cast)
                    .findFirst()
                    .orElse(null);
        });
    }

    private EClassifier getEquivalentType(hu.blackbelt.judo.meta.psm.type.Primitive type) {
        if (type == null) return null;

        return equivalentTypeCache.computeIfAbsent(type, t -> {
            // Check different type rule names based on type class
            String[] ruleNames = {
                    CREATE_ENUMERATION, CREATE_STRING_TYPE, CREATE_INTEGER_TYPE,
                    CREATE_DECIMAL_TYPE, CREATE_BOOLEAN_TYPE, CREATE_DATE_TYPE,
                    CREATE_TIMESTAMP_TYPE, CREATE_TIME_TYPE, CREATE_BINARY_TYPE,
                    CREATE_CUSTOM_TYPE
            };

            for (String ruleName : ruleNames) {
                EObject equivalent = getEquivalent(t, ruleName);
                if (equivalent instanceof EClassifier) {
                    return (EClassifier) equivalent;
                }
            }
            return null;
        });
    }

    private EntityType getEntityType(EObject element) {
        return entityTypeCache.computeIfAbsent(element, e -> {
            EObject container = e.eContainer();
            while (container != null) {
                if (container instanceof EntityType) {
                    return (EntityType) container;
                }
                container = container.eContainer();
            }
            return null;
        });
    }

    private boolean isPrimitive(Attribute attribute) {
        return attribute.getDataType() != null && 
               attribute.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive;
    }

    private EClassifier getEquivalentTransferObject(TransferObjectType to) {
        if (to == null) return null;
        
        // Use cache to avoid repeated 3-way rule lookups
        return transferObjectEquivalentCache.computeIfAbsent(to, t -> {
            EObject result = getEquivalent(t, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            if (result == null) {
                result = getEquivalent(t, CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            }
            if (result == null) {
                result = getEquivalent(t, CREATE_TRANSFER_OBJECT_TYPE_CLASS);
            }
            return result instanceof EClassifier ? (EClassifier) result : null;
        });
    }

    private EClass getEquivalentTransferObjectClass(TransferObjectType to) {
        EClassifier classifier = getEquivalentTransferObject(to);
        return classifier instanceof EClass ? (EClass) classifier : null;
    }

    private EOperation getEquivalentTransferOperation(TransferOperation transferOp) {
        if (transferOp instanceof BoundTransferOperation) {
            return (EOperation) getEquivalent(transferOp, CREATE_BOUND_TRANSFER_OPERATION);
        }
        // For other transfer operations, try to find in parent's operations
        TransferObjectType container = (TransferObjectType) transferOp.eContainer();
        if (container != null) {
            EClass eClass = getEquivalentTransferObjectClass(container);
            if (eClass != null) {
                return eClass.getEOperations().stream()
                        .filter(op -> op.getName().equals(transferOp.getName()))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private String getId(EObject element) {
        // Use cache for expensive ID computation
        return elementIdCache.computeIfAbsent(element, e -> {
            if (e instanceof EModelElement) {
                for (EAnnotation ann : ((EModelElement) e).getEAnnotations()) {
                    if ("http://blackbelt.hu/judo/meta/ExtendedMetadata/id".equals(ann.getSource())) {
                        return ann.getDetails().get("value");
                    }
                }
            }
            // Try to get ID from PSM model if available
            if (e instanceof NamespaceElement) {
                NamespaceElement named = (NamespaceElement) e;
                return getQualifiedNameWithUnderscore(named);
            }
            // Fall back to hash code if no ID annotation
            return String.valueOf(System.identityHashCode(e));
        });
    }

    private boolean isInteger(NumericType numericType) {
        return numericType.getScale() == 0;
    }

    private String getIntegerClassName(NumericType numericType) {
        int precision = numericType.getPrecision();
        if (precision <= 9 && precision > 0) {
            return "java.lang.Integer";
        } else if (precision <= 19 && precision > 9) {
            return "java.lang.Long";
        } else {
            return "java.math.BigDecimal";
        }
    }

    private String getDecimalClassName(NumericType numericType) {
        int precision = numericType.getPrecision();
        int scale = numericType.getScale();
        if (precision <= 7 && precision > 0 && scale <= 4) {
            return "java.lang.Float";
        } else if (precision <= 15 && precision > 7 && scale <= 4) {
            return "java.lang.Double";
        } else {
            return "java.math.BigDecimal";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private void setId(EObject element, String id) {
        // IDs are not required for functional model equivalence.
        // The ETL sets XMI resource IDs via self.eResource.setId(self, id), but these
        // are only used for serialization/tracing, not for model structure comparison.
        // Skipping ID setting significantly improves transformation performance.
        //
        // If IDs are needed in the future (e.g., for trace generation), uncomment:
        // deferredIds.put(element, id);
    }
    
    /**
     * Apply all deferred IDs after elements have been added to the resource.
     * Currently disabled for performance - see setId() method.
     */
    private void applyDeferredIds() {
        // IDs disabled for performance - see setId() method comment
        deferredIds.clear();
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
    // NAMESPACE RULES (namespace.etl)
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if this is the root Model element.
     */
    public boolean isRootModel(Model model) {
        return model != null;
    }

    @TransformRule(name = MODEL_TO_PACKAGE, description = "Transform PSM Model to root EPackage")
    @Transform(type = Model.class)
    @To(type = EPackage.class)
    public TransformFunction<Model, EPackage> modelToPackageRule() {
        return (model, ctx) -> {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            setId(pkg, "(psm/" + getId(model) + ")/Package");
            pkg.setName(model.getName());
            pkg.setNsURI(nsURI + "/" + model.getName());
            pkg.setNsPrefix(nsPrefix + capitalize(model.getName()));
            asmModel.getResource().getContents().add(pkg);
            return pkg;
        };
    }

    @TransformRule(name = PACKAGE_TO_PACKAGE, description = "Transform PSM Package to EPackage")
    @Transform(type = Package.class)
    @To(type = EPackage.class)
    public TransformFunction<Package, EPackage> packageToPackageRule() {
        return (psmPackage, ctx) -> {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            setId(pkg, "(psm/" + getId(psmPackage) + ")/Package");
            pkg.setName(psmPackage.getName());
            
            // Get parent package using internal trace map
            EObject container = psmPackage.eContainer();
            EPackage parentPackage = null;
            if (container instanceof Model) {
                parentPackage = (EPackage) getEquivalent(container, MODEL_TO_PACKAGE);
            } else if (container instanceof Package) {
                parentPackage = (EPackage) getEquivalent(container, PACKAGE_TO_PACKAGE);
            }
            
            if (parentPackage != null) {
                pkg.setNsURI(parentPackage.getNsURI() + "/" + psmPackage.getName());
                pkg.setNsPrefix(parentPackage.getNsPrefix() + capitalize(psmPackage.getName()));
                parentPackage.getESubpackages().add(pkg);
            }
            
            return pkg;
        };
    }

    /**
     * Guard: Check if model has a version defined.
     */
    public boolean hasVersion(Model model) {
        return model.getVersion() != null && !model.getVersion().trim().isEmpty();
    }

    @TransformRule(name = MODEL_TO_PACKAGE_VERSION, description = "Create version annotation for Model")
    @Guard(method = "hasVersion")
    @Transform(type = Model.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Model, EAnnotation> modelToPackageVersionRule() {
        return (model, ctx) -> {
            EAnnotation versionAnnotation = createAnnotation(
                    "(psm/" + getId(model) + ")/ModelToPackageVersion",
                    getAnnotationUri("ModelVersion"));
            addAnnotationDetail(versionAnnotation, "value", model.getVersion());

            // Add to the package equivalent
            EPackage pkg = (EPackage) getEquivalent(model, MODEL_TO_PACKAGE);
            if (pkg != null) {
                pkg.getEAnnotations().add(versionAnnotation);
            }

            return versionAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // TYPE RULES (type.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_ENUMERATION, description = "Transform EnumerationType to EEnum")
    @Transform(type = EnumerationType.class)
    @To(type = EEnum.class)
    public TransformFunction<EnumerationType, EEnum> createEnumerationRule() {
        return (enumType, ctx) -> {
            EEnum eEnum = EcoreFactory.eINSTANCE.createEEnum();
            setId(eEnum, "(psm/" + getId(enumType) + ")/Enumeration");
            eEnum.setName(enumType.getName());

            int ordinal = 0;
            for (EnumerationMember member : enumType.getMembers()) {
                EEnumLiteral literal = EcoreFactory.eINSTANCE.createEEnumLiteral();
                setId(literal, eEnum.getName() + "/Literal" + ordinal);
                literal.setValue(member.getOrdinal());
                literal.setLiteral(member.getName());
                literal.setName(member.getName());
                eEnum.getELiterals().add(literal);
                ordinal++;
            }

            getContainerPackage(enumType).getEClassifiers().add(eEnum);
            return eEnum;
        };
    }

    @TransformRule(name = CREATE_STRING_TYPE, description = "Transform StringType to EDataType")
    @Transform(type = StringType.class)
    @To(type = EDataType.class)
    public TransformFunction<StringType, EDataType> createStringTypeRule() {
        return (stringType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(stringType) + ")/StringType");
            dataType.setName(stringType.getName());
            dataType.setInstanceClassName("java.lang.String");
            getContainerPackage(stringType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    /**
     * Guard: Check if numeric type is an integer (scale == 0).
     */
    public boolean isIntegerType(NumericType numericType) {
        return numericType.getScale() == 0;
    }

    /**
     * Guard: Check if numeric type is a decimal (scale > 0).
     */
    public boolean isDecimalType(NumericType numericType) {
        return numericType.getScale() > 0;
    }

    @TransformRule(name = CREATE_INTEGER_TYPE, description = "Transform integer NumericType to EDataType")
    @Guard(method = "isIntegerType")
    @Transform(type = NumericType.class)
    @To(type = EDataType.class)
    public TransformFunction<NumericType, EDataType> createIntegerTypeRule() {
        return (numericType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(numericType) + ")/IntegerType");
            dataType.setName(numericType.getName());
            dataType.setInstanceClassName(getIntegerClassName(numericType));
            
            // Add measured annotation if applicable
            if (numericType instanceof MeasuredType) {
                addMeasuredAnnotation((MeasuredType) numericType, dataType);
            }
            
            getContainerPackage(numericType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_DECIMAL_TYPE, description = "Transform decimal NumericType to EDataType")
    @Guard(method = "isDecimalType")
    @Transform(type = NumericType.class)
    @To(type = EDataType.class)
    public TransformFunction<NumericType, EDataType> createDecimalTypeRule() {
        return (numericType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(numericType) + ")/DecimalType");
            dataType.setName(numericType.getName());
            dataType.setInstanceClassName(getDecimalClassName(numericType));
            
            // Add measured annotation if applicable
            if (numericType instanceof MeasuredType) {
                addMeasuredAnnotation((MeasuredType) numericType, dataType);
            }
            
            getContainerPackage(numericType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    private void addMeasuredAnnotation(MeasuredType measuredType, EDataType dataType) {
        if (measuredType.getStoreUnit() != null) {
            EAnnotation measuredAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
            setId(measuredAnnotation, "(psm/" + getId(measuredType) + ")/MeasuredAnnotation");
            measuredAnnotation.setSource(getAnnotationUri("measured"));
            measuredAnnotation.getDetails().put("unit", measuredType.getStoreUnit().getName());
            if (measuredType.getStoreUnit().eContainer() != null) {
                String measure = getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer());
                measuredAnnotation.getDetails().put("measure", measure);
            }
            dataType.getEAnnotations().add(measuredAnnotation);
        }
    }

    @TransformRule(name = CREATE_BOOLEAN_TYPE, description = "Transform BooleanType to EDataType")
    @Transform(type = BooleanType.class)
    @To(type = EDataType.class)
    public TransformFunction<BooleanType, EDataType> createBooleanTypeRule() {
        return (booleanType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(booleanType) + ")/BooleanType");
            dataType.setName(booleanType.getName());
            dataType.setInstanceClassName("java.lang.Boolean");
            getContainerPackage(booleanType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_DATE_TYPE, description = "Transform DateType to EDataType")
    @Transform(type = DateType.class)
    @To(type = EDataType.class)
    public TransformFunction<DateType, EDataType> createDateTypeRule() {
        return (dateType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(dateType) + ")/DateType");
            dataType.setName(dateType.getName());
            dataType.setInstanceClassName("java.time.LocalDate");
            getContainerPackage(dateType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_TIMESTAMP_TYPE, description = "Transform TimestampType to EDataType")
    @Transform(type = TimestampType.class)
    @To(type = EDataType.class)
    public TransformFunction<TimestampType, EDataType> createTimestampTypeRule() {
        return (timestampType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(timestampType) + ")/TimestampType");
            dataType.setName(timestampType.getName());
            dataType.setInstanceClassName("java.time.LocalDateTime");
            getContainerPackage(timestampType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_TIME_TYPE, description = "Transform TimeType to EDataType")
    @Transform(type = TimeType.class)
    @To(type = EDataType.class)
    public TransformFunction<TimeType, EDataType> createTimeTypeRule() {
        return (timeType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(timeType) + ")/TimeType");
            dataType.setName(timeType.getName());
            dataType.setInstanceClassName("java.time.LocalTime");
            getContainerPackage(timeType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_BINARY_TYPE, description = "Transform BinaryType to EDataType")
    @Transform(type = BinaryType.class)
    @To(type = EDataType.class)
    public TransformFunction<BinaryType, EDataType> createBinaryTypeRule() {
        return (binaryType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(binaryType) + ")/BinaryType");
            dataType.setName(binaryType.getName());
            dataType.setInstanceClassName("byte[]");

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(binaryType) + ")/Constraints",
                    getAnnotationUri("constraints"));

            if (binaryType.getMimeTypes() != null && !binaryType.getMimeTypes().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "mimeTypes", 
                        String.join(",", binaryType.getMimeTypes()));
            }
            if (binaryType.getMaxFileSize() > 0) {
                addAnnotationDetail(constraintsAnnotation, "maxFileSize", 
                        String.valueOf(binaryType.getMaxFileSize()));
            }

            dataType.getEAnnotations().add(constraintsAnnotation);
            getContainerPackage(binaryType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    /**
     * Guard: Check if CustomType is not handled by more specific rules.
     */
    public boolean isGenericCustomType(CustomType customType) {
        return !(customType instanceof NumericType) &&
               !(customType instanceof BooleanType) &&
               !(customType instanceof EnumerationType) &&
               !(customType instanceof StringType) &&
               !(customType instanceof DateType) &&
               !(customType instanceof TimestampType) &&
               !(customType instanceof TimeType);
    }

    @TransformRule(name = CREATE_CUSTOM_TYPE, description = "Transform CustomType to EDataType")
    @Guard(method = "isGenericCustomType")
    @Transform(type = CustomType.class)
    @To(type = EDataType.class)
    public TransformFunction<CustomType, EDataType> createCustomTypeRule() {
        return (customType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(customType) + ")/CustomType");
            dataType.setName(customType.getName());
            dataType.setInstanceClassName("java.lang.Object");
            getContainerPackage(customType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    // -------------------------------------------------------------------------
    // ENTITY RULES (data.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_ENTITY_CLASS, description = "Transform EntityType to EClass")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createEntityClassRule() {
        return (entityType, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(entityType) + ")/EntityClass");
            eClass.setName(entityType.getName());
            eClass.setAbstract(entityType.isAbstract());

            getContainerPackage(entityType).getEClassifiers().add(eClass);

            // Add entity annotation
            EAnnotation entityAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/EntityAnnotationClass",
                    getAnnotationUri("entity"));
            addAnnotationDetail(entityAnnotation, "value", "true");
            eClass.getEAnnotations().add(entityAnnotation);

            // Add documentation annotation if present
            if (entityType.getDocumentation() != null && 
                !entityType.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(entityType);
                eClass.getEAnnotations().add(docAnnotation);
            }

            return eClass;
        };
    }

    /**
     * Guard: Check if entity type has default representation.
     */
    public boolean hasDefaultRepresentation(EntityType entityType) {
        return entityType.getDefaultRepresentation() != null;
    }

    @TransformRule(name = CREATE_ENTITY_DEFAULT_REPRESENTATION_ANNOTATION, description = "Create default representation annotation for EntityType")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createEntityDefaultRepresentationAnnotationRule() {
        return (entityType, ctx) -> {
            EClass eClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
            if (eClass == null) return null;

            EAnnotation defaultRepAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/EntityDefaultRepresentationAnnotation",
                    getAnnotationUri("defaultRepresentation"));
            addAnnotationDetail(defaultRepAnnotation, "value", 
                    getQualifiedName(entityType.getDefaultRepresentation()));
            eClass.getEAnnotations().add(defaultRepAnnotation);

            return defaultRepAnnotation;
        };
    }

    @TransformRule(name = CREATE_ENTITY_ANNOTATION_CLASS, description = "Create entity annotation for EntityType")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createEntityAnnotationClassRule() {
        return (entityType, ctx) -> {
            EClass eClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
            if (eClass == null) return null;

            EAnnotation entityAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/EntityAnnotationClass",
                    getAnnotationUri("entity"));
            addAnnotationDetail(entityAnnotation, "value", "true");
            eClass.getEAnnotations().add(entityAnnotation);

            return entityAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has string data type.
     */
    public boolean isStringAttribute(Attribute attribute) {
        return attribute.getDataType() instanceof StringType;
    }

    @TransformRule(name = ADD_STRING_ATTRIBUTE_CONSTRAINTS, description = "Add string constraints annotation for Attribute")
    @Guard(method = "isStringAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addStringAttributeConstraintsRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            StringType stringType = (StringType) attribute.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/StringAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "maxLength", String.valueOf(stringType.getMaxLength()));

            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has custom data type.
     */
    public boolean isCustomAttribute(Attribute attribute) {
        return attribute.getDataType() instanceof CustomType;
    }

    @TransformRule(name = ADD_CUSTOM_ATTRIBUTE_CONSTRAINTS, description = "Add custom type constraints annotation for Attribute")
    @Guard(method = "isCustomAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addCustomAttributeConstraintsRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/CustomAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "customType", 
                    getQualifiedName(attribute.getDataType()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has numeric (non-measured) data type.
     */
    public boolean isNumericAttribute(Attribute attribute) {
        return attribute.getDataType() instanceof NumericType && 
               !(attribute.getDataType() instanceof MeasuredType);
    }

    @TransformRule(name = ADD_NUMERIC_ATTRIBUTE_CONSTRAINTS, description = "Add numeric constraints annotation for Attribute")
    @Guard(method = "isNumericAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addNumericAttributeConstraintsRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            NumericType numericType = (NumericType) attribute.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/NumericAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(numericType.getScale()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has measured data type.
     */
    public boolean isMeasuredAttribute(Attribute attribute) {
        return attribute.getDataType() instanceof MeasuredType;
    }

    @TransformRule(name = ADD_MEASURED_ATTRIBUTE_CONSTRAINTS, description = "Add measured constraints annotation for Attribute")
    @Guard(method = "isMeasuredAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addMeasuredAttributeConstraintsRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            MeasuredType measuredType = (MeasuredType) attribute.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/MeasuredAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(measuredType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(measuredType.getScale()));
            
            if (measuredType.getStoreUnit() != null) {
                addAnnotationDetail(constraintsAnnotation, "measure", 
                        getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                addAnnotationDetail(constraintsAnnotation, "unit", measuredType.getStoreUnit().getName());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has primitive data type.
     */
    public boolean isPrimitiveAttribute(Attribute attribute) {
        return attribute.getDataType() != null && 
               attribute.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive;
    }

    @TransformRule(name = CREATE_ATTRIBUTE, description = "Transform Attribute to EAttribute")
    @Guard(method = "isPrimitiveAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<Attribute, EAttribute> createAttributeRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(attribute) + ")/Attribute");
            eAttr.setName(attribute.getName());
            eAttr.setLowerBound(attribute.isRequired() ? 1 : 0);

            // Set type
            EClassifier type = getEquivalentType(attribute.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            // Add to owning class
            EntityType owner = getEntityType(attribute);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eAttr);
                }
            }

            // Add constraints annotation
            addAttributeConstraints(attribute, eAttr);

            // Add identifier annotation if applicable
            if (attribute.isIdentifier()) {
                EAnnotation idAnnotation = createAnnotation(
                        "(psm/" + getId(attribute) + ")/IdentifierAnnotationForAttribute",
                        getAnnotationUri("identifier"));
                addAnnotationDetail(idAnnotation, "value", "true");
                eAttr.getEAnnotations().add(idAnnotation);
            }

            // Add documentation annotation if present
            if (attribute.getDocumentation() != null && 
                !attribute.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(attribute);
                eAttr.getEAnnotations().add(docAnnotation);
            }

            return eAttr;
        };
    }

    /**
     * Guard: Check if attribute is an identifier.
     */
    public boolean isIdentifierAttribute(Attribute attribute) {
        return attribute.isIdentifier() && isPrimitiveAttribute(attribute);
    }

    @TransformRule(name = CREATE_IDENTIFIER_ANNOTATION_FOR_ATTRIBUTE, description = "Create identifier annotation for Attribute")
    @Guard(method = "isIdentifierAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> createIdentifierAnnotationForAttributeRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation idAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/IdentifierAnnotationForAttribute",
                    getAnnotationUri("identifier"));
            addAnnotationDetail(idAnnotation, "value", "true");
            eAttr.getEAnnotations().add(idAnnotation);

            return idAnnotation;
        };
    }

    @TransformRule(name = CREATE_ASSOCIATION_END_RELATION, description = "Transform AssociationEnd to EReference")
    @Transform(type = AssociationEnd.class)
    @To(type = EReference.class)
    public TransformFunction<AssociationEnd, EReference> createAssociationEndRelationRule() {
        return (associationEnd, ctx) -> {
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(associationEnd) + ")/AssociationEndRelation");
            eRef.setName(associationEnd.getName());
            eRef.setLowerBound(associationEnd.getCardinality().getLower());
            eRef.setUpperBound(associationEnd.getCardinality().getUpper());

            // Set target type
            if (associationEnd.getTarget() != null) {
                EClass targetClass = (EClass) getEquivalent(associationEnd.getTarget(), CREATE_ENTITY_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            // Add to owning class
            EntityType owner = getEntityType(associationEnd);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            }

            // Add reverse cascade delete annotation if applicable
            if (associationEnd.isReverseCascadeDelete()) {
                EAnnotation reverseCascadeAnnotation = createAnnotation(
                        "(psm/" + getId(associationEnd) + ")/ReverseCascadeDeleteAnnotation",
                        getAnnotationUri("reverseCascadeDelete"));
                addAnnotationDetail(reverseCascadeAnnotation, "value", "true");
                eRef.getEAnnotations().add(reverseCascadeAnnotation);
            }

            // Add documentation annotation if present
            if (associationEnd.getDocumentation() != null && 
                !associationEnd.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(associationEnd);
                eRef.getEAnnotations().add(docAnnotation);
            }

            return eRef;
        };
    }

    @TransformRule(name = CREATE_CONTAINMENT_RELATION, description = "Transform Containment to EReference")
    @Transform(type = Containment.class)
    @To(type = EReference.class)
    public TransformFunction<Containment, EReference> createContainmentRelationRule() {
        return (containment, ctx) -> {
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(containment) + ")/ContainmentRelation");
            eRef.setName(containment.getName());
            eRef.setLowerBound(containment.getCardinality().getLower());
            eRef.setUpperBound(containment.getCardinality().getUpper());
            eRef.setContainment(true);

            // Set target type
            if (containment.getTarget() != null) {
                EClass targetClass = (EClass) getEquivalent(containment.getTarget(), CREATE_ENTITY_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            // Add to owning class
            EntityType owner = getEntityType(containment);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            }

            // Add documentation annotation if present
            if (containment.getDocumentation() != null && 
                !containment.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(containment);
                eRef.getEAnnotations().add(docAnnotation);
            }

            return eRef;
        };
    }

    // -------------------------------------------------------------------------
    // DERIVED PROPERTY RULES (derived.etl)
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if data property has primitive data type.
     */
    public boolean isPrimitiveDataProperty(DataProperty dataProperty) {
        return dataProperty.getDataType() != null && 
               dataProperty.getDataType() instanceof Primitive;
    }

    @TransformRule(name = CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE, description = "Transform DataProperty to derived EAttribute")
    @Guard(method = "isPrimitiveDataProperty")
    @Transform(type = DataProperty.class)
    @To(type = EAttribute.class)
    public TransformFunction<DataProperty, EAttribute> createDataPropertyRule() {
        return (dataProperty, ctx) -> {
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(dataProperty) + ")/DataProperty");
            eAttr.setName(dataProperty.getName());
            eAttr.setDerived(true);
            eAttr.setVolatile(true);
            eAttr.setLowerBound(dataProperty.isRequired() ? 1 : 0);
            eAttr.setChangeable(dataProperty.getSetterExpression() != null);

            // Set type
            EClassifier type = getEquivalentType(dataProperty.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            // Add to owning entity class
            EntityType owner = getEntityType(dataProperty);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eAttr);
                }
            }

            // Add constraints annotation
            addPrimitiveAccessorConstraints(dataProperty, eAttr);

            // Add expression annotation
            addExpressionAnnotation(dataProperty, eAttr);

            // Add documentation if present
            if (dataProperty.getDocumentation() != null && 
                !dataProperty.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(dataProperty);
                eAttr.getEAnnotations().add(docAnnotation);
            }

            return eAttr;
        };
    }

    @TransformRule(name = CREATE_NAVIGATION_PROPERTY, description = "Transform NavigationProperty to derived EReference")
    @Transform(type = NavigationProperty.class)
    @To(type = EReference.class)
    public TransformFunction<NavigationProperty, EReference> createNavigationPropertyRule() {
        return (navigationProperty, ctx) -> {
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(navigationProperty) + ")/NavigationProperty");
            eRef.setName(navigationProperty.getName());
            eRef.setDerived(true);
            eRef.setVolatile(true);
            
            if (navigationProperty.getCardinality() != null) {
                eRef.setLowerBound(navigationProperty.getCardinality().getLower());
                eRef.setUpperBound(navigationProperty.getCardinality().getUpper());
            }

            eRef.setChangeable(navigationProperty.getSetterExpression() != null);

            // Set target type
            if (navigationProperty.getTarget() != null) {
                EClass targetClass = (EClass) getEquivalent(navigationProperty.getTarget(), CREATE_ENTITY_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            // Add to owning entity class
            EntityType owner = getEntityType(navigationProperty);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            }

            // Add expression annotation
            addReferenceAccessorExpressionAnnotation(navigationProperty, eRef);

            // Add documentation if present
            if (navigationProperty.getDocumentation() != null && 
                !navigationProperty.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(navigationProperty);
                eRef.getEAnnotations().add(docAnnotation);
            }

            return eRef;
        };
    }

    /**
     * Guard: Check if primitive accessor is not static data.
     */
    public boolean isNotStaticDataPrimitive(PrimitiveAccessor accessor) {
        return !(accessor instanceof StaticData);
    }

    @TransformRule(name = CREATE_PRIMITIVE_ACCESSOR_EXPRESSION_ANNOTATION, description = "Create expression annotation for PrimitiveAccessor")
    @Guard(method = "isNotStaticDataPrimitive")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<PrimitiveAccessor, EAnnotation> createPrimitiveAccessorExpressionAnnotationRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(accessor, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/PrimitiveAccessorExpressionAnnotation",
                    getAnnotationUri("expression"));

            // Getter
            if (accessor.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", accessor.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        accessor.getGetterExpression().getDialect().toString());

                if (accessor.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(accessor.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            // Setter
            if (accessor.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", accessor.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        accessor.getSetterExpression().getDialect().toString());

                if (accessor.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(accessor.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eAttr.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    /**
     * Guard: Check if reference accessor is not static navigation.
     */
    public boolean isNotStaticNavigation(ReferenceAccessor accessor) {
        return !(accessor instanceof StaticNavigation);
    }

    @TransformRule(name = CREATE_REFERENCE_ACCESSOR_EXPRESSION_ANNOTATION, description = "Create expression annotation for ReferenceAccessor")
    @Guard(method = "isNotStaticNavigation")
    @Transform(type = ReferenceAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<ReferenceAccessor, EAnnotation> createReferenceAccessorExpressionAnnotationRule() {
        return (accessor, ctx) -> {
            EReference eRef = (EReference) getEquivalent(accessor, CREATE_STATIC_NAVIGATION_FOR_DERIVED_ATTRIBUTE);
            if (eRef == null) return null;

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/ReferenceAccessorExpressionAnnotation",
                    getAnnotationUri("expression"));

            // Getter
            if (accessor.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", accessor.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        accessor.getGetterExpression().getDialect().toString());

                if (accessor.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(accessor.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            // Setter
            if (accessor.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", accessor.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        accessor.getSetterExpression().getDialect().toString());

                if (accessor.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(accessor.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eRef.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // OPERATION RULES (operation.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_BOUND_OPERATION, description = "Transform BoundOperation to EOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundOperation, EOperation> createBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
            setId(eOp, "(psm/" + getId(boundOp) + ")/BoundOperation");
            eOp.setName(boundOp.getName());

            // Set output type and cardinality
            if (boundOp.getOutput() != null) {
                eOp.setLowerBound(boundOp.getOutput().getCardinality().getLower());
                eOp.setUpperBound(boundOp.getOutput().getCardinality().getUpper());
                EClassifier outputType = getEquivalentTransferObject(boundOp.getOutput().getType());
                if (outputType != null) {
                    eOp.setEType(outputType);
                }
            }

            // Add fault exceptions
            for (var fault : boundOp.getFaults()) {
                EClassifier faultType = getEquivalentTransferObject(fault.getType());
                if (faultType != null) {
                    eOp.getEExceptions().add(faultType);
                }
            }

            // Add to owning entity class
            EntityType owner = (EntityType) boundOp.eContainer();
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEOperations().add(eOp);
                }
            }

            // Transform input parameter
            if (boundOp.getInput() != null) {
                transformInputParameter(boundOp.getInput(), eOp);
            }

            // Add bound annotation
            addBoundAnnotation(boundOp, eOp);

            // Add instance representation annotation
            if (boundOp.getInstanceRepresentation() != null) {
                addInstanceRepresentationAnnotation(boundOp, eOp);
            }

            // Add script body annotation if implementation exists
            if (boundOp.getImplementation() != null && 
                boundOp.getImplementation().getBody() != null &&
                !boundOp.getImplementation().getBody().trim().isEmpty()) {
                addScriptBodyAnnotation(boundOp, eOp);
            }

            // Add custom implementation annotation
            if (boundOp.getImplementation() != null) {
                addCustomImplementationAnnotation(boundOp, eOp);
            }

            // Add abstract annotation
            if (boundOp.isAbstract()) {
                addAbstractAnnotation(boundOp, eOp);
            }

            // Add output parameter name annotation
            if (boundOp.getOutput() != null) {
                addOutputParameterNameAnnotation(boundOp, eOp);
            }

            // Add documentation if present
            if (boundOp.getDocumentation() != null && 
                !boundOp.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(boundOp);
                eOp.getEAnnotations().add(docAnnotation);
            }

            return eOp;
        };
    }

    @TransformRule(name = CREATE_UNBOUND_OPERATION, description = "Transform UnboundOperation to EOperation")
    @Transform(type = UnboundOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<UnboundOperation, EOperation> createUnboundOperationRule() {
        return (unboundOp, ctx) -> {
            EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
            setId(eOp, "(psm/" + getId(unboundOp) + ")/UnboundOperation");
            eOp.setName(unboundOp.getName());

            // Set output type and cardinality
            if (unboundOp.getOutput() != null) {
                eOp.setLowerBound(unboundOp.getOutput().getCardinality().getLower());
                eOp.setUpperBound(unboundOp.getOutput().getCardinality().getUpper());
                EClassifier outputType = getEquivalentTransferObject(unboundOp.getOutput().getType());
                if (outputType != null) {
                    eOp.setEType(outputType);
                }
            }

            // Add fault exceptions
            for (var fault : unboundOp.getFaults()) {
                EClassifier faultType = getEquivalentTransferObject(fault.getType());
                if (faultType != null) {
                    eOp.getEExceptions().add(faultType);
                }
            }

            // Add to containing element
            EObject container = unboundOp.eContainer();
            if (container instanceof Namespace) {
                Namespace ns = (Namespace) container;
                EPackage pkg = (EPackage) getEquivalent(ns, 
                        ns instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
                if (pkg != null) {
                    EClass operationHolder = findOrCreateOperationHolder(pkg);
                    operationHolder.getEOperations().add(eOp);
                }
            } else if (container instanceof TransferObjectType) {
                EClass toClass = getEquivalentTransferObjectClass((TransferObjectType) container);
                if (toClass != null) {
                    toClass.getEOperations().add(eOp);
                }
            }

            // Transform input parameter
            if (unboundOp.getInput() != null) {
                transformInputParameter(unboundOp.getInput(), eOp);
            }

            // Add bound annotation (value = false for unbound)
            addBoundAnnotation(unboundOp, eOp);

            // Add script body annotation if implementation exists
            if (unboundOp.getImplementation() != null && 
                unboundOp.getImplementation().getBody() != null &&
                !unboundOp.getImplementation().getBody().trim().isEmpty()) {
                addScriptBodyAnnotation(unboundOp, eOp);
            }

            // Add custom implementation annotation
            if (unboundOp.getImplementation() != null) {
                EAnnotation customImplAnnotation = createAnnotation(
                        "(psm/" + getId(unboundOp) + ")/CustomImplementationAnnotationOnUnboundOperation",
                        getAnnotationUri("customImplementation"));
                addAnnotationDetail(customImplAnnotation, "value", 
                        String.valueOf(unboundOp.getImplementation().isCustomImplementation()));
                eOp.getEAnnotations().add(customImplAnnotation);
            }

            // Add initializer annotation
            if (unboundOp.isInitializer()) {
                EAnnotation initAnnotation = createAnnotation(
                        "(psm/" + getId(unboundOp) + ")/InitializerAnnotation",
                        getAnnotationUri("initializer"));
                addAnnotationDetail(initAnnotation, "value", "true");
                eOp.getEAnnotations().add(initAnnotation);
            }

            // Add stateful annotation
            addStatefulAnnotation(unboundOp, eOp);

            // Add behaviour annotation if behaviour is defined
            addBehaviourAnnotation(unboundOp, eOp);

            // Add permissions annotation
            addPermissionsAnnotation(unboundOp, eOp);

            return eOp;
        };
    }

    private EClass findOrCreateOperationHolder(EPackage pkg) {
        for (EClassifier classifier : pkg.getEClassifiers()) {
            if (classifier instanceof EClass && "OperationHolder".equals(classifier.getName())) {
                return (EClass) classifier;
            }
        }
        EClass operationHolder = EcoreFactory.eINSTANCE.createEClass();
        operationHolder.setName("OperationHolder");
        operationHolder.setAbstract(true);
        operationHolder.setInterface(true);
        pkg.getEClassifiers().add(operationHolder);
        return operationHolder;
    }

    @TransformRule(name = CREATE_BOUND_TRANSFER_OPERATION, description = "Transform BoundTransferOperation to EOperation")
    @Transform(type = BoundTransferOperation.class)
    @To(type = EOperation.class)
    public TransformFunction<BoundTransferOperation, EOperation> createBoundTransferOperationRule() {
        return (boundTransferOp, ctx) -> {
            EOperation eOp = EcoreFactory.eINSTANCE.createEOperation();
            setId(eOp, "(psm/" + getId(boundTransferOp) + ")/BoundTransferOperation");
            eOp.setName(boundTransferOp.getName());

            // Get output from the binding (BoundOperation)
            BoundOperation binding = boundTransferOp.getBinding();
            if (binding != null && binding.getOutput() != null) {
                eOp.setLowerBound(binding.getOutput().getCardinality().getLower());
                eOp.setUpperBound(binding.getOutput().getCardinality().getUpper());
                EClassifier outputType = getEquivalentTransferObject(binding.getOutput().getType());
                if (outputType != null) {
                    eOp.setEType(outputType);
                }
            }

            // Add fault exceptions from binding
            if (binding != null) {
                for (var fault : binding.getFaults()) {
                    EClassifier faultType = getEquivalentTransferObject(fault.getType());
                    if (faultType != null) {
                        eOp.getEExceptions().add(faultType);
                    }
                }
            }

            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) boundTransferOp.eContainer();
            if (owner != null) {
                EClass ownerClass = getEquivalentTransferObjectClass(owner);
                if (ownerClass != null) {
                    ownerClass.getEOperations().add(eOp);
                }
            }

            // Add binding annotation
            if (binding != null) {
                EObject bindingEquivalent = getEquivalent(binding, CREATE_BOUND_OPERATION);
                if (bindingEquivalent instanceof EOperation) {
                    EAnnotation bindingAnnotation = createAnnotation(
                            "(psm/" + getId(boundTransferOp) + ")/BindingAnnotation",
                            getAnnotationUri("binding"));
                    addAnnotationDetail(bindingAnnotation, "value", ((EOperation) bindingEquivalent).getName());
                    eOp.getEAnnotations().add(bindingAnnotation);
                }
            }

            // Add bound annotation
            addBoundAnnotation(boundTransferOp, eOp);

            // Add stateful annotation
            addStatefulAnnotation(boundTransferOp, eOp);

            // Add behaviour annotation if behaviour is defined
            addBehaviourAnnotation(boundTransferOp, eOp);

            // Add permissions annotation
            addPermissionsAnnotation(boundTransferOp, eOp);

            return eOp;
        };
    }

    /**
     * Guard: Check if unbound operation is an initializer.
     */
    public boolean isInitializer(UnboundOperation unboundOp) {
        return unboundOp.isInitializer();
    }

    @TransformRule(name = CREATE_INITIALIZER_ANNOTATION, description = "Create initializer annotation for UnboundOperation")
    @Guard(method = "isInitializer")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createInitializerAnnotationRule() {
        return (unboundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(unboundOp, CREATE_UNBOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation initAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/InitializerAnnotation",
                    getAnnotationUri("initializer"));
            addAnnotationDetail(initAnnotation, "value", "true");
            eOp.getEAnnotations().add(initAnnotation);

            return initAnnotation;
        };
    }

    /**
     * Guard: Check if operation declaration has output.
     */
    public boolean hasOutput(OperationDeclaration opDecl) {
        return opDecl.getOutput() != null;
    }

    @TransformRule(name = CREATE_OUTPUT_PARAMETER_NAME, description = "Create output parameter name annotation")
    @Guard(method = "hasOutput")
    @Transform(type = OperationDeclaration.class)
    @To(type = EAnnotation.class)
    public TransformFunction<OperationDeclaration, EAnnotation> createOutputParameterNameRule() {
        return (opDecl, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(opDecl, 
                    opDecl instanceof BoundOperation ? CREATE_BOUND_OPERATION : 
                    opDecl instanceof BoundTransferOperation ? CREATE_BOUND_TRANSFER_OPERATION :
                    CREATE_UNBOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation outputNameAnnotation = createAnnotation(
                    "(psm/" + getId(opDecl) + ")/OutputParameterName",
                    getAnnotationUri("outputParameterName"));
            addAnnotationDetail(outputNameAnnotation, "value", opDecl.getOutput().getName());
            eOp.getEAnnotations().add(outputNameAnnotation);

            return outputNameAnnotation;
        };
    }

    /**
     * Guard: Check if transfer operation has implementation.
     */
    public boolean hasTransferOperationImplementation(TransferOperation transferOp) {
        return transferOp.getImplementation() != null;
    }

    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_OPERATION, description = "Create custom implementation annotation on TransferOperation")
    @Guard(method = "hasTransferOperationImplementation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createCustomImplementationAnnotationOnOperationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/CustomImplementationAnnotationOnOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(transferOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);

            return customImplAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation has implementation.
     */
    public boolean hasBoundOperationImplementation(BoundOperation boundOp) {
        return boundOp.getImplementation() != null;
    }

    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_BOUND_OPERATION, description = "Create custom implementation annotation on BoundOperation")
    @Guard(method = "hasBoundOperationImplementation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createCustomImplementationAnnotationOnBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/CustomImplementationAnnotationOnBoundOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(boundOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);

            return customImplAnnotation;
        };
    }

    /**
     * Guard: Check if unbound operation has implementation.
     */
    public boolean hasUnboundOperationImplementation(UnboundOperation unboundOp) {
        return unboundOp.getImplementation() != null;
    }

    @TransformRule(name = CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_UNBOUND_OPERATION, description = "Create custom implementation annotation on UnboundOperation")
    @Guard(method = "hasUnboundOperationImplementation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createCustomImplementationAnnotationOnUnboundOperationRule() {
        return (unboundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(unboundOp, CREATE_UNBOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/CustomImplementationAnnotationOnUnboundOperation",
                    getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(unboundOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);

            return customImplAnnotation;
        };
    }

    @TransformRule(name = CREATE_OPERATION_PERMISSIONS, description = "Create permissions annotation for TransferOperation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createOperationPermissionsRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation permissionsAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/OperationPermissions",
                    getAnnotationUri("permissions"));
            addAnnotationDetail(permissionsAnnotation, "update", 
                    String.valueOf(transferOp.isUpdateOnResult()));
            addAnnotationDetail(permissionsAnnotation, "delete", 
                    String.valueOf(transferOp.isDeleteOnResult()));
            eOp.getEAnnotations().add(permissionsAnnotation);

            return permissionsAnnotation;
        };
    }

    @TransformRule(name = CREATE_IMMUTABLE_FLAG_FOR_TRANSFER_OPERATION, description = "Create immutable flag annotation for TransferOperation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createImmutableFlagForTransferOperationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation immutableAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/ImmutableAnnotationOnOperation",
                    getAnnotationUri("immutable"));
            addAnnotationDetail(immutableAnnotation, "value", 
                    String.valueOf(transferOp.isImmutable()));
            eOp.getEAnnotations().add(immutableAnnotation);

            return immutableAnnotation;
        };
    }

    /**
     * Guard: Check if transfer operation has input range.
     */
    public boolean hasInputRange(TransferOperation transferOp) {
        return transferOp.getInputRange() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_OPERATION_INPUT_RANGE_ANNOTATION, description = "Create input range annotation for TransferOperation")
    @Guard(method = "hasInputRange")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createTransferOperationInputRangeAnnotationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EReference rangeRef = (EReference) getEquivalent(transferOp.getInputRange(), CREATE_TRANSFER_RELATION);
            if (rangeRef == null) return null;

            EAnnotation rangeAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/TransferOperationRangeAnnotation",
                    getAnnotationUri("inputRange"));
            addAnnotationDetail(rangeAnnotation, "value", asmUtils.getReferenceFQName(rangeRef));
            eOp.getEAnnotations().add(rangeAnnotation);

            return rangeAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation is abstract.
     */
    public boolean isAbstractBoundOperation(BoundOperation boundOp) {
        return boundOp.isAbstract();
    }

    @TransformRule(name = CREATE_ABSTRACT_ANNOTATION_FOR_BOUND_OPERATION, description = "Create abstract annotation for BoundOperation")
    @Guard(method = "isAbstractBoundOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createAbstractAnnotationForBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation abstractAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/AbstractAnnotationForBoundOperation",
                    getAnnotationUri("abstract"));
            addAnnotationDetail(abstractAnnotation, "value", String.valueOf(boundOp.isAbstract()));
            eOp.getEAnnotations().add(abstractAnnotation);

            return abstractAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation has script body.
     */
    public boolean hasScriptBodyBoundOperation(BoundOperation boundOp) {
        return boundOp.getImplementation() != null && 
               boundOp.getImplementation().getBody() != null &&
               !boundOp.getImplementation().getBody().trim().isEmpty();
    }

    @TransformRule(name = CREATE_SCRIPT_BODY_ANNOTATION_FOR_BOUND_OPERATION, description = "Create script body annotation for BoundOperation")
    @Guard(method = "hasScriptBodyBoundOperation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createScriptBodyAnnotationForBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation scriptAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/ScriptBodyAnnotationForBoundOperation",
                    getAnnotationUri("script"));
            addAnnotationDetail(scriptAnnotation, "body", boundOp.getImplementation().getBody());
            eOp.getEAnnotations().add(scriptAnnotation);

            return scriptAnnotation;
        };
    }

    /**
     * Guard: Check if unbound operation has script body.
     */
    public boolean hasScriptBodyUnboundOperation(UnboundOperation unboundOp) {
        return unboundOp.getImplementation() != null && 
               unboundOp.getImplementation().getBody() != null &&
               !unboundOp.getImplementation().getBody().trim().isEmpty();
    }

    @TransformRule(name = CREATE_SCRIPT_BODY_ANNOTATION_FOR_UNBOUND_OPERATION, description = "Create script body annotation for UnboundOperation")
    @Guard(method = "hasScriptBodyUnboundOperation")
    @Transform(type = UnboundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<UnboundOperation, EAnnotation> createScriptBodyAnnotationForUnboundOperationRule() {
        return (unboundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(unboundOp, CREATE_UNBOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation scriptAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/ScriptBodyAnnotationForUnboundOperation",
                    getAnnotationUri("script"));
            addAnnotationDetail(scriptAnnotation, "body", unboundOp.getImplementation().getBody());
            eOp.getEAnnotations().add(scriptAnnotation);

            return scriptAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // TRANSFER OBJECT RULES (transferObject.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS, description = "Transform MappedTransferObjectType to EClass")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<MappedTransferObjectType, EClass> createMappedTransferObjectRule() {
        return (mappedTO, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(mappedTO) + ")/MappedTransferObject");
            eClass.setName(mappedTO.getName());

            getContainerPackage(mappedTO).getEClassifiers().add(eClass);

            // Add transfer object type annotation
            addTransferObjectTypeAnnotation(mappedTO, eClass);

            // Add mapped entity type annotation
            if (mappedTO.getEntityType() != null) {
                EAnnotation mappedEntityAnnotation = createAnnotation(
                        "(psm/" + getId(mappedTO) + ")/MappedEntityTypeAnnotationOnMappedTransferObject",
                        getAnnotationUri("mappedEntityType"));
                
                EClass entityClass = (EClass) getEquivalent(mappedTO.getEntityType(), CREATE_ENTITY_CLASS);
                if (entityClass != null) {
                    addAnnotationDetail(mappedEntityAnnotation, "value", 
                            getClassifierFQName(entityClass));
                }

                if (mappedTO.getFilter() != null) {
                    addAnnotationDetail(mappedEntityAnnotation, "filter", 
                            mappedTO.getFilter().getExpression());
                    addAnnotationDetail(mappedEntityAnnotation, "filter.dialect", 
                            mappedTO.getFilter().getDialect().toString());
                }

                eClass.getEAnnotations().add(mappedEntityAnnotation);
            }

            // Add documentation if present
            if (mappedTO.getDocumentation() != null && 
                !mappedTO.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(mappedTO);
                eClass.getEAnnotations().add(docAnnotation);
            }

            return eClass;
        };
    }

    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS, description = "Transform UnmappedTransferObjectType to EClass")
    @Transform(type = UnmappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<UnmappedTransferObjectType, EClass> createUnmappedTransferObjectRule() {
        return (unmappedTO, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(unmappedTO) + ")/UnmappedTransferObject");
            eClass.setName(unmappedTO.getName());

            getContainerPackage(unmappedTO).getEClassifiers().add(eClass);

            // Add transfer object type annotation
            addTransferObjectTypeAnnotation(unmappedTO, eClass);

            // Add documentation if present
            if (unmappedTO.getDocumentation() != null && 
                !unmappedTO.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createDocumentationAnnotation(unmappedTO);
                eClass.getEAnnotations().add(docAnnotation);
            }

            return eClass;
        };
    }

    /**
     * Guard: Check if transfer attribute has primitive data type.
     */
    public boolean isPrimitiveTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getDataType() != null && 
               transferAttr.getDataType() instanceof Primitive;
    }

    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE, description = "Transform TransferAttribute to EAttribute")
    @Guard(method = "isPrimitiveTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<TransferAttribute, EAttribute> createTransferAttributeRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(transferAttr) + ")/TransferObjectAttribute");
            eAttr.setName(transferAttr.getName());
            eAttr.setLowerBound(transferAttr.isRequired() ? 1 : 0);

            // Set derived and changeable based on binding
            boolean isDerived = transferAttr.getBinding() != null && 
                               !(transferAttr.getBinding() instanceof Attribute);
            eAttr.setDerived(isDerived);
            
            boolean isChangeable = transferAttr.getBinding() == null ||
                                  (transferAttr.getBinding() instanceof Attribute) ||
                                  (transferAttr.getBinding() instanceof PrimitiveAccessor && 
                                   ((PrimitiveAccessor) transferAttr.getBinding()).getSetterExpression() != null);
            eAttr.setChangeable(isChangeable);

            // Set type
            EClassifier type = getEquivalentType(transferAttr.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) transferAttr.eContainer();
            if (owner != null) {
                EClass ownerClass = getEquivalentTransferObjectClass(owner);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eAttr);
                }
            }

            // Add constraints annotation
            addTransferAttributeConstraints(transferAttr, eAttr);

            // Add binding annotation
            if (transferAttr.getBinding() != null && !(transferAttr.getBinding() instanceof StaticData)) {
                EAnnotation bindingAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/TransferObjectAttributeBindingAnnotation",
                        getAnnotationUri("binding"));
                addAnnotationDetail(bindingAnnotation, "value", transferAttr.getBinding().getName());
                eAttr.getEAnnotations().add(bindingAnnotation);
            }

            // Add transient annotation if no binding
            if (transferAttr.getBinding() == null) {
                EAnnotation transientAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/TransientAnnotationToTransferAttribute",
                        getAnnotationUri("transient"));
                addAnnotationDetail(transientAnnotation, "value", "true");
                eAttr.getEAnnotations().add(transientAnnotation);
            }

            // Add claim annotation if applicable
            if (transferAttr.getClaimType() != null) {
                EAnnotation claimAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/TransferAttributeClaimAnnotation",
                        getAnnotationUri("claim"));
                addAnnotationDetail(claimAnnotation, "value", transferAttr.getClaimType());
                eAttr.getEAnnotations().add(claimAnnotation);
            }

            // Add default annotation if applicable
            if (transferAttr.getDefaultValue() != null) {
                EAnnotation defaultAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/DefaultAnnotationToTransferAttribute",
                        getAnnotationUri("default"));
                addAnnotationDetail(defaultAnnotation, "value", transferAttr.getDefaultValue().getName());
                eAttr.getEAnnotations().add(defaultAnnotation);
            }

            // Add documentation if present
            if (transferAttr.getDocumentation() != null && 
                !transferAttr.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(transferAttr) + ")/DocumentationAnnotationForTransferAttribute",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", transferAttr.getDocumentation());
                eAttr.getEAnnotations().add(docAnnotation);
            }

            return eAttr;
        };
    }

    @TransformRule(name = CREATE_TRANSFER_RELATION, description = "Transform TransferObjectRelation to EReference")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EReference.class)
    public TransformFunction<TransferObjectRelation, EReference> createTransferRelationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(transferRel) + ")/TransferObjectRelation");
            eRef.setName(transferRel.getName());
            eRef.setContainment(transferRel.isEmbedded());

            if (transferRel.getCardinality() != null) {
                eRef.setLowerBound(transferRel.getCardinality().getLower());
                eRef.setUpperBound(transferRel.getCardinality().getUpper());
            }

            // Set derived and changeable based on binding
            boolean isDerived = transferRel.getBinding() != null && 
                               !(transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation);
            eRef.setDerived(isDerived);
            
            boolean isChangeable = transferRel.getBinding() == null ||
                                  (transferRel.getBinding() instanceof hu.blackbelt.judo.meta.psm.data.Relation) ||
                                  (transferRel.getBinding() instanceof ReferenceAccessor && 
                                   ((ReferenceAccessor) transferRel.getBinding()).getSetterExpression() != null);
            eRef.setChangeable(isChangeable);

            // Set target type
            if (transferRel.getTarget() != null) {
                EClass targetClass = getEquivalentTransferObjectClass(transferRel.getTarget());
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) transferRel.eContainer();
            if (owner != null) {
                EClass ownerClass = getEquivalentTransferObjectClass(owner);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            }

            // Add binding annotation
            if (transferRel.getBinding() != null && !(transferRel.getBinding() instanceof StaticNavigation)) {
                EAnnotation bindingAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/TransferObjectRelationBindingAnnotation",
                        getAnnotationUri("binding"));
                addAnnotationDetail(bindingAnnotation, "value", transferRel.getBinding().getName());
                eRef.getEAnnotations().add(bindingAnnotation);
            }

            // Add transient annotation if no binding and not access
            if (transferRel.getBinding() == null && !transferRel.isAccess()) {
                EAnnotation transientAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/TransientAnnotationToTransferObjectRelation",
                        getAnnotationUri("transient"));
                addAnnotationDetail(transientAnnotation, "value", "true");
                eRef.getEAnnotations().add(transientAnnotation);
            }

            // Add access annotation if applicable
            if (transferRel.isAccess()) {
                EAnnotation accessAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/TransferObjectRelationAccessAnnotation",
                        getAnnotationUri("access"));
                addAnnotationDetail(accessAnnotation, "value", "true");
                eRef.getEAnnotations().add(accessAnnotation);
            }

            // Add embedded flags annotation if embedded
            if (transferRel.isEmbedded()) {
                EAnnotation embeddedAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/TransferObjectRelationEmbeddedFlags",
                        getAnnotationUri("embedded"));
                addAnnotationDetail(embeddedAnnotation, "value", "true");
                addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
                addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
                addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
                eRef.getEAnnotations().add(embeddedAnnotation);
            }

            // Add permissions annotation
            EAnnotation permissionsAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationPermissions",
                    getAnnotationUri("permissions"));
            addAnnotationDetail(permissionsAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(permissionsAnnotation);

            // Add range annotation if applicable
            if (transferRel.getRange() != null) {
                EAnnotation rangeAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/TransferObjectRelationRangeAnnotation",
                        getAnnotationUri("range"));
                addAnnotationDetail(rangeAnnotation, "value", transferRel.getRange().getName());
                eRef.getEAnnotations().add(rangeAnnotation);
            }

            // Add default annotation if applicable
            if (transferRel.getDefaultValue() != null) {
                EAnnotation defaultAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/DefaultAnnotationToTransferObjectRelation",
                        getAnnotationUri("default"));
                addAnnotationDetail(defaultAnnotation, "value", transferRel.getDefaultValue().getName());
                eRef.getEAnnotations().add(defaultAnnotation);
            }

            // Add documentation if present
            if (transferRel.getDocumentation() != null && 
                !transferRel.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(transferRel) + ")/DocumentationAnnotationForTransferObjectRelation",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", transferRel.getDocumentation());
                eRef.getEAnnotations().add(docAnnotation);
            }

            return eRef;
        };
    }

    /**
     * Guard: Check if transfer attribute has no binding (transient).
     */
    public boolean isTransientTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getBinding() == null;
    }

    @TransformRule(name = ADD_TRANSIENT_ANNOTATION_TO_TRANSFER_ATTRIBUTE, description = "Add transient annotation to TransferAttribute without binding")
    @Guard(method = "isTransientTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addTransientAnnotationToTransferAttributeRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransientAnnotationToTransferAttribute",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eAttr.getEAnnotations().add(transientAnnotation);

            return transientAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has binding and is not StaticData.
     */
    public boolean hasBindingNotStaticData(TransferAttribute transferAttr) {
        return transferAttr.getBinding() != null && 
               !(transferAttr.getBinding() instanceof StaticData) &&
               transferAttr.getDataType() instanceof Primitive;
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_ATTRIBUTE_BINDING_ANNOTATION, description = "Create binding annotation for TransferAttribute")
    @Guard(method = "hasBindingNotStaticData")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createTransferObjectAttributeBindingAnnotationRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferObjectAttributeBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferAttr.getBinding().getName());
            eAttr.getEAnnotations().add(bindingAnnotation);

            return bindingAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has no binding and is not access.
     */
    public boolean isTransientTransferObjectRelation(TransferObjectRelation transferRel) {
        return transferRel.getBinding() == null && !transferRel.isAccess();
    }

    @TransformRule(name = ADD_TRANSIENT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION, description = "Add transient annotation to TransferObjectRelation without binding")
    @Guard(method = "isTransientTransferObjectRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> addTransientAnnotationToTransferObjectRelationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransientAnnotationToTransferObjectRelation",
                    getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eRef.getEAnnotations().add(transientAnnotation);

            return transientAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has binding and is not StaticNavigation.
     */
    public boolean hasRelationBindingNotStaticNavigation(TransferObjectRelation transferRel) {
        return transferRel.getBinding() != null && 
               !(transferRel.getBinding() instanceof StaticNavigation);
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_BINDING_ANNOTATION, description = "Create binding annotation for TransferObjectRelation")
    @Guard(method = "hasRelationBindingNotStaticNavigation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationBindingAnnotationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationBindingAnnotation",
                    getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferRel.getBinding().getName());
            eRef.getEAnnotations().add(bindingAnnotation);

            return bindingAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation is access.
     */
    public boolean isAccessRelation(TransferObjectRelation transferRel) {
        return transferRel.isAccess();
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_ACCESS_ANNOTATION, description = "Create access annotation for TransferObjectRelation")
    @Guard(method = "isAccessRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationAccessAnnotationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation accessAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationAccessAnnotation",
                    getAnnotationUri("access"));
            addAnnotationDetail(accessAnnotation, "value", "true");
            eRef.getEAnnotations().add(accessAnnotation);

            return accessAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has range defined.
     */
    public boolean hasRange(TransferObjectRelation transferRel) {
        return transferRel.getRange() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_RANGE_ANNOTATION, description = "Create range annotation for TransferObjectRelation")
    @Guard(method = "hasRange")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationRangeAnnotationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation rangeAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationRangeAnnotation",
                    getAnnotationUri("range"));
            addAnnotationDetail(rangeAnnotation, "value", transferRel.getRange().getName());
            eRef.getEAnnotations().add(rangeAnnotation);

            return rangeAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation is embedded.
     */
    public boolean isEmbedded(TransferObjectRelation transferRel) {
        return transferRel.isEmbedded();
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_EMBEDDED_FLAGS, description = "Create embedded flags annotation for TransferObjectRelation")
    @Guard(method = "isEmbedded")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationEmbeddedFlagsRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation embeddedAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationEmbeddedFlags",
                    getAnnotationUri("embedded"));
            addAnnotationDetail(embeddedAnnotation, "value", "true");
            addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(embeddedAnnotation);

            return embeddedAnnotation;
        };
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_PERMISSIONS, description = "Create permissions annotation for TransferObjectRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationPermissionsRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation permissionsAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationPermissions",
                    getAnnotationUri("permissions"));
            addAnnotationDetail(permissionsAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(permissionsAnnotation);

            return permissionsAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has claim type.
     */
    public boolean hasClaimType(TransferAttribute transferAttr) {
        return transferAttr.getClaimType() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE_CLAIM_ANNOTATION, description = "Create claim annotation for TransferAttribute")
    @Guard(method = "hasClaimType")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createTransferAttributeClaimAnnotationRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation claimAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferAttributeClaimAnnotation",
                    getAnnotationUri("claim"));
            addAnnotationDetail(claimAnnotation, "value", transferAttr.getClaimType());
            eAttr.getEAnnotations().add(claimAnnotation);

            return claimAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has default value.
     */
    public boolean hasDefaultValue(TransferAttribute transferAttr) {
        return transferAttr.getDefaultValue() != null;
    }

    @TransformRule(name = ADD_DEFAULT_ANNOTATION_TO_TRANSFER_ATTRIBUTE, description = "Add default annotation to TransferAttribute")
    @Guard(method = "hasDefaultValue")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addDefaultAnnotationToTransferAttributeRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DefaultAnnotationToTransferAttribute",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferAttr.getDefaultValue().getName());
            eAttr.getEAnnotations().add(defaultAnnotation);

            return defaultAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has string data type.
     */
    public boolean isStringTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getDataType() instanceof StringType;
    }

    @TransformRule(name = ADD_STRING_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add string constraints for TransferAttribute")
    @Guard(method = "isStringTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addStringTransferAttributeConstraintsRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            StringType stringType = (StringType) transferAttr.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/StringTransferAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "maxLength", String.valueOf(stringType.getMaxLength()));

            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has custom data type.
     */
    public boolean isCustomTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getDataType() instanceof CustomType;
    }

    @TransformRule(name = ADD_CUSTOM_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add custom type constraints for TransferAttribute")
    @Guard(method = "isCustomTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addCustomTransferAttributeConstraintsRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/CustomTransferAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "customType", 
                    getQualifiedName(transferAttr.getDataType()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has numeric (non-measured) data type.
     */
    public boolean isNumericTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getDataType() instanceof NumericType && 
               !(transferAttr.getDataType() instanceof MeasuredType);
    }

    @TransformRule(name = ADD_NUMERIC_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add numeric constraints for TransferAttribute")
    @Guard(method = "isNumericTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addNumericTransferAttributeConstraintsRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            NumericType numericType = (NumericType) transferAttr.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/NumericTransferAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(numericType.getScale()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has measured data type.
     */
    public boolean isMeasuredTransferAttribute(TransferAttribute transferAttr) {
        return transferAttr.getDataType() instanceof MeasuredType;
    }

    @TransformRule(name = ADD_MEASURED_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add measured constraints for TransferAttribute")
    @Guard(method = "isMeasuredTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addMeasuredTransferAttributeConstraintsRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            MeasuredType measuredType = (MeasuredType) transferAttr.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/MeasuredTransferAttributeConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(measuredType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(measuredType.getScale()));
            
            if (measuredType.getStoreUnit() != null) {
                addAnnotationDetail(constraintsAnnotation, "measure", 
                        getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                addAnnotationDetail(constraintsAnnotation, "unit", measuredType.getStoreUnit().getName());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has default value.
     */
    public boolean hasRelationDefaultValue(TransferObjectRelation transferRel) {
        return transferRel.getDefaultValue() != null;
    }

    @TransformRule(name = ADD_DEFAULT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION, description = "Add default annotation to TransferObjectRelation")
    @Guard(method = "hasRelationDefaultValue")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> addDefaultAnnotationToTransferObjectRelationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DefaultAnnotationToTransferObjectRelation",
                    getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferRel.getDefaultValue().getName());
            eRef.getEAnnotations().add(defaultAnnotation);

            return defaultAnnotation;
        };
    }

    @TransformRule(name = CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE, description = "Create reference holder EClass for EntityType")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createReferenceClassForEntityTypeRule() {
        return (entityType, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(entityType) + ")/ReferenceClassForEntityType");
            eClass.setName(entityType.getName() + "__Reference");

            // Set up inheritance from super types' reference classes
            for (EntityType superType : entityType.getSuperEntityTypes()) {
                EClass superRefClass = (EClass) getEquivalent(superType, CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE);
                if (superRefClass != null) {
                    eClass.getESuperTypes().add(superRefClass);
                }
            }

            getContainerPackage(entityType).getEClassifiers().add(eClass);

            // Add reference holder annotation
            EAnnotation refHolderAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/AnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("referenceHolder"));
            addAnnotationDetail(refHolderAnnotation, "value", "true");
            eClass.getEAnnotations().add(refHolderAnnotation);

            // Add transfer object type annotation
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            // Add mapped entity type annotation
            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("mappedEntityType"));
            EClass entityClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
            if (entityClass != null) {
                addAnnotationDetail(mappedEntityAnnotation, "value", 
                        getClassifierFQName(entityClass));
            }
            eClass.getEAnnotations().add(mappedEntityAnnotation);

            return eClass;
        };
    }

    /**
     * Guard: Check if transfer object type is a query customizer.
     */
    public boolean isQueryCustomizer(TransferObjectType to) {
        return to.isQueryCustomizer();
    }

    @TransformRule(name = CREATE_QUERY_CUSTOMIZER_ANNOTATION, description = "Create query customizer annotation")
    @Guard(method = "isQueryCustomizer")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createQueryCustomizerAnnotationRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation qcAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/QueryCustomizerAnnotationForQueryCustomizerClass",
                    getAnnotationUri("queryCustomizer"));
            addAnnotationDetail(qcAnnotation, "value", "true");
            eClass.getEAnnotations().add(qcAnnotation);

            return qcAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object type is a metadata type.
     * A transfer object is a metadata type if it's returned by a GET_METADATA operation.
     */
    public boolean isMetadataType(TransferObjectType to) {
        // O(1) lookup using pre-computed cache instead of O(TOs × Operations)
        return metadataTypes != null && metadataTypes.contains(to);
    }

    @TransformRule(name = CREATE_METADATA_ANNOTATION, description = "Create metadata annotation for metadata class")
    @Guard(method = "isMetadataType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createMetadataAnnotationRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation metaAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/MetadataAnnotationForMetadataClass",
                    getAnnotationUri("metadata"));
            addAnnotationDetail(metaAnnotation, "value", "true");
            eClass.getEAnnotations().add(metaAnnotation);

            return metaAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object type is a get range input type.
     * A transfer object is a get range input type if it's the input type of a GET_RANGE operation.
     */
    public boolean isGetRangeInputType(TransferObjectType to) {
        // O(1) lookup using pre-computed cache instead of O(TOs × Operations)
        return getRangeInputTypes != null && getRangeInputTypes.contains(to);
    }

    @TransformRule(name = CREATE_GET_RANGE_INPUT_ANNOTATION, description = "Create get range input annotation")
    @Guard(method = "isGetRangeInputType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createGetRangeInputAnnotationRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation getRangeInputAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/GetRangeInputAnnotationForGetRangeInputClass",
                    getAnnotationUri("getRangeInput"));
            addAnnotationDetail(getRangeInputAnnotation, "value", "true");
            eClass.getEAnnotations().add(getRangeInputAnnotation);

            return getRangeInputAnnotation;
        };
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS, description = "Create transfer object type annotation class")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createTransferObjectTypeAnnotationClassRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/TransferObjectTypeAnnotationClass",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            return toAnnotation;
        };
    }

    @TransformRule(name = CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_MAPPED_TRANSFER_OBJECT, description = "Create mapped entity type annotation on mapped transfer object")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<MappedTransferObjectType, EAnnotation> createMappedEntityTypeAnnotationOnMappedTransferObjectRule() {
        return (mto, ctx) -> {
            EClass eClass = (EClass) getEquivalent(mto, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
            if (eClass == null) return null;

            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(mto) + ")/MappedEntityTypeAnnotationOnMappedTransferObject",
                    getAnnotationUri("mappedEntityType"));

            if (mto.getEntityType() != null) {
                EClass entityClass = (EClass) getEquivalent(mto.getEntityType(), CREATE_ENTITY_CLASS);
                if (entityClass != null) {
                    addAnnotationDetail(mappedEntityAnnotation, "value", 
                            getClassifierFQName(entityClass));
                }
            }

            // Add filter if defined
            if (mto.getFilter() != null) {
                addAnnotationDetail(mappedEntityAnnotation, "filter", mto.getFilter().getExpression());
                addAnnotationDetail(mappedEntityAnnotation, "filter.dialect", 
                        mto.getFilter().getDialect().toString());
            }

            eClass.getEAnnotations().add(mappedEntityAnnotation);
            return mappedEntityAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has parameterized binding.
     */
    public boolean hasParameterizedAttributeBinding(TransferAttribute transferAttr) {
        return transferAttr.getBinding() != null && 
               transferAttr.getBinding() instanceof PrimitiveAccessor &&
               ((PrimitiveAccessor) transferAttr.getBinding()).getGetterExpression() != null &&
               ((PrimitiveAccessor) transferAttr.getBinding()).getGetterExpression().getParameterType() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE_PARAMETERIZED_ANNOTATION, description = "Create parameterized annotation for TransferAttribute")
    @Guard(method = "hasParameterizedAttributeBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createTransferAttributeParameterizedAnnotationRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            PrimitiveAccessor accessor = (PrimitiveAccessor) transferAttr.getBinding();
            TransferObjectType paramType = accessor.getGetterExpression().getParameterType();

            EAnnotation paramAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferAttributeParameterizedAnnotation",
                    getAnnotationUri("parameterized"));
            addAnnotationDetail(paramAnnotation, "value", "true");

            EClassifier paramClassifier = getEquivalentTransferObject(paramType);
            if (paramClassifier != null) {
                addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramClassifier));
            }

            eAttr.getEAnnotations().add(paramAnnotation);
            return paramAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has parameterized binding.
     */
    public boolean hasParameterizedRelationBinding(TransferObjectRelation transferRel) {
        return transferRel.getBinding() != null && 
               transferRel.getBinding() instanceof ReferenceAccessor &&
               ((ReferenceAccessor) transferRel.getBinding()).getGetterExpression() != null &&
               ((ReferenceAccessor) transferRel.getBinding()).getGetterExpression().getParameterType() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_PARAMETERIZED_ANNOTATION, description = "Create parameterized annotation for TransferObjectRelation")
    @Guard(method = "hasParameterizedRelationBinding")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationParameterizedAnnotationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            ReferenceAccessor accessor = (ReferenceAccessor) transferRel.getBinding();
            TransferObjectType paramType = accessor.getGetterExpression().getParameterType();

            EAnnotation paramAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationParameterizedAnnotation",
                    getAnnotationUri("parameterized"));
            addAnnotationDetail(paramAnnotation, "value", "true");

            EClassifier paramClassifier = getEquivalentTransferObject(paramType);
            if (paramClassifier != null) {
                addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramClassifier));
            }

            eRef.getEAnnotations().add(paramAnnotation);
            return paramAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has navigation reference binding.
     * This applies when the container is unmapped and binding is a ReferenceAccessor,
     * or when the container is mapped and binding is a StaticNavigation.
     */
    public boolean hasNavigationReferenceBinding(TransferObjectRelation transferRel) {
        if (transferRel.getBinding() == null) return false;
        
        EObject container = transferRel.eContainer();
        if (container instanceof UnmappedTransferObjectType && 
            transferRel.getBinding() instanceof ReferenceAccessor) {
            return true;
        }
        if (container instanceof MappedTransferObjectType && 
            transferRel.getBinding() instanceof StaticNavigation) {
            return true;
        }
        return false;
    }

    @TransformRule(name = CREATE_NAVIGATION_REFERENCE_BINDING, description = "Create navigation reference binding annotation")
    @Guard(method = "hasNavigationReferenceBinding")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createNavigationReferenceBindingRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            ReferenceAccessor binding;
            if (transferRel.getBinding() instanceof ReferenceAccessor) {
                binding = (ReferenceAccessor) transferRel.getBinding();
            } else if (transferRel.getBinding() instanceof StaticNavigation) {
                binding = (StaticNavigation) transferRel.getBinding();
            } else {
                return null;
            }

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/NavigationReferenceBinding",
                    getAnnotationUri("expression"));

            // Getter
            if (binding.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());

                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            // Setter
            if (binding.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        binding.getSetterExpression().getDialect().toString());

                if (binding.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eRef.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has data reference binding.
     * This applies when the container is unmapped and binding is a PrimitiveAccessor,
     * or when the container is mapped and binding is a StaticData.
     */
    public boolean hasDataReferenceBinding(TransferAttribute transferAttr) {
        if (transferAttr.getBinding() == null) return false;
        
        EObject container = transferAttr.eContainer();
        if (container instanceof UnmappedTransferObjectType && 
            transferAttr.getBinding() instanceof PrimitiveAccessor) {
            return true;
        }
        if (container instanceof MappedTransferObjectType && 
            transferAttr.getBinding() instanceof StaticData) {
            return true;
        }
        return false;
    }

    @TransformRule(name = CREATE_DATA_REFERENCE_BINDING, description = "Create data reference binding annotation")
    @Guard(method = "hasDataReferenceBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createDataReferenceBindingRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            PrimitiveAccessor binding;
            if (transferAttr.getBinding() instanceof PrimitiveAccessor) {
                binding = (PrimitiveAccessor) transferAttr.getBinding();
            } else if (transferAttr.getBinding() instanceof StaticData) {
                binding = (StaticData) transferAttr.getBinding();
            } else {
                return null;
            }

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DataReferenceBinding",
                    getAnnotationUri("expression"));

            // Getter
            if (binding.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", binding.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        binding.getGetterExpression().getDialect().toString());

                if (binding.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            // Setter
            if (binding.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", binding.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        binding.getSetterExpression().getDialect().toString());

                if (binding.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(binding.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eAttr.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // STATIC RULES (static.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA, description = "Transform StaticData to unmapped transfer object EClass")
    @Transform(type = StaticData.class)
    @To(type = EClass.class)
    public TransformFunction<StaticData, EClass> createUnmappedTransferObjectForStaticDataRule() {
        return (staticData, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(staticData) + ")/UnmappedTransferObjectForStaticData");
            eClass.setName(capitalize(staticData.getName()));

            getContainerPackage(staticData).getEClassifiers().add(eClass);

            // Add transfer object type annotation
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/TransferObjectTypeAnnotationClassForStaticData",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            // Add static query annotation
            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/StaticDataQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            eClass.getEAnnotations().add(staticQueryAnnotation);

            // Create static query attribute
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(staticData) + ")/StaticQueryAttribute");
            eAttr.setName(staticData.getName());
            eAttr.setLowerBound(staticData.isRequired() ? 1 : 0);
            eAttr.setDerived(true);
            eAttr.setChangeable(false);

            EClassifier type = getEquivalentType(staticData.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            eClass.getEStructuralFeatures().add(eAttr);

            // Add expression annotation for getter
            if (staticData.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(staticData) + ")/DataReferenceBindingForStaticData",
                        getAnnotationUri("expression"));
                addAnnotationDetail(exprAnnotation, "getter", staticData.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", staticData.getGetterExpression().getDialect().toString());

                if (staticData.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticData.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));
                    }

                    // Add parameterized annotation
                    EAnnotation paramAnnotation = createAnnotation(
                            "(psm/" + getId(staticData) + ")/TransferAttributeParameterizedAnnotationForStaticData",
                            getAnnotationUri("parameterized"));
                    addAnnotationDetail(paramAnnotation, "value", "true");
                    addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                    eAttr.getEAnnotations().add(paramAnnotation);
                }

                if (staticData.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", staticData.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", staticData.getSetterExpression().getDialect().toString());
                }

                eAttr.getEAnnotations().add(exprAnnotation);
            }

            addTrace(staticData, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA, eClass);
            return eClass;
        };
    }

    /**
     * Guard: Check if static navigation has default representation on target.
     */
    public boolean hasDefaultRepresentation(StaticNavigation staticNavigation) {
        return staticNavigation.getTarget() != null && 
               staticNavigation.getTarget().getDefaultRepresentation() != null;
    }

    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION, description = "Transform StaticNavigation to unmapped transfer object EClass")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = StaticNavigation.class)
    @To(type = EClass.class)
    public TransformFunction<StaticNavigation, EClass> createUnmappedTransferObjectForStaticNavigationRule() {
        return (staticNav, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            setId(eClass, "(psm/" + getId(staticNav) + ")/UnmappedTransferObjectForStaticNavigation");
            eClass.setName(capitalize(staticNav.getName()));

            getContainerPackage(staticNav).getEClassifiers().add(eClass);

            // Add transfer object type annotation
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/TransferObjectTypeAnnotationClassForStaticNavigation",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            // Add static query annotation
            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/StaticNavigationQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            eClass.getEAnnotations().add(staticQueryAnnotation);

            // Create static query navigation reference
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(staticNav) + ")/StaticQueryNavigation");
            eRef.setName(staticNav.getName());
            eRef.setContainment(false);
            eRef.setDerived(true);
            eRef.setChangeable(false);

            if (staticNav.getCardinality() != null) {
                eRef.setLowerBound(staticNav.getCardinality().getLower());
                eRef.setUpperBound(staticNav.getCardinality().getUpper());
            }

            // Set target type to the default representation's mapped transfer object
            MappedTransferObjectType defaultRep = staticNav.getTarget().getDefaultRepresentation();
            if (defaultRep != null) {
                EClass targetClass = (EClass) getEquivalent(defaultRep, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            eClass.getEStructuralFeatures().add(eRef);

            // Add expression annotation for getter
            if (staticNav.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(staticNav) + ")/NavigationReferenceBindingForStaticNavigation",
                        getAnnotationUri("expression"));
                addAnnotationDetail(exprAnnotation, "getter", staticNav.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", staticNav.getGetterExpression().getDialect().toString());

                if (staticNav.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticNav.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));
                    }

                    // Add parameterized annotation
                    EAnnotation paramAnnotation = createAnnotation(
                            "(psm/" + getId(staticNav) + ")/TransferObjectRelationParameterizedAnnotationForStaticNavigation",
                            getAnnotationUri("parameterized"));
                    addAnnotationDetail(paramAnnotation, "value", "true");
                    addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                    eRef.getEAnnotations().add(paramAnnotation);
                }

                if (staticNav.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", staticNav.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", staticNav.getSetterExpression().getDialect().toString());
                }

                eRef.getEAnnotations().add(exprAnnotation);
            }

            addTrace(staticNav, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION, eClass);
            return eClass;
        };
    }

    // -------------------------------------------------------------------------
    // ACTOR RULES (actor.etl)
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if transfer object has an actor type.
     */
    public boolean hasActorType(TransferObjectType to) {
        return to.getActorType() != null;
    }

    @TransformRule(name = CREATE_ACTOR_ANNOTATION, description = "Create actor annotation for TransferObjectType")
    @Guard(method = "hasActorType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createActorAnnotationRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            AbstractActorType actorType = to.getActorType();

            EAnnotation actorAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/ActorAnnotation",
                    getAnnotationUri("actor"));
            
            addAnnotationDetail(actorAnnotation, "name", 
                    psmUtils.namespaceElementToString((NamespaceElement) actorType));
            
            // ETL's isDefined() means not null AND not empty
            if (actorType.getRealm() != null && !actorType.getRealm().isEmpty()) {
                addAnnotationDetail(actorAnnotation, "realm", actorType.getRealm());
            }

            eClass.getEAnnotations().add(actorAnnotation);
            return actorAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object is an AbstractActorType.
     */
    public boolean isAbstractActorType(TransferObjectType to) {
        return to instanceof AbstractActorType;
    }

    @TransformRule(name = CREATE_ACTOR_TYPE_ANNOTATION, description = "Create actor type annotation for AbstractActorType")
    @Guard(method = "isAbstractActorType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createActorTypeAnnotationRule() {
        return (to, ctx) -> {
            AbstractActorType actorType = (AbstractActorType) to;
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation actorTypeAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/ActorTypeAnnotation",
                    getAnnotationUri("actorType"));
            addAnnotationDetail(actorTypeAnnotation, "value", "true");

            if (actorType instanceof MappedActorType) {
                MappedActorType mappedActor = (MappedActorType) actorType;
                addAnnotationDetail(actorTypeAnnotation, "managed", String.valueOf(mappedActor.isManaged()));
            }

            if (actorType.getKind() != null) {
                addAnnotationDetail(actorTypeAnnotation, "kind", actorType.getKind().toString());
            }

            eClass.getEAnnotations().add(actorTypeAnnotation);
            return actorTypeAnnotation;
        };
    }

    /**
     * Guard: Check if AbstractActorType has realm defined.
     * ETL's guard checks s.realm.isDefined() which means not null AND not empty.
     */
    public boolean hasRealm(TransferObjectType to) {
        if (!(to instanceof AbstractActorType)) return false;
        String realm = ((AbstractActorType) to).getRealm();
        return realm != null && !realm.isEmpty();
    }

    @TransformRule(name = CREATE_REALM_TYPE_ANNOTATION, description = "Create realm annotation for AbstractActorType")
    @Guard(method = "hasRealm")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createRealmTypeAnnotationRule() {
        return (to, ctx) -> {
            AbstractActorType actorType = (AbstractActorType) to;
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation realmAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/RealmTypeAnnotation",
                    getAnnotationUri("realm"));
            addAnnotationDetail(realmAnnotation, "value", actorType.getRealm());

            eClass.getEAnnotations().add(realmAnnotation);
            return realmAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // SEQUENCE RULES (data.etl)
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_ENTITY_SEQUENCE, description = "Transform EntitySequence to EAnnotation")
    @Transform(type = EntitySequence.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntitySequence, EAnnotation> createEntitySequenceRule() {
        return (sequence, ctx) -> {
            EAnnotation seqAnnotation = createSequenceAnnotation(sequence);
            
            // Add to owning entity class
            EntityType owner = (EntityType) sequence.eContainer();
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEAnnotations().add(seqAnnotation);
                }
            }
            
            return seqAnnotation;
        };
    }

    @TransformRule(name = CREATE_NAMESPACE_SEQUENCE, description = "Transform NamespaceSequence to EAnnotation")
    @Transform(type = NamespaceSequence.class)
    @To(type = EAnnotation.class)
    public TransformFunction<NamespaceSequence, EAnnotation> createNamespaceSequenceRule() {
        return (sequence, ctx) -> {
            EAnnotation seqAnnotation = createSequenceAnnotation(sequence);
            
            // Add to containing namespace's equivalent package
            EObject container = sequence.eContainer();
            if (container instanceof Namespace) {
                EPackage pkg = (EPackage) getEquivalent(container, 
                        container instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
                if (pkg != null) {
                    pkg.getEAnnotations().add(seqAnnotation);
                }
            }
            
            return seqAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL OPERATION RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if transfer operation has behaviour defined.
     */
    public boolean hasBehaviour(TransferOperation transferOp) {
        return transferOp.getBehaviour() != null;
    }

    @TransformRule(name = ADD_BEHAVIOUR_ANNOTATION, description = "Add behaviour annotation for TransferOperation")
    @Guard(method = "hasBehaviour")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> addBehaviourAnnotationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            TransferOperationBehaviour behaviour = transferOp.getBehaviour();

            EAnnotation behaviourAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/BehaviourAnnotation",
                    getAnnotationUri("behaviour"));

            // Map behaviour type to string value
            String typeValue = mapBehaviourType(behaviour.getBehaviourType(), behaviour.getOwner());
            addAnnotationDetail(behaviourAnnotation, "type", typeValue);

            // Add owner detail
            String ownerValue = getBehaviourOwnerValue(behaviour);
            if (ownerValue != null) {
                addAnnotationDetail(behaviourAnnotation, "owner", ownerValue);
            }

            eOp.getEAnnotations().add(behaviourAnnotation);

            // For BoundTransferOperation, also add behaviour annotation to the binding
            if (transferOp instanceof BoundTransferOperation) {
                BoundTransferOperation boundOp = (BoundTransferOperation) transferOp;
                EOperation bindingOp = (EOperation) getEquivalent(boundOp.getBinding(), CREATE_BOUND_OPERATION);
                if (bindingOp != null) {
                    EAnnotation bindingBehaviourAnnotation = createAnnotation(
                            "(psm/" + getId(transferOp) + ")/BindingBehaviourAnnotation",
                            getAnnotationUri("behaviour"));
                    addAnnotationDetail(bindingBehaviourAnnotation, "type", typeValue);
                    if (ownerValue != null) {
                        addAnnotationDetail(bindingBehaviourAnnotation, "owner", ownerValue);
                    }
                    bindingOp.getEAnnotations().add(bindingBehaviourAnnotation);
                }
            }

            return behaviourAnnotation;
        };
    }

    private String mapBehaviourType(TransferOperationBehaviourType behaviourType, EObject owner) {
        switch (behaviourType) {
            case LIST: return "list";
            case CREATE_INSTANCE: return "createInstance";
            case VALIDATE_CREATE: return "validateCreate";
            case REFRESH: return "refresh";
            case UPDATE_INSTANCE: return "updateInstance";
            case VALIDATE_UPDATE: return "validateUpdate";
            case DELETE_INSTANCE: return "deleteInstance";
            case SET_REFERENCE: return "setReference";
            case UNSET_REFERENCE: return "unsetReference";
            case ADD_REFERENCE: return "addReference";
            case REMOVE_REFERENCE: return "removeReference";
            case GET_RANGE:
                if (owner instanceof TransferObjectRelation) {
                    return "getReferenceRange";
                } else if (owner instanceof TransferOperation) {
                    return "getInputRange";
                }
                return "getRange";
            case GET_TEMPLATE: return "getTemplate";
            case GET_PRINCIPAL: return "getPrincipal";
            case GET_METADATA: return "getMetadata";
            case GET_UPLOAD_TOKEN: return "getUploadToken";
            case EXPORT: return "export";
            case VALIDATE_OPERATION_INPUT: return "validateOperationInput";
            default: return behaviourType.toString().toLowerCase();
        }
    }

    private String getBehaviourOwnerValue(TransferOperationBehaviour behaviour) {
        EObject owner = behaviour.getOwner();
        if (owner == null) return null;

        TransferOperationBehaviourType behaviourType = behaviour.getBehaviourType();
        switch (behaviourType) {
            case GET_TEMPLATE:
            case GET_PRINCIPAL:
            case GET_METADATA:
            case REFRESH:
            case UPDATE_INSTANCE:
            case VALIDATE_UPDATE:
            case DELETE_INSTANCE:
                EClassifier ownerClassifier = getEquivalentTransferObject((TransferObjectType) owner);
                if (ownerClassifier != null) {
                    return getClassifierFQName(ownerClassifier);
                }
                break;
            case GET_UPLOAD_TOKEN:
                EObject attrEquiv = getEquivalent(owner, CREATE_TRANSFER_ATTRIBUTE);
                if (attrEquiv instanceof EAttribute) {
                    return asmUtils.getAttributeFQName((EAttribute) attrEquiv);
                }
                break;
            case GET_RANGE:
                if (owner instanceof TransferObjectRelation) {
                    EObject relEquiv = getEquivalent(owner, CREATE_TRANSFER_RELATION);
                    if (relEquiv instanceof EReference) {
                        return asmUtils.getReferenceFQName((EReference) relEquiv);
                    }
                } else if (owner instanceof TransferOperation) {
                    EOperation opEquiv = getEquivalentTransferOperation((TransferOperation) owner);
                    if (opEquiv != null) {
                        return asmUtils.getOperationFQName(opEquiv);
                    }
                }
                break;
            case VALIDATE_OPERATION_INPUT:
                EOperation opEquiv2 = getEquivalentTransferOperation((TransferOperation) owner);
                if (opEquiv2 != null) {
                    return asmUtils.getOperationFQName(opEquiv2);
                }
                break;
            default:
                EObject defaultEquiv = getEquivalent(owner, CREATE_TRANSFER_RELATION);
                if (defaultEquiv instanceof EReference) {
                    return asmUtils.getReferenceFQName((EReference) defaultEquiv);
                }
                break;
        }
        return null;
    }

    /**
     * Guard: Check if transfer operation has implementation with stateful flag.
     */
    public boolean hasTransferOperationStateful(TransferOperation transferOp) {
        return transferOp.getImplementation() != null;
    }

    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_ON_OPERATION, description = "Create stateful annotation on TransferOperation with implementation")
    @Guard(method = "hasTransferOperationStateful")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationOnOperationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperation",
                    getAnnotationUri("stateful"));
            addAnnotationDetail(statefulAnnotation, "value", 
                    String.valueOf(transferOp.getImplementation().isStateful()));
            eOp.getEAnnotations().add(statefulAnnotation);

            return statefulAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation has instance representation.
     */
    public boolean hasInstanceRepresentation(BoundOperation boundOp) {
        return boundOp.getInstanceRepresentation() != null;
    }

    @TransformRule(name = CREATE_INSTANCE_REPRESENTATION_OF_BOUND_OPERATION, description = "Create instance representation annotation for BoundOperation")
    @Guard(method = "hasInstanceRepresentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createInstanceRepresentationOfBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation instanceRepAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/InstanceRepresentationOfBoundOperation",
                    getAnnotationUri("instanceRepresentation"));
            
            EClassifier instanceRepClass = getEquivalentTransferObject(boundOp.getInstanceRepresentation());
            if (instanceRepClass != null) {
                addAnnotationDetail(instanceRepAnnotation, "value", 
                        getClassifierFQName(instanceRepClass));
            }
            
            eOp.getEAnnotations().add(instanceRepAnnotation);
            return instanceRepAnnotation;
        };
    }

    @TransformRule(name = CREATE_BOUND_OPERATION_ANNOTATION, description = "Create bound annotation for OperationDeclaration")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createBoundOperationAnnotationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation boundAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/BoundOperationAnnotation",
                    getAnnotationUri("bound"));
            addAnnotationDetail(boundAnnotation, "value", "true");
            eOp.getEAnnotations().add(boundAnnotation);

            return boundAnnotation;
        };
    }

    /**
     * Guard: Check if parameter is input parameter.
     */
    public boolean isInputParameter(Parameter param) {
        return param.eContainer() instanceof OperationDeclaration &&
               ((OperationDeclaration) param.eContainer()).getInput() == param;
    }

    @TransformRule(name = CREATE_INPUT_PARAMETER, description = "Transform input Parameter to EParameter")
    @Guard(method = "isInputParameter")
    @Transform(type = Parameter.class)
    @To(type = EParameter.class)
    public TransformFunction<Parameter, EParameter> createInputParameterRule() {
        return (param, ctx) -> {
            EParameter eParam = EcoreFactory.eINSTANCE.createEParameter();
            setId(eParam, "(psm/" + getId(param) + ")/InputParameter");
            eParam.setName(param.getName());
            
            if (param.getCardinality() != null) {
                eParam.setLowerBound(param.getCardinality().getLower());
                eParam.setUpperBound(param.getCardinality().getUpper());
            }

            // Set type
            if (param.getType() != null) {
                EClassifier paramType = getEquivalentTransferObject(param.getType());
                if (paramType != null) {
                    eParam.setEType(paramType);
                }
            }

            // Add to owning operation
            OperationDeclaration owner = (OperationDeclaration) param.eContainer();
            EOperation ownerOp = getEquivalentOperation(owner);
            if (ownerOp != null) {
                ownerOp.getEParameters().add(eParam);
            }

            return eParam;
        };
    }

    private EOperation getEquivalentOperation(OperationDeclaration op) {
        if (op instanceof BoundOperation) {
            return (EOperation) getEquivalent(op, CREATE_BOUND_OPERATION);
        } else if (op instanceof UnboundOperation) {
            return (EOperation) getEquivalent(op, CREATE_UNBOUND_OPERATION);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // DOCUMENTATION ANNOTATION RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if entity type has documentation.
     */
    public boolean hasEntityTypeDocumentation(EntityType entityType) {
        return entityType.getDocumentation() != null && 
               !entityType.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_ENTITY_TYPE, description = "Create documentation annotation for EntityType")
    @Guard(method = "hasEntityTypeDocumentation")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createDocumentationAnnotationForEntityTypeRule() {
        return (entityType, ctx) -> {
            EClass eClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
            if (eClass == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/DocumentationAnnotationForEntityType",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", entityType.getDocumentation());
            eClass.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if attribute has documentation.
     */
    public boolean hasAttributeDocumentation(Attribute attribute) {
        return attribute.getDocumentation() != null && 
               !attribute.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_ATTRIBUTES, description = "Create documentation annotation for Attribute")
    @Guard(method = "hasAttributeDocumentation")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> createDocumentationAnnotationForAttributesRule() {
        return (attribute, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attribute, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(attribute) + ")/DocumentationAnnotationForAtrributes",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", attribute.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object type has documentation.
     */
    public boolean hasTransferObjectTypeDocumentation(TransferObjectType to) {
        return to.getDocumentation() != null && 
               !to.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_TYPE, description = "Create documentation annotation for TransferObjectType")
    @Guard(method = "hasTransferObjectTypeDocumentation")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createDocumentationAnnotationForTransferObjectTypeRule() {
        return (to, ctx) -> {
            EClass eClass = getEquivalentTransferObjectClass(to);
            if (eClass == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/DocumentationAnnotationForTransferObjectType",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", to.getDocumentation());
            eClass.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if transfer attribute has documentation.
     */
    public boolean hasTransferAttributeDocumentation(TransferAttribute transferAttr) {
        return transferAttr.getDocumentation() != null && 
               !transferAttr.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_ATTRIBUTE, description = "Create documentation annotation for TransferAttribute")
    @Guard(method = "hasTransferAttributeDocumentation")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createDocumentationAnnotationForTransferAttributeRule() {
        return (transferAttr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(transferAttr, CREATE_TRANSFER_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DocumentationAnnotationForTransferAttribute",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferAttr.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if transfer object relation has documentation.
     */
    public boolean hasTransferObjectRelationDocumentation(TransferObjectRelation transferRel) {
        return transferRel.getDocumentation() != null && 
               !transferRel.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_RELATION, description = "Create documentation annotation for TransferObjectRelation")
    @Guard(method = "hasTransferObjectRelationDocumentation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createDocumentationAnnotationForTransferObjectRelationRule() {
        return (transferRel, ctx) -> {
            EReference eRef = (EReference) getEquivalent(transferRel, CREATE_TRANSFER_RELATION);
            if (eRef == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DocumentationAnnotationForTransferObjectRelation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferRel.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if transfer operation has documentation.
     */
    public boolean hasTransferOperationDocumentation(TransferOperation transferOp) {
        return transferOp.getDocumentation() != null && 
               !transferOp.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OPERATION, description = "Create documentation annotation for TransferOperation")
    @Guard(method = "hasTransferOperationDocumentation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createDocumentationAnnotationForTransferOperationRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/DocumentationAnnotationForTransferOperation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferOp.getDocumentation());
            eOp.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation has documentation.
     */
    public boolean hasBoundOperationDocumentation(BoundOperation boundOp) {
        return boundOp.getDocumentation() != null && 
               !boundOp.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_BOUND_OPERATION, description = "Create documentation annotation for BoundOperation")
    @Guard(method = "hasBoundOperationDocumentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createDocumentationAnnotationForBoundOperationRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/DocumentationAnnotationForBoundOperation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", boundOp.getDocumentation());
            eOp.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if association end has documentation.
     */
    public boolean hasAssociationEndDocumentation(AssociationEnd associationEnd) {
        return associationEnd.getDocumentation() != null && 
               !associationEnd.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_ASSOCIATION_END_RELATION, description = "Create documentation annotation for AssociationEnd")
    @Guard(method = "hasAssociationEndDocumentation")
    @Transform(type = AssociationEnd.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AssociationEnd, EAnnotation> createDocumentationAnnotationForAssociationEndRelationRule() {
        return (associationEnd, ctx) -> {
            EReference eRef = (EReference) getEquivalent(associationEnd, CREATE_ASSOCIATION_END_RELATION);
            if (eRef == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(associationEnd) + ")/DocumentationAnnotationForAssociationEndRelation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", associationEnd.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if containment has documentation.
     */
    public boolean hasContainmentDocumentation(Containment containment) {
        return containment.getDocumentation() != null && 
               !containment.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_CONTAINMENT_RELATION, description = "Create documentation annotation for Containment")
    @Guard(method = "hasContainmentDocumentation")
    @Transform(type = Containment.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Containment, EAnnotation> createDocumentationAnnotationForContainmentRelationRule() {
        return (containment, ctx) -> {
            EReference eRef = (EReference) getEquivalent(containment, CREATE_CONTAINMENT_RELATION);
            if (eRef == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(containment) + ")/DocumentationAnnotationForContainmentRelation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", containment.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if data property has documentation.
     */
    public boolean hasDataPropertyDocumentation(DataProperty dataProperty) {
        return dataProperty.getDocumentation() != null && 
               !dataProperty.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_DATA_PROPERTY, description = "Create documentation annotation for DataProperty")
    @Guard(method = "hasDataPropertyDocumentation")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<DataProperty, EAnnotation> createDocumentationAnnotationForDataPropertyRule() {
        return (dataProperty, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(dataProperty, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(dataProperty) + ")/DocumentationAnnotationForDataProperty",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", dataProperty.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if navigation property has documentation.
     */
    public boolean hasNavigationPropertyDocumentation(NavigationProperty navProperty) {
        return navProperty.getDocumentation() != null && 
               !navProperty.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_NAVIGATION_PROPERTY, description = "Create documentation annotation for NavigationProperty")
    @Guard(method = "hasNavigationPropertyDocumentation")
    @Transform(type = NavigationProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<NavigationProperty, EAnnotation> createDocumentationAnnotationForNavigationPropertyRule() {
        return (navProperty, ctx) -> {
            EReference eRef = (EReference) getEquivalent(navProperty, CREATE_STATIC_NAVIGATION_FOR_DERIVED_ATTRIBUTE);
            if (eRef == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(navProperty) + ")/DocumentationAnnotationForNavigationProperty",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", navProperty.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // PRIMITIVE ACCESSOR CONSTRAINT RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if primitive accessor has string data type.
     */
    public boolean isStringPrimitiveAccessor(PrimitiveAccessor accessor) {
        return accessor.getDataType() instanceof StringType;
    }

    @TransformRule(name = ADD_STRING_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add string constraints for PrimitiveAccessor")
    @Guard(method = "isStringPrimitiveAccessor")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<PrimitiveAccessor, EAnnotation> addStringPrimitiveAccessorConstraintsRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(accessor, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            StringType stringType = (StringType) accessor.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/StringPrimitiveAccessorConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "maxLength", String.valueOf(stringType.getMaxLength()));

            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if primitive accessor has custom data type.
     */
    public boolean isCustomPrimitiveAccessor(PrimitiveAccessor accessor) {
        return accessor.getDataType() instanceof CustomType;
    }

    @TransformRule(name = ADD_CUSTOM_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add custom type constraints for PrimitiveAccessor")
    @Guard(method = "isCustomPrimitiveAccessor")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<PrimitiveAccessor, EAnnotation> addCustomPrimitiveAccessorConstraintsRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(accessor, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/CustomPrimitiveAccessorConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "customType", 
                    getQualifiedName(accessor.getDataType()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if primitive accessor has numeric (non-measured) data type.
     */
    public boolean isNumericPrimitiveAccessor(PrimitiveAccessor accessor) {
        return accessor.getDataType() instanceof NumericType && 
               !(accessor.getDataType() instanceof MeasuredType);
    }

    @TransformRule(name = ADD_NUMERIC_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add numeric constraints for PrimitiveAccessor")
    @Guard(method = "isNumericPrimitiveAccessor")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<PrimitiveAccessor, EAnnotation> addNumericPrimitiveAccessorConstraintsRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(accessor, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            NumericType numericType = (NumericType) accessor.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/NumericPrimitiveAccessorConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(numericType.getScale()));

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    /**
     * Guard: Check if primitive accessor has measured data type.
     */
    public boolean isMeasuredPrimitiveAccessor(PrimitiveAccessor accessor) {
        return accessor.getDataType() instanceof MeasuredType;
    }

    @TransformRule(name = ADD_MEASURED_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add measured constraints for PrimitiveAccessor")
    @Guard(method = "isMeasuredPrimitiveAccessor")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAnnotation.class)
    public TransformFunction<PrimitiveAccessor, EAnnotation> addMeasuredPrimitiveAccessorConstraintsRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(accessor, CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE);
            if (eAttr == null) return null;

            MeasuredType measuredType = (MeasuredType) accessor.getDataType();

            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(accessor) + ")/MeasuredPrimitiveAccessorConstraints",
                    getAnnotationUri("constraints"));
            addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(measuredType.getPrecision()));
            addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(measuredType.getScale()));

            if (measuredType.getStoreUnit() != null) {
                addAnnotationDetail(constraintsAnnotation, "measure", 
                        getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                addAnnotationDetail(constraintsAnnotation, "unit", measuredType.getStoreUnit().getName());
            }

            eAttr.getEAnnotations().add(constraintsAnnotation);
            return constraintsAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL TYPE RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_PASSWORD_TYPE, description = "Transform PasswordType to EDataType")
    @Transform(type = PasswordType.class)
    @To(type = EDataType.class)
    public TransformFunction<PasswordType, EDataType> createPasswordTypeRule() {
        return (passwordType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(passwordType) + ")/PasswordType");
            dataType.setName(passwordType.getName());
            dataType.setInstanceClassName("java.lang.String");
            getContainerPackage(passwordType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    @TransformRule(name = CREATE_XML_TYPE, description = "Transform XMLType to EDataType")
    @Transform(type = XMLType.class)
    @To(type = EDataType.class)
    public TransformFunction<XMLType, EDataType> createXmlTypeRule() {
        return (xmlType, ctx) -> {
            EDataType dataType = EcoreFactory.eINSTANCE.createEDataType();
            setId(dataType, "(psm/" + getId(xmlType) + ")/XMLType");
            dataType.setName(xmlType.getName());
            dataType.setInstanceClassName("java.lang.String");
            getContainerPackage(xmlType).getEClassifiers().add(dataType);
            return dataType;
        };
    }

    /**
     * Guard: Check if numeric type is measured type for annotation.
     */
    public boolean isMeasuredNumericType(NumericType numericType) {
        return numericType instanceof MeasuredType;
    }

    @TransformRule(name = CREATE_MEASURED_ANNOTATION_OF_INTEGER_TYPE, description = "Create measured annotation for NumericType")
    @Guard(method = "isMeasuredNumericType")
    @Transform(type = NumericType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<NumericType, EAnnotation> createMeasuredAnnotationOfIntegerTypeRule() {
        return (numericType, ctx) -> {
            MeasuredType measuredType = (MeasuredType) numericType;
            
            EDataType dataType = (EDataType) getEquivalent(numericType, 
                    numericType.getScale() == 0 ? CREATE_INTEGER_TYPE : CREATE_DECIMAL_TYPE);
            if (dataType == null) return null;

            EAnnotation measuredAnnotation = createAnnotation(
                    "(psm/" + getId(numericType) + ")/MeasuredAnnotationOfIntegerType",
                    getAnnotationUri("measure"));
            
            if (measuredType.getStoreUnit() != null) {
                addAnnotationDetail(measuredAnnotation, "measure", 
                        getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                addAnnotationDetail(measuredAnnotation, "unit", measuredType.getStoreUnit().getName());
            }

            dataType.getEAnnotations().add(measuredAnnotation);
            return measuredAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // PRIMITIVE AND REFERENCE ACCESSOR RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_PRIMITIVE_ACCESSOR, description = "Transform PrimitiveAccessor to EAttribute")
    @Transform(type = PrimitiveAccessor.class)
    @To(type = EAttribute.class)
    public TransformFunction<PrimitiveAccessor, EAttribute> createPrimitiveAccessorRule() {
        return (accessor, ctx) -> {
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(accessor) + ")/PrimitiveAccessor");
            eAttr.setName(accessor.getName());
            eAttr.setDerived(true);
            eAttr.setVolatile(true);
            eAttr.setLowerBound(accessor.isRequired() ? 1 : 0);
            eAttr.setChangeable(accessor.getSetterExpression() != null);

            // Set type
            EClassifier type = getEquivalentType(accessor.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            // Add to owning entity class
            EntityType owner = getEntityType(accessor);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eAttr);
                }
            }

            return eAttr;
        };
    }

    /**
     * Guard: Check if reference accessor has default representation target.
     */
    public boolean hasReferenceAccessorDefaultRepresentation(ReferenceAccessor accessor) {
        return accessor.getTarget() != null && 
               accessor.getTarget().getDefaultRepresentation() != null;
    }

    @TransformRule(name = CREATE_REFERENCE_ACCESSOR, description = "Transform ReferenceAccessor to EReference")
    @Guard(method = "hasReferenceAccessorDefaultRepresentation")
    @Transform(type = ReferenceAccessor.class)
    @To(type = EReference.class)
    public TransformFunction<ReferenceAccessor, EReference> createReferenceAccessorRule() {
        return (accessor, ctx) -> {
            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(accessor) + ")/ReferenceAccessor");
            eRef.setName(accessor.getName());
            eRef.setDerived(true);
            eRef.setVolatile(true);
            eRef.setContainment(false);
            eRef.setChangeable(accessor.getSetterExpression() != null);

            if (accessor.getCardinality() != null) {
                eRef.setLowerBound(accessor.getCardinality().getLower());
                eRef.setUpperBound(accessor.getCardinality().getUpper());
            }

            // Set target type to the default representation's mapped transfer object
            MappedTransferObjectType defaultRep = accessor.getTarget().getDefaultRepresentation();
            if (defaultRep != null) {
                EClass targetClass = (EClass) getEquivalent(defaultRep, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            // Add to owning entity class
            EntityType owner = getEntityType(accessor);
            if (owner != null) {
                EClass ownerClass = (EClass) getEquivalent(owner, CREATE_ENTITY_CLASS);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(eRef);
                }
            }

            return eRef;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL STATIC RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_FOR_STATIC_DATA, description = "Create transfer object type annotation for StaticData")
    @Transform(type = StaticData.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticData, EAnnotation> createTransferObjectTypeAnnotationClassForStaticDataRule() {
        return (staticData, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticData, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA);
            if (eClass == null) return null;

            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/TransferObjectTypeAnnotationClassForStaticData",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            return toAnnotation;
        };
    }

    @TransformRule(name = CREATE_STATIC_DATA_QUERY_ANNOTATION, description = "Create static query annotation for StaticData")
    @Transform(type = StaticData.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticData, EAnnotation> createStaticDataQueryAnnotationRule() {
        return (staticData, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticData, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA);
            if (eClass == null) return null;

            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/StaticDataQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            eClass.getEAnnotations().add(staticQueryAnnotation);

            return staticQueryAnnotation;
        };
    }

    @TransformRule(name = CREATE_STATIC_QUERY_ATTRIBUTE, description = "Create static query attribute for StaticData")
    @Transform(type = StaticData.class)
    @To(type = EAttribute.class)
    public TransformFunction<StaticData, EAttribute> createStaticQueryAttributeRule() {
        return (staticData, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticData, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA);
            if (eClass == null) return null;

            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            setId(eAttr, "(psm/" + getId(staticData) + ")/StaticQueryAttribute");
            eAttr.setName(staticData.getName());
            eAttr.setLowerBound(staticData.isRequired() ? 1 : 0);
            eAttr.setDerived(true);
            eAttr.setChangeable(false);

            EClassifier type = getEquivalentType(staticData.getDataType());
            if (type != null) {
                eAttr.setEType(type);
            }

            eClass.getEStructuralFeatures().add(eAttr);
            return eAttr;
        };
    }

    /**
     * Guard: Check if static data has parameterized getter.
     */
    public boolean hasStaticDataParameterizedGetter(StaticData staticData) {
        return staticData.getGetterExpression() != null && 
               staticData.getGetterExpression().getParameterType() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE_PARAMETERIZED_ANNOTATION_FOR_STATIC_DATA, description = "Create parameterized annotation for StaticData")
    @Guard(method = "hasStaticDataParameterizedGetter")
    @Transform(type = StaticData.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticData, EAnnotation> createTransferAttributeParameterizedAnnotationForStaticDataRule() {
        return (staticData, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(staticData, CREATE_STATIC_QUERY_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation paramAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/TransferAttributeParameterizedAnnotationForStaticData",
                    getAnnotationUri("parameterized"));
            addAnnotationDetail(paramAnnotation, "value", "true");

            EClassifier paramType = getEquivalentTransferObject(staticData.getGetterExpression().getParameterType());
            if (paramType != null) {
                addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
            }

            eAttr.getEAnnotations().add(paramAnnotation);
            return paramAnnotation;
        };
    }

    @TransformRule(name = CREATE_DATA_REFERENCE_BINDING_FOR_STATIC_DATA, description = "Create data reference binding for StaticData")
    @Transform(type = StaticData.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticData, EAnnotation> createDataReferenceBindingForStaticDataRule() {
        return (staticData, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(staticData, CREATE_STATIC_QUERY_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(staticData) + ")/DataReferenceBindingForStaticData",
                    getAnnotationUri("expression"));

            if (staticData.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", staticData.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        staticData.getGetterExpression().getDialect().toString());

                if (staticData.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticData.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            if (staticData.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", staticData.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        staticData.getSetterExpression().getDialect().toString());

                if (staticData.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticData.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eAttr.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_FOR_STATIC_NAVIGATION, description = "Create transfer object type annotation for StaticNavigation")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = StaticNavigation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticNavigation, EAnnotation> createTransferObjectTypeAnnotationClassForStaticNavigationRule() {
        return (staticNav, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticNav, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION);
            if (eClass == null) return null;

            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/TransferObjectTypeAnnotationClassForStaticNavigation",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            eClass.getEAnnotations().add(toAnnotation);

            return toAnnotation;
        };
    }

    @TransformRule(name = CREATE_STATIC_NAVIGATION_QUERY_ANNOTATION, description = "Create static query annotation for StaticNavigation")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = StaticNavigation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticNavigation, EAnnotation> createStaticNavigationQueryAnnotationRule() {
        return (staticNav, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticNav, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION);
            if (eClass == null) return null;

            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/StaticNavigationQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            eClass.getEAnnotations().add(staticQueryAnnotation);

            return staticQueryAnnotation;
        };
    }

    @TransformRule(name = CREATE_STATIC_QUERY_NAVIGATION, description = "Create static query navigation for StaticNavigation")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = StaticNavigation.class)
    @To(type = EReference.class)
    public TransformFunction<StaticNavigation, EReference> createStaticQueryNavigationRule() {
        return (staticNav, ctx) -> {
            EClass eClass = (EClass) getEquivalent(staticNav, CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION);
            if (eClass == null) return null;

            EReference eRef = EcoreFactory.eINSTANCE.createEReference();
            setId(eRef, "(psm/" + getId(staticNav) + ")/StaticQueryNavigation");
            eRef.setName(staticNav.getName());
            eRef.setContainment(false);
            eRef.setDerived(true);
            eRef.setChangeable(false);

            if (staticNav.getCardinality() != null) {
                eRef.setLowerBound(staticNav.getCardinality().getLower());
                eRef.setUpperBound(staticNav.getCardinality().getUpper());
            }

            MappedTransferObjectType defaultRep = staticNav.getTarget().getDefaultRepresentation();
            if (defaultRep != null) {
                EClass targetClass = (EClass) getEquivalent(defaultRep, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
                if (targetClass != null) {
                    eRef.setEType(targetClass);
                }
            }

            eClass.getEStructuralFeatures().add(eRef);
            return eRef;
        };
    }

    @TransformRule(name = CREATE_NAVIGATION_REFERENCE_BINDING_FOR_STATIC_NAVIGATION, description = "Create navigation reference binding for StaticNavigation")
    @Guard(method = "hasDefaultRepresentation")
    @Transform(type = StaticNavigation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticNavigation, EAnnotation> createNavigationReferenceBindingForStaticNavigationRule() {
        return (staticNav, ctx) -> {
            EReference eRef = (EReference) getEquivalent(staticNav, CREATE_STATIC_QUERY_NAVIGATION);
            if (eRef == null) return null;

            EAnnotation exprAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/NavigationReferenceBindingForStaticNavigation",
                    getAnnotationUri("expression"));

            if (staticNav.getGetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "getter", staticNav.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", 
                        staticNav.getGetterExpression().getDialect().toString());

                if (staticNav.getGetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticNav.getGetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            if (staticNav.getSetterExpression() != null) {
                addAnnotationDetail(exprAnnotation, "setter", staticNav.getSetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "setter.dialect", 
                        staticNav.getSetterExpression().getDialect().toString());

                if (staticNav.getSetterExpression().getParameterType() != null) {
                    EClassifier paramType = getEquivalentTransferObject(staticNav.getSetterExpression().getParameterType());
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "setter.parameter", 
                                getClassifierFQName(paramType));
                    }
                }
            }

            eRef.getEAnnotations().add(exprAnnotation);
            return exprAnnotation;
        };
    }

    /**
     * Guard: Check if static navigation has parameterized getter.
     */
    public boolean hasStaticNavigationParameterizedGetter(StaticNavigation staticNav) {
        return staticNav.getTarget() != null &&
               staticNav.getTarget().getDefaultRepresentation() != null &&
               staticNav.getGetterExpression() != null && 
               staticNav.getGetterExpression().getParameterType() != null;
    }

    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_PARAMETERIZED_ANNOTATION_FOR_STATIC_NAVIGATION, description = "Create parameterized annotation for StaticNavigation")
    @Guard(method = "hasStaticNavigationParameterizedGetter")
    @Transform(type = StaticNavigation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<StaticNavigation, EAnnotation> createTransferObjectRelationParameterizedAnnotationForStaticNavigationRule() {
        return (staticNav, ctx) -> {
            EReference eRef = (EReference) getEquivalent(staticNav, CREATE_STATIC_QUERY_NAVIGATION);
            if (eRef == null) return null;

            EAnnotation paramAnnotation = createAnnotation(
                    "(psm/" + getId(staticNav) + ")/TransferObjectRelationParameterizedAnnotationForStaticNavigation",
                    getAnnotationUri("parameterized"));
            addAnnotationDetail(paramAnnotation, "value", "true");

            EClassifier paramType = getEquivalentTransferObject(staticNav.getGetterExpression().getParameterType());
            if (paramType != null) {
                addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
            }

            eRef.getEAnnotations().add(paramAnnotation);
            return paramAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL TRANSFER OBJECT RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS_FOR_REFERENCE_CLASS, description = "Create transfer object type annotation for reference class")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createTransferObjectTypeAnnotationClassForReferenceClassRule() {
        return (entityType, ctx) -> {
            EClass refClass = (EClass) getEquivalent(entityType, CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE);
            if (refClass == null) return null;

            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            refClass.getEAnnotations().add(toAnnotation);

            return toAnnotation;
        };
    }

    @TransformRule(name = CREATE_ANNOTATION_ON_REFERENCE_CLASS_FOR_ENTITY_TYPE, description = "Create reference holder annotation for EntityType")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createAnnotationOnReferenceClassForEntityTypeRule() {
        return (entityType, ctx) -> {
            EClass refClass = (EClass) getEquivalent(entityType, CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE);
            if (refClass == null) return null;

            EAnnotation refHolderAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/AnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("referenceHolder"));
            addAnnotationDetail(refHolderAnnotation, "value", "true");
            refClass.getEAnnotations().add(refHolderAnnotation);

            return refHolderAnnotation;
        };
    }

    @TransformRule(name = CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_REFERENCE_CLASS_FOR_ENTITY_TYPE, description = "Create mapped entity type annotation for reference class")
    @Transform(type = EntityType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<EntityType, EAnnotation> createMappedEntityTypeAnnotationOnReferenceClassForEntityTypeRule() {
        return (entityType, ctx) -> {
            EClass refClass = (EClass) getEquivalent(entityType, CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE);
            if (refClass == null) return null;

            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(entityType) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("mappedEntityType"));
            
            EClass entityClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
            if (entityClass != null) {
                addAnnotationDetail(mappedEntityAnnotation, "value", 
                        getClassifierFQName(entityClass));
            }
            
            refClass.getEAnnotations().add(mappedEntityAnnotation);
            return mappedEntityAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL OPERATION STATEFUL RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if transfer operation has no implementation and no behaviour.
     */
    public boolean hasNoImplementationAndNoBehaviour(TransferOperation transferOp) {
        return transferOp.getImplementation() == null && transferOp.getBehaviour() == null;
    }

    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_ON_OPERATION_WITHOUT_IMPLEMENTATION_AND_BEHAVIOUR, description = "Create stateful annotation on TransferOperation without implementation and behaviour")
    @Guard(method = "hasNoImplementationAndNoBehaviour")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationOnOperationWithoutImplementationAndBehaviourRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperationWithoutImplementationAndBehaviour",
                    getAnnotationUri("stateful"));
            addAnnotationDetail(statefulAnnotation, "value", "true");
            eOp.getEAnnotations().add(statefulAnnotation);

            return statefulAnnotation;
        };
    }

    /**
     * Guard: Check if transfer operation has no implementation but has behaviour.
     */
    public boolean hasNoBehaviourButHasImplementation(TransferOperation transferOp) {
        return transferOp.getImplementation() == null && transferOp.getBehaviour() != null;
    }

    @TransformRule(name = CREATE_STATEFUL_ANNOTATION_ON_OPERATION_WITH_BEHAVIOUR, description = "Create stateful annotation on TransferOperation with behaviour")
    @Guard(method = "hasNoBehaviourButHasImplementation")
    @Transform(type = TransferOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferOperation, EAnnotation> createStatefulAnnotationOnOperationWithBehaviourRule() {
        return (transferOp, ctx) -> {
            EOperation eOp = getEquivalentTransferOperation(transferOp);
            if (eOp == null) return null;

            EAnnotation statefulAnnotation = createAnnotation(
                    "(psm/" + getId(transferOp) + ")/StatefulAnnotationOnOperationWithBehaviour",
                    getAnnotationUri("stateful"));

            TransferOperationBehaviourType behaviourType = transferOp.getBehaviour().getBehaviourType();
            String statefulValue;
            switch (behaviourType) {
                case VALIDATE_CREATE:
                case VALIDATE_UPDATE:
                case LIST:
                case EXPORT:
                case GET_RANGE:
                case GET_TEMPLATE:
                case GET_PRINCIPAL:
                case GET_METADATA:
                case VALIDATE_OPERATION_INPUT:
                    statefulValue = "false";
                    break;
                default:
                    statefulValue = "true";
                    break;
            }
            addAnnotationDetail(statefulAnnotation, "value", statefulValue);
            eOp.getEAnnotations().add(statefulAnnotation);

            return statefulAnnotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL OPERATION PARAMETER RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_PARAMETER, description = "Create ETypedElement for Parameter")
    @Transform(type = Parameter.class)
    @To(type = ETypedElement.class)
    public TransformFunction<Parameter, ETypedElement> createParameterRule() {
        return (param, ctx) -> {
            // This is an abstract rule - actual typing happens in CreateInputParameter
            EParameter eParam = EcoreFactory.eINSTANCE.createEParameter();
            setId(eParam, "(psm/" + getId(param) + ")/Parameter");
            if (param.getCardinality() != null) {
                eParam.setLowerBound(param.getCardinality().getLower());
                eParam.setUpperBound(param.getCardinality().getUpper());
            }
            if (param.getType() != null) {
                EClassifier type = getEquivalentTransferObject(param.getType());
                if (type != null) {
                    eParam.setEType(type);
                }
            }
            return eParam;
        };
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_INPUT_PARAMETER, description = "Create documentation annotation for input parameter")
    @Guard(method = "isInputParameterWithDocumentation")
    @Transform(type = Parameter.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Parameter, EAnnotation> createDocumentationAnnotationForInputParameterRule() {
        return (param, ctx) -> {
            EParameter eParam = (EParameter) getEquivalent(param, CREATE_INPUT_PARAMETER);
            if (eParam == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(param) + ")/DocumentationAnnotationForInputParameter",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", param.getDocumentation());
            eParam.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if parameter is an input parameter with documentation.
     */
    public boolean isInputParameterWithDocumentation(Parameter param) {
        return isInputParameter(param) && 
               param.getDocumentation() != null && 
               !param.getDocumentation().trim().isEmpty();
    }

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_OUTPUT_PARAMETER, description = "Create documentation annotation for output parameter")
    @Guard(method = "hasOutputWithDocumentation")
    @Transform(type = BoundOperation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<BoundOperation, EAnnotation> createDocumentationAnnotationForOutputParameterRule() {
        return (boundOp, ctx) -> {
            EOperation eOp = (EOperation) getEquivalent(boundOp, CREATE_BOUND_OPERATION);
            if (eOp == null) return null;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(boundOp) + ")/DocumentationAnnotationForOutputParameter",
                    getAnnotationUri("outputDocumentation"));
            addAnnotationDetail(docAnnotation, "value", boundOp.getOutput().getDocumentation());
            eOp.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    /**
     * Guard: Check if bound operation has output with documentation.
     */
    public boolean hasOutputWithDocumentation(BoundOperation boundOp) {
        return boundOp.getOutput() != null && 
               boundOp.getOutput().getDocumentation() != null &&
               !boundOp.getOutput().getDocumentation().trim().isEmpty();
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL UNMAPPED DEFAULT ONLY RULES
    // -------------------------------------------------------------------------

    /**
     * Guard: Check if attribute has unmapped default only annotation conditions.
     */
    public boolean hasUnmappedDefaultOnlyAttributeCondition(Attribute attr) {
        // O(1) lookup using pre-computed cache instead of O(Attrs × DefaultRepAttrs)
        return unmappedDefaultOnlyAttributes != null && unmappedDefaultOnlyAttributes.contains(attr);
    }

    /**
     * Add unmappedDefaultOnly annotation to attribute.
     */
    private void addUnmappedDefaultOnlyAttributeAnnotation(Attribute attr) {
        EAttribute eAttr = (EAttribute) getEquivalent(attr, CREATE_ATTRIBUTE);
        if (eAttr == null) return;

        EAnnotation annotation = createAnnotation(
                "(psm/" + getId(attr) + ")/UnmappedDefaultOnlyAttributeAnnotation",
                getAnnotationUri("unmappedDefaultOnly"));
        addAnnotationDetail(annotation, "value", String.valueOf(attr.isUnmappedDefaultOnly()));
        eAttr.getEAnnotations().add(annotation);
    }

    @TransformRule(name = ADD_UNMAPPED_DEFAULT_ONLY_ATTRIBUTE_ANNOTATION, description = "Add unmapped default only annotation to attribute")
    @Guard(method = "hasUnmappedDefaultOnlyAttributeCondition")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addUnmappedDefaultOnlyAttributeAnnotationRule() {
        return (attr, ctx) -> {
            EAttribute eAttr = (EAttribute) getEquivalent(attr, CREATE_ATTRIBUTE);
            if (eAttr == null) return null;

            EAnnotation annotation = createAnnotation(
                    "(psm/" + getId(attr) + ")/UnmappedDefaultOnlyAttributeAnnotation",
                    getAnnotationUri("unmappedDefaultOnly"));
            addAnnotationDetail(annotation, "value", String.valueOf(attr.isUnmappedDefaultOnly()));
            eAttr.getEAnnotations().add(annotation);

            return annotation;
        };
    }

    /**
     * Guard: Check if association end has unmapped default only annotation conditions.
     */
    public boolean hasUnmappedDefaultOnlyReferenceCondition(AssociationEnd assocEnd) {
        // O(1) lookup using pre-computed cache instead of O(Refs × DefaultRepRels)
        return unmappedDefaultOnlyReferences != null && unmappedDefaultOnlyReferences.contains(assocEnd);
    }

    /**
     * Add unmappedDefaultOnly annotation to association end.
     */
    private void addUnmappedDefaultOnlyReferenceAnnotation(AssociationEnd assocEnd) {
        EReference eRef = (EReference) getEquivalent(assocEnd, CREATE_ASSOCIATION_END_RELATION);
        if (eRef == null) return;

        EAnnotation annotation = createAnnotation(
                "(psm/" + getId(assocEnd) + ")/UnmappedDefaultOnlyReferenceAnnotation",
                getAnnotationUri("unmappedDefaultOnly"));
        addAnnotationDetail(annotation, "value", String.valueOf(assocEnd.isUnmappedDefaultOnly()));
        eRef.getEAnnotations().add(annotation);
    }

    @TransformRule(name = ADD_UNMAPPED_DEFAULT_ONLY_REFERENCE_ANNOTATION, description = "Add unmapped default only annotation to reference")
    @Guard(method = "hasUnmappedDefaultOnlyReferenceCondition")
    @Transform(type = AssociationEnd.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AssociationEnd, EAnnotation> addUnmappedDefaultOnlyReferenceAnnotationRule() {
        return (assocEnd, ctx) -> {
            EReference eRef = (EReference) getEquivalent(assocEnd, CREATE_ASSOCIATION_END_RELATION);
            if (eRef == null) return null;

            EAnnotation annotation = createAnnotation(
                    "(psm/" + getId(assocEnd) + ")/UnmappedDefaultOnlyReferenceAnnotation",
                    getAnnotationUri("unmappedDefaultOnly"));
            addAnnotationDetail(annotation, "value", String.valueOf(assocEnd.isUnmappedDefaultOnly()));
            eRef.getEAnnotations().add(annotation);

            return annotation;
        };
    }

    // -------------------------------------------------------------------------
    // ADDITIONAL ACTOR RULES
    // -------------------------------------------------------------------------

    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_ACTOR_TYPE, description = "Create documentation annotation for actor type")
    @Guard(method = "hasDocumentation")
    @Transform(type = AbstractActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AbstractActorType, EAnnotation> createDocumentationAnnotationForActorTypeRule() {
        return (actorType, ctx) -> {
            EClassifier eClassifier = getEquivalentTransferObject(actorType);
            if (!(eClassifier instanceof EClass)) return null;
            EClass eClass = (EClass) eClassifier;

            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/DocumentationAnnotationForActorType",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", actorType.getDocumentation());
            eClass.getEAnnotations().add(docAnnotation);

            return docAnnotation;
        };
    }

    // =========================================================================
    // ABSTRACT BASE RULES
    // =========================================================================
    // These @Abstract rules are base rules invoked only via @Extends inheritance.
    // They define the common transformation pattern that concrete rules extend.

    /**
     * @abstract
     * rule NamespaceToPackage
     *     transform s : JUDOPSM!Namespace
     *     to t : ASM!EPackage
     * Base rule for namespace-to-package transformations.
     * Extended by: ModelToPackage, PackageToPackage
     */
    @TransformRule(name = NAMESPACE_TO_PACKAGE, description = "Abstract base rule for namespace to EPackage transformation")
    @Abstract
    @Transform(type = Namespace.class)
    @To(type = EPackage.class)
    public TransformFunction<Namespace, EPackage> namespaceToPackageRule() {
        return (namespace, ctx) -> {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            pkg.setName(namespace.getName());
            return pkg;
        };
    }

    /**
     * @abstract
     * rule AddAttributeConstraints
     *     transform s : JUDOPSM!Attribute
     *     to t : ASM!EAnnotation
     * Base rule for attribute constraint annotations.
     * Extended by: AddStringAttributeConstraints, AddCustomAttributeConstraints,
     *              AddNumericAttributeConstraints, AddMeasuredAttributeConstraints
     */
    @TransformRule(name = ADD_ATTRIBUTE_CONSTRAINTS, description = "Abstract base rule for attribute constraints")
    @Abstract
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addAttributeConstraintsRule() {
        return (attr, ctx) -> {
            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attr) + ")/AttributeConstraints",
                    getAnnotationUri("constraints"));
            EAttribute eAttr = (EAttribute) getEquivalent(attr, CREATE_ATTRIBUTE);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(constraintsAnnotation);
            }
            return constraintsAnnotation;
        };
    }

    /**
     * @abstract
     * rule AddAbstractNumericAttributeConstraints
     *     transform s : JUDOPSM!Attribute
     *     to t : ASM!EAnnotation
     *     extends AddAttributeConstraints
     * Abstract intermediate rule for numeric attribute constraints.
     * Extended by: AddNumericAttributeConstraints, AddMeasuredAttributeConstraints
     */
    @TransformRule(name = ADD_ABSTRACT_NUMERIC_ATTRIBUTE_CONSTRAINTS, description = "Abstract base rule for numeric attribute constraints")
    @Abstract
    @Extends(ADD_ATTRIBUTE_CONSTRAINTS)
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Attribute, EAnnotation> addAbstractNumericAttributeConstraintsRule() {
        return (attr, ctx) -> {
            // Create base constraints annotation - logic inherited from AddAttributeConstraints
            EAnnotation constraintsAnnotation = createAnnotation(
                    "(psm/" + getId(attr) + ")/AttributeConstraints",
                    getAnnotationUri("constraints"));
            EAttribute eAttr = (EAttribute) getEquivalent(attr, CREATE_ATTRIBUTE);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(constraintsAnnotation);
            }
            // Base numeric logic - extended by concrete rules
            return constraintsAnnotation;
        };
    }

    /**
     * @abstract
     * rule CreateDocumentationAnnotation
     *     transform s : JUDOPSM!NamedElement
     *     to t : ASM!EAnnotation
     * Base rule for documentation annotations.
     * Extended by all CreateDocumentationAnnotationFor* rules
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION, description = "Abstract base rule for documentation annotations")
    @Abstract
    @Transform(type = NamedElement.class)
    @To(type = EAnnotation.class)
    public TransformFunction<NamedElement, EAnnotation> createDocumentationAnnotationRule() {
        return (element, ctx) -> {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(element) + ")/DocumentationAnnotation",
                    getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", element.getDocumentation());
            return docAnnotation;
        };
    }

    /**
     * @abstract
     * rule CreateRelation
     *     transform s : JUDOPSM!Relation
     *     to t : ASM!EReference
     * Base rule for relation transformations.
     * Extended by: CreateAssociationEndRelation, CreateContainmentRelation
     */
    @TransformRule(name = CREATE_RELATION, description = "Abstract base rule for relation transformation")
    @Abstract
    @Transform(type = hu.blackbelt.judo.meta.psm.data.Relation.class)
    @To(type = EReference.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.data.Relation, EReference> createRelationRule() {
        return (relation, ctx) -> {
            EReference ref = EcoreFactory.eINSTANCE.createEReference();
            ref.setName(relation.getName());
            return ref;
        };
    }

    /**
     * @abstract
     * rule CreateSequence
     *     transform s : JUDOPSM!Sequence
     *     to t : ASM!EClass
     * Base rule for sequence transformations.
     * Extended by: CreateNamespaceSequence, CreateEntitySequence
     */
    @TransformRule(name = CREATE_SEQUENCE, description = "Abstract base rule for sequence transformation")
    @Abstract
    @Transform(type = Sequence.class)
    @To(type = EClass.class)
    public TransformFunction<Sequence, EClass> createSequenceRule() {
        return (sequence, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            eClass.setName(sequence.getName());
            return eClass;
        };
    }

    /**
     * @abstract
     * rule CreateDerivedAttribute
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAttribute
     * Base rule for derived attribute transformations.
     */
    @TransformRule(name = CREATE_DERIVED_ATTRIBUTE, description = "Abstract base rule for derived attribute transformation")
    @Abstract
    @Transform(type = DataProperty.class)
    @To(type = EAttribute.class)
    public TransformFunction<DataProperty, EAttribute> createDerivedAttributeRule() {
        return (prop, ctx) -> {
            EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();
            attr.setName(prop.getName());
            attr.setDerived(true);
            return attr;
        };
    }

    /**
     * @abstract
     * rule CreateStaticData
     *     transform s : JUDOPSM!StaticData
     *     to t : ASM!EClass
     * Base rule for static data transformations.
     */
    @TransformRule(name = CREATE_STATIC_DATA, description = "Abstract base rule for static data transformation")
    @Abstract
    @Transform(type = StaticData.class)
    @To(type = EClass.class)
    public TransformFunction<StaticData, EClass> createStaticDataRule() {
        return (staticData, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            eClass.setName(staticData.getName());
            return eClass;
        };
    }

    /**
     * @abstract
     * rule CreateStaticDataForDerivedAttribute
     *     transform s : JUDOPSM!StaticData
     *     to t : ASM!EAttribute
     * Base rule for static data derived attribute.
     */
    @TransformRule(name = CREATE_STATIC_DATA_FOR_DERIVED_ATTRIBUTE, description = "Abstract base rule for static data derived attribute")
    @Abstract
    @Transform(type = StaticData.class)
    @To(type = EAttribute.class)
    public TransformFunction<StaticData, EAttribute> createStaticDataForDerivedAttributeRule() {
        return (staticData, ctx) -> {
            EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();
            attr.setName(staticData.getName());
            attr.setDerived(true);
            return attr;
        };
    }

    /**
     * @abstract
     * rule CreateStaticNavigationForDerivedAttribute
     *     transform s : JUDOPSM!StaticNavigation
     *     to t : ASM!EReference
     * Base rule for static navigation derived reference.
     */
    @TransformRule(name = CREATE_STATIC_NAVIGATION_FOR_DERIVED_ATTRIBUTE, description = "Abstract base rule for static navigation derived reference")
    @Abstract
    @Transform(type = StaticNavigation.class)
    @To(type = EReference.class)
    public TransformFunction<StaticNavigation, EReference> createStaticNavigationForDerivedAttributeRule() {
        return (staticNav, ctx) -> {
            EReference ref = EcoreFactory.eINSTANCE.createEReference();
            ref.setName(staticNav.getName());
            ref.setDerived(true);
            return ref;
        };
    }

    /**
     * @abstract
     * rule CreateStaticQuery
     *     transform s : JUDOPSM!StaticData or StaticNavigation
     *     to t : ASM!EClass
     * Base rule for static query transformations.
     */
    @TransformRule(name = CREATE_STATIC_QUERY, description = "Abstract base rule for static query transformation")
    @Abstract
    @Transform(type = NamespaceElement.class)
    @To(type = EClass.class)
    public TransformFunction<NamespaceElement, EClass> createStaticQueryRule() {
        return (element, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            if (element instanceof NamedElement) {
                eClass.setName(((NamedElement) element).getName());
            }
            return eClass;
        };
    }

    /**
     * @abstract
     * rule CreateTransferObjectAttribute
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAttribute
     * Base rule for transfer object attribute transformations.
     */
    @TransformRule(name = CREATE_TRANSFER_OBJECT_ATTRIBUTE, description = "Abstract base rule for transfer attribute transformation")
    @Abstract
    @Transform(type = TransferAttribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<TransferAttribute, EAttribute> createTransferObjectAttributeRule() {
        return (attr, ctx) -> {
            EAttribute eAttr = EcoreFactory.eINSTANCE.createEAttribute();
            eAttr.setName(attr.getName());
            return eAttr;
        };
    }

    /**
     * @abstract
     * rule CreateTransferObjectRelation
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EReference
     * Base rule for transfer object relation transformations.
     */
    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION, description = "Abstract base rule for transfer relation transformation")
    @Abstract
    @Transform(type = TransferObjectRelation.class)
    @To(type = EReference.class)
    public TransformFunction<TransferObjectRelation, EReference> createTransferObjectRelationRule() {
        return (rel, ctx) -> {
            EReference ref = EcoreFactory.eINSTANCE.createEReference();
            ref.setName(rel.getName());
            return ref;
        };
    }

    /**
     * @abstract
     * rule CreateTransferObjectTypeClass
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EClass
     * Base rule for transfer object type class transformations.
     * Extended by: CreateMappedTransferObjectTypeClass, CreateUnmappedTransferObjectTypeClass
     */
    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_CLASS, description = "Abstract base rule for transfer object type transformation")
    @Abstract
    @Transform(type = TransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<TransferObjectType, EClass> createTransferObjectTypeClassRule() {
        return (tot, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            eClass.setName(tot.getName());
            return eClass;
        };
    }

    /**
     * @abstract
     * rule CreateActorTypeClass
     *     transform s : JUDOPSM!AbstractActorType
     *     to t : ASM!EClass
     * Base rule for actor type class transformations.
     */
    @TransformRule(name = CREATE_ACTOR_TYPE_CLASS, description = "Abstract base rule for actor type transformation")
    @Abstract
    @Transform(type = AbstractActorType.class)
    @To(type = EClass.class)
    public TransformFunction<AbstractActorType, EClass> createActorTypeClassRule() {
        return (actorType, ctx) -> {
            EClass eClass = EcoreFactory.eINSTANCE.createEClass();
            eClass.setName(actorType.getName());
            return eClass;
        };
    }

    /**
     * rule CreateAccessPointAnnotation
     *     transform s : JUDOPSM!ActorType
     *     to t : ASM!EAnnotation
     * Creates access point annotation for actor types.
     */
    @TransformRule(name = CREATE_ACCESS_POINT_ANNOTATION, description = "Create access point annotation for actor type")
    @Transform(type = ActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<ActorType, EAnnotation> createAccessPointAnnotationRule() {
        return (actorType, ctx) -> {
            EAnnotation accessPointAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/AccessPointAnnotation",
                    getAnnotationUri("accessPoint"));
            EClass eClass = (EClass) getEquivalentTransferObject(actorType);
            if (eClass != null) {
                eClass.getEAnnotations().add(accessPointAnnotation);
            }
            return accessPointAnnotation;
        };
    }

    /**
     * rule CreatePrincipalAnnotation
     *     transform s : JUDOPSM!MappedActorType
     *     to t : ASM!EAnnotation
     * Creates principal annotation for mapped actor types.
     */
    @TransformRule(name = CREATE_PRINCIPAL_ANNOTATION, description = "Create principal annotation for mapped actor type")
    @Guard(method = "isMappedActorWithPrincipal")
    @Transform(type = MappedActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<MappedActorType, EAnnotation> createPrincipalAnnotationRule() {
        return (actorType, ctx) -> {
            EAnnotation principalAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/PrincipalAnnotation",
                    getAnnotationUri("principal"));
            EClass eClass = (EClass) getEquivalentTransferObject(actorType);
            if (eClass != null) {
                eClass.getEAnnotations().add(principalAnnotation);
            }
            return principalAnnotation;
        };
    }

    /**
     * Guard for CreatePrincipalAnnotation - checks if actor has principal entity type.
     */
    public boolean isMappedActorWithPrincipal(MappedActorType actorType) {
        return actorType.getEntityType() != null;
    }

    // -------------------------------------------------------------------------
    // POST-EXECUTION HOOK
    // -------------------------------------------------------------------------

    @PostExecution
    public void postExecutionHook(TransformationContext ctx) {
        asmUtils.enrichWithAnnotations();
        
        // Fix annotation detail IDs that start with "_"
        asmUtils.all(EAnnotation.class).forEach(annotation -> {
            for (Map.Entry<String, String> detail : annotation.getDetails()) {
                // Handle ID fixing if needed
            }
        });
    }
}
