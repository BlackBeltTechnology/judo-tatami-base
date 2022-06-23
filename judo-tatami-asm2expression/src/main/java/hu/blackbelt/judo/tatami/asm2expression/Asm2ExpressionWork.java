package hu.blackbelt.judo.tatami.asm2expression;

import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;

import static hu.blackbelt.judo.meta.expression.runtime.ExpressionModel.buildExpressionModel;

@Slf4j
public class Asm2ExpressionWork extends AbstractTransformationWork {

	public Asm2ExpressionWork(TransformationContext transformationContext) {
		super(transformationContext);
	}

	@Override
	public void execute() throws Exception {

		Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
		asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

		Optional<MeasureModel> measureModel = getTransformationContext().getByClass(MeasureModel.class);

		ExpressionModel
				expressionModel = getTransformationContext().getByClass(ExpressionModel.class)
				.orElseGet(() -> buildExpressionModel().name(asmModel.get().getName()).build());
		getTransformationContext().put(expressionModel);

		Asm2ExpressionConfiguration config = getTransformationContext().getByClass(Asm2ExpressionConfiguration.class)
				.orElse(new Asm2ExpressionConfiguration());

		Asm2Expression.executeAsm2Expression(Asm2Expression.Asm2ExpressionParameter.asm2ExpressionParameter()
				.asmModel(asmModel.get())
				.measureModel(measureModel.orElse(null))
				.expressionModel(expressionModel)
				.config(config));
	}
}
