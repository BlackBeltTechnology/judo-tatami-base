package hu.blackbelt.judo.tatami.rdbms2liquibase.etl;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase :: ETL
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
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseEtlEngine;
import org.slf4j.Logger;

/**
 * {@link Rdbms2LiquibaseEtlEngine} backed by the Epsilon ETL scripts of this artifact.
 * Discovered via META-INF/services (plain Java) or registered by {@code osgi.Rdbms2LiquibaseEtlEngineComponent} (Karaf).
 */
public class Rdbms2LiquibaseEtlEngineImpl implements Rdbms2LiquibaseEtlEngine {

    @Override
    public void execute(RdbmsModel rdbmsModel, LiquibaseModel liquibaseModel, String dialect, Logger log,
                        boolean createTrace, boolean useCache, boolean parallel) throws Exception {
        Rdbms2Liquibase.executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(liquibaseModel)
                .dialect(dialect)
                .log(log)
                .createTrace(createTrace)
                .useCache(useCache)
                .parallel(parallel));
    }
}
