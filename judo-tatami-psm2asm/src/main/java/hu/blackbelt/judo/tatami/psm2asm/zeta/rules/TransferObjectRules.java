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
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

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
    public TransformGuard isPrimitiveTransferAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute ta)) return false;
            return ta.getDataType() != null && ta.getDataType() instanceof Primitive;
        };
    }

    /**
     * Guard: mapped transfer object has entity type
     */
    public TransformGuard hasEntityType() {
        return (source, ctx) -> {
            if (!(source instanceof MappedTransferObjectType s)) return false;
            return s.getEntityType() != null;
        };
    }

    /**
     * Guard: transfer attribute has binding (excluding StaticData)
     * ETL: s.isPrimitive() and s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!StaticData)
     */
    public TransformGuard hasBinding() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute attr)) return false;
            Object binding = attr.getBinding();
            // Exclude StaticData bindings - they don't get binding annotation
            return binding != null && !(binding instanceof StaticData);
        };
    }

    /**
     * Guard: transfer attribute has no binding (transient)
     */
    public TransformGuard hasNoBinding() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getBinding() == null;
        };
    }

    /**
     * Guard: transfer attribute has string type
     */
    public TransformGuard isStringTransferAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getDataType() instanceof StringType;
        };
    }

    /**
     * Guard: transfer attribute has custom type
     */
    public TransformGuard isCustomTypeTransferAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getDataType() instanceof CustomType;
        };
    }

    /**
     * Guard: transfer attribute has numeric type (not measured)
     */
    public TransformGuard isNumericTransferAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute ta)) return false;
            return ta.getDataType() instanceof NumericType && !(ta.getDataType() instanceof MeasuredType);
        };
    }

    /**
     * Guard: transfer attribute has measured type
     */
    public TransformGuard isMeasuredTransferAttribute() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getDataType() instanceof MeasuredType;
        };
    }

    /**
     * Guard: transfer relation is access relation
     */
    public TransformGuard isAccessRelation() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectRelation s)) return false;
            return s.isAccess();
        };
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
    public TransformGuard hasNavigationBinding() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectRelation rel)) return false;
            Object binding = rel.getBinding();
            EObject container = rel.eContainer();
            if (binding == null) {
                return false;
            }
            return (container instanceof UnmappedTransferObjectType && binding instanceof ReferenceAccessor) ||
                   (container instanceof MappedTransferObjectType && binding instanceof StaticNavigation);
        };
    }

    /**
     * Guard: transfer attribute has data binding to PrimitiveAccessor (unmapped) or StaticData (mapped)
     * ETL: s.binding.isDefined() and
     *      ((s.eContainer().isKindOf(UnmappedTransferObjectType) and s.binding.isKindOf(PrimitiveAccessor)) or
     *       (s.eContainer().isKindOf(MappedTransferObjectType) and s.binding.isKindOf(StaticData)))
     */
    public TransformGuard hasDataBinding() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute attr)) return false;
            Object binding = attr.getBinding();
            EObject container = attr.eContainer();
            if (binding == null) {
                return false;
            }
            return (container instanceof UnmappedTransferObjectType && binding instanceof PrimitiveAccessor) ||
                   (container instanceof MappedTransferObjectType && binding instanceof StaticData);
        };
    }

    /**
     * Guard: transfer object type has an actorType reference
     * ETL: s.actorType.isDefined()
     */
    public TransformGuard hasActorType() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectType s)) return false;
            return s.getActorType() != null;
        };
    }

    /**
     * Guard: transfer object type is a get range input type.
     * A transfer object is a get range input type if it's the input type of a GET_RANGE operation.
     * Uses pre-computed set from Psm2AsmZetaTransformation.
     */
    @SuppressWarnings("unchecked")
    public TransformGuard isGetRangeInputType() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectType)) return false;
            Object attr = ctx.getAttribute("getRangeInputTypes");
            if (attr instanceof java.util.Set) {
                return ((java.util.Set<TransferObjectType>) attr).contains(source);
            }
            return false;
        };
    }

    /**
     * Guard: transfer attribute has a default value
     * ETL: s.defaultValue.isDefined()
     */
    public TransformGuard hasDefaultValue() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getDefaultValue() != null;
        };
    }

    /**
     * Guard: transfer object relation has a default value
     * ETL: s.defaultValue.isDefined()
     */
    public TransformGuard hasDefaultValueRelation() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectRelation s)) return false;
            return s.getDefaultValue() != null;
        };
    }

    /**
     * Guard: transfer attribute has a claim type
     * ETL: s.claimType.isDefined()
     */
    public TransformGuard hasClaimType() {
        return (source, ctx) -> {
            if (!(source instanceof TransferAttribute s)) return false;
            return s.getClaimType() != null;
        };
    }

    /**
     * Guard: transfer object type is a query customizer
     * ETL: s.queryCustomizer
     */
    public TransformGuard isQueryCustomizer() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectType s)) return false;
            return s.isQueryCustomizer();
        };
    }

    /**
     * Guard: transfer object type is a metadata type.
     * Uses pre-computed set from Psm2AsmZetaTransformation for O(1) lookup instead of
     * O(n) model traversal per call.
     * <p>
     * ETL logic: JUDOPSM!TransferOperation.all().exists(o | o.behaviour.isDefined()
     *      and o.behaviour.behaviourType == GET_METADATA
     *      and o.output.isDefined()
     *      and (o.output.type == self or o.output.type.relations.exists(r | r.target == self)))
     * </p>
     */
    @SuppressWarnings("unchecked")
    public TransformGuard isMetadataType() {
        return (source, ctx) -> {
            if (!(source instanceof TransferObjectType)) return false;
            Object attr = ctx.getAttribute("metadataTypes");
            if (attr instanceof java.util.Set) {
                return ((java.util.Set<TransferObjectType>) attr).contains(source);
            }
            return false;
        };
    }

    /**
     * Guard: element has documentation
     */
    public TransformGuard hasDocumentation() {
        return (source, ctx) -> {
            if (!(source instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement s)) return false;
            String doc = s.getDocumentation();
            return doc != null && !doc.isEmpty();
        };
    }

    /**
     * Helper: Convert iterator to stream
     */
    private static <T> java.util.stream.Stream<T> streamOf(java.util.Iterator<T> iterator) {
        return java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(iterator, 0), false);
    }

    // =========================================================================
    // TRANSFER OBJECT TYPE RULES
    // =========================================================================

    /**
     * rule CreateMappedTransferObjectTypeClass
     *     transform s : JUDOPSM!MappedTransferObjectType
     *     to t : ASM!EClass
     */
    @TransformRule(name = CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS, description = "Transform MappedTransferObjectType to EClass")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<MappedTransferObjectType, EClass> createMappedTransferObjectTypeClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            t.setName(s.getName());
            
            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * rule CreateMappedTransferObjectAnnotation
     *     transform s : JUDOPSM!MappedTransferObjectType
     *     to t : ASM!EAnnotation
     *
     * When s.filter.isDefined(), adds:
     *   - filter key with value s.filter.expression
     *   - filter.dialect key with value s.filter.dialect.asString()
     */
    @TransformRule(name = CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_MAPPED_TRANSFER_OBJECT, description = "Add mappedEntityType annotation")
    @Greedy
    @Guard(method = "hasEntityType")
    @Transform(type = MappedTransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<MappedTransferObjectType, EAnnotation> createMappedTransferObjectAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("mappedEntityType"));
            addAnnotationDetail(t, "value", getQualifiedName(s.getEntityType()));

            // Add filter and filterDialect details if filter is defined
            if (s.getFilter() != null) {
                addAnnotationDetail(t, "filter", s.getFilter().getExpression());
                addAnnotationDetail(t, "filter.dialect", s.getFilter().getDialect().toString());
            }

            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * rule CreateUnmappedTransferObjectTypeClass
     *     transform s : JUDOPSM!UnmappedTransferObjectType
     *     to t : ASM!EClass
     */
    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS, description = "Transform UnmappedTransferObjectType to EClass")
    @Transform(type = UnmappedTransferObjectType.class)
    @To(type = EClass.class)
    public TransformFunction<UnmappedTransferObjectType, EClass> createUnmappedTransferObjectTypeClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            t.setName(s.getName());
            
            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addClassifier(containerPkg, t);

            return t;
        };
    }

    /**
     * rule CreateTransferObjectTypeAnnotation
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation
     */
    @TransformRule(name = CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS, description = "Add transferObjectType annotation")
    @Greedy
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createTransferObjectTypeAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("transferObjectType"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * rule CreateQueryCustomizerAnnotationForQueryCustomizerClass
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation {
     *         guard: s.queryCustomizer
     *         t.source = asmUtils.getAnnotationUri("queryCustomizer");
     *         t.details.add({key="value", value="true"});
     *         s.asmEquivalent().eAnnotations.add(t);
     *     }
     */
    @TransformRule(name = CREATE_QUERY_CUSTOMIZER_ANNOTATION, description = "Add queryCustomizer annotation to query customizer types")
    @Greedy
    @Guard(method = "isQueryCustomizer")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createQueryCustomizerAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("queryCustomizer"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * rule CreateMetadataAnnotationForMetadataClass
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation {
     *         guard: s.isMetadataType()
     *         t.source = asmUtils.getAnnotationUri("metadata");
     *         t.details.add({key="value", value="true"});
     *         s.asmEquivalent().eAnnotations.add(t);
     *     }
     */
    @TransformRule(name = CREATE_METADATA_ANNOTATION, description = "Add metadata annotation to metadata types")
    @Greedy
    @Guard(method = "isMetadataType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createMetadataAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("metadata"));
            addAnnotationDetail(t, "value", "true");

            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

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
    @TransformRule(name = CREATE_ACTOR_ANNOTATION, description = "Add actor annotation to transfer objects referencing an actor")
    @Greedy
    @Guard(method = "hasActorType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createActorAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("actor"));
            
            AbstractActorType actorType = s.getActorType();
            
            // Add actor name - use :: separator format to match ETL (enrichWithAnnotations expects this)
            addAnnotationDetail(t, "name", getQualifiedNameWithColons((NamespaceElement) actorType));
            
            // Add realm if defined
            if (actorType.getRealm() != null && !actorType.getRealm().isEmpty()) {
                addAnnotationDetail(t, "realm", actorType.getRealm());
            }
            
            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

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
    @TransformRule(name = CREATE_GET_RANGE_INPUT_ANNOTATION, description = "Add getRangeInput annotation for GET_RANGE input types")
    @Greedy
    @Guard(method = "isGetRangeInputType")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createGetRangeInputAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("getRangeInput"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForTransferObjectType
     *     transform s : JUDOPSM!TransferObjectType
     *     to t : ASM!EAnnotation
     *         extends CreateDocumentationAnnotation
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_TYPE, description = "Add documentation annotation to transfer object type")
    @Greedy
    @Guard(method = "hasDocumentation")
    @Transform(type = TransferObjectType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectType, EAnnotation> createDocumentationAnnotationForTransferObjectType() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

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
    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE, description = "Transform TransferAttribute to EAttribute")
    @Guard(method = "isPrimitiveTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAttribute.class)
    public TransformFunction<TransferAttribute, EAttribute> createTransferAttribute() {
        return (s, ctx) -> {
            EAttribute t = ctx.createTarget(EAttribute.class);
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
            
            // Add to owning transfer object class (thread-safe)
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addStructuralFeature(ownerClass, t);
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
    @TransformRule(name = CREATE_TRANSFER_OBJECT_ATTRIBUTE_BINDING_ANNOTATION, description = "Add binding annotation to transfer attribute")
    @Guard(method = "hasBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> addTransferAttributeBindingAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("binding"));
            
            // Get binding path
            String bindingPath = s.getBinding().getName();
            addAnnotationDetail(t, "value", bindingPath);
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = ADD_TRANSIENT_ANNOTATION_TO_TRANSFER_ATTRIBUTE, description = "Add transient annotation to unbound transfer attribute")
    @Guard(method = "hasNoBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addTransientAnnotationToTransferAttribute() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("transient"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = ADD_STRING_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add string constraints for transfer attribute")
    @Guard(method = "isStringTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addStringTransferAttributeConstraints() {
        return (s, ctx) -> {
            StringType stringType = (StringType) s.getDataType();

            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "maxLength", String.valueOf(stringType.getMaxLength()));
            
            if (stringType.getRegExp() != null && !stringType.getRegExp().trim().isEmpty()) {
                addAnnotationDetail(t, "pattern", stringType.getRegExp());
            }
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = ADD_CUSTOM_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add custom type constraints for transfer attribute")
    @Guard(method = "isCustomTypeTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addCustomTransferAttributeConstraints() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            
            String qualifiedName = getQualifiedName((NamespaceElement) s.getDataType());
            addAnnotationDetail(t, "customType", qualifiedName);
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = ADD_NUMERIC_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add numeric constraints for transfer attribute")
    @Guard(method = "isNumericTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addNumericTransferAttributeConstraints() {
        return (s, ctx) -> {
            NumericType numericType = (NumericType) s.getDataType();

            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(t, "scale", String.valueOf(numericType.getScale()));
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = ADD_MEASURED_TRANSFER_ATTRIBUTE_CONSTRAINTS, description = "Add measured constraints for transfer attribute")
    @Guard(method = "isMeasuredTransferAttribute")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addMeasuredTransferAttributeConstraints() {
        return (s, ctx) -> {
            MeasuredType measuredType = (MeasuredType) s.getDataType();

            EAnnotation t = ctx.createTarget(EAnnotation.class);
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
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = CREATE_DATA_REFERENCE_BINDING, description = "Add expression annotation for data bindings")
    @Guard(method = "hasDataBinding")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> createDataReferenceBinding() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
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
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule AddDefaultAnnotationToTransferAttribute
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation {
     *         guard: s.defaultValue.isDefined()
     *         t.source = asmUtils.getAnnotationUri("default");
     *         t.details.add({key="value", value=s.defaultValue.name});
     *         s.equivalent("CreateTransferObjectAttribute").eAnnotations.add(t);
     *     }
     */
    @TransformRule(name = ADD_DEFAULT_ANNOTATION_TO_TRANSFER_ATTRIBUTE, description = "Add default annotation to transfer attribute with default value")
    @Guard(method = "hasDefaultValue")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> addDefaultAnnotationToTransferAttribute() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("default"));
            addAnnotationDetail(t, "value", s.getDefaultValue().getName());

            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule CreateTransferAttributeClaimAnnotation
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation {
     *         guard: s.claimType.isDefined()
     *         t.source = asmUtils.getAnnotationUri("claim");
     *         t.details.add({key="value", value=s.claimType});
     *         s.equivalent("CreateTransferObjectAttribute").eAnnotations.add(t);
     *     }
     */
    @TransformRule(name = CREATE_TRANSFER_ATTRIBUTE_CLAIM_ANNOTATION, description = "Add claim annotation to transfer attribute with claim type")
    @Guard(method = "hasClaimType")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferAttribute, EAnnotation> createTransferAttributeClaimAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("claim"));
            addAnnotationDetail(t, "value", s.getClaimType());

            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForTransferAttribute
     *     transform s : JUDOPSM!TransferAttribute
     *     to t : ASM!EAnnotation
     *         extends CreateDocumentationAnnotation
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_ATTRIBUTE, description = "Add documentation annotation to transfer attribute")
    @Greedy
    @Guard(method = "hasDocumentation")
    @Transform(type = TransferAttribute.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferAttribute, EAnnotation> createDocumentationAnnotationForTransferAttribute() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

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
    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION, description = "Transform TransferObjectRelation to EReference")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EReference.class)
    public TransformFunction<TransferObjectRelation, EReference> createTransferObjectRelation() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
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
            
            // Add binding annotation inline (avoids recursive update from separate rule, thread-safe)
            // ETL rule guard: s.binding.isDefined() and not s.binding.isKindOf(JUDOPSM!StaticNavigation)
            if (s.getBinding() != null && !(s.getBinding() instanceof StaticNavigation)) {
                EAnnotation bindingAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationBindingAnnotation",
                        getAnnotationUri("binding"));
                addAnnotationDetail(bindingAnnotation, "value", s.getBinding().getName());
                addAnnotation(t, bindingAnnotation);
            }

            // Add range annotation for relations that have a range (thread-safe)
            // ETL rule: CreateTransferObjectRelationRangeAnnotation guard: s.range.isDefined()
            if (s.getRange() != null) {
                EAnnotation rangeAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationRangeAnnotation",
                        getAnnotationUri("range"));
                addAnnotationDetail(rangeAnnotation, "value", s.getRange().getName());
                addAnnotation(t, rangeAnnotation);
            }

            // Add embedded annotation inline (avoids recursive update from separate rule, thread-safe)
            // ETL rule: CreateTransferObjectRelationEmbeddedFlags adds value, create, update, delete
            if (s.isEmbedded()) {
                EAnnotation embeddedAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransferObjectRelationEmbeddedFlags",
                        getAnnotationUri("embedded"));
                addAnnotationDetail(embeddedAnnotation, "value", "true");
                addAnnotationDetail(embeddedAnnotation, "create", String.valueOf(s.isEmbeddedCreate()));
                addAnnotationDetail(embeddedAnnotation, "update", String.valueOf(s.isEmbeddedUpdate()));
                addAnnotationDetail(embeddedAnnotation, "delete", String.valueOf(s.isEmbeddedDelete()));
                addAnnotation(t, embeddedAnnotation);
            }

            // Add transient annotation for relations without binding and not access (thread-safe)
            // ETL rule: AddTransientAnnotationToTransferObjectRelation
            if (s.getBinding() == null && !isAccessRelation(s)) {
                EAnnotation transientAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/TransientAnnotationToTransferObjectRelation",
                        getAnnotationUri("transient"));
                addAnnotationDetail(transientAnnotation, "value", "true");
                addAnnotation(t, transientAnnotation);
            }

            // Add to owning transfer object class (thread-safe)
            TransferObjectType owner = (TransferObjectType) s.eContainer();
            if (owner != null) {
                EClass ownerClass = ctx.equivalent(owner, EClass.class);
                addStructuralFeature(ownerClass, t);
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
    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_ACCESS_ANNOTATION, description = "Add access annotation to access relations")
    @Guard(method = "isAccessRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationAccessAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("access"));
            addAnnotationDetail(t, "value", "true");
            
            // Add to equivalent reference (thread-safe)
            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

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
    @TransformRule(name = CREATE_TRANSFER_OBJECT_RELATION_PERMISSIONS, description = "Add permissions annotation to transfer relations")
    @Greedy
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createTransferObjectRelationPermissions() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("permissions"));
            addAnnotationDetail(t, "create", String.valueOf(s.isEmbeddedCreate()));
            addAnnotationDetail(t, "update", String.valueOf(s.isEmbeddedUpdate()));
            addAnnotationDetail(t, "delete", String.valueOf(s.isEmbeddedDelete()));
            
            // Add to equivalent reference (thread-safe)
            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

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
    @TransformRule(name = CREATE_NAVIGATION_REFERENCE_BINDING, description = "Add expression annotation for navigation bindings")
    @Guard(method = "hasNavigationBinding")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferObjectRelation, EAnnotation> createNavigationReferenceBinding() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
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
            
            // Add to equivalent reference (thread-safe)
            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

            return t;
        };
    }

    /**
     * rule AddDefaultAnnotationToTransferObjectRelation
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EAnnotation {
     *         guard: s.defaultValue.isDefined()
     *         t.source = asmUtils.getAnnotationUri("default");
     *         t.details.add({key="value", value=s.defaultValue.name});
     *         s.equivalent("CreateTransferObjectRelation").eAnnotations.add(t);
     *     }
     */
    @TransformRule(name = ADD_DEFAULT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION, description = "Add default annotation to transfer relation with default value")
    @Guard(method = "hasDefaultValueRelation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<TransferObjectRelation, EAnnotation> addDefaultAnnotationToTransferObjectRelation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("default"));
            addAnnotationDetail(t, "value", s.getDefaultValue().getName());

            // Add to equivalent reference (thread-safe)
            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForTransferObjectRelation
     *     transform s : JUDOPSM!TransferObjectRelation
     *     to t : ASM!EAnnotation
     *         extends CreateDocumentationAnnotation
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_RELATION, description = "Add documentation annotation to transfer object relation")
    @Greedy
    @Guard(method = "hasDocumentation")
    @Transform(type = TransferObjectRelation.class)
    @To(type = EAnnotation.class)
    public TransformFunction<TransferObjectRelation, EAnnotation> createDocumentationAnnotationForTransferObjectRelation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());

            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

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
    @TransformRule(name = CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE, description = "Create reference holder class for EntityType")
    @Transform(type = EntityType.class)
    @To(type = EClass.class)
    public TransformFunction<EntityType, EClass> createReferenceClassForEntityType() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            t.setName(s.getName() + "__Reference");
            
            // Add reference holder annotation (thread-safe)
            EAnnotation refHolderAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/AnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("referenceHolder"));
            addAnnotationDetail(refHolderAnnotation, "value", "true");
            addAnnotation(t, refHolderAnnotation);

            // Add transfer object type annotation (thread-safe)
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClassForReferenceClass",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            addAnnotation(t, toAnnotation);

            // Add mapped entity type annotation - uses qualified name of the entity (thread-safe)
            EAnnotation mappedEntityAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/MappedEntityTypeAnnotationOnReferenceClassForEntityType",
                    getAnnotationUri("mappedEntityType"));
            addAnnotationDetail(mappedEntityAnnotation, "value", getQualifiedName(s));
            addAnnotation(t, mappedEntityAnnotation);

            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addClassifier(containerPkg, t);
            
            // Note: Inheritance setup is deferred to post-processing in Psm2AsmZetaTransformation
            // because parent reference classes may not exist yet when this rule runs.
            
            return t;
        };
    }

    // Note: Reference class inheritance (SetupReferenceClassInheritance) is handled in post-processing
    // in Psm2AsmZetaTransformation.postProcess() to ensure all reference classes exist first.

}
