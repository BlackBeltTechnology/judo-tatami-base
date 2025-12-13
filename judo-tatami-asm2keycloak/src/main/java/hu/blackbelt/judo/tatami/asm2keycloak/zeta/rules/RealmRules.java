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
import hu.blackbelt.judo.meta.keycloak.KeycloakFactory;
import hu.blackbelt.judo.meta.keycloak.Realm;
import hu.blackbelt.judo.zeta.annotation.PreExecution;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Realm transformation rules from realm.etl.
 * <p>
 * This implements the pre-execution block that creates Keycloak realms
 * from unique realm names found in actor type annotations.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class RealmRules {

    private final KeycloakFactory keycloakFactory;
    private final AsmUtils asmUtils;
    private final Consumer<Realm> realmConsumer;
    private final BiConsumer<EClass, Realm> traceConsumer;

    /**
     * Pre-execution hook that creates realms before transformation rules run.
     * 
     * ETL equivalent (realm.etl pre block):
     * <pre>
     * pre {
     *     var realms = new Set();
     *     for (actor in asmUtils.getAllActorTypes()) {
     *         var realm = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
     *         if (realm.present) {
     *             realms.add(realm.get);
     *         }
     *     }
     *     for (realmName in realms) {
     *         var r = new KEYCLOAK!Realm;
     *         r.id = realmName;
     *         r.realm = realmName;
     *         r.enabled = true;
     *         r.loginWithEmailAllowed = true;
     *     }
     * }
     * </pre>
     */
    @PreExecution
    public void createRealmsPreHook(TransformationContext ctx) {
        createRealms();
    }

    /**
     * Create realms from unique realm names in actor types.
     */
    public void createRealms() {
        log.debug("Creating realms");

        // Collect actor types grouped by realm name (first actor for each realm is the source for tracing)
        Map<String, EClass> realmToFirstActor = new LinkedHashMap<>();
        asmUtils.getAllActorTypes().forEach(actor -> {
            Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
            if (realmOpt.isPresent() && !realmOpt.get().trim().isEmpty()) {
                String realmName = realmOpt.get().trim();
                realmToFirstActor.putIfAbsent(realmName, actor);
            }
        });

        // Create realm for each unique name
        for (Map.Entry<String, EClass> entry : realmToFirstActor.entrySet()) {
            createRealm(entry.getKey(), entry.getValue());
        }
    }

    private void createRealm(String realmName, EClass sourceActor) {
        log.debug("  Creating realm: {} (from actor: {})", realmName, sourceActor.getName());

        Realm realm = keycloakFactory.createRealm();
        realm.setId(realmName);
        realm.setRealm(realmName);
        realm.setEnabled(true);
        realm.setLoginWithEmailAllowed(true);

        realmConsumer.accept(realm);
        traceConsumer.accept(sourceActor, realm);

        log.debug("Realm created: {}", realm.getRealm());
    }
}
