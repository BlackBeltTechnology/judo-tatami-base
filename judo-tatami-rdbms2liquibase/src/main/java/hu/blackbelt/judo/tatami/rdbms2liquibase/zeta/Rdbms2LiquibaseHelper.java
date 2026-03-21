package hu.blackbelt.judo.tatami.rdbms2liquibase.zeta;

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

import hu.blackbelt.judo.meta.rdbms.RdbmsField;

/**
 * Helper class with shared utility methods for RDBMS to Liquibase transformations.
 * These methods are used by both full and incremental Zeta transformations.
 */
public final class Rdbms2LiquibaseHelper {

    private Rdbms2LiquibaseHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts an RDBMS field definition to a Liquibase type definition string.
     * <p>
     * Format examples:
     * <ul>
     *   <li>VARCHAR(255)</li>
     *   <li>DECIMAL(10, 2)</li>
     *   <li>INTEGER</li>
     * </ul>
     *
     * @param field the RDBMS field to convert
     * @return the type definition string, or empty string if field has no type name
     */
    public static String toFieldDefinition(RdbmsField field) {
        if (field.getRdbmsTypeName() != null) {
            StringBuilder typedef = new StringBuilder(field.getRdbmsTypeName().toUpperCase());
            if (field.getPrecision() > 0) {
                typedef.append("(").append(field.getPrecision());
                if (field.getScale() > 0) {
                    typedef.append(", ").append(field.getScale());
                }
                typedef.append(")");
            } else if (field.getSize() > 0) {
                typedef.append("(").append(field.getSize()).append(")");
            }
            return typedef.toString();
        }
        return "";
    }

    /**
     * Abbreviates a string to the specified maximum length.
     *
     * @param str       the string to abbreviate
     * @param maxLength the maximum length
     * @return the abbreviated string, or empty string if input is null
     */
    public static String abbreviate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength);
    }
}
