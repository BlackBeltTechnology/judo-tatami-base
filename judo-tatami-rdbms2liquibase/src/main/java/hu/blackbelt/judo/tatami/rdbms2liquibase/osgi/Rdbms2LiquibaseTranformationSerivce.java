package hu.blackbelt.judo.tatami.rdbms2liquibase.osgi;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseNamespaceFixUriHandler;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2LiquibaseWork;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Runs the RDBMS to Liquibase transformation (hsqldb) through {@link Rdbms2LiquibaseWork}, honouring the
 * transformation mode (ZETA by default); ETL requires the judo-tatami-rdbms2liquibase-etl bundle.
 * See change: split-etl-artifacts.
 */
@Component(immediate = true, service = Rdbms2LiquibaseTranformationSerivce.class)
@Slf4j
public class Rdbms2LiquibaseTranformationSerivce {

    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public LiquibaseModel install(RdbmsModel rdbmsModel) throws Exception {
        URIHandler liquibaseNamespaceFixUriHandlerFromBundle =
                new LiquibaseNamespaceFixUriHandler(
                        new BundleEntryURIHandler("liquibase", "", bundleContext.getBundle())
                );

        URI liquibasUri = URI.createURI("liquibase:" + rdbmsModel.getName() + ".changlelog.xml");

        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name(rdbmsModel.getName())
                .version(rdbmsModel.getVersion())
                .uri(liquibasUri)
                .uriHandler(liquibaseNamespaceFixUriHandlerFromBundle)
                .build();

        String dialect = "hsqldb";
        TransformationContext transformationContext = new TransformationContext(rdbmsModel.getName());
        transformationContext.put("rdbms:" + dialect, rdbmsModel);
        transformationContext.put(liquibaseModel);
        try {
            new Rdbms2LiquibaseWork(transformationContext, dialect).execute();
        } catch (Exception e) {
            log.error("RDBMS to Liquibase transformation failed for model: " + rdbmsModel.getName(), e);
            throw e;
        }

        return liquibaseModel;
    }
}
