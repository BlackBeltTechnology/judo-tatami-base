package hu.blackbelt.judo.tatami.asm2keycloak;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
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
import org.slf4j.Logger;

/**
 * Contract of the optional ASM to Keycloak ETL engine, implemented by {@code judo-tatami-asm2keycloak-etl}
 * and located through {@link hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry}.
 * See change: split-etl-artifacts.
 */
public interface Asm2KeycloakEtlEngine {

    String ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-asm2keycloak-etl";

    /**
     * Runs the ETL transformation.
     *
     * @param log logger for the Epsilon execution; {@code null} uses a buffered SLF4J logger
     */
    Asm2KeycloakTransformationTrace execute(AsmModel asmModel, KeycloakModel keycloakModel, Logger log,
                                            boolean createTrace, boolean useCache, boolean parallel) throws Exception;
}
