package hu.blackbelt.judo.tatami.asm2expression.osgi;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.tatami.asm2expression.Asm2Expression;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.component.annotations.Component;

import static hu.blackbelt.judo.tatami.core.AnsiColor.red;

@Component(immediate = true, service = Asm2ExpressionTranformationSerivce.class)
@Slf4j

public class Asm2ExpressionTranformationSerivce {

    public ExpressionModel install(AsmModel asmModel, MeasureModel measureModel) {
        ExpressionModel expressionModel = ExpressionModel.buildExpressionModel()
                .name(asmModel.getName())
                .version(asmModel.getVersion())
                .uri(URI.createURI("expression:" + asmModel.getName() + ".model"))
                .build();

        // The former Epsilon StringBuilderLogger was never passed to the transformation, so its buffer
        // was always empty; plain SLF4J logging keeps the same observable behaviour without Epsilon.
        try {
            Asm2Expression.executeAsm2Expression(Asm2Expression.Asm2ExpressionParameter.asm2ExpressionParameter()
                    .asmModel(asmModel)
                    .measureModel(measureModel)
                    .expressionModel(expressionModel));

            log.debug("ASM to expression transformation completed for model {}", asmModel.getName());
        } catch (Exception e) {
            log.error(red("ASM to expression transformation failed for model {}"), asmModel.getName(), e);
            throw e;
        }
        return expressionModel;
    }
}
