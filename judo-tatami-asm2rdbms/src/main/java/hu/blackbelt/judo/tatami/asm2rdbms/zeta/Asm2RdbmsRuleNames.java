package hu.blackbelt.judo.tatami.asm2rdbms.zeta;

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
 * Constants for ASM to RDBMS transformation rule names.
 * These constants are used for tracing transformations and identifying
 * the source rules that created target elements.
 */
public final class Asm2RdbmsRuleNames {

    private Asm2RdbmsRuleNames() {
        // Utility class - prevent instantiation
    }

    // Package transformation rules
    public static final String ROOT_PACKAGE_TO_MODEL = "rootPackegeToModel";
    public static final String ROOT_PACKAGE_TO_CONFIGURATION = "rootPackegeToConfiguration";

    // Class transformation rules
    public static final String ECLASS_TO_RDBMS_TABLE = "EClassToRdbmsTable";
    public static final String ECLASS_TO_TABLE_ID_FIELD = "EClassToTableIdField";
    public static final String ECLASS_TO_TABLE_TYPE_FIELD = "EClassToTableTypeField";
    public static final String ECLASS_TO_TABLE_VERSION_FIELD = "EClassToTableVersionField";
    public static final String ECLASS_TO_TABLE_CREATE_USERNAME_FIELD = "EClassToTableCreateUsernameField";
    public static final String ECLASS_TO_TABLE_CREATE_USER_ID_FIELD = "EClassToTableCreateUserIdField";
    public static final String ECLASS_TO_TABLE_CREATE_TIMESTAMP_FIELD = "EClassToTableCreateTimestampField";
    public static final String ECLASS_TO_TABLE_UPDATE_USERNAME_FIELD = "EClassToTableUpdateUsernameField";
    public static final String ECLASS_TO_TABLE_UPDATE_USER_ID_FIELD = "EClassToTableUpdateUserIdField";
    public static final String ECLASS_TO_TABLE_UPDATE_TIMESTAMP_FIELD = "EClassToTableUpdateTimestampField";

    // Attribute transformation rules
    public static final String EATTRIBUTE_TO_TABLE_VALUE_FIELD = "EAttributeToTableValueField";
    public static final String EATTRIBUTE_TO_INDEX = "EAttributeToIndex";

    // Reference transformation rules
    public static final String EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY = "EReferenceToRdbmsTableForeignKey";
    public static final String EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY = "EReferenceToRdbmsTableInverseForeignKey";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE = "EReferenceToRdbmsJunctionTable";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE_PRIMARY_KEY = "EReferenceToRdbmsJunctionTablePrimaryKey";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_BIDIRECTIONAL = "EReferenceToRdbmsJunctionTableForeignKeyBidirectional";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_1 = "EReferenceToRdbmsJunctionTableForeignKeyUnidirectional1";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_2 = "EReferenceToRdbmsJunctionTableForeignKeyUnidirectional2";
}
