package hu.blackbelt.judo.tatami.psm2measure.etl.osgi;

/*-
 * #%L
 * Judo :: Tatami :: Psm2Measure :: ETL
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
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureEtlEngine;
import hu.blackbelt.judo.tatami.psm2measure.etl.Psm2MeasureEtlEngineImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Karaf: makes the psm2measure ETL engine available to the core bundle while this bundle is active
 * (ServiceLoader does not cross OSGi bundle class loaders).
 */
@Component(immediate = true, service = Psm2MeasureEtlEngineComponent.class)
public class Psm2MeasureEtlEngineComponent {

    private final Psm2MeasureEtlEngine engine = new Psm2MeasureEtlEngineImpl();

    @Activate
    public void activate() {
        EtlEngineRegistry.register(Psm2MeasureEtlEngine.class, engine);
    }

    @Deactivate
    public void deactivate() {
        EtlEngineRegistry.unregister(Psm2MeasureEtlEngine.class, engine);
    }
}
