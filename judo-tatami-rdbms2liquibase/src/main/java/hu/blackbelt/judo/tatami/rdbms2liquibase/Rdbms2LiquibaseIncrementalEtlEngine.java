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

/**
 * Contract of the optional incremental RDBMS to Liquibase ETL engine, implemented by
 * {@code judo-tatami-rdbms2liquibase-etl} and located through
 * {@link hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry}. The Zeta engine of this artifact needs no add-on.
 * See change: rdbms-incremental-without-epsilon.
 */
public interface Rdbms2LiquibaseIncrementalEtlEngine {

    String ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl";

    /**
     * Runs the incremental ETL (Epsilon) transformation into the eight phase models of {@code parameter}.
     *
     * @return the missing review scripts and the generated backup-data SQL files
     */
    Rdbms2LiquibaseIncrementalResult execute(Rdbms2LiquibaseIncrementalParameter parameter) throws Exception;
}
