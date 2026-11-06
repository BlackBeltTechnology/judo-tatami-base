package hu.blackbelt.judo.tatami.expression.psm.validation.etl.osgi;

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

import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.expression.psm.validation.ExpressionValidationOnPsmEtlEngine;
import hu.blackbelt.judo.tatami.expression.psm.validation.etl.ExpressionValidationOnPsmEtlEngineImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/** Registers the EVL {@link ExpressionValidationOnPsmEtlEngine} in the {@link EtlEngineRegistry} while this bundle is active. */
@Component(immediate = true, service = ExpressionValidationOnPsmEtlEngineComponent.class)
public class ExpressionValidationOnPsmEtlEngineComponent {

    private final ExpressionValidationOnPsmEtlEngine engine = new ExpressionValidationOnPsmEtlEngineImpl();

    @Activate
    public void activate() {
        EtlEngineRegistry.register(ExpressionValidationOnPsmEtlEngine.class, engine);
    }

    @Deactivate
    public void deactivate() {
        EtlEngineRegistry.unregister(ExpressionValidationOnPsmEtlEngine.class, engine);
    }
}
