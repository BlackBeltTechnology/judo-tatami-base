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
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.tatami.asm2expression.ExpressionModelOrigin;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Builds the query model of the run and puts it into the {@link TransformationContext}.
 *
 * <p>Reads the {@link AsmModel}, the {@link ExpressionModel} that {@code Asm2ExpressionWork} created <em>in this run</em>
 * (per its {@link ExpressionModelOrigin} context value) and the optional {@link MeasureModel} (an empty measure set when
 * absent). Without an expression model, or with one that was not created in this run (preloaded), the work is skipped
 * with a WARN and no {@code QueryModel} is put into the context. The expression model is never modified.</p>
 *
 * <p>Whether the stage is wanted at all (a {@code QueryModel} already in the context, ignored stages) is decided by the
 * output predicate of each workflow helper, not here.</p>
 */
@Slf4j
public class Asm2QueryWork extends AbstractTransformationWork {

    public Asm2QueryWork(final TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        final TransformationContext context = getTransformationContext();

        final AsmModel asmModel = context.getByClass(AsmModel.class)
                .orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        final Optional<ExpressionModel> expressionModel = context.getByClass(ExpressionModel.class);
        if (expressionModel.isEmpty()) {
            log.warn("Query model of {} is not built: expression model not found in transformation context", asmModel.getName());
            return;
        }
        final Optional<ExpressionModelOrigin> origin = context.getByClass(ExpressionModelOrigin.class);
        if (origin.isEmpty() || origin.get() != ExpressionModelOrigin.CREATED_IN_THIS_RUN) {
            log.warn("Query model of {} is not built: expression model not extracted in this run (origin: {})",
                    asmModel.getName(), origin.map(Enum::name).orElse("unknown"));
            return;
        }

        final Asm2QueryConfiguration configuration = context.getByClass(Asm2QueryConfiguration.class)
                .orElseGet(Asm2QueryConfiguration::new);
        final MeasureModel measureModel = context.getByClass(MeasureModel.class).orElse(null);

        final Asm2QueryResult result = Asm2Query.executeAsm2Query(Asm2Query.Asm2QueryParameter.asm2QueryParameter()
                .asmResourceSet(asmModel.getResourceSet())
                .measureResourceSet(measureModel != null ? measureModel.getResourceSet() : null)
                .expressionResourceSet(expressionModel.get().getResourceSet())
                .modelName(asmModel.getName())
                .modelVersion(asmModel.getVersion())
                .customJoinDefinitions(configuration.getCustomJoinDefinitions())
                // the expression model of the run is saved next to the query model: leave it as the extraction made it
                .preserveExpressionModel(true));

        context.put(result.getQueryModel());
    }
}
