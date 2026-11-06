package hu.blackbelt.judo.tatami.expression.asm.validation;

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
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * expression-on-ASM validation work. ZETA/DUAL mode runs {@link ExpressionValidationOnAsmZetaWork} (Epsilon-free, in this artifact);
 * ETL mode delegates the EVL validation to the optional {@link ExpressionValidationOnAsmEtlEngine} add-on ({@code judo-tatami-expression-asm-validation-etl}).
 * The mode comes from a {@link ExpressionValidationOnAsmWorkParameter} in the context, else {@code judo.transformation.mode}.
 * See change: split-etl-artifacts.
 */
@Slf4j
public class ExpressionValidationOnAsmWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "expressionValidationOnAsmWorkParameter")
    @Getter
    public static final class ExpressionValidationOnAsmWorkParameter {
        /** Validation engine; defaults to {@code judo.transformation.mode} (ZETA when unset). */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    public ExpressionValidationOnAsmWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<ExpressionModel> expressionModel = getTransformationContext().getByClass(ExpressionModel.class);
        expressionModel.orElseThrow(() -> new IllegalArgumentException("Expression Model does not found in transformation context"));
        Optional<MeasureModel> measureModel = getTransformationContext().getByClass(MeasureModel.class);
        measureModel.orElseThrow(() -> new IllegalArgumentException("Measure Model does not found in transformation context"));
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        ExpressionValidationOnAsmWorkParameter workParam = getTransformationContext().getByClass(ExpressionValidationOnAsmWorkParameter.class)
                .orElseGet(() -> ExpressionValidationOnAsmWorkParameter.expressionValidationOnAsmWorkParameter().build());

        if (workParam.transformationMode.isZeta()) {
            log.info("Executing expression-on-ASM validation using Zeta engine");
            new ExpressionValidationOnAsmZetaWork(getTransformationContext()).execute();
        } else {
            log.info("Executing expression-on-ASM validation using EVL engine");
            EtlEngineRegistry.require(ExpressionValidationOnAsmEtlEngine.class, ExpressionValidationOnAsmEtlEngine.ARTIFACT_COORDINATES)
                    .validate(expressionModel.get(), measureModel.get(), asmModel.get(), log);
        }
    }
}
