package hu.blackbelt.judo.tatami.asm.validation;

/*-
 * #%L
 * JUDO Tatami parent
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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.validation.AsmValidator;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Work class for ASM validation using Zeta (native Java) validation rules.
 * This provides an alternative to {@link AsmValidationWork} which uses Epsilon EVL.
 * <p>
 * Note: The ASM validation currently has no rules (asm.evl is empty).
 * This class provides infrastructure for future validation rules.
 */
@Slf4j
public class AsmValidationZetaWork extends AbstractTransformationWork {

    public AsmValidationZetaWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        // AsmValidator.validateAsm throws AsmValidationException if validation fails
        AsmValidator.validateAsm(log, asmModel.get());

        log.info("ASM Zeta validation completed successfully");
    }
}
