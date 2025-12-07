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
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.data.OperationBody;
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
import hu.blackbelt.judo.meta.psm.service.UnboundOperation;
import hu.blackbelt.judo.meta.psm.service.UnmappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.type.*;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

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
        this.nsPrefix = nsPrefix != null ? nsPrefix : "runtime";
    }

    /**
     * Helper method to get all elements of a given type from the PSM model.
     */
    private <T> Stream<T> all(Class<T> clazz) {
        return psmUtils.all(psmResourceSet, clazz);
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting PSM to ASM Zeta transformation for model: {}", modelName);
        long startTime = System.currentTimeMillis();

        // Phase 1: Transform namespaces/packages
        transformNamespaces();

        // Phase 2: Transform types
        transformTypes();

        // Phase 3: Transform entities and their members
        transformEntities();

        // Phase 4: Transform derived properties
        transformDerivedProperties();

        // Phase 5: Transform operations
        transformOperations();

        // Phase 6: Transform transfer objects
        transformTransferObjects();

        // Phase 7: Transform actors
        transformActors();

        // Phase 8: Post-processing
        postProcess();

        long duration = System.currentTimeMillis() - startTime;
        log.info("PSM to ASM Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
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
                    asmUtils.getAnnotationUri("ModelVersion"));
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
        if (numericType instanceof MeasuredType) {
            // MeasuredType is handled separately
            return;
        }

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
                asmUtils.getAnnotationUri("constraints"));

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
                asmUtils.getAnnotationUri("entity"));
        addAnnotationDetail(entityAnnotation, "value", "true");
        eClass.getEAnnotations().add(entityAnnotation);
        addTrace(entityType, CREATE_ENTITY_ANNOTATION_CLASS, entityAnnotation);

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
                    asmUtils.getAnnotationUri("identifier"));
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
                    asmUtils.getAnnotationUri("reverseCascadeDelete"));
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
                asmUtils.getAnnotationUri("constraints"));

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
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                psmUtils.namespaceElementToString(
                                        (NamespaceElement) 
                                        measuredType.getStoreUnit().eContainer()).replace("::", "."));
                    }
                }
            }
        } else if (dataType instanceof CustomType && !(dataType instanceof NumericType) 
                && !(dataType instanceof BooleanType) && !(dataType instanceof EnumerationType)
                && !(dataType instanceof StringType)) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    psmUtils.namespaceElementToString(dataType).replace("::", "."));
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
                asmUtils.getAnnotationUri("expression"));

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
                        asmUtils.getClassifierFQName((EClassifier) paramType));
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
                asmUtils.getAnnotationUri("expression"));

        addAnnotationDetail(exprAnnotation, "getter", 
                accessor.getGetterExpression().getExpression());
        addAnnotationDetail(exprAnnotation, "getter.dialect", 
                accessor.getGetterExpression().getDialect().toString());

        if (accessor.getGetterExpression().getParameterType() != null) {
            EObject paramType = getEquivalentTransferObject(accessor.getGetterExpression().getParameterType());
            if (paramType instanceof EClassifier) {
                addAnnotationDetail(exprAnnotation, "getter.parameter", 
                        asmUtils.getClassifierFQName((EClassifier) paramType));
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
    }

    private void transformBoundOperation(BoundOperation boundOp) {
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

        addTrace(boundOp, CREATE_BOUND_OPERATION, eOp);

        // Transform input parameter (single parameter, not a list)
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

        // Add to containing namespace
        Namespace ns = (Namespace) unboundOp.eContainer();
        if (ns != null) {
            EPackage pkg = (EPackage) getEquivalent(ns, 
                    ns instanceof Model ? MODEL_TO_PACKAGE : PACKAGE_TO_PACKAGE);
            if (pkg != null) {
                // Unbound operations go to a class in the package
                // For now, add to package annotation or create operation holder
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

        // Add custom implementation annotation
        if (unboundOp.getImplementation() != null) {
            EAnnotation customImplAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/CustomImplementationAnnotationOnUnboundOperation",
                    asmUtils.getAnnotationUri("customImplementation"));
            addAnnotationDetail(customImplAnnotation, "value", 
                    String.valueOf(unboundOp.getImplementation().isCustomImplementation()));
            eOp.getEAnnotations().add(customImplAnnotation);
        }

        // Add initializer annotation
        if (unboundOp.isInitializer()) {
            EAnnotation initAnnotation = createAnnotation(
                    "(psm/" + getId(unboundOp) + ")/InitializerAnnotation",
                    asmUtils.getAnnotationUri("initializer"));
            addAnnotationDetail(initAnnotation, "value", "true");
            eOp.getEAnnotations().add(initAnnotation);
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
                    asmUtils.getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", param.getDocumentation());
            eParam.getEAnnotations().add(docAnnotation);
        }
    }

    private void addBoundAnnotation(OperationDeclaration op, EOperation eOp) {
        EAnnotation boundAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/BoundOperationAnnotation",
                asmUtils.getAnnotationUri("bound"));
        boolean isBound = op instanceof BoundOperation || op instanceof BoundTransferOperation;
        addAnnotationDetail(boundAnnotation, "value", String.valueOf(isBound));
        eOp.getEAnnotations().add(boundAnnotation);
    }

    private void addInstanceRepresentationAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation instRepAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/InstanceRepresentationOfBoundOperation",
                asmUtils.getAnnotationUri("instanceRepresentation"));
        EClassifier instRep = getEquivalentTransferObject(boundOp.getInstanceRepresentation());
        if (instRep != null) {
            addAnnotationDetail(instRepAnnotation, "value", asmUtils.getClassifierFQName(instRep));
        }
        eOp.getEAnnotations().add(instRepAnnotation);
    }

    private void addScriptBodyAnnotation(BoundOperation op, EOperation eOp) {
        EAnnotation scriptAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/ScriptBodyAnnotation",
                asmUtils.getAnnotationUri("script"));
        addAnnotationDetail(scriptAnnotation, "body", op.getImplementation().getBody());
        eOp.getEAnnotations().add(scriptAnnotation);
    }

    private void addScriptBodyAnnotation(UnboundOperation op, EOperation eOp) {
        EAnnotation scriptAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/ScriptBodyAnnotation",
                asmUtils.getAnnotationUri("script"));
        addAnnotationDetail(scriptAnnotation, "body", op.getImplementation().getBody());
        eOp.getEAnnotations().add(scriptAnnotation);
    }

    private void addCustomImplementationAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation customImplAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/CustomImplementationAnnotationOnBoundOperation",
                asmUtils.getAnnotationUri("customImplementation"));
        addAnnotationDetail(customImplAnnotation, "value", 
                String.valueOf(boundOp.getImplementation().isCustomImplementation()));
        eOp.getEAnnotations().add(customImplAnnotation);
    }

    private void addAbstractAnnotation(BoundOperation boundOp, EOperation eOp) {
        EAnnotation abstractAnnotation = createAnnotation(
                "(psm/" + getId(boundOp) + ")/AbstractAnnotationForBoundOperation",
                asmUtils.getAnnotationUri("abstract"));
        addAnnotationDetail(abstractAnnotation, "value", String.valueOf(boundOp.isAbstract()));
        eOp.getEAnnotations().add(abstractAnnotation);
    }

    private void addOutputParameterNameAnnotation(OperationDeclaration op, EOperation eOp) {
        if (op.getOutput() == null) return;
        
        EAnnotation outputAnnotation = createAnnotation(
                "(psm/" + getId(op) + ")/OutputParameterName",
                asmUtils.getAnnotationUri("outputParameterName"));
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
        all(EntityType.class).forEach(this::createReferenceClassForEntityType);
        
        // Third pass: transform attributes and relations
        all(TransferAttribute.class).forEach(this::transformTransferAttribute);
        all(TransferObjectRelation.class).forEach(this::transformTransferObjectRelation);
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
                    asmUtils.getAnnotationUri("mappedEntityType"));
            
            EClass entityClass = (EClass) getEquivalent(mappedTO.getEntityType(), CREATE_ENTITY_CLASS);
            if (entityClass != null) {
                addAnnotationDetail(mappedEntityAnnotation, "value", 
                        asmUtils.getClassifierFQName(entityClass));
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
                asmUtils.getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add query customizer annotation if applicable
        if (to.isQueryCustomizer()) {
            EAnnotation qcAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/QueryCustomizerAnnotationForQueryCustomizerClass",
                    asmUtils.getAnnotationUri("queryCustomizer"));
            addAnnotationDetail(qcAnnotation, "value", "true");
            eClass.getEAnnotations().add(qcAnnotation);
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
                asmUtils.getAnnotationUri("referenceHolder"));
        addAnnotationDetail(refHolderAnnotation, "value", "true");
        eClass.getEAnnotations().add(refHolderAnnotation);

        // Add transfer object type annotation
        EAnnotation toAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                asmUtils.getAnnotationUri("transferObjectType"));
        addAnnotationDetail(toAnnotation, "value", "true");
        eClass.getEAnnotations().add(toAnnotation);

        // Add mapped entity type annotation
        EAnnotation mappedEntityAnnotation = createAnnotation(
                "(psm/" + getId(entityType) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                asmUtils.getAnnotationUri("mappedEntityType"));
        EClass entityClass = (EClass) getEquivalent(entityType, CREATE_ENTITY_CLASS);
        if (entityClass != null) {
            addAnnotationDetail(mappedEntityAnnotation, "value", 
                    asmUtils.getClassifierFQName(entityClass));
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

        // Add constraints annotation
        addTransferAttributeConstraints(transferAttr, eAttr);

        // Add binding annotation
        if (transferAttr.getBinding() != null && !(transferAttr.getBinding() instanceof StaticData)) {
            EAnnotation bindingAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferObjectAttributeBindingAnnotation",
                    asmUtils.getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferAttr.getBinding().getName());
            eAttr.getEAnnotations().add(bindingAnnotation);
        }

        // Add transient annotation if no binding
        if (transferAttr.getBinding() == null) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransientAnnotationToTransferAttribute",
                    asmUtils.getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eAttr.getEAnnotations().add(transientAnnotation);
        }

        // Add claim annotation if applicable
        if (transferAttr.getClaimType() != null) {
            EAnnotation claimAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/TransferAttributeClaimAnnotation",
                    asmUtils.getAnnotationUri("claim"));
            addAnnotationDetail(claimAnnotation, "value", transferAttr.getClaimType());
            eAttr.getEAnnotations().add(claimAnnotation);
        }

        // Add default annotation if applicable
        if (transferAttr.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DefaultAnnotationToTransferAttribute",
                    asmUtils.getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferAttr.getDefaultValue().getName());
            eAttr.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferAttr.getDocumentation() != null && 
            !transferAttr.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferAttr) + ")/DocumentationAnnotationForTransferAttribute",
                    asmUtils.getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferAttr.getDocumentation());
            eAttr.getEAnnotations().add(docAnnotation);
        }
    }

    private void addTransferAttributeConstraints(TransferAttribute transferAttr, EAttribute eAttr) {
        Primitive dataType = transferAttr.getDataType();
        if (dataType == null) return;

        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(transferAttr) + ")/TransferAttributeConstraints",
                asmUtils.getAnnotationUri("constraints"));

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
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                psmUtils.namespaceElementToString(
                                        (NamespaceElement) 
                                        measuredType.getStoreUnit().eContainer()).replace("::", "."));
                    }
                }
            }
        } else if (dataType instanceof CustomType && !(dataType instanceof NumericType) 
                && !(dataType instanceof BooleanType) && !(dataType instanceof EnumerationType)
                && !(dataType instanceof StringType)) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    psmUtils.namespaceElementToString(dataType).replace("::", "."));
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
                    asmUtils.getAnnotationUri("binding"));
            addAnnotationDetail(bindingAnnotation, "value", transferRel.getBinding().getName());
            eRef.getEAnnotations().add(bindingAnnotation);
        }

        // Add transient annotation if no binding and not access
        if (transferRel.getBinding() == null && !transferRel.isAccess()) {
            EAnnotation transientAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransientAnnotationToTransferObjectRelation",
                    asmUtils.getAnnotationUri("transient"));
            addAnnotationDetail(transientAnnotation, "value", "true");
            eRef.getEAnnotations().add(transientAnnotation);
        }

        // Add access annotation if applicable
        if (transferRel.isAccess()) {
            EAnnotation accessAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationAccessAnnotation",
                    asmUtils.getAnnotationUri("access"));
            addAnnotationDetail(accessAnnotation, "value", "true");
            eRef.getEAnnotations().add(accessAnnotation);
        }

        // Add embedded flags annotation if embedded
        if (transferRel.isEmbedded()) {
            EAnnotation embeddedAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationEmbeddedFlags",
                    asmUtils.getAnnotationUri("embedded"));
            addAnnotationDetail(embeddedAnnotation, "value", "true");
            addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
            addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
            addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
            eRef.getEAnnotations().add(embeddedAnnotation);
        }

        // Add permissions annotation
        EAnnotation permissionsAnnotation = createAnnotation(
                "(psm/" + getId(transferRel) + ")/TransferObjectRelationPermissions",
                asmUtils.getAnnotationUri("permissions"));
        addAnnotationDetail(permissionsAnnotation, "create", String.valueOf(transferRel.isEmbeddedCreate()));
        addAnnotationDetail(permissionsAnnotation, "update", String.valueOf(transferRel.isEmbeddedUpdate()));
        addAnnotationDetail(permissionsAnnotation, "delete", String.valueOf(transferRel.isEmbeddedDelete()));
        eRef.getEAnnotations().add(permissionsAnnotation);

        // Add range annotation if applicable
        if (transferRel.getRange() != null) {
            EAnnotation rangeAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/TransferObjectRelationRangeAnnotation",
                    asmUtils.getAnnotationUri("range"));
            addAnnotationDetail(rangeAnnotation, "value", transferRel.getRange().getName());
            eRef.getEAnnotations().add(rangeAnnotation);
        }

        // Add default annotation if applicable
        if (transferRel.getDefaultValue() != null) {
            EAnnotation defaultAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DefaultAnnotationToTransferObjectRelation",
                    asmUtils.getAnnotationUri("default"));
            addAnnotationDetail(defaultAnnotation, "value", transferRel.getDefaultValue().getName());
            eRef.getEAnnotations().add(defaultAnnotation);
        }

        // Add documentation if present
        if (transferRel.getDocumentation() != null && 
            !transferRel.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(transferRel) + ")/DocumentationAnnotationForTransferObjectRelation",
                    asmUtils.getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", transferRel.getDocumentation());
            eRef.getEAnnotations().add(docAnnotation);
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
                asmUtils.getAnnotationUri("actor"));
        
        addAnnotationDetail(actorAnnotation, "name", 
                psmUtils.namespaceElementToString((NamespaceElement) actorType));
        
        if (actorType.getRealm() != null) {
            addAnnotationDetail(actorAnnotation, "realm", actorType.getRealm());
        }

        eClass.getEAnnotations().add(actorAnnotation);
    }

    private void addActorTypeAnnotation(AbstractActorType actorType) {
        // Find the transfer object for this actor type
        TransferObjectType to = null;
        if (actorType instanceof MappedActorType) {
            to = (TransferObjectType) actorType;
        } else if (actorType instanceof ActorType) {
            to = ((ActorType) actorType).getTransferObjectType();
        }
        
        if (to == null) return;
        
        EClass eClass = getEquivalentTransferObjectClass(to);
        if (eClass == null) return;

        EAnnotation actorTypeAnnotation = createAnnotation(
                "(psm/" + getId(to) + ")/ActorTypeAnnotation",
                asmUtils.getAnnotationUri("actorType"));
        addAnnotationDetail(actorTypeAnnotation, "value", "true");

        if (actorType instanceof MappedActorType) {
            MappedActorType mappedActor = (MappedActorType) actorType;
            addAnnotationDetail(actorTypeAnnotation, "managed", String.valueOf(mappedActor.isManaged()));
        }

        if (actorType.getKind() != null) {
            addAnnotationDetail(actorTypeAnnotation, "kind", actorType.getKind().toString());
        }

        eClass.getEAnnotations().add(actorTypeAnnotation);

        // Add realm annotation if applicable
        if (actorType.getRealm() != null) {
            EAnnotation realmAnnotation = createAnnotation(
                    "(psm/" + getId(to) + ")/RealmTypeAnnotation",
                    asmUtils.getAnnotationUri("realm"));
            addAnnotationDetail(realmAnnotation, "value", actorType.getRealm());
            eClass.getEAnnotations().add(realmAnnotation);
        }

        // Add documentation if present
        if (actorType.getDocumentation() != null && 
            !actorType.getDocumentation().trim().isEmpty()) {
            EAnnotation docAnnotation = createAnnotation(
                    "(psm/" + getId(actorType) + ")/DocumentationAnnotationForActorType",
                    asmUtils.getAnnotationUri("documentation"));
            addAnnotationDetail(docAnnotation, "value", actorType.getDocumentation());
            eClass.getEAnnotations().add(docAnnotation);
        }
    }

    // =========================================================================
    // POST-PROCESSING
    // =========================================================================

    private void postProcess() {
        asmUtils.enrichWithAnnotations();

        // Fix annotation detail IDs that start with "_"
        asmUtils.all(EAnnotation.class).forEach(annotation -> {
            for (Map.Entry<String, String> detail : annotation.getDetails()) {
                // The detail entries are EStringToStringMapEntry objects
                // We need to handle ID fixing here if needed
            }
        });
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void addAttributeConstraints(Attribute attribute, EAttribute eAttr) {
        if (attribute.getDataType() == null) return;

        EAnnotation constraintsAnnotation = createAnnotation(
                "(psm/" + getId(attribute) + ")/AttributeConstraints",
                asmUtils.getAnnotationUri("constraints"));

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
                    addAnnotationDetail(constraintsAnnotation, "unit", 
                            measuredType.getStoreUnit().getName());
                    if (measuredType.getStoreUnit().eContainer() != null) {
                        addAnnotationDetail(constraintsAnnotation, "measure",
                                psmUtils.namespaceElementToString(
                                        (NamespaceElement) 
                                        measuredType.getStoreUnit().eContainer()).replace("::", "."));
                    }
                }
            }
        } else if (attribute.getDataType() instanceof CustomType) {
            addAnnotationDetail(constraintsAnnotation, "customType",
                    psmUtils.namespaceElementToString(attribute.getDataType()).replace("::", "."));
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

    private void addAnnotationDetail(EAnnotation annotation, String key, String value) {
        annotation.getDetails().put(key, value);
    }

    private EAnnotation createDocumentationAnnotation(hu.blackbelt.judo.meta.psm.namespace.NamedElement element) {
        EAnnotation docAnnotation = createAnnotation(
                "(psm/" + getId(element) + ")/DocumentationAnnotation",
                asmUtils.getAnnotationUri("documentation"));
        addAnnotationDetail(docAnnotation, "value", element.getDocumentation());
        return docAnnotation;
    }

    private EPackage getContainerPackage(EObject element) {
        EObject container = element.eContainer();
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
    }

    private EClassifier getEquivalentType(hu.blackbelt.judo.meta.psm.type.Primitive type) {
        if (type == null) return null;

        // Check different type rule names based on type class
        String[] ruleNames = {
                CREATE_ENUMERATION, CREATE_STRING_TYPE, CREATE_INTEGER_TYPE,
                CREATE_DECIMAL_TYPE, CREATE_BOOLEAN_TYPE, CREATE_DATE_TYPE,
                CREATE_TIMESTAMP_TYPE, CREATE_TIME_TYPE, CREATE_BINARY_TYPE,
                CREATE_CUSTOM_TYPE
        };

        for (String ruleName : ruleNames) {
            EObject equivalent = getEquivalent(type, ruleName);
            if (equivalent instanceof EClassifier) {
                return (EClassifier) equivalent;
            }
        }
        return null;
    }

    private EntityType getEntityType(EObject element) {
        EObject container = element.eContainer();
        while (container != null) {
            if (container instanceof EntityType) {
                return (EntityType) container;
            }
            container = container.eContainer();
        }
        return null;
    }

    private boolean isPrimitive(Attribute attribute) {
        return attribute.getDataType() != null && 
               attribute.getDataType() instanceof hu.blackbelt.judo.meta.psm.type.Primitive;
    }

    private EClassifier getEquivalentTransferObject(TransferObjectType to) {
        if (to == null) return null;
        
        EObject result = getEquivalent(to, CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS);
        if (result == null) {
            result = getEquivalent(to, CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS);
        }
        if (result == null) {
            result = getEquivalent(to, CREATE_TRANSFER_OBJECT_TYPE_CLASS);
        }
        return result instanceof EClassifier ? (EClassifier) result : null;
    }

    private EClass getEquivalentTransferObjectClass(TransferObjectType to) {
        EClassifier classifier = getEquivalentTransferObject(to);
        return classifier instanceof EClass ? (EClass) classifier : null;
    }

    private String getId(EObject element) {
        if (element instanceof EModelElement) {
            for (EAnnotation ann : ((EModelElement) element).getEAnnotations()) {
                if ("http://blackbelt.hu/judo/meta/ExtendedMetadata/id".equals(ann.getSource())) {
                    return ann.getDetails().get("value");
                }
            }
        }
        // Try to get ID from PSM model if available
        if (element instanceof NamespaceElement) {
            NamespaceElement named = (NamespaceElement) element;
            return psmUtils.namespaceElementToString(named).replace("::", "_");
        }
        // Fall back to hash code if no ID annotation
        return String.valueOf(System.identityHashCode(element));
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
        // Use extended metadata to set ID
        if (element instanceof EModelElement) {
            EAnnotation idAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
            idAnnotation.setSource("http://blackbelt.hu/judo/meta/ExtendedMetadata/id");
            idAnnotation.getDetails().put("value", id);
            ((EModelElement) element).getEAnnotations().add(idAnnotation);
        }
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
