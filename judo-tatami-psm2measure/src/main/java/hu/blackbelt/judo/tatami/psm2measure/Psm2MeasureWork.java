package hu.blackbelt.judo.tatami.psm2measure;

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

import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
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
        @Builder.Default
        Boolean useCache = false;
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
                .orElseGet(() -> buildMeasureModel()
                        .name(psmModel.get().getName())
                        .version(psmModel.get().getVersion())
                        .build());
        getTransformationContext().put(measureModel);

        Psm2MeasureWorkParameter workParam = getTransformationContext().getByClass(Psm2MeasureWorkParameter.class)
                .orElseGet(() -> Psm2MeasureWorkParameter.psm2MeasureWorkParameter().build());

        try (final StringBuilderLogger logger = new StringBuilderLogger(log)) {

            Psm2MeasureTransformationTrace psm2measureTransformationTrace = executePsm2MeasureTransformation(Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter()
                    .psmModel(psmModel.get())
                    .measureModel(measureModel)
                    .log(getTransformationContext().getByClass(Logger.class).orElseGet(() -> logger))
                    .scriptUri(transformationScriptRoot)
                    .createTrace(workParam.createTrace)
                    .useCache(workParam.useCache)
                    .parallel(workParam.parallel));

            getTransformationContext().put(psm2measureTransformationTrace);
        }

    }
}
