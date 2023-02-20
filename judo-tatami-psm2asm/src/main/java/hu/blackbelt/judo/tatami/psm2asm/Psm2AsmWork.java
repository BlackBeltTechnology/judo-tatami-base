package hu.blackbelt.judo.tatami.psm2asm;

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

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;

import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;

@Slf4j
public class Psm2AsmWork extends AbstractTransformationWork {

	final URI transformationScriptRoot;

	@Builder(builderMethodName = "psm2AsmWorkParameter")
	public static final class Psm2AsmWorkParameter {
		@Builder.Default
		Boolean createTrace = false;
		@Builder.Default
		Boolean parallel = true;
	}

	public Psm2AsmWork(TransformationContext transformationContext, URI transformationScriptRoot) {
		super(transformationContext);
		this.transformationScriptRoot = transformationScriptRoot;
	}

	public Psm2AsmWork(TransformationContext transformationContext) {
		this(transformationContext, Psm2Asm.calculatePsm2AsmTransformationScriptURI());
	}

	@Override
	public void execute() throws Exception {

		Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
		psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

		AsmModel asmModel = getTransformationContext().getByClass(AsmModel.class)
				.orElseGet(() -> buildAsmModel()
						.name(psmModel.get().getName())
						.version(psmModel.get().getVersion())
						.build());
		getTransformationContext().put(asmModel);

		Psm2AsmWorkParameter workParam = getTransformationContext().getByClass(Psm2AsmWorkParameter.class)
				.orElseGet(() -> Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter().build());

		try (final Log logger = new StringBuilderLogger(log)) {

			Psm2AsmTransformationTrace psm2AsmTransformationTrace = executePsm2AsmTransformation(Psm2Asm.Psm2AsmParameter.psm2AsmParameter()
					.psmModel(psmModel.get())
					.asmModel(asmModel)
					.log(getTransformationContext().getByClass(Log.class).orElseGet(() -> logger))
					.scriptUri(transformationScriptRoot)
					.createTrace(workParam.createTrace)
					.parallel(workParam.parallel));

			getTransformationContext().put(psm2AsmTransformationTrace);
		}
	}
}
