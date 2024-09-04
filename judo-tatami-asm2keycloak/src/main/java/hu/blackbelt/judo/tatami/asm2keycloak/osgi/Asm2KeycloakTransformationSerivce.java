package hu.blackbelt.judo.tatami.asm2keycloak.osgi;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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

import com.google.common.collect.Maps;
import hu.blackbelt.epsilon.runtime.execution.impl.LogLevel;
import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakTransformationTrace;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static hu.blackbelt.judo.tatami.core.AnsiColor.red;
import static hu.blackbelt.judo.tatami.core.AnsiColor.yellow;

@Component(immediate = true, service = Asm2KeycloakTransformationSerivce.class)
@Slf4j
public class Asm2KeycloakTransformationSerivce {

    Map<AsmModel, ServiceRegistration<TransformationTrace>> asm2keycloakTransformationTraceRegistration = Maps.newHashMap();
    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }


    public KeycloakModel install(AsmModel asmModel) throws Exception {

        KeycloakModel keycloakModel = KeycloakModel.buildKeycloakModel()
                .name(asmModel.getName())
                .version(asmModel.getVersion())
                .build();

        StringBuilderLogger logger = new StringBuilderLogger(LogLevel.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/asm2keycloak/transformations/asmToKeycloak.etl")
                            .toURI()
                            .resolve(".");

            Asm2KeycloakTransformationTrace asm2KeycloakTransformationTrace = executeAsm2KeycloakTransformation(Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter()
                            .asmModel(asmModel)
                            .keycloakModel(keycloakModel)
                            .log(logger)
                            .scriptUri(scriptUri));

            asm2keycloakTransformationTraceRegistration.put(asmModel,
                    bundleContext.registerService(TransformationTrace.class, asm2KeycloakTransformationTrace, new Hashtable<>()));

            log.info(yellow("{}"), logger.getBuffer());
        } catch (Exception e) {
            log.info(red("{}"), logger.getBuffer());
            throw e;
        }
        return keycloakModel;
    }

    public void uninstall(AsmModel asmModel) {
        if (asm2keycloakTransformationTraceRegistration.containsKey(asmModel)) {
            asm2keycloakTransformationTraceRegistration.get(asmModel).unregister();
        } else {
            log.error("ASM model is not installed: " + asmModel.toString());
        }
    }
}
