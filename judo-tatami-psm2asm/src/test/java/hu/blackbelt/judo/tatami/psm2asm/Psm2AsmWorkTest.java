package hu.blackbelt.judo.tatami.psm2asm;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.calculatePsm2AsmTransformationScriptURI;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;

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
class Psm2AsmWorkTest {

    public static final String NORTHWIND = "northwind";
    public static final String NORTHWIND_PSM_MODEL = "northwind-psm.model";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    Psm2AsmWork psm2AsmWork;
    TransformationContext transformationContext;

	@BeforeEach
	void setUp() throws IOException, PsmModel.PsmValidationException, URISyntaxException {
		PsmModel psmModel = new Demo().fullDemo();

		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(psmModel);

		psm2AsmWork = new Psm2AsmWork(transformationContext, calculatePsm2AsmTransformationScriptURI());
	}

	@Test
	void testSimpleWorkflow() {
		WorkFlow workflow = aNewSequentialFlow().execute(psm2AsmWork).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workflow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}

}