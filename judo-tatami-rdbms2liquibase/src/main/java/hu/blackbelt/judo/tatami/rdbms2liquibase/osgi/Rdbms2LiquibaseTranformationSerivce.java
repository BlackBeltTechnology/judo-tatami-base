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

import hu.blackbelt.epsilon.runtime.execution.impl.LogLevel;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.epsilon.runtime.osgi.BundleURIHandler;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseNamespaceFixUriHandler;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import static hu.blackbelt.judo.tatami.core.AnsiColor.red;
import static hu.blackbelt.judo.tatami.core.AnsiColor.yellow;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;

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
                        new BundleURIHandler("liquibase", "", bundleContext.getBundle())
                );

        URI liquibasUri = URI.createURI("liquibase:" + rdbmsModel.getName() + ".changlelog.xml");

        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name(rdbmsModel.getName())
                .version(rdbmsModel.getVersion())
                .uri(liquibasUri)
                .uriHandler(liquibaseNamespaceFixUriHandlerFromBundle)
                .build();

        StringBuilderLogger logger = new StringBuilderLogger(LogLevel.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/rdbms2liquibase/transformations/rdbmsToLiquibase.etl")
                            .toURI()
                            .resolve(".");

            executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .log(logger)
                    .scriptUri(scriptUri)
                    .dialect("hsqldb"));

            log.info(yellow("{}"), logger.getBuffer());
        } catch (Exception e) {
            log.info(red("{}"), logger.getBuffer());
            throw e;
        }

        return liquibaseModel;
    }
}
