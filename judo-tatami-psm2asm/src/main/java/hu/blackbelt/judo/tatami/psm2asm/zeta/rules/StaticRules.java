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

import hu.blackbelt.judo.meta.psm.derived.ReferenceAccessor;
import hu.blackbelt.judo.meta.psm.derived.StaticData;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.service.MappedTransferObjectType;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Static data and navigation transformation rules from static.etl.
 * <p>
 * StaticData Rules:
 * <ul>
 *   <li>CreateUnmappedTransferObjectForStaticData - transforms StaticData to EClass</li>
 *   <li>CreateTransferObjectTypeAnnotationClassForStaticData - adds transferObjectType annotation</li>
 *   <li>CreateStaticDataQueryAnnotation - adds staticQuery annotation</li>
 *   <li>CreateStaticQueryAttribute - creates attribute for static query</li>
 *   <li>CreateDataReferenceBindingForStaticData - adds expression annotation</li>
 * </ul>
 * <p>
 * StaticNavigation Rules:
 * <ul>
 *   <li>CreateUnmappedTransferObjectForStaticNavigation - transforms StaticNavigation to EClass</li>
 *   <li>CreateTransferObjectTypeAnnotationClassForStaticNavigation - adds transferObjectType annotation</li>
 *   <li>CreateStaticNavigationQueryAnnotation - adds staticQuery annotation</li>
 *   <li>CreateStaticQueryNavigation - creates EReference for navigation</li>
 *   <li>CreateNavigationReferenceBindingForStaticNavigation - adds expression annotation</li>
 *   <li>CreateTransferObjectRelationParameterizedAnnotationForStaticNavigation - adds parameterized annotation</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = StaticData.class, target = EClass.class)
public class StaticRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public StaticRules() {
    }

    // =========================================================================
    // STATIC DATA RULES
    // =========================================================================

    /**
     * rule CreateUnmappedTransferObjectForStaticData
     *     transform s : JUDOPSM!StaticData
     *     to t : ASM!EClass
     */
    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA, description = "Transform StaticData to unmapped transfer object EClass")
    @Transform(type = StaticData.class)
    @To(type = EClass.class)
    @Greedy
    public TransformFunction<StaticData, EClass> createUnmappedTransferObjectForStaticData() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);

            // Name is the static data name with first letter uppercased
            String name = s.getName();
            if (name != null && !name.isEmpty()) {
                t.setName(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            // Add transferObjectType annotation inline
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClassForStaticData",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            t.getEAnnotations().add(toAnnotation);
            
            // Add staticQuery annotation inline
            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/StaticDataQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            t.getEAnnotations().add(staticQueryAnnotation);
            
            // Create and add the static query attribute
            EAttribute attr = EcorePackage.eINSTANCE.getEcoreFactory().createEAttribute();
            attr.setName(s.getName());
            attr.setLowerBound(s.isRequired() ? 1 : 0);
            attr.setDerived(true);
            attr.setChangeable(false);
            
            // Set type
            if (s.getDataType() != null) {
                EClassifier type = ctx.equivalent(s.getDataType(), EClassifier.class);
                if (type != null) {
                    attr.setEType(type);
                }
            }
            
            t.getEStructuralFeatures().add(attr);
            
            // Add expression annotation for the attribute
            if (s.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/DataReferenceBindingForStaticData",
                        getAnnotationUri("expression"));
                addAnnotationDetail(exprAnnotation, "getter", s.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", s.getGetterExpression().getDialect().toString());
                
                // Add parameter type if defined
                if (s.getGetterExpression().getParameterType() != null) {
                    EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));
                    }
                }
                
                attr.getEAnnotations().add(exprAnnotation);
                
                // Add parameterized annotation if has parameter type
                if (s.getGetterExpression().getParameterType() != null) {
                    EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                    if (paramType != null) {
                        EAnnotation paramAnnotation = createAnnotation(
                                "(psm/" + getId(s) + ")/TransferAttributeParameterizedAnnotationForStaticData",
                                getAnnotationUri("parameterized"));
                        addAnnotationDetail(paramAnnotation, "value", "true");
                        addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                        attr.getEAnnotations().add(paramAnnotation);
                    }
                }
            }
            
            return t;
        };
    }

    // =========================================================================
    // STATIC NAVIGATION RULES
    // =========================================================================

    /**
     * rule CreateUnmappedTransferObjectForStaticNavigation
     *     transform s : JUDOPSM!StaticNavigation
     *     to t : ASM!EClass
     *     guard: s.target.defaultRepresentation.isDefined()
     * 
     * Creates an EClass for StaticNavigation elements that have a default representation.
     * Also adds transferObjectType and staticQuery annotations, and creates the navigation EReference.
     */
    @TransformRule(name = CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION, description = "Transform StaticNavigation to unmapped transfer object EClass")
    @Transform(type = StaticNavigation.class)
    @To(type = EClass.class)
    @Greedy
    public TransformFunction<StaticNavigation, EClass> createUnmappedTransferObjectForStaticNavigation() {
        return (s, ctx) -> {
            // Guard: s.target.defaultRepresentation.isDefined()
            if (s.getTarget() == null || s.getTarget().getDefaultRepresentation() == null) {
                return null;
            }
            
            EClass t = ctx.createTarget(EClass.class);

            // Name is the static navigation name with first letter uppercased
            String name = s.getName();
            if (name != null && !name.isEmpty()) {
                t.setName(Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
            
            // Add to container package
            EPackage containerPkg = getContainerPackage(s, ctx);
            if (containerPkg != null) {
                containerPkg.getEClassifiers().add(t);
            }
            
            // Add transferObjectType annotation inline
            EAnnotation toAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/TransferObjectTypeAnnotationClassForStaticNavigation",
                    getAnnotationUri("transferObjectType"));
            addAnnotationDetail(toAnnotation, "value", "true");
            t.getEAnnotations().add(toAnnotation);
            
            // Add staticQuery annotation inline
            EAnnotation staticQueryAnnotation = createAnnotation(
                    "(psm/" + getId(s) + ")/StaticNavigationQueryAnnotation",
                    getAnnotationUri("staticQuery"));
            t.getEAnnotations().add(staticQueryAnnotation);
            
            // Create and add the static query navigation EReference
            EReference ref = EcorePackage.eINSTANCE.getEcoreFactory().createEReference();
            ref.setName(s.getName());
            ref.setContainment(false);
            
            // Set cardinality
            if (s.getCardinality() != null) {
                ref.setLowerBound(s.getCardinality().getLower());
                ref.setUpperBound(s.getCardinality().getUpper());
            }
            
            // Set type to the mapped transfer object for the default representation
            MappedTransferObjectType defaultRep = s.getTarget().getDefaultRepresentation();
            if (defaultRep != null) {
                EClass targetClass = ctx.equivalent(defaultRep, EClass.class);
                if (targetClass != null) {
                    ref.setEType(targetClass);
                }
            }
            
            ref.setDerived(true);
            ref.setChangeable(false);
            
            t.getEStructuralFeatures().add(ref);
            
            // Add expression annotation for the reference
            if (s.getGetterExpression() != null) {
                EAnnotation exprAnnotation = createAnnotation(
                        "(psm/" + getId(s) + ")/NavigationReferenceBindingForStaticNavigation",
                        getAnnotationUri("expression"));
                addAnnotationDetail(exprAnnotation, "getter", s.getGetterExpression().getExpression());
                addAnnotationDetail(exprAnnotation, "getter.dialect", s.getGetterExpression().getDialect().toString());
                
                // Add parameter type if defined
                if (s.getGetterExpression().getParameterType() != null) {
                    EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                    if (paramType != null) {
                        addAnnotationDetail(exprAnnotation, "getter.parameter", getClassifierFQName(paramType));
                    }
                }
                
                // Add setter if defined
                if (s.getSetterExpression() != null) {
                    addAnnotationDetail(exprAnnotation, "setter", s.getSetterExpression().getExpression());
                    addAnnotationDetail(exprAnnotation, "setter.dialect", s.getSetterExpression().getDialect().toString());
                    
                    if (s.getSetterExpression().getParameterType() != null) {
                        EClass setterParamType = ctx.equivalent(s.getSetterExpression().getParameterType(), EClass.class);
                        if (setterParamType != null) {
                            addAnnotationDetail(exprAnnotation, "setter.parameter", getClassifierFQName(setterParamType));
                        }
                    }
                }
                
                ref.getEAnnotations().add(exprAnnotation);
                
                // Add parameterized annotation if has parameter type and is ReferenceAccessor
                // Guard: s.isKindOf(JUDOPSM!ReferenceAccessor) and s.getterExpression.parameterType.isDefined()
                if (s instanceof ReferenceAccessor && s.getGetterExpression().getParameterType() != null) {
                    EClass paramType = ctx.equivalent(s.getGetterExpression().getParameterType(), EClass.class);
                    if (paramType != null) {
                        EAnnotation paramAnnotation = createAnnotation(
                                "(psm/" + getId(s) + ")/TransferObjectRelationParameterizedAnnotationForStaticNavigation",
                                getAnnotationUri("parameterized"));
                        addAnnotationDetail(paramAnnotation, "value", "true");
                        addAnnotationDetail(paramAnnotation, "type", getClassifierFQName(paramType));
                        ref.getEAnnotations().add(paramAnnotation);
                    }
                }
            }
            
            return t;
        };
    }

}
