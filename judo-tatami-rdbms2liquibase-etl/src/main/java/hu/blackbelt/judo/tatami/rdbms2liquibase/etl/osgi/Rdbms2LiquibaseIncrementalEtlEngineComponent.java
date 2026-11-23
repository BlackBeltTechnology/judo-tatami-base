package hu.blackbelt.judo.tatami.rdbms2liquibase.etl.osgi;

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

import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseIncrementalEtlEngine;
import hu.blackbelt.judo.tatami.rdbms2liquibase.etl.Rdbms2LiquibaseIncrementalEtlEngineImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Karaf: makes the incremental rdbms2liquibase ETL engine available to the core bundle while this bundle is active
 * (ServiceLoader does not cross OSGi bundle class loaders). See change: rdbms-incremental-without-epsilon.
 */
@Component(immediate = true, service = Rdbms2LiquibaseIncrementalEtlEngineComponent.class)
public class Rdbms2LiquibaseIncrementalEtlEngineComponent {

    private final Rdbms2LiquibaseIncrementalEtlEngine engine = new Rdbms2LiquibaseIncrementalEtlEngineImpl();

    @Activate
    public void activate() {
        EtlEngineRegistry.register(Rdbms2LiquibaseIncrementalEtlEngine.class, engine);
    }

    @Deactivate
    public void deactivate() {
        EtlEngineRegistry.unregister(Rdbms2LiquibaseIncrementalEtlEngine.class, engine);
    }
}
