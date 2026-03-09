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

import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.derived.NavigationProperty;
import hu.blackbelt.judo.meta.psm.derived.PrimitiveAccessor;
import hu.blackbelt.judo.meta.psm.derived.ReferenceAccessor;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.type.CustomType;
import hu.blackbelt.judo.meta.psm.type.NumericType;
import hu.blackbelt.judo.meta.psm.type.Primitive;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.meta.psm.derived.StaticData;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Derived property transformation rules from derived.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateDataPropertyForDerivedAttribute - transforms DataProperty to EAttribute</li>
 *   <li>CreateNavigationPropertyForDerivedReference - transforms NavigationProperty to EReference</li>
 *   <li>AddPrimitiveAccessorConstraints - adds constraints annotation for derived attributes</li>
 *   <li>AddPrimitiveAccessorExpressionAnnotation - adds getter/setter expression annotations</li>
 *   <li>AddReferenceAccessorExpressionAnnotation - adds getter/setter expression for references</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = DataProperty.class, target = EAttribute.class)
public class DerivedRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public DerivedRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: data property has a primitive data type
     */
    public TransformGuard isPrimitiveDataProperty() {
        return (source, ctx) -> {
            if (!(source instanceof DataProperty dp)) return false;
            return dp.getDataType() != null && dp.getDataType() instanceof Primitive;
        };
    }

    /**
     * Guard: element has documentation
     */
    public TransformGuard hasDocumentation() {
        return (source, ctx) -> {
            if (!(source instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement named)) return false;
            String doc = named.getDocumentation();
            return doc != null && !doc.isEmpty();
        };
    }

    /**
     * Guard: primitive accessor has getter expression
     */
    public TransformGuard hasGetterExpression() {
        return (source, ctx) -> {
            if (!(source instanceof PrimitiveAccessor pa)) return false;
            return pa.getGetterExpression() != null;
        };
    }

    /**
     * Guard: reference accessor has getter expression
     */
    public TransformGuard hasReferenceGetterExpression() {
        return (source, ctx) -> {
            if (!(source instanceof ReferenceAccessor ra)) return false;
            return ra.getGetterExpression() != null;
        };
    }

    /**
     * Guard: data property has string type
     */
    public TransformGuard isStringPrimitiveAccessor() {
        return (source, ctx) -> {
            if (!(source instanceof DataProperty dp)) return false;
            return dp.getDataType() instanceof StringType;
        };
    }

    /**
     * Guard: data property has numeric type
     */
    public TransformGuard isNumericPrimitiveAccessor() {
        return (source, ctx) -> {
            if (!(source instanceof DataProperty dp)) return false;
            return dp.getDataType() instanceof NumericType;
        };
    }

    /**
     * Guard: data property has custom type (not StaticData)
     * Matches ETL: not s.isKindOf(JUDOPSM!StaticData) and s.dataType.isKindOf(JUDOPSM!CustomType)
     */
    public TransformGuard isCustomTypePrimitiveAccessor() {
        return (source, ctx) -> {
            if (!(source instanceof DataProperty dp) || source instanceof StaticData) return false;
            return dp.getDataType() instanceof CustomType;
        };
    }

    // =========================================================================
    // DATA PROPERTY RULES
    // =========================================================================

    /**
     * rule CreateDataPropertyForDerivedAttribute
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAttribute {
     *         guard: s.dataType.isKindOf(Primitive)
     *     }
     */
    @TransformRule(name = CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE, description = "Transform DataProperty to derived EAttribute")
    @Guard(method = "isPrimitiveDataProperty")
    @Transform(type = DataProperty.class)
    @To(type = EAttribute.class)
    public TransformFunction<DataProperty, EAttribute> createDataPropertyForDerivedAttribute() {
        return (s, ctx) -> {
            EAttribute t = ctx.createTarget(EAttribute.class);
            t.setName(s.getName());
            t.setDerived(true);
            t.setVolatile(true);
            t.setLowerBound(s.isRequired() ? 1 : 0);
            t.setChangeable(s.getSetterExpression() != null);
            
            // Set type
            EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
            if (type != null) {
                t.setEType(type);
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

    /**
     * rule AddStringPrimitiveAccessorConstraints
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAnnotation {
     *         guard: s.dataType.isKindOf(StringType)
     *     }
     */
    @TransformRule(name = ADD_STRING_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add string constraints for derived attribute")
    @Guard(method = "isStringPrimitiveAccessor")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<DataProperty, EAnnotation> addStringPrimitiveAccessorConstraints() {
        return (s, ctx) -> {
            StringType stringType = (StringType) s.getDataType();
            
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "maxLength", String.valueOf(stringType.getMaxLength()));
            
            if (stringType.getRegExp() != null && !stringType.getRegExp().isEmpty()) {
                addAnnotationDetail(t, "pattern", stringType.getRegExp());
            }
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule AddNumericPrimitiveAccessorConstraints
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAnnotation {
     *         guard: s.dataType.isKindOf(NumericType)
     *     }
     */
    @TransformRule(name = ADD_NUMERIC_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add numeric constraints for derived attribute")
    @Guard(method = "isNumericPrimitiveAccessor")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<DataProperty, EAnnotation> addNumericPrimitiveAccessorConstraints() {
        return (s, ctx) -> {
            NumericType numericType = (NumericType) s.getDataType();
            
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            addAnnotationDetail(t, "precision", String.valueOf(numericType.getPrecision()));
            addAnnotationDetail(t, "scale", String.valueOf(numericType.getScale()));
            
            // Add measured annotations if applicable
            if (numericType instanceof MeasuredType) {
                MeasuredType measuredType = (MeasuredType) numericType;
                if (measuredType.getStoreUnit() != null) {
                    if (measuredType.getStoreUnit().eContainer() instanceof NamespaceElement) {
                        addAnnotationDetail(t, "measure",
                            getQualifiedName((NamespaceElement) measuredType.getStoreUnit().eContainer()));
                    }
                    addAnnotationDetail(t, "unit", measuredType.getStoreUnit().getName());
                }
            }
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule AddCustomPrimitiveAccessorConstraints
     *     transform s : JUDOPSM!PrimitiveAccessor
     *     to t : ASM!EAnnotation
     *     extends AddPrimitiveAccessorConstraints {
     *         guard: not s.isKindOf(JUDOPSM!StaticData) and s.dataType.isKindOf(JUDOPSM!CustomType)
     *     }
     */
    @TransformRule(name = ADD_CUSTOM_PRIMITIVE_ACCESSOR_CONSTRAINTS, description = "Add custom type constraints for derived attribute")
    @Guard(method = "isCustomTypePrimitiveAccessor")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    @Greedy
    public TransformFunction<DataProperty, EAnnotation> addCustomPrimitiveAccessorConstraints() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("constraints"));
            
            // Add customType detail
            String qualifiedName = getQualifiedName((NamespaceElement) s.getDataType());
            addAnnotationDetail(t, "customType", qualifiedName);
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule AddPrimitiveAccessorExpressionAnnotation
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAnnotation {
     *         guard: s.getterExpression.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_PRIMITIVE_ACCESSOR_EXPRESSION_ANNOTATION, description = "Add getter/setter expression annotation")
    @Guard(method = "hasGetterExpression")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<DataProperty, EAnnotation> addPrimitiveAccessorExpressionAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("expression"));
            
            // Add getter expression
            addAnnotationDetail(t, "getter", s.getGetterExpression().getExpression());
            addAnnotationDetail(t, "getter.dialect", s.getGetterExpression().getDialect().toString());
            
            // Add parameter type if defined
            if (s.getGetterExpression().getParameterType() != null) {
                EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                if (paramType != null) {
                    addAnnotationDetail(t, "getter.parameter", 
                        getClassifierFQName(paramType));
                }
            }
            
            // Add setter expression if defined
            if (s.getSetterExpression() != null) {
                addAnnotationDetail(t, "setter", s.getSetterExpression().getExpression());
                addAnnotationDetail(t, "setter.dialect", s.getSetterExpression().getDialect().toString());
            }
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    /**
     * rule CreateDocumentationAnnotationForDataProperty
     *     transform s : JUDOPSM!DataProperty
     *     to t : ASM!EAnnotation {
     *         guard: s.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_DATA_PROPERTY, description = "Add documentation annotation")
    @Guard(method = "hasDocumentation")
    @Transform(type = DataProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<DataProperty, EAnnotation> createDocumentationAnnotationForDataProperty() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());
            
            // Add to equivalent attribute (thread-safe)
            EAttribute eAttr = ctx.equivalent(s, EAttribute.class);
            addAnnotation(eAttr, t);

            return t;
        };
    }

    // =========================================================================
    // NAVIGATION PROPERTY RULES
    // =========================================================================

    /**
     * rule CreateNavigationPropertyForDerivedReference
     *     transform s : JUDOPSM!NavigationProperty
     *     to t : ASM!EReference
     */
    @TransformRule(name = CREATE_NAVIGATION_PROPERTY, description = "Transform NavigationProperty to derived EReference")
    @Transform(type = NavigationProperty.class)
    @To(type = EReference.class)
    public TransformFunction<NavigationProperty, EReference> createNavigationPropertyForDerivedReference() {
        return (s, ctx) -> {
            EReference t = ctx.createTarget(EReference.class);
            t.setName(s.getName());
            t.setDerived(true);
            t.setVolatile(true);
            t.setChangeable(s.getSetterExpression() != null);
            
            if (s.getCardinality() != null) {
                t.setLowerBound(s.getCardinality().getLower());
                t.setUpperBound(s.getCardinality().getUpper());
            }
            
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

    /**
     * rule AddReferenceAccessorExpressionAnnotation
     *     transform s : JUDOPSM!NavigationProperty
     *     to t : ASM!EAnnotation {
     *         guard: s.getterExpression.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_REFERENCE_ACCESSOR_EXPRESSION_ANNOTATION, description = "Add getter/setter expression for navigation")
    @Guard(method = "hasReferenceGetterExpression")
    @Transform(type = NavigationProperty.class)
    @To(type = EAnnotation.class)
    public TransformFunction<NavigationProperty, EAnnotation> addReferenceAccessorExpressionAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("expression"));
            
            // Add getter expression
            addAnnotationDetail(t, "getter", s.getGetterExpression().getExpression());
            addAnnotationDetail(t, "getter.dialect", s.getGetterExpression().getDialect().toString());
            
            // Add parameter type if defined
            if (s.getGetterExpression().getParameterType() != null) {
                EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                if (paramType != null) {
                    addAnnotationDetail(t, "getter.parameter",
                        getClassifierFQName(paramType));
                }
            }
            
            // Add setter expression if defined
            if (s.getSetterExpression() != null) {
                addAnnotationDetail(t, "setter", s.getSetterExpression().getExpression());
                addAnnotationDetail(t, "setter.dialect", s.getSetterExpression().getDialect().toString());
            }
            
            // Add to equivalent reference (thread-safe)
            EReference eRef = ctx.equivalent(s, EReference.class);
            addAnnotation(eRef, t);

            return t;
        };
    }

}
