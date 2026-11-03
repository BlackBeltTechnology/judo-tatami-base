package hu.blackbelt.judo.tatami.psm.validation.etl;

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

import hu.blackbelt.judo.meta.psm.PsmEpsilonValidator;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.tatami.psm.validation.PsmValidationEtlEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EVL implementation of {@link PsmValidationEtlEngine}: the former body of {@code PsmValidationWork#execute}.
 * Found via META-INF/services (plain Java) or registered by an OSGi DS component. See change: split-etl-artifacts.
 */
public class PsmValidationEtlEngineImpl implements PsmValidationEtlEngine {

    @Override
    public void validate(PsmModel psmModel, Logger log) throws Exception {
        Logger target = log != null ? log : LoggerFactory.getLogger(PsmValidationEtlEngineImpl.class);
        try (final StringBuilderLogger logger = new StringBuilderLogger(target)) {
            PsmEpsilonValidator.validatePsm(logger, psmModel, PsmEpsilonValidator.calculatePsmValidationScriptURI());
        }
    }
}
