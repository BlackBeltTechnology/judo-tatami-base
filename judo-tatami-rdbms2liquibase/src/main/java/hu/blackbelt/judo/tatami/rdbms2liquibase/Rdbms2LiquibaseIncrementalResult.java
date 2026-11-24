package hu.blackbelt.judo.tatami.rdbms2liquibase;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase
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

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of an incremental RDBMS to Liquibase transformation, identical for the Zeta and the ETL engine.
 * See change: rdbms-incremental-without-epsilon.
 */
@Builder(builderMethodName = "rdbms2LiquibaseIncrementalResult")
@Getter
public final class Rdbms2LiquibaseIncrementalResult {

    /** Backup-data SQL scripts: file name to script content. */
    @Builder.Default
    private final Map<String, String> backupDataSqlFiles = new HashMap<>();

    /** Review scripts the resolver does not know: script name to a human readable message. */
    @Builder.Default
    private final Map<String, String> missingReviewScripts = new HashMap<>();
}
