package hu.blackbelt.judo.tatami.expression.psm.validation;

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

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.expression.runtime.ExpressionModel.buildExpressionModel;
import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static hu.blackbelt.judo.tatami.asm2expression.Asm2Expression.Asm2ExpressionParameter.asm2ExpressionParameter;
import static hu.blackbelt.judo.tatami.asm2expression.Asm2Expression.executeAsm2Expression;
import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import hu.blackbelt.judo.tatami.psm2asm.Psm2Asm;
import hu.blackbelt.judo.tatami.psm2measure.Psm2Measure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Disabled
class ExpressionValidationOnPsmWorkTest {

	public static final String NORTHWIND = "northwind";

    TransformationContext transformationContext;
    ExpressionValidationOnPsmWork expressionValidationOnPsmWork;

	@BeforeEach
	void setUp() throws Exception {
		Demo demo = new Demo();
		PsmModel psmModel = demo.fullDemo();

		AsmModel asmModel = buildAsmModel().name(NORTHWIND).build();
		executePsm2AsmTransformation(psm2AsmParameter()
				.psmModel(psmModel)
				.asmModel(asmModel));

		MeasureModel measureModel = buildMeasureModel().name(NORTHWIND).build();
		executePsm2MeasureTransformation(psm2MeasureParameter()
				.psmModel(psmModel)
				.measureModel(measureModel));

		ExpressionModel expressionModel = buildExpressionModel().name(NORTHWIND).build();
		executeAsm2Expression(asm2ExpressionParameter()
				.asmModel(asmModel)
				.measureModel(measureModel)
				.expressionModel(expressionModel));

		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(expressionModel);
		transformationContext.put(psmModel);

		expressionValidationOnPsmWork = new ExpressionValidationOnPsmWork(transformationContext);
	}

	@Test
	void testSimpleWorkflow() {
		WorkFlow workflow = aNewSequentialFlow().execute(expressionValidationOnPsmWork).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workflow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}

}
