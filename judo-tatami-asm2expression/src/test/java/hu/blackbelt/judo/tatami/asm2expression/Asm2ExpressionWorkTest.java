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

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static hu.blackbelt.judo.tatami.psm2asm.etl.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.etl.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.tatami.psm2measure.etl.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.etl.Psm2Measure.executePsm2MeasureTransformation;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
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
                .build();

        //EmfUtils.initDefaultCachedResourceSet();
        //EmfUtils.addEmfPackagesToResourceSet(asmModel.getResourceSet());

        // Create empty Measure model
        MeasureModel measureModel = MeasureModel.buildMeasureModel()
                .name(psmModel.getName())
                .build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .parallel(true)
                .useCache(true)
        );

        executePsm2MeasureTransformation(psm2MeasureParameter()
                .psmModel(psmModel)
                .measureModel(measureModel)
                .parallel(true)
                .useCache(true)
        );

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
    @Test
    void testExpressionModelCreatedInThisRun() {
        WorkReport workReport = aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(asm2ExpressionWork).build());
        assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));

        assertThat(transformationContext.getByClass(ExpressionModel.class).isPresent(), equalTo(true));
        assertThat(transformationContext.getByClass(ExpressionModelOrigin.class).orElse(null),
                equalTo(ExpressionModelOrigin.CREATED_IN_THIS_RUN));
    }

    @Test
    void testPreloadedExpressionModelIsReusedAndMarkedPreloaded() {
        final ExpressionModel preloaded = ExpressionModel.buildExpressionModel()
                .name(NORTHWIND)
                .build();
        transformationContext.put(preloaded);

        WorkReport workReport = aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(asm2ExpressionWork).build());
        assertThat(workReport.getStatus(), equalTo(WorkStatus.COMPLETED));

        assertThat(transformationContext.getByClass(ExpressionModel.class).orElse(null), sameInstance(preloaded));
        assertThat(transformationContext.getByClass(ExpressionModelOrigin.class).orElse(null),
                equalTo(ExpressionModelOrigin.PRELOADED));
    }

    /**
     * TP-62 (asm2expression part): a saved -expression.model put into the context before the work is reused, and the
     * origin tells the query stage that it was not created in this run.
     */
    @Test
    void testSavedExpressionModelPreloadedBeforeWork() throws Exception {
        // 1. a run that creates the expression model, then saves it
        WorkReport first = aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(asm2ExpressionWork).build());
        assertThat(first.getStatus(), equalTo(WorkStatus.COMPLETED));
        assertThat(transformationContext.getByClass(ExpressionModelOrigin.class).orElse(null), equalTo(ExpressionModelOrigin.CREATED_IN_THIS_RUN));
        final ExpressionModel created = transformationContext.getByClass(ExpressionModel.class).orElseThrow();
        final ByteArrayOutputStream saved = new ByteArrayOutputStream();
        created.saveExpressionModel(ExpressionModel.SaveArguments.expressionSaveArgumentsBuilder().outputStream(saved));

        // 2. a new run with the saved model preloaded
        final ExpressionModel loaded = ExpressionModel.loadExpressionModel(ExpressionModel.LoadArguments.expressionLoadArgumentsBuilder()
                .inputStream(new ByteArrayInputStream(saved.toByteArray()))
                .name(NORTHWIND));
        final TransformationContext second = new TransformationContext(NORTHWIND);
        second.put(transformationContext.getByClass(AsmModel.class).orElseThrow());
        second.put(transformationContext.getByClass(MeasureModel.class).orElseThrow());
        second.put(loaded);

        WorkReport report = aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(new Asm2ExpressionWork(second)).build());
        assertThat(report.getStatus(), equalTo(WorkStatus.COMPLETED));

        assertThat(second.getByClass(ExpressionModel.class).orElse(null), sameInstance(loaded));
        assertThat(second.getByClass(ExpressionModelOrigin.class).orElse(null), equalTo(ExpressionModelOrigin.PRELOADED));
    }

    /**
     * A failed extraction must not mark the expression model as created in this run: the query stage would otherwise
     * build and persist a query model over a partially filled expression model.
     */
    @Test
    void failedExtractionRecordsNoOrigin() {
        final AsmModel asmModel = transformationContext.getByClass(AsmModel.class).orElseThrow();
        final FailingResourceSet failing = new FailingResourceSet();
        final MeasureModel measureModel = MeasureModel.buildMeasureModel()
                .name(NORTHWIND)
                .resourceSet(failing)
                .build();
        failing.armed = true;
        final TransformationContext context = new TransformationContext(NORTHWIND);
        context.put(asmModel);
        context.put(measureModel);

        final WorkReport report = aNewWorkFlowEngine().build()
                .run(aNewSequentialFlow().execute(new Asm2ExpressionWork(context)).build());

        assertThat(report.getStatus(), equalTo(WorkStatus.FAILED));
        assertThat(context.getByClass(ExpressionModelOrigin.class).isPresent(), equalTo(false));
    }

    /** Throws on adapter registration once armed, which makes the measure provider (and so the extraction) fail. */
    private static final class FailingResourceSet extends org.eclipse.emf.ecore.resource.impl.ResourceSetImpl {
        private boolean armed;

        @Override
        public org.eclipse.emf.common.util.EList<org.eclipse.emf.common.notify.Adapter> eAdapters() {
            if (armed) {
                throw new IllegalStateException("simulated extraction failure");
            }
            return super.eAdapters();
        }
    }
}
