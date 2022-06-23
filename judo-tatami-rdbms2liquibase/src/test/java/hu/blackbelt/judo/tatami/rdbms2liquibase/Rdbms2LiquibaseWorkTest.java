package hu.blackbelt.judo.tatami.rdbms2liquibase;

import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngine;
import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.Work;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus.COMPLETED;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class Rdbms2LiquibaseWorkTest {

    public static final String NORTHWIND = "northwind";
    public static final List<String> DIALECT_LIST = new LinkedList<>(asList("hsqldb", "oracle"));

    @Test
    void testSimpleWorkflow() {
        final RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().name(NORTHWIND).build();

        final TransformationContext transformationContext = new TransformationContext(NORTHWIND);
        DIALECT_LIST.forEach(dialect -> transformationContext.put("rdbms:" + dialect, rdbmsModel));

        final List<Rdbms2LiquibaseWork> rdbms2LiquibaseWorks = new ArrayList<>();
        DIALECT_LIST.forEach(dialect -> rdbms2LiquibaseWorks.add(new Rdbms2LiquibaseWork(transformationContext, dialect)));

//        WorkFlow workflow = aNewParallelFlow().execute(rdbms2LiquibaseWorks.toArray(new Work[0])).build();
        WorkFlow workflow = aNewSequentialFlow().execute(rdbms2LiquibaseWorks.toArray(new Work[0])).build();

        WorkFlowEngine workFlowEngine = aNewWorkFlowEngine().build();
        WorkReport workReport = workFlowEngine.run(workflow);

        log.info("Workflow completed with status {}", workReport.getStatus(), workReport.getError());
        assertEquals(COMPLETED, workReport.getStatus());
    }

}
