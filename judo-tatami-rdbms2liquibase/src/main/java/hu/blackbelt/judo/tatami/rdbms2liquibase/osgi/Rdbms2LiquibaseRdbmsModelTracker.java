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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.AbstractModelTracker;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Slf4j
public class Rdbms2LiquibaseRdbmsModelTracker extends AbstractModelTracker<RdbmsModel> {

    @Reference
    Rdbms2LiquibaseTranformationSerivce psm2LiquibaseSerivce;

    Map<String, ServiceRegistration<hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel>> registrations = new ConcurrentHashMap<>();
    Map<String, hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel> models = new HashMap<>();


    @Activate
    protected void activate(ComponentContext contextPar) {
        componentContext = contextPar;
        openTracker(contextPar.getBundleContext());
    }

    @Deactivate
    protected void deactivate() {
        closeTracker();
        registrations.forEach((k, v) -> v.unregister());
    }

    private ComponentContext componentContext;

    @Override
    public void install(RdbmsModel psmModel) {
        String key = psmModel.getName();
        hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel liquibaseModel = null;
        if (models.containsKey(key)) {
            log.error("Model already loaded: " + psmModel.getName());
            return;
        }

        try {
            liquibaseModel = psm2LiquibaseSerivce.install(psmModel);
            log.info("Registering model: " + liquibaseModel);
            ServiceRegistration<LiquibaseModel> modelServiceRegistration =
                    componentContext.getBundleContext()
                            .registerService(LiquibaseModel.class, liquibaseModel, liquibaseModel.toDictionary());
            models.put(key, liquibaseModel);
            registrations.put(key, modelServiceRegistration);
        } catch (Exception e) {
            log.error("Could not register PSM Model: " + psmModel.getName(), e);
        }
    }

    @Override
    public void uninstall(RdbmsModel psmModel) {
        String key = psmModel.getName();
        if (!registrations.containsKey(key)) {
            log.error("Model is not registered: " + psmModel.getName());
        } else {
            registrations.get(key).unregister();
            registrations.remove(key);
            models.remove(key);
        }
    }

    @Override
    public Class<RdbmsModel> getModelClass() {
        return RdbmsModel.class;
    }


}
