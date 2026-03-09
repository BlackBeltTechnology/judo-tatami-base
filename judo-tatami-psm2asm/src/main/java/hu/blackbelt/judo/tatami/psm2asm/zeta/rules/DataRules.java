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
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

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
    public TransformGuard isPrimitiveAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof Attribute attr)) return false;
            return attr.getDataType() != null && attr.getDataType() instanceof Primitive;
        };
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
    @TransformRule(name = CREATE_ENTITY_CLASS, description = "Transform EntityType to EClass")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createEntityClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            t.setName(s.getName());
            t.setAbstract(s.isAbstract());
            
            // Add entity annotation inline (thread-safe)
            EAnnotation entityAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/EntityAnnotationClass",
                    getAnnotationUri("entity"));
            addAnnotationDetail(entityAnnotation, "value", "true");
            addAnnotation(t, entityAnnotation);

            // Add default representation annotation inline if applicable
            if (s.getDefaultRepresentation() != null) {
                EAnnotation defaultRepAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/EntityDefaultRepresentationAnnotation",
                        getAnnotationUri("defaultRepresentation"));
                // Use dot notation for defaultRepresentation value (matches ETL)
                String defaultRepValue = getQualifiedName(s.getDefaultRepresentation()).replace("::", ".");
                addAnnotationDetail(defaultRepAnnotation, "value", defaultRepValue);
                addAnnotation(t, defaultRepAnnotation);
            }

            // Add documentation annotation inline if applicable
            // Note: ETL guard trims documentation: s.documentation.isDefined() and s.documentation.trim().length() > 0
            if (s.getDocumentation() != null && !s.getDocumentation().trim().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/DocumentationAnnotationForEntityType",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", s.getDocumentation().trim());
                addAnnotation(t, docAnnotation);
            }

            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addClassifier(containerPkg, t);

            // Set up inheritance (thread-safe)
            for (EntityType superType : s.getSuperEntityTypes()) {
                EClass superClass = ctx.equivalent(superType, EClass.class);
                addSuperType(t, superClass);
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
    @TransformRule(name = CREATE_ATTRIBUTE, description = "Transform Attribute to EAttribute")
    @Guard(method = "isPrimitiveAttribute")
    @Transform(type = Attribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<Attribute, EAttribute> createAttribute() {
        return (s, ctx) -> {
            EAttribute t = ctx.createTarget(EAttribute.class);
            t.setName(s.getName());
            t.setLowerBound(s.isRequired() ? 1 : 0);
            
            // Set type
            EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
            if (type != null) {
                t.setEType(type);
            }

            // Add identifier annotation inline if applicable (thread-safe)
            if (s.isIdentifier()) {
                EAnnotation idAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/IdentifierAnnotationForAttribute",
                        getAnnotationUri("identifier"));
                addAnnotationDetail(idAnnotation, "value", "true");
                addAnnotation(t, idAnnotation);
            }

            // Add string constraints annotation inline if applicable (thread-safe)
            if (s.getDataType() instanceof StringType) {
                StringType stringType = (StringType) s.getDataType();
                EAnnotation constraintsAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/AttributeConstraints",
                        getAnnotationUri("constraints"));
                addAnnotationDetail(constraintsAnnotation, "maxLength", String.valueOf(stringType.getMaxLength()));
                if (stringType.getRegExp() != null && !stringType.getRegExp().isEmpty()) {
                    addAnnotationDetail(constraintsAnnotation, "pattern", stringType.getRegExp());
                }
                addAnnotation(t, constraintsAnnotation);
            }

            // Add numeric constraints annotation inline if applicable (thread-safe)
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
                addAnnotation(t, constraintsAnnotation);
            }

            // Add custom type constraints annotation inline if applicable (thread-safe)
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
                addAnnotation(t, constraintsAnnotation);
            }

            // Add to owning entity class (thread-safe)
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addStructuralFeature(ownerClass, t);
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
    @TransformRule(name = CREATE_ASSOCIATION_END_RELATION, description = "Transform AssociationEnd to EReference")
    @Transform(type = AssociationEnd.class)
    @To(type = EReference.class)
    public TransformFunction<AssociationEnd, EReference> createAssociationEndRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
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
            
            // Add reverse cascade delete annotation inline if applicable (thread-safe)
            if (s.isReverseCascadeDelete()) {
                EAnnotation reverseCascadeAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/ReverseCascadeDeleteAnnotation",
                        getAnnotationUri("reverseCascadeDelete"));
                addAnnotationDetail(reverseCascadeAnnotation, "value", "true");
                addAnnotation(t, reverseCascadeAnnotation);
            }

            // Add documentation annotation inline if applicable (thread-safe)
            if (s.getDocumentation() != null && !s.getDocumentation().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/DocumentationAnnotation",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", s.getDocumentation());
                addAnnotation(t, docAnnotation);
            }

            // Add to owning entity class (thread-safe)
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addStructuralFeature(ownerClass, t);
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
    @TransformRule(name = CREATE_CONTAINMENT_RELATION, description = "Transform Containment to EReference with containment=true")
    @Transform(type = Containment.class)
    @To(type = EReference.class)
    public TransformFunction<Containment, EReference> createContainmentRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
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
            
            // Add documentation annotation inline if applicable (thread-safe)
            if (s.getDocumentation() != null && !s.getDocumentation().isEmpty()) {
                EAnnotation docAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/DocumentationAnnotation",
                        getAnnotationUri("documentation"));
                addAnnotationDetail(docAnnotation, "value", s.getDocumentation());
                addAnnotation(t, docAnnotation);
            }

            // Add to owning entity class (thread-safe)
            EntityType owner = getEntityType(s);
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addStructuralFeature(ownerClass, t);
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
    @TransformRule(name = CREATE_NAMESPACE_SEQUENCE)
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

            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addAnnotation(containerPkg, t);

            return t;
        };
    }

    /**
     * Create sequence annotation for EntitySequence.
     * Adds a sequence annotation to the owning entity class.
     */
    @TransformRule(name = CREATE_ENTITY_SEQUENCE)
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

            // Add to owning entity class using named equivalent (thread-safe)
            EntityType owner = (EntityType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class, CREATE_ENTITY_CLASS);
                addAnnotation(ownerClass, t);
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
    @TransformRule(name = ADD_UNMAPPED_DEFAULT_ONLY_ATTRIBUTE_ANNOTATION,
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

            // Add to the equivalent EAttribute (thread-safe)
            EAttribute attr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(attr, t);

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
    @TransformRule(name = ADD_UNMAPPED_DEFAULT_ONLY_REFERENCE_ANNOTATION,
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

            // Add to the equivalent EReference (thread-safe)
            EReference ref = ctx.equivalent(s, EReference.class);
            addAnnotation(ref, t);

            return t;
        };
    }

}
