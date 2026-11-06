package hu.blackbelt.judo.tatami.expression.psm.validation;

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

import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * expression-on-PSM validation work. ZETA/DUAL mode runs {@link ExpressionValidationOnPsmZetaWork} (Epsilon-free, in this artifact);
 * ETL mode delegates the EVL validation to the optional {@link ExpressionValidationOnPsmEtlEngine} add-on ({@code judo-tatami-expression-psm-validation-etl}).
 * The mode comes from a {@link ExpressionValidationOnPsmWorkParameter} in the context, else {@code judo.transformation.mode}.
 * See change: split-etl-artifacts.
 */
@Slf4j
public class ExpressionValidationOnPsmWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "expressionValidationOnPsmWorkParameter")
    @Getter
    public static final class ExpressionValidationOnPsmWorkParameter {
        /** Validation engine; defaults to {@code judo.transformation.mode} (ZETA when unset). */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    public ExpressionValidationOnPsmWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<ExpressionModel> expressionModel = getTransformationContext().getByClass(ExpressionModel.class);
        expressionModel.orElseThrow(() -> new IllegalArgumentException("Expression Model does not found in transformation context"));
        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        ExpressionValidationOnPsmWorkParameter workParam = getTransformationContext().getByClass(ExpressionValidationOnPsmWorkParameter.class)
                .orElseGet(() -> ExpressionValidationOnPsmWorkParameter.expressionValidationOnPsmWorkParameter().build());

        if (workParam.transformationMode.isZeta()) {
            log.info("Executing expression-on-PSM validation using Zeta engine");
            new ExpressionValidationOnPsmZetaWork(getTransformationContext()).execute();
        } else {
            log.info("Executing expression-on-PSM validation using EVL engine");
            EtlEngineRegistry.require(ExpressionValidationOnPsmEtlEngine.class, ExpressionValidationOnPsmEtlEngine.ARTIFACT_COORDINATES)
                    .validate(expressionModel.get(), psmModel.get(), log);
        }
    }
}
