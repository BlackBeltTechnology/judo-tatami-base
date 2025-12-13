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

import hu.blackbelt.judo.meta.psm.accesspoint.AbstractActorType;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.CustomType;
import hu.blackbelt.judo.meta.psm.type.NumericType;
import hu.blackbelt.judo.meta.psm.type.Primitive;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.Relation;
import hu.blackbelt.judo.meta.psm.derived.PrimitiveAccessor;
import hu.blackbelt.judo.meta.psm.derived.ReferenceAccessor;
import hu.blackbelt.judo.meta.psm.derived.StaticData;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;

/**
 * Transfer object transformation rules from service.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateMappedTransferObjectTypeClass - transforms MappedTransferObjectType to EClass</li>
 *   <li>CreateUnmappedTransferObjectTypeClass - transforms UnmappedTransferObjectType to EClass</li>
 *   <li>CreateTransferAttribute - transforms TransferAttribute to EAttribute</li>
 *   <li>CreateTransferObjectRelation - transforms TransferObjectRelation to EReference</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = TransferObjectType.class, target = EClass.class)
public class TransferObjectRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public TransferObjectRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: transfer attribute has a primitive data type
     */
    public boolean isPrimitiveTransferAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            TransferAttribute ta = (TransferAttribute) source;
            return ta.getDataType() != null && ta.getDataType() instanceof Primitive;
        }
        return false;
    }

    /**
     * Guard: element has documentation
     */
    public boolean hasDocumentation(EObject source, TransformationContext ctx) {
        if (source instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement) {
            String doc = ((hu.blackbelt.judo.meta.psm.namespace.NamedElement) source).getDocumentation();
            return doc != null && !doc.isEmpty();
        }
        return false;
    }

    /**
     * Guard: mapped transfer object has entity type
     */
    public boolean hasEntityType(EObject source, TransformationContext ctx) {
        if (source instanceof MappedTransferObjectType) {
            return ((MappedTransferObjectType) source).getEntityType() != null;
        }
        return false;
    }

    /**
     * Guard: transfer relation is embedded
     */
    public boolean isEmbeddedRelation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectRelation) {
            return ((TransferObjectRelation) source).isEmbedded();
        }
        return false;
    }

    /**
     * Guard: transfer attribute has binding (excluding StaticData)
     * ETL: s.isPrimitive() and s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!StaticData)
     */
    public boolean hasBinding(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            TransferAttribute attr = (TransferAttribute) source;
            Object binding = attr.getBinding();
            // Exclude StaticData bindings - they don't get binding annotation
            return binding != null && !(binding instanceof StaticData);
        }
        return false;
    }

    /**
     * Guard: transfer relation has binding
     */
    public boolean hasRelationBinding(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectRelation) {
            return ((TransferObjectRelation) source).getBinding() != null;
        }
        return false;
    }

    /**
     * Guard: transfer relation has a target type
     */
    public boolean hasTargetType(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectRelation) {
            return ((TransferObjectRelation) source).getTarget() != null;
        }
        return false;
    }

    /**
     * Guard: transfer attribute has no binding (transient)
     */
    public boolean hasNoBinding(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            return ((TransferAttribute) source).getBinding() == null;
        }
        return false;
    }

    /**
     * Guard: transfer attribute has string type
     */
    public boolean isStringTransferAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            return ((TransferAttribute) source).getDataType() instanceof StringType;
        }
        return false;
    }

    /**
     * Guard: transfer attribute has custom type
     */
    public boolean isCustomTypeTransferAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            return ((TransferAttribute) source).getDataType() instanceof CustomType;
        }
        return false;
    }

    /**
     * Guard: transfer attribute has numeric type (not measured)
     */
    public boolean isNumericTransferAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            TransferAttribute ta = (TransferAttribute) source;
            return ta.getDataType() instanceof NumericType && !(ta.getDataType() instanceof MeasuredType);
        }
        return false;
    }

    /**
     * Guard: transfer attribute has measured type
     */
    public boolean isMeasuredTransferAttribute(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            return ((TransferAttribute) source).getDataType() instanceof MeasuredType;
        }
        return false;
    }

    /**
     * Guard: transfer relation is access relation
     */
    public boolean isAccessRelation(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectRelation) {
            return ((TransferObjectRelation) source).isAccess();
        }
        return false;
    }

    /**
     * Helper: check if transfer relation is access relation
     */
    private boolean isAccessRelation(TransferObjectRelation relation) {
        return relation != null && relation.isAccess();
    }

    /**
     * Guard: transfer relation has binding to ReferenceAccessor (unmapped) or StaticNavigation (mapped)
     * ETL: s.binding.isDefined() and
     *      ((s.eContainer().isKindOf(UnmappedTransferObjectType) and s.binding.isKindOf(ReferenceAccessor)) or
     *       (s.eContainer().isKindOf(MappedTransferObjectType) and s.binding.isKindOf(StaticNavigation)))
     */
    public boolean hasNavigationBinding(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectRelation) {
            TransferObjectRelation rel = (TransferObjectRelation) source;
            Object binding = rel.getBinding();
            EObject container = rel.eContainer();
            if (binding == null) {
                return false;
            }
            return (container instanceof UnmappedTransferObjectType && binding instanceof ReferenceAccessor) ||
                   (container instanceof MappedTransferObjectType && binding instanceof StaticNavigation);
        }
        return false;
    }

    /**
     * Guard: transfer attribute has data binding to PrimitiveAccessor (unmapped) or StaticData (mapped)
     * ETL: s.binding.isDefined() and
     *      ((s.eContainer().isKindOf(UnmappedTransferObjectType) and s.binding.isKindOf(PrimitiveAccessor)) or
     *       (s.eContainer().isKindOf(MappedTransferObjectType) and s.binding.isKindOf(StaticData)))
     */
    public boolean hasDataBinding(EObject source, TransformationContext ctx) {
        if (source instanceof TransferAttribute) {
            TransferAttribute attr = (TransferAttribute) source;
            Object binding = attr.getBinding();
            EObject container = attr.eContainer();
            if (binding == null) {
                return false;
            }
            return (container instanceof UnmappedTransferObjectType && binding instanceof PrimitiveAccessor) ||
                   (container instanceof MappedTransferObjectType && binding instanceof StaticData);
        }
        return false;
    }

    /**
     * Guard: transfer object type has an actorType reference
     * ETL: s.actorType.isDefined()
     */
    public boolean hasActorType(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectType) {
            return ((TransferObjectType) source).getActorType() != null;
        }
        return false;
    }

    /**
     * Guard: transfer object type is a get range input type.
     * A transfer object is a get range input type if it's the input type of a GET_RANGE operation.
     * Uses pre-computed set from Psm2AsmZetaTransformation.
     */
    @SuppressWarnings("unchecked")
    public boolean isGetRangeInputType(EObject source, TransformationContext ctx) {
        if (source instanceof TransferObjectType) {
            Object attr = ctx.getAttribute("getRangeInputTypes");
            if (attr instanceof java.util.Set) {
                return ((java.util.Set<TransferObjectType>) attr).contains(source);
            }
        }
        return false;
    }

    // =========================================================================
    // TRANSFER OBJECT TYPE RULES
    // =========================================================================

    /**
     * rule CreateMappedTransferObjectTypeClass
     *     transform s : JUDOPSM!MappedTransferObjectType
     *     to t : ASM!EClass
     */
    @TransformRule(name = "CreateMappedTransferObjectTypeClass", description = "Transform MappedTransferObjectType to EClass")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<MappedTransferObjectType, EClass> createMappedTransferObjectTypeClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            setId(t, "(psm/" + getId(s) + ")/MappedTransferObjectTypeClass");
            t.setName(s.getName());
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateMappedTransferObjectAnnotation
     *     transform s : JUDOPSM!MappedTransferObjectType
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = "CreateMappedTransferObjectAnnotation", description = "Add mappedEntityType annotation")
    @Guard(method = "hasEntityType")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<MappedTransferObjectType, EAnnotation> createMappedTransferObjectAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/MappedTransferObjectAnnotation");
            t.setSource(getAnnotationUri("mappedEntityType"));
            addAnnotationDetail(t, "value", getQualifiedName(s.getEntityType()));
            
            // Add to equivalent class
            EClass eClass = ctx.equivalent(s, EClass.class);
            if (eClass != null) {
                eClass.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateUnmappedTransferObjectTypeClass
     *     transform s : JUDOPSM!UnmappedTransferObjectType
     *     to t : ASM!EClass
     */
    @TransformRule(name = "CreateUnmappedTransferObjectTypeClass", description = "Transform UnmappedTransferObjectType to EClass")
    @Transform(type = UnmappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<UnmappedTransferObjectType, EClass> createUnmappedTransferObjectTypeClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            setId(t, "(psm/" + getId(s) + ")/UnmappedTransferObjectTypeClass");
            t.setName(s.getName());
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateTransferObjectTypeAnnotation
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = "CreateTransferObjectTypeAnnotation", description = "Add transferObjectType annotation")
    @Greedy
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createTransferObjectTypeAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/TransferObjectTypeAnnotation");
            t.setSource(getAnnotationUri("transferObjectType"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent class
            EClass eClass = ctx.equivalent(s, EClass.class);
            if (eClass != null) {
                eClass.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateActorAnnotation
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation {
     *         guard: s.actorType.isDefined()
     *     }
     * 
     * Adds an "actor" annotation to transfer object types that reference an ActorType.
     */
    @TransformRule(name = "CreateActorAnnotation", description = "Add actor annotation to transfer objects referencing an actor")
    @Greedy
    @Guard(method = "hasActorType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createActorAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/ActorAnnotation");
            t.setSource(getAnnotationUri("actor"));
            
            AbstractActorType actorType = s.getActorType();
            
            // Add actor name - use :: separator format to match ETL (enrichWithAnnotations expects this)
            addAnnotationDetail(t, "name", getQualifiedNameWithColons((NamespaceElement) actorType));
            
            // Add realm if defined
            if (actorType.getRealm() != null && !actorType.getRealm().isEmpty()) {
                addAnnotationDetail(t, "realm", actorType.getRealm());
            }
            
            // Add to equivalent class
            EClass eClass = ctx.equivalent(s, EClass.class);
            if (eClass != null) {
                eClass.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateGetRangeInputAnnotationForGetRangeInputClass
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation {
     *         guard: s.isGetRangeInputType()
     *     }
     * 
     * Adds getRangeInput annotation if this transfer object type is used as input for GET_RANGE operation.
     */
    @TransformRule(name = "CreateGetRangeInputAnnotation", description = "Add getRangeInput annotation for GET_RANGE input types")
    @Greedy
    @Guard(method = "isGetRangeInputType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createGetRangeInputAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/GetRangeInputAnnotationForGetRangeInputClass");
            t.setSource(getAnnotationUri("getRangeInput"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent class
            EClass eClass = ctx.equivalent(s, EClass.class);
            if (eClass != null) {
                eClass.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // TRANSFER ATTRIBUTE RULES
    // =========================================================================

    /**
     * rule CreateTransferAttribute
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAttribute {
     *         guard: s.dataType.isKindOf(Primitive)
     *     }
     */
    @TransformRule(name = "CreateTransferAttribute", description = "Transform TransferAttribute to EAttribute")
    @Guard(method = "isPrimitiveTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<TransferAttribute, EAttribute> createTransferAttribute() {
        return (s, ctx) -> {
            EAttribute t = ctx.createTarget(EAttribute.class);
            setId(t, "(psm/" + getId(s) + ")/TransferAttribute");
            t.setName(s.getName());
            t.setLowerBound(s.isRequired() ? 1 : 0);
            
            // Set type
            EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
            if (type != null) {
                t.setEType(type);
            }
            
            // Set derived: binding is defined and is not an Attribute
            // ETL: t.derived = s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!Attribute);
            Object binding = s.getBinding();
            t.setDerived(binding != null && !(binding instanceof Attribute));
            
            // Set changeable: no binding, or binding is Attribute, or (binding is PrimitiveAccessor and has setter)
            // ETL: t.changeable = not s.binding.isDefined() or
            //      (s.binding.isKindOf(JUDOPSM!Attribute) or
            //       (s.binding.isKindOf(JUDOPSM!PrimitiveAccessor) and s.binding.setterExpression.isDefined()));
            boolean changeable = binding == null ||
                (binding instanceof Attribute) ||
                (binding instanceof PrimitiveAccessor && ((PrimitiveAccessor) binding).getSetterExpression() != null);
            t.setChangeable(changeable);
            
            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(t);
                }
            }
            
            return t;
        };
    }

    /**
     * rule AddTransferAttributeBindingAnnotation
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation {
     *         guard: s.binding.isDefined()
     *     }
     */
    @TransformRule(name = "AddTransferAttributeBindingAnnotation", description = "Add binding annotation to transfer attribute")
    @Guard(method = "hasBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addTransferAttributeBindingAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/TransferAttributeBindingAnnotation");
            t.setSource(getAnnotationUri("binding"));
            
            // Get binding path
            String bindingPath = s.getBinding().getName();
            addAnnotationDetail(t, "value", bindingPath);
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddTransientAnnotationToTransferAttribute
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation {
     *         guard: s.binding.isUndefined()
     *     }
     */
    @TransformRule(name = "AddTransientAnnotationToTransferAttribute", description = "Add transient annotation to unbound transfer attribute")
    @Guard(method = "hasNoBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addTransientAnnotationToTransferAttribute() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/TransientAnnotationToTransferAttribute");
            t.setSource(getAnnotationUri("transient"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddStringTransferAttributeConstraints
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation
     *     extends AddTransferAttributeConstraints {
     *         guard: s.dataType.isKindOf(JUDOPSM!StringType)
     *     }
     */
    @TransformRule(name = "AddStringTransferAttributeConstraints", description = "Add string constraints for transfer attribute")
    @Guard(method = "isStringTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addStringTransferAttributeConstraints() {
        return (s, ctx) -> {
            StringType stringType = (StringType) s.getDataType();
            
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/StringTransferAttributeConstraints");
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "maxLength", String.valueOf(stringType.getMaxLength()));
            
            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(t, "pattern", stringType.getRegExp());
            }
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddCustomTransferAttributeConstraints
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation
     *     extends AddTransferAttributeConstraints {
     *         guard: s.dataType.isKindOf(JUDOPSM!CustomType)
     *     }
     */
    @TransformRule(name = "AddCustomTransferAttributeConstraints", description = "Add custom type constraints for transfer attribute")
    @Guard(method = "isCustomTypeTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addCustomTransferAttributeConstraints() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/CustomTransferAttributeConstraints");
            t.setSource(getAnnotationUri("constraints"));
            
            String qualifiedName = getQualifiedName((NamespaceElement) s.getDataType());
            addAnnotationDetail(t, "customType", qualifiedName);
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddNumericTransferAttributeConstraints
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation
     *     extends AddAbstractNumericTransferAttributeConstraints {
     *         guard: s.dataType.isKindOf(JUDOPSM!NumericType) and not s.dataType.isKindOf(JUDOPSM!MeasuredType)
     *     }
     */
    @TransformRule(name = "AddNumericTransferAttributeConstraints", description = "Add numeric constraints for transfer attribute")
    @Guard(method = "isNumericTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addNumericTransferAttributeConstraints() {
        return (s, ctx) -> {
            NumericType numericType = (NumericType) s.getDataType();
            
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/NumericTransferAttributeConstraints");
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(t, "scale", String.valueOf(numericType.getScale()));
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule AddMeasuredTransferAttributeConstraints
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation
     *     extends AddAbstractNumericTransferAttributeConstraints {
     *         guard: s.dataType.isKindOf(JUDOPSM!MeasuredType)
     *     }
     */
    @TransformRule(name = "AddMeasuredTransferAttributeConstraints", description = "Add measured constraints for transfer attribute")
    @Guard(method = "isMeasuredTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addMeasuredTransferAttributeConstraints() {
        return (s, ctx) -> {
            MeasuredType measuredType = (MeasuredType) s.getDataType();
            
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/MeasuredTransferAttributeConstraints");
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "precision", String.valueOf(measuredType.getPrecision()));
            addAnnotationDetail(t, "scale", String.valueOf(measuredType.getScale()));
            
            // Add measured annotations
            if (measuredType.getStoreUnit() != null) {
                if (measuredType.getStoreUnit().eContainer() instanceof NamespaceElement) {
                    addAnnotationDetail(t, "measure",
                        getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                }
                addAnnotationDetail(t, "unit", measuredType.getStoreUnit().getName());
            }
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateDataReferenceBinding
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation {
     *         guard: s.binding.isDefined() and
     *             ((s.eContainer().isKindOf(UnmappedTransferObjectType) and s.binding.isKindOf(PrimitiveAccessor)) or
     *              (s.eContainer().isKindOf(MappedTransferObjectType) and s.binding.isKindOf(StaticData)))
     *     }
     * 
     * Creates expression annotation for derived attributes with PrimitiveAccessor or StaticData binding.
     */
    @TransformRule(name = "CreateDataReferenceBinding", description = "Add expression annotation for data bindings")
    @Guard(method = "hasDataBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> createDataReferenceBinding() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/DataReferenceBinding");
            t.setSource(getAnnotationUri("expression"));
            
            Object binding = s.getBinding();
            
            // Add getter expression based on binding type
            if (binding instanceof PrimitiveAccessor) {
                PrimitiveAccessor accessor = (PrimitiveAccessor) binding;
                if (accessor.getGetterExpression() != null) {
                    addAnnotationDetail(t, "getter", accessor.getGetterExpression().getExpression());
                    if (accessor.getGetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "getter.dialect", accessor.getGetterExpression().getDialect().toString());
                    }
                    // Add parameter type if defined
                    if (accessor.getGetterExpression().getParameterType() != null) {
                        EClass paramType = ctx.equivalent(accessor.getGetterExpression().getParameterType(), EClass.class);
                        if (paramType != null) {
                            addAnnotationDetail(t, "getter.parameter", getClassifierFQName(paramType));
                        }
                    }
                }
                // Add setter expression if defined
                if (accessor.getSetterExpression() != null) {
                    addAnnotationDetail(t, "setter", accessor.getSetterExpression().getExpression());
                    if (accessor.getSetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "setter.dialect", accessor.getSetterExpression().getDialect().toString());
                    }
                    // Add setter parameter type if defined
                    if (accessor.getSetterExpression().getParameterType() != null) {
                        EClass setterParamType = ctx.equivalent(accessor.getSetterExpression().getParameterType(), EClass.class);
                        if (setterParamType != null) {
                            addAnnotationDetail(t, "setter.parameter", getClassifierFQName(setterParamType));
                        }
                    }
                }
            } else if (binding instanceof StaticData) {
                StaticData data = (StaticData) binding;
                if (data.getGetterExpression() != null) {
                    addAnnotationDetail(t, "getter", data.getGetterExpression().getExpression());
                    if (data.getGetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "getter.dialect", data.getGetterExpression().getDialect().toString());
                    }
                    // Add parameter type if defined
                    if (data.getGetterExpression().getParameterType() != null) {
                        EClass paramType = ctx.equivalent(data.getGetterExpression().getParameterType(), EClass.class);
                        if (paramType != null) {
                            addAnnotationDetail(t, "getter.parameter", getClassifierFQName(paramType));
                        }
                    }
                }
                // Add setter expression if defined
                if (data.getSetterExpression() != null) {
                    addAnnotationDetail(t, "setter", data.getSetterExpression().getExpression());
                    if (data.getSetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "setter.dialect", data.getSetterExpression().getDialect().toString());
                    }
                    // Add setter parameter type if defined
                    if (data.getSetterExpression().getParameterType() != null) {
                        EClass setterParamType = ctx.equivalent(data.getSetterExpression().getParameterType(), EClass.class);
                        if (setterParamType != null) {
                            addAnnotationDetail(t, "setter.parameter", getClassifierFQName(setterParamType));
                        }
                    }
                }
            }
            
            // Add to equivalent attribute
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            if (eAttr != null) {
                eAttr.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // TRANSFER RELATION RULES
    // =========================================================================

    /**
     * rule CreateTransferObjectRelation
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EReference
     * 
     * Note: Annotations (binding, embedded) are created inline to avoid recursive update
     * issues that would occur if separate annotation rules called ctx.equivalent(s, EReference.class).
     */
    @TransformRule(name = "CreateTransferObjectRelation", description = "Transform TransferObjectRelation to EReference")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EReference.class)
    public TransformFunction<TransferObjectRelation, EReference> createTransferObjectRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
            setId(t, "(psm/" + getId(s) + ")/TransferObjectRelation");
            t.setName(s.getName());
            
            if (s.getCardinality() != null) {
                t.setLowerBound(s.getCardinality().getLower());
                t.setUpperBound(s.getCardinality().getUpper());
            }
            
            // Set containment for embedded relations
            t.setContainment(s.isEmbedded());
            
            // Set derived: binding is defined and is not a Relation
            // ETL: t.derived = s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!Relation);
            Object binding = s.getBinding();
            t.setDerived(binding != null && !(binding instanceof Relation));
            
            // Set changeable: no binding, or binding is Relation, or (binding is ReferenceAccessor and has setter)
            // ETL: t.changeable = not s.binding.isDefined() or 
            //      (s.binding.isKindOf(JUDOPSM!Relation) or 
            //       (s.binding.isKindOf(JUDOPSM!ReferenceAccessor) and s.binding.setterExpression.isDefined()));
            boolean changeable = binding == null ||
                (binding instanceof Relation) ||
                (binding instanceof ReferenceAccessor && ((ReferenceAccessor) binding).getSetterExpression() != null);
            t.setChangeable(changeable);
            
            // NOTE: Target type is set in post-processing to avoid recursive update
            // when transfer object types have circular references
            
            // Add binding annotation inline (avoids recursive update from separate rule)
            // ETL rule guard: s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!StaticNavigation)
            if (s.getBinding() != null && !(s.getBinding() instanceof StaticNavigation)) {
                EAnnotation bindingAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationBindingAnnotation",
                        getAnnotationUri("binding"));
                addAnnotationDetail(bindingAnnotation, "value", s.getBinding().getName());
                t.getEAnnotations().add(bindingAnnotation);
            }
            
            // Add range annotation for relations that have a range
            // ETL rule: CreateTransferObjectRelationRangeAnnotation guard: s.range.isDefined()
            if (s.getRange() != null) {
                EAnnotation rangeAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationRangeAnnotation",
                        getAnnotationUri("range"));
                addAnnotationDetail(rangeAnnotation, "value", s.getRange().getName());
                t.getEAnnotations().add(rangeAnnotation);
            }
            
            // Add embedded annotation inline (avoids recursive update from separate rule)
            // ETL rule: CreateTransferObjectRelationEmbeddedFlags adds value, create, update, delete
            if (s.isEmbedded()) {
                EAnnotation embeddedAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationEmbeddedFlags",
                        getAnnotationUri("embedded"));
                addAnnotationDetail(embeddedAnnotation, "value", "true");
                addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(s.isEmbeddedCreate()));
                addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(s.isEmbeddedUpdate()));
                addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(s.isEmbeddedDelete()));
                t.getEAnnotations().add(embeddedAnnotation);
            }
            
            // Add transient annotation for relations without binding and not access
            // ETL rule: AddTransientAnnotationToTransferObjectRelation
            if (s.getBinding() == null && !isAccessRelation(s)) {
                EAnnotation transientAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransientAnnotationToTransferObjectRelation",
                        getAnnotationUri("transient"));
                addAnnotationDetail(transientAnnotation, "value", "true");
                t.getEAnnotations().add(transientAnnotation);
            }
            
            // Add to owning transfer object class
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                if (ownerClass != null) {
                    ownerClass.getEStructuralFeatures().add(t);
                }
            }
            
            return t;
        };
    }

    // NOTE: SetTransferObjectRelationTarget is handled in post-processing
    // to avoid recursive update issues when transfer objects have circular references.
    // See Psm2AsmZetaTransformation.postProcess()

    /**
     * rule CreateTransferObjectRelationAccessAnnotation
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EAnnotation {
     *         guard: s.isAccess()
     *         t.source = asmUtils.getAnnotationUri("access");
     *         t.details.add({key="value", value="true"});
     *     }
     */
    @TransformRule(name = "CreateTransferObjectRelationAccessAnnotation", description = "Add access annotation to access relations")
    @Guard(method = "isAccessRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationAccessAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/TransferObjectRelationAccessAnnotation");
            t.setSource(getAnnotationUri("access"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent reference
            EReference eRef = ctx.equivalent(s, EReference.class);
            if (eRef != null) {
                eRef.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateTransferObjectRelationPermissions
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EAnnotation
     * 
     * Adds permissions annotation with create/update/delete flags for embedded relations.
     */
    @TransformRule(name = "CreateTransferObjectRelationPermissions", description = "Add permissions annotation to transfer relations")
    @Greedy
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationPermissions() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/TransferObjectRelationPermissions");
            t.setSource(getAnnotationUri("permissions"));
            addAnnotationDetail(t, "create", String.valueOf(s.isEmbeddedCreate()));
            addAnnotationDetail(t, "update", String.valueOf(s.isEmbeddedUpdate()));
            addAnnotationDetail(t, "delete", String.valueOf(s.isEmbeddedDelete()));
            
            // Add to equivalent reference
            EReference eRef = ctx.equivalent(s, EReference.class);
            if (eRef != null) {
                eRef.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    /**
     * rule CreateNavigationReferenceBinding
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EAnnotation {
     *         guard: s.binding.isDefined() and
     *             ((s.eContainer().isKindOf(UnmappedTransferObjectType) and s.binding.isKindOf(ReferenceAccessor)) or
     *              (s.eContainer().isKindOf(MappedTransferObjectType) and s.binding.isKindOf(StaticNavigation)))
     *     }
     */
    @TransformRule(name = "CreateNavigationReferenceBinding", description = "Add expression annotation for navigation bindings")
    @Guard(method = "hasNavigationBinding")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferObjectRelation, EAnnotation> createNavigationReferenceBinding() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            setId(t, "(psm/" + getId(s) + ")/NavigationReferenceBinding");
            t.setSource(getAnnotationUri("expression"));
            
            Object binding = s.getBinding();
            
            // Add getter expression based on binding type
            if (binding instanceof ReferenceAccessor) {
                ReferenceAccessor accessor = (ReferenceAccessor) binding;
                if (accessor.getGetterExpression() != null) {
                    addAnnotationDetail(t, "getter", accessor.getGetterExpression().getExpression());
                    if (accessor.getGetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "getter.dialect", accessor.getGetterExpression().getDialect().toString());
                    }
                    // Add parameter type if defined
                    if (accessor.getGetterExpression().getParameterType() != null) {
                        EClass paramType = ctx.equivalent(accessor.getGetterExpression().getParameterType(), EClass.class);
                        if (paramType != null) {
                            addAnnotationDetail(t, "getter.parameter", getClassifierFQName(paramType));
                        }
                    }
                }
                // Add setter expression if defined
                if (accessor.getSetterExpression() != null) {
                    addAnnotationDetail(t, "setter", accessor.getSetterExpression().getExpression());
                    if (accessor.getSetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "setter.dialect", accessor.getSetterExpression().getDialect().toString());
                    }
                }
            } else if (binding instanceof StaticNavigation) {
                StaticNavigation nav = (StaticNavigation) binding;
                if (nav.getGetterExpression() != null) {
                    addAnnotationDetail(t, "getter", nav.getGetterExpression().getExpression());
                    if (nav.getGetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "getter.dialect", nav.getGetterExpression().getDialect().toString());
                    }
                    // Add parameter type if defined
                    if (nav.getGetterExpression().getParameterType() != null) {
                        EClass paramType = ctx.equivalent(nav.getGetterExpression().getParameterType(), EClass.class);
                        if (paramType != null) {
                            addAnnotationDetail(t, "getter.parameter", getClassifierFQName(paramType));
                        }
                    }
                }
                // Add setter expression if defined
                if (nav.getSetterExpression() != null) {
                    addAnnotationDetail(t, "setter", nav.getSetterExpression().getExpression());
                    if (nav.getSetterExpression().getDialect() != null) {
                        addAnnotationDetail(t, "setter.dialect", nav.getSetterExpression().getDialect().toString());
                    }
                }
            }
            
            // Add to equivalent reference
            EReference eRef = ctx.equivalent(s, EReference.class);
            if (eRef != null) {
                eRef.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // ENTITY REFERENCE CLASS RULES
    // =========================================================================

    /**
     * rule CreateReferenceClassForEntityType
     *     transform s : JUDOPSM!EntityType
     *     to t : ASM!EClass
     * 
     * Creates an additional Entity__Reference class for each EntityType.
     * This is a transfer object that holds references to entities.
     * 
     * Note: Inheritance setup is deferred and handled by post-processing because
     * the Zeta framework doesn't guarantee that parent reference classes exist
     * when child reference classes are created.
     */
    @TransformRule(name = "CreateReferenceClassForEntityType", description = "Create reference holder class for EntityType")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createReferenceClassForEntityType() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            setId(t, "(psm/" + getId(s) + ")/ReferenceClassForEntityType");
            t.setName(s.getName() + "__Reference");
            
            // Add reference holder annotation
            EAnnotation refHolderAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/AnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("referenceHolder"));
            addAnnotationDetail(refHolderAnnotation, "value", "true");
            t.getEAnnotations().add(refHolderAnnotation);
            
            // Add transfer object type annotation
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            t.getEAnnotations().add(toAnnotation);
            
            // Add mapped entity type annotation - uses qualified name of the entity
            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("mappedEntityType"));
            addAnnotationDetail(mappedEntityAnnotation, "value", getQualifiedName(s));
            t.getEAnnotations().add(mappedEntityAnnotation);
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            // Note: Inheritance setup is deferred to post-processing in Psm2AsmZetaTransformation
            // because parent reference classes may not exist yet when this rule runs.
            
            return t;
        };
    }

    // Note: Reference class inheritance (SetupReferenceClassInheritance) is handled in post-processing
    // in Psm2AsmZetaTransformation.postProcess() to ensure all reference classes exist first.

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

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

    /**
     * Gets the fully qualified name for an EClassifier (package.name format).
     */
    private String getClassifierFQName(EClassifier classifier) {
        if (classifier == null) {
            return null;
        }
        EPackage pkg = classifier.getEPackage();
        if (pkg != null) {
            return pkg.getName() + "." + classifier.getName();
        }
        return classifier.getName();
    }
}
