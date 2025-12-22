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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.rules.ClientRules;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.rules.RealmRules;
import hu.blackbelt.judo.zeta.common.ExtensionMethodRegistry;
import hu.blackbelt.judo.zeta.common.ModelProvider;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import hu.blackbelt.judo.zeta.transformation.core.TransformationExecutor;
import hu.blackbelt.judo.zeta.transformation.core.TransformationRegistry;
import hu.blackbelt.judo.zeta.transformation.core.TransformationResult;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.Collection;

/**
 * ASM to Keycloak transformation using Zeta framework's TransformationRegistry and TransformationExecutor.
 * <p>
 * This class uses the Zeta framework's declarative rule-based transformation approach:
 * <ul>
 *   <li>Rules are declared in separate classes with @TransformRule annotations</li>
 *   <li>TransformationRegistry scans and registers all rules</li>
 *   <li>TransformationExecutor executes rules in proper order</li>
 * </ul>
 * </p>
 * <p>
 * Rule classes:
 * <ul>
 *   <li>{@link RealmRules} - realm.etl rules (pre-execution realm creation)</li>
 *   <li>{@link ClientRules} - client.etl rules (CreateKeycloakClient, CreateKeycloakClientClaim)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Asm2KeycloakZetaTransformation {

    private final AsmModel asmModel;
    private final KeycloakModel keycloakModel;

    @Builder
    public Asm2KeycloakZetaTransformation(
            @NonNull AsmModel asmModel,
            @NonNull KeycloakModel keycloakModel) {
        this.asmModel = asmModel;
        this.keycloakModel = keycloakModel;
    }

    /**
     * Execute the transformation using TransformationExecutor.
     *
     * @return Zeta TransformationTrace containing source to target element mappings
     */
    public TransformationTrace execute() {
        log.info("Starting ASM to Keycloak Zeta transformation");
        long startTime = System.currentTimeMillis();

        // Create registry and register all rule classes
        TransformationRegistry registry = createRegistry();

        // Create transformation context
        TransformationContext context = createContext(registry);

        // Create executor with sequential execution
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)
                .build();

        // Execute transformation
        log.debug("Starting executor.transform()");
        TransformationResult result = executor.transform();
        log.debug("Finished executor.transform()");

        long duration = System.currentTimeMillis() - startTime;
        log.info("ASM to Keycloak Zeta transformation completed in {}ms", duration);

        // Return native Zeta trace
        return result.getTrace();
    }

    /**
     * Creates and configures the TransformationRegistry with all rule classes.
     */
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();

        // Phase 1: Realm rules (pre-execution creates realms)
        registry.register(RealmRules.class);

        // Phase 2: Client rules (transforms actor types to clients)
        registry.register(ClientRules.class);

        log.debug("Registered {} rule classes with TransformationRegistry", 2);
        return registry;
    }

    /**
     * Creates and configures the TransformationContext.
     */
    private TransformationContext createContext(TransformationRegistry registry) {
        ResourceSet sourceResourceSet = asmModel.getResourceSet();
        ResourceSet targetResourceSet = keycloakModel.getResourceSet();

        // Create model provider
        ModelProvider modelProvider = new Asm2KeycloakModelProvider(asmModel);

        // Create extension method registry
        ExtensionMethodRegistry extensionRegistry = new ExtensionMethodRegistry();

        // Create context
        TransformationContext context = new TransformationContext(
                modelProvider,
                sourceResourceSet,
                targetResourceSet,
                extensionRegistry
        );

        // Configure context
        context.setTransformationRegistry(registry);

        // Register resources with aliases
        context.registerResource("source", sourceResourceSet);
        context.registerResource("asm", sourceResourceSet);
        context.registerResource("target", targetResourceSet);
        context.registerResource("keycloak", targetResourceSet);

        // Store utilities in context attributes for rules to access
        AsmUtils asmUtils = new AsmUtils(sourceResourceSet);
        context.setAttribute("asmUtils", asmUtils);
        context.setAttribute("keycloakResource", keycloakModel.getResource());

        return context;
    }

    /**
     * ModelProvider implementation for ASM to Keycloak transformation.
     */
    private static class Asm2KeycloakModelProvider implements ModelProvider {
        private final AsmModel asmModel;
        private final AsmUtils asmUtils;

        public Asm2KeycloakModelProvider(AsmModel asmModel) {
            this.asmModel = asmModel;
            this.asmUtils = new AsmUtils(asmModel.getResourceSet());
        }

        @Override
        public <T extends EObject> Collection<T> getAllContents(ResourceSet resourceSet, Class<T> type) {
            return asmUtils.all(type).toList();
        }

        @Override
        public String getName(EObject element) {
            if (element instanceof org.eclipse.emf.ecore.ENamedElement) {
                return ((org.eclipse.emf.ecore.ENamedElement) element).getName();
            }
            return ModelProvider.super.getName(element);
        }

        @Override
        public String getTypeName(EObject element) {
            return element.eClass().getName();
        }
    }
}
