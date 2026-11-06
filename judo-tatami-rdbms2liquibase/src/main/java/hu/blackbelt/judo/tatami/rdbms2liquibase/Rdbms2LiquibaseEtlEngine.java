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
import org.slf4j.Logger;

/**
 * Contract of the optional RDBMS to Liquibase ETL engine, implemented by {@code judo-tatami-rdbms2liquibase-etl}
 * and located through {@link hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry}.
 * See change: split-etl-artifacts.
 */
public interface Rdbms2LiquibaseEtlEngine {

    String ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl";

    /**
     * Runs the ETL transformation into {@code liquibaseModel}.
     *
     * @param log logger for the Epsilon execution; {@code null} uses a buffered SLF4J logger
     */
    void execute(RdbmsModel rdbmsModel, LiquibaseModel liquibaseModel, String dialect, Logger log,
                 boolean createTrace, boolean useCache, boolean parallel) throws Exception;
}
