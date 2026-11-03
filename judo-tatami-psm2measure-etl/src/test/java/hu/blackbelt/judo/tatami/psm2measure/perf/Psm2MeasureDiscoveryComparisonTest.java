package hu.blackbelt.judo.tatami.psm2measure.perf;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureWork;
import hu.blackbelt.judo.tatami.test.util.AbstractDualComparisonTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Tag;

import java.io.FileInputStream;
import java.nio.file.Path;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;

/**
 * Discovery comparison test for PSM2Measure transformation.
 *
 * <p>Discovers models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}), then compares
 * ETL vs ZETA transformation outputs.
 */
@Tag("comparison")
@Tag("performance")
public class Psm2MeasureDiscoveryComparisonTest extends AbstractDualComparisonTest<PsmModel, MeasureModel> {

    @Override
    protected String getModuleName() {
        return "psm2measure";
    }

    @Override
    protected String getOutputLabel() {
        return "Measures";
    }

    @Override
    protected PsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("psm");
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI("source-psm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    @Override
    protected MeasureModel executeEtl(PsmModel source, ExternalModelConfig config) throws Exception {
        return executeTransformation(source, TransformationMode.ETL);
    }

    @Override
    protected MeasureModel executeZeta(PsmModel source, ExternalModelConfig config) throws Exception {
        return executeTransformation(source, TransformationMode.ZETA);
    }

    @Override
    protected Resource getResource(MeasureModel model) {
        return model.getResourceSet().getResources().get(0);
    }

    private MeasureModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("DiscoveryComparisonTest");
        context.put(psmModel);
        context.put(Psm2MeasureWork.Psm2MeasureWorkParameter.psm2MeasureWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .build());

        Psm2MeasureWork work = new Psm2MeasureWork(context);
        work.execute();

        return context.getByClass(MeasureModel.class)
                .orElseThrow(() -> new IllegalStateException("Measure Model not found after transformation"));
    }
}
