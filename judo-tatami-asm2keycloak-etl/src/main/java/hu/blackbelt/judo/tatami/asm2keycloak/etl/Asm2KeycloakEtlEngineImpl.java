package hu.blackbelt.judo.tatami.asm2keycloak.etl;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak :: ETL
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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakEtlEngine;
import hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakTransformationTrace;
import org.slf4j.Logger;

/**
 * {@link Asm2KeycloakEtlEngine} backed by the Epsilon ETL scripts of this artifact.
 * Discovered via META-INF/services (plain Java) or registered by {@code osgi.Asm2KeycloakEtlEngineComponent} (Karaf).
 */
public class Asm2KeycloakEtlEngineImpl implements Asm2KeycloakEtlEngine {

    @Override
    public Asm2KeycloakTransformationTrace execute(AsmModel asmModel, KeycloakModel keycloakModel, Logger log,
                                                   boolean createTrace, boolean useCache, boolean parallel) throws Exception {
        return Asm2Keycloak.executeAsm2KeycloakTransformation(Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter()
                .asmModel(asmModel)
                .keycloakModel(keycloakModel)
                .log(log)
                .createTrace(createTrace)
                .useCache(useCache)
                .parallel(parallel));
    }
}
