package hu.blackbelt.judo.tatami.asm2rdbms;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.Work;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Asm2RdbmsWorkTest {

	public static final String NORTHWIND = "northwind";
	public static final List<String> DIALECT_LIST = new LinkedList<String>(Arrays.asList("hsqldb", "oracle"));

	List<Asm2RdbmsWork> asm2RdbmsWorks = Lists.newArrayList();
	TransformationContext transformationContext;

	@BeforeEach
	void setUp() throws Exception {
		PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        AsmModel asmModel = AsmModel.buildAsmModel()
                .name(NORTHWIND)
                .build();

		executePsm2AsmTransformation(psm2AsmParameter()
				.psmModel(psmModel)
				.asmModel(asmModel));

		transformationContext = new TransformationContext(NORTHWIND);
		transformationContext.put(asmModel);
		
		DIALECT_LIST.forEach(dialect -> {
				asm2RdbmsWorks.add(new Asm2RdbmsWork(transformationContext, dialect));
		});
	}

	@Test
	void testSimpleWorkflow() {
		WorkFlow workflow = aNewSequentialFlow().execute(asm2RdbmsWorks.toArray(new Work[asm2RdbmsWorks.size()])).build();
		//WorkFlow workflow = aNewParallelFlow().execute(asm2RdbmsWorks.toArray(new Work[asm2RdbmsWorks.size()])).build();

		WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
		WorkReport workReport = workFlowEngine.run(workflow);

		log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
		assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));
	}
}
