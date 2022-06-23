package hu.blackbelt.judo.tatami.psm.validation;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PsmValidationWorkTest {

	public static final String NORTHWIND = "northwind";

    TransformationContext transformationContext;
    PsmValidationWork psmValidationWork;

	@BeforeEach
	void setUp() {

		Demo demo = new Demo();
		PsmModel psmModel = demo.fullDemo();

		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(psmModel);

		psmValidationWork = new PsmValidationWork(transformationContext);
	}

	@Test
	void testSimpleWorkflow() {
		WorkFlow workflow = aNewSequentialFlow().execute(psmValidationWork).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workflow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}

}
