package hu.blackbelt.judo.tatami.asm2query;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import hu.blackbelt.judo.tatami.asm2expression.Asm2ExpressionWork;
import hu.blackbelt.judo.tatami.asm2expression.ExpressionModelOrigin;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * {@link Asm2QueryWork} over Northwind: the expression model of the run is left unchanged, and the stage is skipped with a
 * WARN when that model is absent or was not created in this run.
 */
public class Asm2QueryWorkTest {

    /**
     * Root by root, each with a fresh EqualityHelper (one shared helper pairs objects across roots and fails on its own state).
     */
    private static boolean equalContents(final List<EObject> original, final List<EObject> copy) {
        if (original.size() != copy.size()) {
            return false;
        }
        for (int i = 0; i < original.size(); i++) {
            if (!new EcoreUtil.EqualityHelper().equals(original.get(i), copy.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String describe(final WorkReport report) {
        if (report.getError() == null) {
            return "no error";
        }
        final java.io.StringWriter text = new java.io.StringWriter();
        report.getError().printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }

    private static WorkReport run(final AbstractTransformationWork work) {
        return aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(work).build());
    }

    private static WorkReport runAll(final AbstractTransformationWork first, final AbstractTransformationWork second) {
        return aNewWorkFlowEngine().build().run(aNewSequentialFlow().execute(first).then(second).build());
    }

    /**
     * TP-8: the stage leaves the context's expression model unchanged and adds one QueryModel.
     */
    @Test
    void stageLeavesTheContextExpressionModelUnchanged() {
        final NorthwindFixture fixture = NorthwindFixture.create();
        final TransformationContext context = fixture.context;
        assertThat(context.getByClass(ExpressionModelOrigin.class).orElse(null), equalTo(ExpressionModelOrigin.CREATED_IN_THIS_RUN));
        final ExpressionModel before = context.getByClass(ExpressionModel.class).orElseThrow();
        final List<EObject> copy = new ArrayList<>(EcoreUtil.copyAll(before.getResource().getContents()));
        final List<EObject> original = new ArrayList<>(before.getResource().getContents());
        assertThat(original.size(), org.hamcrest.Matchers.greaterThan(0));
        assertThat(context.getByClass(QueryModel.class).isPresent(), equalTo(false));
        assertThat("the comparison itself holds before the stage", equalContents(original, copy), equalTo(true));

        final WorkReport report = run(new Asm2QueryWork(context));
        assertThat(describe(report), report.getStatus(), equalTo(WorkStatus.COMPLETED));

        assertThat(context.getByClass(ExpressionModel.class).orElseThrow(), sameInstance(before));
        assertThat(before.getResource().getContents(), equalTo(original));
        assertThat("EqualityHelper-equal to the pre-stage copy", equalContents(original, copy), equalTo(true));

        final QueryModel queryModel = context.getByClass(QueryModel.class).orElseThrow();
        assertThat(queryModel.getResource().getURI().toString(), equalTo(fixture.asmModel.getName() + "-query.model"));
        assertThat(QueryIndex.getQueryModelInfo(queryModel.getResource()).getQueries().size(), org.hamcrest.Matchers.greaterThan(0));
    }

    /**
     * The converters attach iterator variables to the expression model; the build removes them again only when asked to
     * (the Work asks), a runtime build keeps the historic behaviour.
     */
    @Test
    void buildPreservesTheExpressionModelOnlyWhenAsked() {
        final NorthwindFixture preserved = NorthwindFixture.create();
        final long before = iteratorVariables(preserved.expressionModel);
        Asm2Query.executeAsm2Query(Asm2Query.Asm2QueryParameter.asm2QueryParameter()
                .asmResourceSet(preserved.asmModel.getResourceSet())
                .measureResourceSet(preserved.measureModel.getResourceSet())
                .expressionResourceSet(preserved.expressionModel.getResourceSet())
                .modelName(preserved.asmModel.getName())
                .preserveExpressionModel(true));
        assertThat(iteratorVariables(preserved.expressionModel), equalTo(before));

        final NorthwindFixture historic = NorthwindFixture.create();
        final long historicBefore = iteratorVariables(historic.expressionModel);
        Asm2Query.executeAsm2Query(historic.asmModel, historic.measureModel, historic.expressionModel);
        assertThat(iteratorVariables(historic.expressionModel), org.hamcrest.Matchers.greaterThan(historicBefore));
    }

    private static long iteratorVariables(final ExpressionModel expressionModel) {
        long count = 0;
        final var contents = expressionModel.getResource().getAllContents();
        while (contents.hasNext()) {
            final EObject object = contents.next();
            if (object instanceof hu.blackbelt.judo.meta.expression.IterableExpression
                    && ((hu.blackbelt.judo.meta.expression.IterableExpression) object).getIteratorVariable() != null) {
                count++;
            }
        }
        return count;
    }

    @Test
    void workBuildsOverTheExpressionModelOfTheContextAndPutsTheQueryModelOnce() {
        final NorthwindFixture fixture = NorthwindFixture.create();
        final WorkReport report = run(new Asm2QueryWork(fixture.context));
        assertThat(describe(report), report.getStatus(), equalTo(WorkStatus.COMPLETED));
        // the model refers to the live expression objects of the run
        final QueryModel queryModel = fixture.context.getByClass(QueryModel.class).orElseThrow();
        final QueryIndex restored = QueryIndex.restore(fixture.asmModel.getResourceSet(), queryModel);
        restored.getEntityTypeExpressionsMap().values().forEach(e ->
                e.getGetterAttributeExpressions().values().forEach(x -> assertThat(x.eResource(), sameInstance(fixture.expressionModel.getResource()))));
    }

    /**
     * No expression model in the context: skipped with a WARN, no QueryModel.
     */
    @Test
    void absentExpressionModelIsSkippedWithAWarning() {
        final NorthwindFixture fixture = NorthwindFixture.create();
        final TransformationContext context = new TransformationContext("northwind");
        context.put(fixture.asmModel);
        context.put(fixture.measureModel);

        try (LogCapture log = new LogCapture(Asm2QueryWork.class)) {
            final WorkReport report = run(new Asm2QueryWork(context));
            assertThat(report.getStatus(), equalTo(WorkStatus.COMPLETED));
            assertThat(log.warnings(), hasSize(1));
            assertThat(log.warnings().get(0), containsString("expression model not found"));
        }
        assertThat(context.getByClass(QueryModel.class).isPresent(), equalTo(false));
    }

    /**
     * TP-62: a saved -expression.model put into the context before Asm2ExpressionWork: the origin is preloaded, the query
     * stage is skipped with the WARN "expression model not extracted in this run", no QueryModel.
     */
    @Test
    void preloadedExpressionModelSkipsTheQueryStage() throws Exception {
        final NorthwindFixture fixture = NorthwindFixture.create();
        final ByteArrayOutputStream saved = new ByteArrayOutputStream();
        fixture.expressionModel.saveExpressionModel(ExpressionModel.SaveArguments.expressionSaveArgumentsBuilder().outputStream(saved));
        final ExpressionModel loaded = ExpressionModel.loadExpressionModel(ExpressionModel.LoadArguments.expressionLoadArgumentsBuilder()
                .inputStream(new ByteArrayInputStream(saved.toByteArray()))
                .name(fixture.asmModel.getName()));

        final TransformationContext context = new TransformationContext("northwind");
        context.put(fixture.asmModel);
        context.put(fixture.measureModel);
        context.put(loaded);

        try (LogCapture log = new LogCapture(Asm2QueryWork.class)) {
            final WorkReport report = runAll(new Asm2ExpressionWork(context), new Asm2QueryWork(context));
            assertThat(describe(report), report.getStatus(), equalTo(WorkStatus.COMPLETED));
            assertThat(context.getByClass(ExpressionModelOrigin.class).orElse(null), equalTo(ExpressionModelOrigin.PRELOADED));
            assertThat(log.warnings(), hasSize(1));
            assertThat(log.warnings().get(0), containsString("expression model not extracted in this run"));
        }
        assertThat(context.getByClass(ExpressionModel.class).orElseThrow(), sameInstance(loaded));
        assertThat(context.getByClass(QueryModel.class).isPresent(), equalTo(false));
    }

    /**
     * An expression model that no Asm2ExpressionWork of this context vouched for (no origin) is not built over either.
     */
    @Test
    void expressionModelWithoutOriginIsSkippedWithAWarning() {
        final NorthwindFixture fixture = NorthwindFixture.create();
        final TransformationContext context = new TransformationContext("northwind");
        context.put(fixture.asmModel);
        context.put(fixture.measureModel);
        context.put(fixture.expressionModel);

        try (LogCapture log = new LogCapture(Asm2QueryWork.class)) {
            assertThat(run(new Asm2QueryWork(context)).getStatus(), equalTo(WorkStatus.COMPLETED));
            assertThat(log.warnings(), hasSize(1));
            assertThat(log.warnings().get(0), containsString("expression model not extracted in this run"));
        }
        assertThat(context.getByClass(QueryModel.class).isPresent(), equalTo(false));
    }

    @Test
    void missingAsmModelFailsTheWork() {
        final TransformationContext context = new TransformationContext("northwind");
        final WorkReport report = run(new Asm2QueryWork(context));
        assertThat(report.getStatus(), equalTo(WorkStatus.FAILED));
        assertThat(report.getError(), instanceOf(IllegalArgumentException.class));
        assertThat(report.getError().getMessage(), containsString("ASM Model"));
    }

    @Test
    void measureModelIsOptionalForAModelWithoutMeasures() {
        final MinimalFixture fixture = MinimalFixture.create();
        final WorkReport report = run(new Asm2QueryWork(fixture.context));
        assertThat(describe(report), report.getStatus(), equalTo(WorkStatus.COMPLETED));
        assertThat(fixture.context.getByClass(QueryModel.class).isPresent(), equalTo(true));
    }

    @Test
    void configurationDefaultsToNoCustomJoins() {
        assertThat(new Asm2QueryConfiguration().getCustomJoinDefinitions().isEmpty(), equalTo(true));
        final Asm2QueryConfiguration configuration = new Asm2QueryConfiguration();
        configuration.setCustomJoinDefinitions(null);
        assertThat(configuration.getCustomJoinDefinitions(), not(org.hamcrest.Matchers.nullValue()));
        assertThat(configuration.getCustomJoinDefinitions().keySet(), empty());
    }
}
