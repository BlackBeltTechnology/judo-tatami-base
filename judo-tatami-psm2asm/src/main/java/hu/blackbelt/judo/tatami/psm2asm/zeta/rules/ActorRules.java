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
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.accesspoint.MappedActorType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.zeta.annotation.*;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.*;

import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper.*;
import static hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmRuleNames.*;

/**
 * Actor type transformation rules from accesspoint.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateActorTypeClass - transforms ActorType to EClass</li>
 *   <li>CreateMappedActorTypeClass - transforms MappedActorType to EClass</li>
 *   <li>CreateActorTypeAnnotation - adds actorType annotation</li>
 *   <li>CreateRealmAnnotation - adds realm annotation</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = AbstractActorType.class, target = EClass.class)
public class ActorRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public ActorRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

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
     * Guard: actor type has realm
     */
    public boolean hasRealm(EObject source, TransformationContext ctx) {
        if (source instanceof AbstractActorType) {
            String realm = ((AbstractActorType) source).getRealm();
            return realm != null && !realm.isEmpty();
        }
        return false;
    }

    // =========================================================================
    // ACTOR TYPE RULES
    // =========================================================================

    /**
     * rule CreateActorTypeClass
     *     transform s : JUDOPSM!ActorType
     *     to t : ASM!EClass
     */
    @TransformRule(name = CREATE_ACTOR_TYPE_CLASS, description = "Transform ActorType to EClass")
    @Transform(type = ActorType.class)
    @To(type = EClass.class)
    public TransformFunction<ActorType, EClass> createActorTypeClass() {
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
     * rule CreateMappedActorTypeClass
     *     transform s : JUDOPSM!MappedActorType
     *     to t : ASM!EClass
     * 
     * Note: The inheritance from entityType should NOT be set on the actor class.
     * ETL creates the actor class via CreateMappedTransferObjectTypeClass which only
     * sets up inheritance from transfer object super types, not from entityType.
     */
    @TransformRule(name = CREATE_MAPPED_ACTOR_TYPE_CLASS, description = "Transform MappedActorType to EClass")
    @Transform(type = MappedActorType.class)
    @To(type = EClass.class)
    public TransformFunction<MappedActorType, EClass> createMappedActorTypeClass() {
        return (s, ctx) -> {
            EClass t = ctx.createTarget(EClass.class);
            t.setName(s.getName());
            t.setAbstract(s.isAbstract());
            
            // Add to container package (thread-safe)
            EPackage containerPkg = getContainerPackage(s, ctx);
            addClassifier(containerPkg, t);

            // Do NOT add inheritance from transferObjectType or entityType here
            // ETL only sets up inheritance from transfer object super types
            
            return t;
        };
    }

    /**
     * Note: transferObjectType annotation is handled by TransferObjectRules.CreateTransferObjectTypeAnnotation
     * which transforms TransferObjectType (base class of AbstractActorType), so no separate rule needed here.
     */

    /**
     * rule CreateActorTypeAnnotation
     *     transform s : JUDOPSM!AbstractActorType
     *     to t : ASM!EAnnotation
     * 
     * ETL includes managed (for MappedActorType) and kind details.
     */
    @TransformRule(name = CREATE_ACTOR_TYPE_ANNOTATION, description = "Add actorType annotation")
    @Greedy
    @Transform(type = AbstractActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AbstractActorType, EAnnotation> createActorTypeAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("actorType"));
            addAnnotationDetail(t, "value", "true");
            
            // Add managed for MappedActorType
            if (s instanceof MappedActorType) {
                addAnnotationDetail(t, "managed", String.valueOf(((MappedActorType) s).isManaged()));
            }
            
            // Add kind if defined
            if (s.getKind() != null) {
                addAnnotationDetail(t, "kind", s.getKind().toString());
            }
            
            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * rule CreateRealmAnnotation
     *     transform s : JUDOPSM!AbstractActorType
     *     to t : ASM!EAnnotation {
     *         guard: s.realm.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_REALM_TYPE_ANNOTATION, description = "Add realm annotation to actor type")
    @Greedy
    @Guard(method = "hasRealm")
    @Transform(type = AbstractActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AbstractActorType, EAnnotation> createRealmAnnotation() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("realm"));
            addAnnotationDetail(t, "value", s.getRealm());
            
            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

    /**
     * Note: mappedEntityType annotation for MappedActorType is handled by TransferObjectRules
     * via CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_MAPPED_TRANSFER_OBJECT since
     * MappedActorType extends MappedTransferObjectType.
     */

    /**
     * rule CreateDocumentationAnnotationForActorType
     *     transform s : JUDOPSM!AbstractActorType
     *     to t : ASM!EAnnotation {
     *         guard: s.documentation.isDefined()
     *     }
     */
    @TransformRule(name = CREATE_DOCUMENTATION_ANNOTATION_FOR_ACTOR_TYPE, description = "Add documentation annotation to actor")
    @Greedy
    @Guard(method = "hasDocumentation")
    @Transform(type = AbstractActorType.class)
    @To(type = EAnnotation.class)
    public TransformFunction<AbstractActorType, EAnnotation> createDocumentationAnnotationForActorType() {
        return (s, ctx) -> {
            EAnnotation t = ctx.createTarget(EAnnotation.class);
            t.setSource(getAnnotationUri("documentation"));
            addAnnotationDetail(t, "value", s.getDocumentation());
            
            // Add to equivalent class (thread-safe)
            EClass eClass = ctx.equivalent(s, EClass.class);
            addAnnotation(eClass, t);

            return t;
        };
    }

}
