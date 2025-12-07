package hu.blackbelt.judo.tatami.asm2keycloak.zeta;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.*;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakRuleNames.*;

/**
 * Java-based ASM to Keycloak transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * in src/main/epsilon/transformations/, providing a type-safe Java alternative
 * with better IDE support and debugging capabilities.
 * </p>
 */
@Slf4j
public class Asm2KeycloakZetaTransformation {

    private final AsmModel asmModel;
    private final KeycloakModel keycloakModel;
    private final AsmUtils asmUtils;
    private final ResourceSet asmResourceSet;
    private final KeycloakFactory keycloakFactory;

    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    // Cache for realms by name
    private final Map<String, Realm> realmCache = new HashMap<>();

    @Builder
    public Asm2KeycloakZetaTransformation(
            @NonNull AsmModel asmModel,
            @NonNull KeycloakModel keycloakModel) {
        this.asmModel = asmModel;
        this.keycloakModel = keycloakModel;
        this.asmResourceSet = asmModel.getResourceSet();
        this.asmUtils = new AsmUtils(asmResourceSet);
        this.keycloakFactory = KeycloakFactory.eINSTANCE;
    }

    /**
     * Helper method to get all elements of a given type from the ASM model.
     */
    private <T> Stream<T> all(Class<T> clazz) {
        return asmUtils.all(clazz);
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting ASM to Keycloak Zeta transformation");
        long startTime = System.currentTimeMillis();

        // Phase 1: Create realms from actor types
        createRealms();

        // Phase 2: Create clients from actor types
        createClients();

        long duration = System.currentTimeMillis() - startTime;
        log.info("ASM to Keycloak Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    // =========================================================================
    // REALM CREATION
    // =========================================================================

    private void createRealms() {
        log.debug("Creating realms");

        // Collect unique realm names from actor types
        Set<String> realmNames = new HashSet<>();
        asmUtils.getAllActorTypes().forEach(actor -> {
            Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
            if (realmOpt.isPresent() && !realmOpt.get().trim().isEmpty()) {
                realmNames.add(realmOpt.get().trim());
            }
        });

        // Create realm for each unique name
        for (String realmName : realmNames) {
            createRealm(realmName);
        }
    }

    private void createRealm(String realmName) {
        log.debug("  Creating realm: {}", realmName);

        Realm realm = keycloakFactory.createRealm();
        realm.setId(realmName);
        realm.setRealm(realmName);
        realm.setEnabled(true);
        realm.setLoginWithEmailAllowed(true);

        keycloakModel.getResource().getContents().add(realm);
        realmCache.put(realmName, realm);

        log.debug("Realm created: {}", realm.getRealm());
    }

    // =========================================================================
    // CLIENT CREATION
    // =========================================================================

    private void createClients() {
        log.debug("Creating clients");

        asmUtils.getAllActorTypes().forEach(this::createClientIfApplicable);
    }

    private void createClientIfApplicable(EClass actorType) {
        // Check guard: must be actor type with realm annotation
        Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actorType, "realm", false);
        if (!realmOpt.isPresent() || realmOpt.get().trim().isEmpty()) {
            return;
        }

        String realmName = realmOpt.get().trim();
        Realm realm = realmCache.get(realmName);
        if (realm == null) {
            log.warn("Realm not found for actor type: {}", actorType.getName());
            return;
        }

        log.debug("  Creating client for actor: {}", asmUtils.getClassifierFQName(actorType));

        // Create client
        Client client = keycloakFactory.createClient();
        String clientName = asmUtils.getClassifierFQName(actorType).replace(".", "-");
        client.setName(clientName);
        client.setClientId(clientName);
        client.setEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.getRedirectUris().add("*");
        client.setPublicClient(true);
        client.setBearerOnly(false);

        realm.getClients().add(client);
        addTrace(actorType, CREATE_KEYCLOAK_CLIENT, client);

        log.debug("Client created: {}", client.getName());

        // Create attribute bindings for each attribute
        for (EAttribute attr : actorType.getEAttributes()) {
            createAttributeBinding(attr, client);
        }
    }

    private void createAttributeBinding(EAttribute attr, Client client) {
        log.debug("    Creating attribute binding for: {}", attr.getName());

        AttributeBinding binding = keycloakFactory.createAttributeBinding();

        // Determine attribute name based on claim annotation
        Optional<String> claimType = asmUtils.getExtensionAnnotationValue(attr, "claim", false);

        if (claimType.isPresent()) {
            String claim = claimType.get();
            if ("EMAIL".equals(claim)) {
                binding.setAttributeName("email");
            } else if ("USERNAME".equals(claim)) {
                binding.setAttributeName("username");
            } else {
                binding.setAttributeName(attr.getName());
            }
        } else if ("email".equals(attr.getName())) {
            binding.setAttributeName("_email");
        } else if ("username".equals(attr.getName())) {
            binding.setAttributeName("_username");
        } else {
            binding.setAttributeName(attr.getName());
        }

        client.getAttributeBindings().add(binding);
        addTrace(attr, CREATE_KEYCLOAK_CLIENT_CLAIM, binding);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void addTrace(EObject source, String ruleName, EObject target) {
        traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                .put(ruleName, target);
    }

    private EObject getEquivalent(EObject source, String ruleName) {
        Map<String, EObject> rules = traceMap.get(source);
        return rules != null ? rules.get(ruleName) : null;
    }

    private Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
