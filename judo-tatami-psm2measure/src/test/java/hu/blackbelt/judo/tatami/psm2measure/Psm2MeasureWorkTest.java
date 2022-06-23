package hu.blackbelt.judo.tatami.psm2measure;

import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.loadPsmModel;
import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.calculatePsm2MeasureTransformationScriptURI;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.PsmValidationException;

@Slf4j
public class Psm2MeasureWorkTest {
	
	public static final String NORTHWIND = "northwind";
	public static final String NORTHWIND_PSM_MODEL = "northwind-psm.model";
	public static final String TARGET_TEST_CLASSES = "target/test-classes";
	
	Psm2MeasureWork psm2measureWork;
	TransformationContext transformationContext;

	@BeforeEach
	void setUp() {
		PsmModel psmModel = new Demo().fullDemo();
		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(psmModel);

		psm2measureWork = new Psm2MeasureWork(transformationContext, calculatePsm2MeasureTransformationScriptURI());
	}

	@Test
	void testSimpleWorkFlow() {
		WorkFlow workFlow = aNewSequentialFlow().execute(psm2measureWork).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workFlow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}
}
