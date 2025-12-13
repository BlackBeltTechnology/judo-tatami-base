package hu.blackbelt.judo.tatami.asm2keycloak.zeta.rules;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.AttributeBinding;
import hu.blackbelt.judo.meta.keycloak.Client;
import hu.blackbelt.judo.meta.keycloak.KeycloakFactory;
import hu.blackbelt.judo.meta.keycloak.Realm;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakRuleNames.*;

/**
 * Client transformation rules from client.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateKeycloakClient - transforms actor types to Keycloak clients</li>
 *   <li>CreateKeycloakClientClaim - transforms actor attributes to attribute bindings</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class ClientRules {

    private final KeycloakFactory keycloakFactory;
    private final AsmUtils asmUtils;
    private final Function<String, Realm> realmResolver;
    private final BiFunction<EObject, String, EObject> traceResolver;

    /**
     * Guard for CreateKeycloakClient: isActorType(s) and has non-empty realm annotation
     */
    public boolean isActorWithRealm(EClass eClass) {
        if (!asmUtils.isActorType(eClass)) {
            return false;
        }
        Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(eClass, "realm", false);
        return realmOpt.isPresent() && !realmOpt.get().trim().isEmpty();
    }

    /**
     * Guard for CreateKeycloakClientClaim: container is actor with realm
     */
    public boolean isAttributeOfActorWithRealm(EAttribute attr) {
        EObject container = attr.eContainer();
        if (!(container instanceof EClass)) {
            return false;
        }
        return isActorWithRealm((EClass) container);
    }

    /**
     * rule CreateKeycloakClient
     *     transform s : ASM!EClass
     *     to t : KEYCLOAK!Client
     */
    @TransformRule(name = CREATE_KEYCLOAK_CLIENT, description = "Transform ASM actor type to Keycloak Client")
    @Guard(method = "isActorWithRealm")
    @Transform(type = EClass.class)
    @To(type = Client.class)
    public TransformFunction<EClass, Client> createKeycloakClient() {
        return (s, ctx) -> {
            String realmName = asmUtils.getExtensionAnnotationValue(s, "realm", false).get().trim();
            Realm realm = realmResolver.apply(realmName);
            if (realm == null) {
                log.warn("Realm not found for actor type: {}", s.getName());
                return null;
            }

            Client t = keycloakFactory.createClient();
            String clientName = asmUtils.getClassifierFQName(s).replace(".", "-");
            t.setName(clientName);
            t.setClientId(clientName);
            t.setEnabled(true);
            t.setDirectAccessGrantsEnabled(true);
            t.getRedirectUris().add("*");
            t.setPublicClient(true);
            t.setBearerOnly(false);

            realm.getClients().add(t);

            log.debug("Client created: {}", t.getName());
            return t;
        };
    }

    /**
     * rule CreateKeycloakClientClaim
     *     transform s : ASM!EAttribute
     *     to t : KEYCLOAK!AttributeBinding
     */
    @TransformRule(name = CREATE_KEYCLOAK_CLIENT_CLAIM, description = "Transform ASM actor attribute to Keycloak AttributeBinding")
    @Guard(method = "isAttributeOfActorWithRealm")
    @Transform(type = EAttribute.class)
    @To(type = AttributeBinding.class)
    public TransformFunction<EAttribute, AttributeBinding> createKeycloakClientClaim() {
        return (s, ctx) -> {
            AttributeBinding t = keycloakFactory.createAttributeBinding();

            // Determine attribute name based on claim annotation
            Optional<String> claimType = asmUtils.getExtensionAnnotationValue(s, "claim", false);

            if (claimType.isPresent()) {
                String claim = claimType.get();
                if ("EMAIL".equals(claim)) {
                    t.setAttributeName("email");
                } else if ("USERNAME".equals(claim)) {
                    t.setAttributeName("username");
                } else {
                    t.setAttributeName(s.getName());
                }
            } else if ("email".equals(s.getName())) {
                t.setAttributeName("_email");
            } else if ("username".equals(s.getName())) {
                t.setAttributeName("_username");
            } else {
                t.setAttributeName(s.getName());
            }

            // Get the equivalent client for the container actor type
            EClass actorType = (EClass) s.eContainer();
            Client client = (Client) traceResolver.apply(actorType, CREATE_KEYCLOAK_CLIENT);
            if (client != null) {
                client.getAttributeBindings().add(t);
            }

            return t;
        };
    }
}
