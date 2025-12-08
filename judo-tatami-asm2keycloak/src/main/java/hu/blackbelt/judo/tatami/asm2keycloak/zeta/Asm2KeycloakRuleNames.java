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

/**
 * Rule name constants for ASM to Keycloak transformation.
 * These correspond to the ETL rules in asmToKeycloak.etl and related modules.
 */
public final class Asm2KeycloakRuleNames {

    private Asm2KeycloakRuleNames() {
        // Private constructor to prevent instantiation
    }

    // Realm transformation rules
    public static final String CREATE_REALM = "CreateRealm";

    // Client transformation rules
    public static final String CREATE_KEYCLOAK_CLIENT = "CreateKeycloakClient";
    public static final String CREATE_KEYCLOAK_CLIENT_CLAIM = "CreateKeycloakClientClaim";
}
