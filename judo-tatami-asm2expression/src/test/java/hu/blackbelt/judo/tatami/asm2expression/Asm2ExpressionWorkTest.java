package hu.blackbelt.judo.tatami.asm2expression;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;

@Slf4j
public class Asm2ExpressionWorkTest {
	public static final String NORTHWIND = "northwind";
	public static final String NORTHWIND_ASM_MODEL = "northwind-asm.model";
	public static final String TARGET_TEST_CLASSES = "target/test-classes";

	Asm2ExpressionWork asm2ExpressionWork;
	TransformationContext transformationContext;

	@BeforeEach
	void setUp() throws Exception {

		PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        AsmModel asmModel = AsmModel.buildAsmModel()
                .name(NORTHWIND)
                .build();

        // Create empty Measure model
        MeasureModel measureModel = MeasureModel.buildMeasureModel()
                .name(NORTHWIND)
                .build();

		executePsm2AsmTransformation(psm2AsmParameter().psmModel(psmModel).asmModel(asmModel));
		executePsm2MeasureTransformation(psm2MeasureParameter().psmModel(psmModel).measureModel(measureModel));

		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(asmModel);
		transformationContext.put(measureModel);

		asm2ExpressionWork = new Asm2ExpressionWork(transformationContext);
	}

	@Test
	void testSimpleWorkflow() {
		WorkFlow workflow = aNewSequentialFlow().execute(asm2ExpressionWork).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workflow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}
}
