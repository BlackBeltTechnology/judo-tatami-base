package hu.blackbelt.judo.tatami.expression.psm.validation;

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

import hu.blackbelt.judo.meta.expression.adapters.psm.PsmModelAdapter;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.expression.validation.ExpressionZetaValidator;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Work class for Expression validation on PSM using Zeta (native Java) validation rules.
 * This provides an alternative to {@link ExpressionValidationOnPsmWork} which uses Epsilon EVL.
 */
@Slf4j
public class ExpressionValidationOnPsmZetaWork extends AbstractTransformationWork {

    public ExpressionValidationOnPsmZetaWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<ExpressionModel> expressionModel = getTransformationContext().getByClass(ExpressionModel.class);
        expressionModel.orElseThrow(() -> new IllegalArgumentException("Expression Model does not found in transformation context"));

        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        // Create PsmModelAdapter for expression validation
        // The measureResourceSet is the same as psmResourceSet since PSM contains measures
        PsmModelAdapter modelAdapter = new PsmModelAdapter(
                psmModel.get().getResourceSet(),
                psmModel.get().getResourceSet()
        );

        // ExpressionZetaValidator.validateExpression throws ExpressionValidationException if validation fails
        ExpressionZetaValidator.validateExpression(log, expressionModel.get(), modelAdapter);

        log.info("Expression on PSM Zeta validation completed successfully");
    }
}
