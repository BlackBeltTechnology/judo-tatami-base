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

import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Namespace transformation rules from namespace.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>NamespaceToPackage - @Abstract base rule for namespace to EPackage</li>
 *   <li>ModelToPackage - transforms Model to root EPackage</li>
 *   <li>ModelToPackageVersion - adds version annotation to model package</li>
 *   <li>PackageToPackage - transforms Package to sub-EPackage</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = Namespace.class, target = EPackage.class)
public class NamespaceRules {

    private String nsURI;
    private String nsPrefix;

    /**
     * Default constructor required for TransformationRegistry.
     */
    public NamespaceRules() {
    }

    /**
     * Constructor with namespace configuration.
     *
     * @param nsURI    the namespace URI prefix
     * @param nsPrefix the namespace prefix
     */
    public NamespaceRules(String nsURI, String nsPrefix) {
        this.nsURI = nsURI;
        this.nsPrefix = nsPrefix;
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard: source has a version (non-null, non-empty)
     */
    public boolean hasVersion(EObject source, TransformationContext ctx) {
        if (source instanceof Model) {
            String version = ((Model) source).getVersion();
            return version != null && !version.isEmpty();
        }
        return false;
    }

    // =========================================================================
    // ABSTRACT RULES
    // =========================================================================

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
    public TransformFunction<Namespace, EPackage> namespaceToPackage() {
        return (s, ctx) -> {
            EPackage t = ctx.createTarget(EPackage.class);
            t.setName(s.getName());
            return t;
        };
    }

    // =========================================================================
    // MODEL RULES
    // =========================================================================

    /**
     * rule ModelToPackage
     *     transform s : JUDOPSM!Model
     *     to t : ASM!EPackage
     *     extends NamespaceToPackage
     */
    @TransformRule(name = MODEL_TO_PACKAGE, description = "Transform Model to root EPackage")
    @Extends({NAMESPACE_TO_PACKAGE})
    @Transform(type = Model.class)
    @To(type = EPackage.class)
    public TransformFunction<Model, EPackage> modelToPackage() {
        return (s, ctx) -> {
            // Execute parent rule
            EPackage t = ctx.executeParentRule(NAMESPACE_TO_PACKAGE, s);

            // Get namespace configuration from context or use defaults
            String modelName = s.getName();
            String ctxNsURI = ctx.getAttribute("nsURI");
            String ctxNsPrefix = ctx.getAttribute("nsPrefix");
            
            String uri = ctxNsURI != null ? ctxNsURI : 
                         (nsURI != null ? nsURI : "http://blackbelt.hu/judo/" + modelName);
            String prefix = ctxNsPrefix != null ? ctxNsPrefix : 
                            (nsPrefix != null ? nsPrefix : "runtime" + capitalize(modelName));
            
            t.setNsURI(uri + "/" + modelName);
            t.setNsPrefix(prefix + capitalize(modelName));
            
            return t;
        };
    }

    /**
     * rule ModelToPackageVersion
     *     transform s : JUDOPSM!Model
     *     to t : ASM!EAnnotation {
     *         guard: s.version.isDefined()
     *     }
     */
    @TransformRule(name = MODEL_TO_PACKAGE_VERSION, description = "Add version annotation to model package")
    @Guard(method = "hasVersion")
    @Transform(type = Model.class)
    @To(type = EAnnotation.class)
    public TransformFunction<Model, EAnnotation> modelToPackageVersion() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("ModelVersion"));
            addAnnotationDetail(t, "value", s.getVersion());
            
            // Add to package
            EPackage pkg = ctx.equivalent(s, EPackage.class);
            if (pkg != null) {
                pkg.getEAnnotations().add(t);
            }
            
            return t;
        };
    }

    // =========================================================================
    // PACKAGE RULES
    // =========================================================================

    /**
     * rule PackageToPackage
     *     transform s : JUDOPSM!Package
     *     to t : ASM!EPackage
     *     extends NamespaceToPackage
     */
    @TransformRule(name = PACKAGE_TO_PACKAGE, description = "Transform Package to sub-EPackage")
    @Extends({NAMESPACE_TO_PACKAGE})
    @Transform(type = Package.class)
    @To(type = EPackage.class)
    public TransformFunction<Package, EPackage> packageToPackage() {
        return (s, ctx) -> {
            // Execute parent rule
            EPackage t = ctx.executeParentRule(NAMESPACE_TO_PACKAGE, s);

            // Get parent package - for Package, the parent is always Model or Package
            EPackage parentPkg = getContainerPackage(s, ctx);
            if (parentPkg != null) {
                // Set namespace URI and prefix based on parent
                t.setNsURI(parentPkg.getNsURI() + "/" + s.getName());
                t.setNsPrefix(parentPkg.getNsPrefix() + capitalize(s.getName()));
                
                // Add to parent
                parentPkg.getESubpackages().add(t);
            }
            
            return t;
        };
    }

}
