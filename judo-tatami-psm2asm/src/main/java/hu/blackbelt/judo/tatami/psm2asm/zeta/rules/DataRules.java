package hu.blackbelt.judo.tatami.psm2asm.zeta.rules;

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

import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.service.MappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.type.*;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;

/**
 * Data transformation rules from data.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateEntityClass - transforms EntityType to EClass (with inline annotations)</li>
 *   <li>CreateAttribute - transforms Attribute to EAttribute (with inline annotations)</li>
 *   <li>CreateAssociationEndRelation - transforms AssociationEnd to EReference</li>
 *   <li>CreateContainmentRelation - transforms Containment to EReference with containment=true</li>
 * </ul>
 * 
 * Note: Annotations are created inline within main rules to avoid recursive update issues
 * that would occur if separate annotation rules called ctx.equivalent(s, targetType).
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = EntityType.class, target = EClass.class)
public class DataRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public DataRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: attribute has a primitive data type
     */
    public boolean isPrimitiveAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof Attribute) {
            Attribute attr = (Attribute) source;
            return attr.getDataType() != null && attr.getDataType() instanceof Primitive;
        }
        return false;
    }

    // =========================================================================
    // ENTITY RULES
    // =========================================================================

    /**
     * rule CreateEntityClass
     *     transform s : JUDOPSM!EntityType
     *     to t : ASM!EClass
     * 
     * Note: All entity annotations are created inline to avoid recursive update issues.
     */
    @TransformRule(name = "CreateEntityClass", description = "Transform EntityType to EClass")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createEntityClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            setId(t, "(psm/" + getId(s) + ")/EntityClass");
            t.setName(s.getName());
            t.setAbstract(s.isAbstract());
            
            // Add entity annotation inline
            EAnnotation entityAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/EntityAnnotationClass",
                    getAnnotationUri("entity"));
            addAnnotationDetail(entityAnnotation, "value", "true");
            t.getEAnnotations().add(entityAnnotation);
            
            // Add default representation annotation inline if applicable
            if (s.getDefaultRepresentation() != null) {
                EAnnotation defaultRepAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/EntityDefaultRepresentationAnnotation",
                        getAnnotationUri("defaultRepresentation"));
                addAnnotationDetail(defaultRepAnnotation, "value", getQualifiedName(s.getDefaultRepresentation()));
                t.getEAnnotations().add(defaultRepAnnotation);
            }
            
            // Add documentation annotation inline if applicable
            if (s.getDocumentation() != null && !s.getDocumentation().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/DocumentationAnnotation",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", s.getDocumentation());
                t.getEAnnotations().add(docAnnotation);
            }
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            // Set up inheritance
            for (EntityType superType : s.getSuperEntityTypes()) {
                EClass superClass = ctx.equivalent(superType, EClass.class);
                if (superClass != null) {
                    t.getESuperTypes().add(superClass);
                }
            }
            
            return t;
        };
    }

    // =========================================================================
    // ATTRIBUTE RULES
    // =========================================================================

    /**
     * rule CreateAttribute
     *     transform s : JUDOPSM!Attribute
     *     to t : ASM!EAttribute {
     *         guard: s.dataType.isKindOf(Primitive)
     *     }
     * 
     * Note: All attribute annotations are created inline to avoid recursive update issues.
     */
    @TransformRule(name = "CreateAttribute", description = "Transform Attribute to EAttribute")
    @Guard(method = "isPrimitiveAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<Attribute, EAttribute> createAttribute() {
        return (s, ctx) -> {
            EAttribute t = ctx.createTarget(EAttribute.class);
            setId(t, "(psm/" + getId(s) + ")/Attribute");
            t.setName(s.getName());
            t.setLowerBound(s.isRequired() ? 1 : 0);
            
            // Set type
            EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
            if (type != null) {
                t.setEType(type);
            }
            
            // Add identifier annotation inline if applicable
            if (s.isIdentifier()) {
                EAnnotation idAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/IdentifierAnnotationForAttribute",
                        getAnnotationUri("identifier"));
                addAnnotationDetail(idAnnotation, "value", "true");
                t.getEAnnotations().add(idAnnotation);
            }
            
            // Add string constraints annotation inline if applicable
            if (s.getDataType() instanceof StringType) {
                StringType stringType = (StringType) s.getDataType();
                EAnnotation constraintsAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/AttributeConstraints",
                        getAnnotationUri("constraints"));
                addAnnotationDetail(constraintsAnnotation, "maxLength", String.valueOf(stringType.getMaxLength()));
                if (stringType.getRegExp() != null && !stringType.getRegExp().isEmpty()) {
                    addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
                }
                t.getEAnnotations().add(constraintsAnnotation);
            }
            
            // Add numeric constraints annotation inline if applicable
            if (s.getDataType() instanceof NumericType) {
                NumericType numericType = (NumericType) s.getDataType();
                EAnnotation constraintsAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/AttributeConstraints",
                        getAnnotationUri("constraints"));
                addAnnotationDetail(constraintsAnnotation, "precision", String.valueOf(numericType.getPrecision()));
                addAnnotationDetail(constraintsAnnotation, "scale", String.valueOf(numericType.getScale()));
                
                // Add measured annotations if applicable
                if (numericType instanceof MeasuredType) {
                    MeasuredType measuredType = (MeasuredType) numericType;
                    if (measuredType.getStoreUnit() != null) {
                        if (measuredType.getStoreUnit().eContainer() instanceof NamespaceElement) {
                            addAnnotationDetail(constraintsAnnotation, "measure", 
                                getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                        }
                        addAnnotationDetail(constraintsAnnotation, "unit", measuredType.getStoreUnit().getName());
                    }
                }
                t.getEAnnotations().add(constraintsAnnotation);
            }
            
            // Add custom type constraints annotation inline if applicable
            if (s.getDataType() instanceof CustomType 
                    && !(s.getDataType() instanceof NumericType)
                    && !(s.getDataType() instanceof BooleanType)
                    && !(s.getDataType() instanceof EnumerationType)
                    && !(s.getDataType() instanceof StringType)) {
                String qualifiedName = getQualifiedName((NamespaceElement) s.getDataType());
                EAnnotation constraintsAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/CustomAttributeConstraints",
                        getAnnotationUri("constraints"));
                addAnnotationDetail(constraintsAnnotation, "customType", qualifiedName);
                t.getEAnnotations().add(constraintsAnnotation);
            }
            
            // Add to owning entity class
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(t);
                }
            }
            
            return t;
        };
    }

    // =========================================================================
    // RELATION RULES
    // =========================================================================

    /**
     * rule CreateAssociationEndRelation
     *     transform s : JUDOPSM!AssociationEnd
     *     to t : ASM!EReference
     * 
     * Note: All relation annotations are created inline to avoid recursive update issues.
     * EOpposite is set in post-processing to avoid bidirectional recursion.
     */
    @TransformRule(name = "CreateAssociationEndRelation", description = "Transform AssociationEnd to EReference")
    @Transform(type = AssociationEnd.class)
    @To(type = EReference.class)
    public TransformFunction<AssociationEnd, EReference> createAssociationEndRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
            setId(t, "(psm/" + getId(s) + ")/AssociationEndRelation");
            t.setName(s.getName());
            t.setLowerBound(s.getCardinality().getLower());
            t.setUpperBound(s.getCardinality().getUpper());
            
            // Set target type
            if (s.getTarget() != null) {
                EClass targetClass = ctx.equivalent(s.getTarget(), EClass.class);
                if (targetClass != null) {
                    t.setEType(targetClass);
                }
            }
            
            // Add reverse cascade delete annotation inline if applicable
            if (s.isReverseCascadeDelete()) {
                EAnnotation reverseCascadeAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/ReverseCascadeDeleteAnnotation",
                        getAnnotationUri("reverseCascadeDelete"));
                addAnnotationDetail(reverseCascadeAnnotation, "value", "true");
                t.getEAnnotations().add(reverseCascadeAnnotation);
            }
            
            // Add to owning entity class
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(t);
                }
            }
            
            // NOTE: EOpposite is set in post-processing to avoid recursive update
            // when processing bidirectional associations
            
            return t;
        };
    }

    // NOTE: SetAssociationEndOpposite is handled in post-processing
    // to avoid recursive update issues when processing bidirectional associations.
    // See Psm2AsmZetaTransformation.postProcess()

    /**
     * rule CreateContainmentRelation
     *     transform s : JUDOPSM!Containment
     *     to t : ASM!EReference
     */
    @TransformRule(name = "CreateContainmentRelation", description = "Transform Containment to EReference with containment=true")
    @Transform(type = Containment.class)
    @To(type = EReference.class)
    public TransformFunction<Containment, EReference> createContainmentRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
            setId(t, "(psm/" + getId(s) + ")/ContainmentRelation");
            t.setName(s.getName());
            t.setLowerBound(s.getCardinality().getLower());
            t.setUpperBound(s.getCardinality().getUpper());
            t.setContainment(true);
            
            // Set target type
            if (s.getTarget() != null) {
                EClass targetClass = ctx.equivalent(s.getTarget(), EClass.class);
                if (targetClass != null) {
                    t.setEType(targetClass);
                }
            }
            
            // Add to owning entity class
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(t);
                }
            }
            
            return t;
        };
    }

    // =========================================================================
    // SEQUENCE RULES
    // =========================================================================

    /**
     * Create sequence annotation for NamespaceSequence.
     * Adds a sequence annotation to the container package.
     */
    @TransformRule(name = "CreateNamespaceSequence")
    @Transform(type = NamespaceSequence.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<NamespaceSequence, EAnnotation> createNamespaceSequence() {
        return (s, ctx) -> {
            EAnnotation t = createAnnotation(
                    "(psm/" + getId(s) + ")/NamespaceSequence",
                    getAnnotationUri("sequence"));
            
            // Add sequence details
            addSequenceDetails(t, s);
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * Create sequence annotation for EntitySequence.
     * Adds a sequence annotation to the owning entity class.
     */
    @TransformRule(name = "CreateEntitySequence")
    @Transform(type = EntitySequence.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<EntitySequence, EAnnotation> createEntitySequence() {
        return (s, ctx) -> {
            EAnnotation t = createAnnotation(
                    "(psm/" + getId(s) + ")/EntitySequence",
                    getAnnotationUri("sequence"));
            
            // Add sequence details
            addSequenceDetails(t, s);
            
            // Add to owning entity class
            EntityType owner = (EntityType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEAnnotations().add(t);
                }
            }
            
            return t;
        };
    }

    /**
     * Add common sequence details to the annotation.
     */
    private void addSequenceDetails(EAnnotation annotation, Sequence sequence) {
        addAnnotationDetail(annotation, "name", sequence.getName());
        addAnnotationDetail(annotation, "initialValue", String.valueOf(sequence.getInitialValue()));
        addAnnotationDetail(annotation, "increment", String.valueOf(sequence.getIncrement()));
        Long maxValue = sequence.getMaximumValue();
        if (maxValue != null && maxValue.longValue() != 0L) {
            addAnnotationDetail(annotation, "maximumValue", String.valueOf(maxValue));
        }
        addAnnotationDetail(annotation, "cyclic", String.valueOf(sequence.isCyclic()));
    }

    // =========================================================================
    // UNMAPPED DEFAULT ONLY ANNOTATION RULES
    // =========================================================================

    /**
     * rule AddUnmappedDefaultOnlyAttributeAnnotation
     *     transform s : JUDOPSM!Attribute
     *     to t : ASM!EAnnotation
     *     guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
     *            and s.eContainer.defaultRepresentation.attributes.exists(
     *                a | a.binding == s and a.defaultValue.isDefined())
     * 
     * Adds unmappedDefaultOnly annotation to attributes that have default values
     * in the entity's default transfer object representation.
     */
    @TransformRule(name = "AddUnmappedDefaultOnlyAttributeAnnotation", 
                   description = "Add unmappedDefaultOnly annotation for attributes with default values")
    @Transform(type = Attribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<Attribute, EAnnotation> addUnmappedDefaultOnlyAttributeAnnotation() {
        return (s, ctx) -> {
            // Guard: entity container with defaultRepresentation having transfer attribute 
            // with this attribute as binding and a default value
            EObject container = s.eContainer();
            if (!(container instanceof EntityType)) {
                return null;
            }
            EntityType entity = (EntityType) container;
            MappedTransferObjectType defaultRep = entity.getDefaultRepresentation();
            if (defaultRep == null) {
                return null;
            }
            
            // Check if any transfer attribute binds to this attribute and has defaultValue
            boolean hasDefaultValue = defaultRep.getAttributes().stream()
                    .anyMatch(a -> a.getBinding() == s && a.getDefaultValue() != null);
            if (!hasDefaultValue) {
                return null;
            }
            
            // Create annotation
            EAnnotation t = createAnnotation(
                    "(psm/" + getId(s) + ")/UnmappedDefaultOnlyAttributeAnnotation",
                    getAnnotationUri("unmappedDefaultOnly"));
            addAnnotationDetail(t, "value", String.valueOf(s.isUnmappedDefaultOnly()));
            
            // Add to the equivalent EAttribute
            EAttribute attr = ctx.equivalent(s, EAttribute.class);
            if (attr != null) {
                attr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddUnmappedDefaultOnlyReferenceAnnotation
     *     transform s : JUDOPSM!AssociationEnd
     *     to t : ASM!EAnnotation
     *     guard: s.eContainer.isDefined() and s.eContainer.defaultRepresentation.isDefined() 
     *            and s.eContainer.defaultRepresentation.relations.exists(
     *                r | r.binding == s and r.defaultValue.isDefined())
     * 
     * Adds unmappedDefaultOnly annotation to association ends that have default values
     * in the entity's default transfer object representation.
     */
    @TransformRule(name = "AddUnmappedDefaultOnlyReferenceAnnotation", 
                   description = "Add unmappedDefaultOnly annotation for references with default values")
    @Transform(type = AssociationEnd.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<AssociationEnd, EAnnotation> addUnmappedDefaultOnlyReferenceAnnotation() {
        return (s, ctx) -> {
            // Guard: entity container with defaultRepresentation having transfer relation 
            // with this associationEnd as binding and a default value
            EObject container = s.eContainer();
            if (!(container instanceof EntityType)) {
                return null;
            }
            EntityType entity = (EntityType) container;
            MappedTransferObjectType defaultRep = entity.getDefaultRepresentation();
            if (defaultRep == null) {
                return null;
            }
            
            // Check if any transfer relation binds to this associationEnd and has defaultValue
            boolean hasDefaultValue = defaultRep.getRelations().stream()
                    .anyMatch(r -> r.getBinding() == s && r.getDefaultValue() != null);
            if (!hasDefaultValue) {
                return null;
            }
            
            // Create annotation
            EAnnotation t = createAnnotation(
                    "(psm/" + getId(s) + ")/UnmappedDefaultOnlyReferenceAnnotation",
                    getAnnotationUri("unmappedDefaultOnly"));
            addAnnotationDetail(t, "value", String.valueOf(s.isUnmappedDefaultOnly()));
            
            // Add to the equivalent EReference
            EReference ref = ctx.equivalent(s, EReference.class);
            if (ref != null) {
                ref.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Gets the owning EntityType for an element.
     */
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

    /**
     * Gets the container package for an element by walking up the container hierarchy
     * to find the nearest Namespace (Model or Package) and looking up its equivalent EPackage.
     */
    private EPackage getContainerPackage(EObject element, TransformationContext ctx) {
        EObject container = element.eContainer();
        while (container != null) {
            if (container instanceof Namespace) {
                if (container instanceof Model) {
                    EPackage pkg = ctx.equivalent(container, EPackage.class);
                    if (pkg != null) {
                        return pkg;
                    }
                } else if (container instanceof Package) {
                    EPackage pkg = ctx.equivalent(container, EPackage.class);
                    if (pkg != null) {
                        return pkg;
                    }
                }
            }
            container = container.eContainer();
        }
        return null;
    }
}
