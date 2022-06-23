package hu.blackbelt.judo.tatami.psm2measure;

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;

import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;

@Slf4j
public class Psm2MeasureWork extends AbstractTransformationWork {

	@Builder(builderMethodName = "psm2MeasureWorkParameter")
	public static final class Psm2MeasureWorkParameter {
		@Builder.Default
		Boolean createTrace = false;
		@Builder.Default
		Boolean parallel = true;
	}

	final URI transformationScriptRoot;
	
	public Psm2MeasureWork(TransformationContext transformationContext, URI transformationScriptRoot) {
		super(transformationContext);
		this.transformationScriptRoot = transformationScriptRoot;
	}

	public Psm2MeasureWork(TransformationContext transformationContext) {
		this(transformationContext, Psm2Measure.calculatePsm2MeasureTransformationScriptURI());
	}

	@Override
	public void execute() throws Exception {

		Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
		psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

		MeasureModel measureModel = getTransformationContext().getByClass(MeasureModel.class)
				.orElseGet(() -> buildMeasureModel().name(psmModel.get().getName()).build());
		getTransformationContext().put(measureModel);

		Psm2MeasureWorkParameter workParam = getTransformationContext().getByClass(Psm2MeasureWorkParameter.class)
				.orElseGet(() -> Psm2MeasureWorkParameter.psm2MeasureWorkParameter().build());

		try (final Log logger = new StringBuilderLogger(log)) {

			Psm2MeasureTransformationTrace psm2measureTransformationTrace = executePsm2MeasureTransformation(Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter()
					.psmModel(psmModel.get())
					.measureModel(measureModel)
					.log(getTransformationContext().getByClass(Log.class).orElseGet(() -> logger))
					.scriptUri(transformationScriptRoot)
					.createTrace(workParam.createTrace)
					.parallel(workParam.parallel));

			getTransformationContext().put(psm2measureTransformationTrace);
		}

	}
}