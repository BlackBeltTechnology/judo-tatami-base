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

/**
 * Rule name constants for RDBMS to Liquibase transformation.
 * These correspond to the ETL rules in rdbmsToLiquibase.etl and related modules.
 */
public final class Rdbms2LiquibaseRuleNames {

    private Rdbms2LiquibaseRuleNames() {
        // Private constructor to prevent instantiation
    }

    // Table transformation rules
    public static final String TABLE_TO_CREATE_TABLE = "TableToCreateTable";
    public static final String TABLE_TO_CREATE_TABLE_CHANGESET = "TableToCreateTableChangeSet";
    public static final String TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET = "TableToCreateForeignKeysChangeSet";
    public static final String TABLE_TO_ADD_NOT_NULL_CHANGESET = "TableToAddNotNullChangeSet";

    // Field transformation rules
    public static final String IDENTIFIER_FIELD_TO_COLUMN = "IdentifierFieldToCreateTableColumn";
    public static final String IDENTIFIER_FIELD_TO_PK_CONSTRAINT = "IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint";
    public static final String VALUE_FIELD_TO_COLUMN = "ValueFieldToCreateTableColumn";
    public static final String FOREIGN_KEY_FIELD_TO_COLUMN = "ForeignKeyFieldToCreateTableColumn";
    public static final String FOREIGN_KEY_TO_ADD_FK_CONSTRAINT = "ForeignKeyFieldToCreateTableAddForeignKeyConstraint";
    public static final String FIELD_TO_ADD_NOT_NULL_CONSTRAINT = "FieldToCreateTableAddNotNullConstraint";

    // Index transformation rules
    public static final String INDEX_TO_CREATE_INDEX = "IndexToCreateIndex";

    // Unique constraint transformation rules
    public static final String UNIQUE_CONSTRAINT_TO_ADD_UNIQUE = "AddUniqueConstraints";
}
