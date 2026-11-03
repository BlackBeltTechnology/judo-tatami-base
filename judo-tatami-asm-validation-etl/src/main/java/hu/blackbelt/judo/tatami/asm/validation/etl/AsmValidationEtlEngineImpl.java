package hu.blackbelt.judo.tatami.asm.validation.etl;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmEpsilonValidator;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.tatami.asm.validation.AsmValidationEtlEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EVL implementation of {@link AsmValidationEtlEngine}: the former body of {@code AsmValidationWork#execute}.
 * Found via META-INF/services (plain Java) or registered by an OSGi DS component. See change: split-etl-artifacts.
 */
public class AsmValidationEtlEngineImpl implements AsmValidationEtlEngine {

    @Override
    public void validate(AsmModel asmModel, Logger log) throws Exception {
        Logger target = log != null ? log : LoggerFactory.getLogger(AsmValidationEtlEngineImpl.class);
        try (final StringBuilderLogger logger = new StringBuilderLogger(target)) {
            AsmEpsilonValidator.validateAsm(logger, asmModel, AsmEpsilonValidator.calculateAsmValidationScriptURI());
        }
    }
}
