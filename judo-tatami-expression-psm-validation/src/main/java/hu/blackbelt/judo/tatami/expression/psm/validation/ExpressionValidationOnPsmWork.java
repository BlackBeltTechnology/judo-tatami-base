package hu.blackbelt.judo.tatami.expression.psm.validation;

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.expression.adapters.psm.ExpressionEpsilonValidatorOnPsm;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ExpressionValidationOnPsmWork extends AbstractTransformationWork {

    public ExpressionValidationOnPsmWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {

        Optional<ExpressionModel> expressionModel = getTransformationContext().getByClass(ExpressionModel.class);
        expressionModel.orElseThrow(() -> new IllegalArgumentException("Expression Model does not found in transformation context"));

        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        try (final Log logger = new StringBuilderLogger(log)) {
            ExpressionEpsilonValidatorOnPsm.validateExpressionOnPsm(logger,
                    psmModel.get(),
                    expressionModel.get(),
                    ExpressionEpsilonValidatorOnPsm.calculateExpressionValidationScriptURI());
        }
    }
}
