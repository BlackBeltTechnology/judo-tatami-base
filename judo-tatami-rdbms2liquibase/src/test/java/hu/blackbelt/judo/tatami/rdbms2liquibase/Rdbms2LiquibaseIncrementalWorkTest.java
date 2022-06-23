package hu.blackbelt.judo.tatami.rdbms2liquibase;

import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.Work;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Rdbms2LiquibaseIncrementalWorkTest {

    private static final Set<String> DIALECTS = new HashSet<>(asList("hsqldb", "postgres", "oracle"));

    @Test
    public void testSimpleWorkflow() {
        final TransformationContext transformationContext = new TransformationContext("M");
        DIALECTS.forEach(dialect -> transformationContext.put("rdbms-incremental:" + dialect, buildRdbmsModel().name("Incremental").build()));

        final Work[] works = DIALECTS.stream().map(d -> new Rdbms2LiquibaseIncrementalWork(transformationContext, d)).toArray(Work[]::new);
        final WorkFlow workFlow = aNewSequentialFlow().execute(works).build();
        final WorkReport workReport = aNewWorkFlowEngine().build().run(workFlow);

        assertEquals(WorkStatus.COMPLETED, workReport.getStatus());
    }

}
