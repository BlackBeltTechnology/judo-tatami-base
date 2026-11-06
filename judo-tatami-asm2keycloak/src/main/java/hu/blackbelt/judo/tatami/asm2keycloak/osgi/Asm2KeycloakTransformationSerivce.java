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
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakTransformationTrace;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakWork;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

/**
 * Runs the ASM to Keycloak transformation through {@link Asm2KeycloakWork}, honouring the transformation mode
 * (ZETA by default); ETL requires the judo-tatami-asm2keycloak-etl bundle. See change: split-etl-artifacts.
 */
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

        TransformationContext transformationContext = new TransformationContext(asmModel.getName());
        transformationContext.put(asmModel);
        transformationContext.put(keycloakModel);
        try {
            new Asm2KeycloakWork(transformationContext).execute();
        } catch (Exception e) {
            log.error("ASM to Keycloak transformation failed for model: " + asmModel.getName(), e);
            throw e;
        }
        Asm2KeycloakTransformationTrace asm2KeycloakTransformationTrace = transformationContext
                .getByClass(Asm2KeycloakTransformationTrace.class)
                .orElseThrow(() -> new IllegalStateException("ASM to Keycloak transformation produced no trace"));

        asm2keycloakTransformationTraceRegistration.put(asmModel,
                bundleContext.registerService(TransformationTrace.class, asm2KeycloakTransformationTrace, new Hashtable<>()));
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
