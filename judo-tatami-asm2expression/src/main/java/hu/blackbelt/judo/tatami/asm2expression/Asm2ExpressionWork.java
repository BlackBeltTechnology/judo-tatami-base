package hu.blackbelt.judo.tatami.asm2expression;

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

import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;

import static hu.blackbelt.judo.meta.expression.runtime.ExpressionModel.buildExpressionModel;

@Slf4j
public class Asm2ExpressionWork extends AbstractTransformationWork {

	public Asm2ExpressionWork(TransformationContext transformationContext) {
		super(transformationContext);
	}

	@Override
	public void execute() throws Exception {

		Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
		asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

		Optional<MeasureModel> measureModel = getTransformationContext().getByClass(MeasureModel.class);

		ExpressionModel
				expressionModel = getTransformationContext().getByClass(ExpressionModel.class)
				.orElseGet(() -> buildExpressionModel()
						.name(asmModel.get().getName())
						.version(asmModel.get().getVersion())
						.build());
		getTransformationContext().put(expressionModel);

		Asm2ExpressionConfiguration config = getTransformationContext().getByClass(Asm2ExpressionConfiguration.class)
				.orElse(new Asm2ExpressionConfiguration());

		Asm2Expression.executeAsm2Expression(Asm2Expression.Asm2ExpressionParameter.asm2ExpressionParameter()
				.asmModel(asmModel.get())
				.measureModel(measureModel.orElse(null))
				.expressionModel(expressionModel)
				.config(config));
	}
}
