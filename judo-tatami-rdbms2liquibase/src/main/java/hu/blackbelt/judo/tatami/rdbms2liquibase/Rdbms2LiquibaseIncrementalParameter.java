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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;

/**
 * Parameters of {@link Rdbms2LiquibaseIncrementalTransformation}. Either {@code reviewResolver} or
 * {@code sqlScriptPath} has to be set. See change: rdbms-incremental-without-epsilon.
 */
@Builder(builderMethodName = "rdbms2LiquibaseIncrementalParameter")
@Getter
public final class Rdbms2LiquibaseIncrementalParameter {

    public static final String DEFAULT_BACKUP_PREFIX = "BACKUP";

    /** Incremental RDBMS model: operation model, meta, previous and new root. */
    @NonNull
    private final RdbmsModel incrementalRdbmsModel;

    @NonNull
    private final LiquibaseModel dbCheckupLiquibaseModel;

    @NonNull
    private final LiquibaseModel dbBackupLiquibaseModel;

    @NonNull
    private final LiquibaseModel beforeIncrementalLiquibaseModel;

    @NonNull
    private final LiquibaseModel updateDataBeforeIncrementalLiquibaseModel;

    @NonNull
    private final LiquibaseModel incrementalLiquibaseModel;

    @NonNull
    private final LiquibaseModel updateDataAfterIncrementalLiquibaseModel;

    @NonNull
    private final LiquibaseModel afterIncrementalLiquibaseModel;

    @NonNull
    private final LiquibaseModel dbDropBackupLiquibaseModel;

    @NonNull
    private final String dialect;

    @Builder.Default
    private final String backupPrefix = DEFAULT_BACKUP_PREFIX;

    /** Maximal table name size; {@code null} or not positive selects 30 for {@code oracle}, 62 otherwise. */
    private final Integer tableNameMaxSize;

    /** Resolves review scripts. Wins over {@code sqlScriptPath}. */
    private final ReviewResolver reviewResolver;

    /** Directory of review scripts; used when no {@code reviewResolver} is given. */
    private final String sqlScriptPath;

    /** Directory where the backup-data SQL files are written; nothing is written when blank. */
    private final String sqlOutput;

    /** Engine selection. Defaults to {@link TransformationMode#ZETA}. */
    @Builder.Default
    private final TransformationMode transformationMode = TransformationMode.ZETA;

    /** ETL engine only: logger of the Epsilon execution; {@code null} uses a buffered SLF4J logger. */
    private final Logger log;

    /** ETL engine only: executes the ETL rules in parallel. */
    @Builder.Default
    private final Boolean parallel = true;
}
