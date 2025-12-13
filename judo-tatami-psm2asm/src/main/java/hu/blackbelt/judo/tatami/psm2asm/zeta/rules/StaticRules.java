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

import hu.blackbelt.judo.meta.psm.derived.StaticData;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;

/**
 * Static data transformation rules from static.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateUnmappedTransferObjectForStaticData - transforms StaticData to EClass</li>
 *   <li>CreateTransferObjectTypeAnnotationClassForStaticData - adds transferObjectType annotation</li>
 *   <li>CreateStaticDataQueryAnnotation - adds staticQuery annotation</li>
 *   <li>CreateStaticQueryAttribute - creates attribute for static query</li>
 *   <li>CreateDataReferenceBindingForStaticData - adds expression annotation</li>
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
    // GUARDS
    // =========================================================================

    /**
     * Guard: static data has parameterized getter
     */
    public boolean hasParameterizedGetter(EObject source, TransformationContext ctx) {
        if (source instanceof StaticData) {
            StaticData sd = (StaticData) source;
            return sd.getGetterExpression() != null && 
                   sd.getGetterExpression().getParameterType() != null;
        }
        return false;
    }

    // =========================================================================
    // STATIC DATA RULES
    // =========================================================================

    /**
     * rule CreateUnmappedTransferObjectForStaticData
     *     transform s : JUDOPSM!StaticData
     *     to t : ASM!EClass
     */
    @TransformRule(name = "CreateUnmappedTransferObjectForStaticData", description = "Transform StaticData to unmapped transfer object EClass")
    @Transform(type = StaticData.class)
    @To(type = EClass.class)
    @Greedy
    public TransformFunction<StaticData, EClass> createUnmappedTransferObjectForStaticData() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            setId(t, "(psm/" + getId(s) + ")/UnmappedTransferObjectForStaticData");
            
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
            setId(attr, "(psm/" + getId(s) + ")/StaticQueryAttribute");
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
     * Gets the fully qualified name of an EClassifier.
     */
    private String getClassifierFQName(EClassifier classifier) {
        if (classifier == null) return null;
        StringBuilder sb = new StringBuilder();
        EPackage pkg = classifier.getEPackage();
        while (pkg != null) {
            if (sb.length() > 0) {
                sb.insert(0, ".");
            }
            sb.insert(0, pkg.getName());
            pkg = pkg.getESuperPackage();
        }
        sb.append(".").append(classifier.getName());
        return sb.toString();
    }
}
