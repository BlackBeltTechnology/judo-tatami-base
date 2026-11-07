package hu.blackbelt.judo.tatami.psm2asm.etl.osgi;

/*-
 * #%L
 * Judo :: Tatami :: Psm2Asm :: ETL
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
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmEtlEngine;
import hu.blackbelt.judo.tatami.psm2asm.etl.Psm2AsmEtlEngineImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Karaf: makes the psm2asm ETL engine available to the core bundle while this bundle is active
 * (ServiceLoader does not cross OSGi bundle class loaders).
 */
@Component(immediate = true, service = Psm2AsmEtlEngineComponent.class)
public class Psm2AsmEtlEngineComponent {

    private final Psm2AsmEtlEngine engine = new Psm2AsmEtlEngineImpl();

    @Activate
    public void activate() {
        EtlEngineRegistry.register(Psm2AsmEtlEngine.class, engine);
    }

    @Deactivate
    public void deactivate() {
        EtlEngineRegistry.unregister(Psm2AsmEtlEngine.class, engine);
    }
}
