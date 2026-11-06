package hu.blackbelt.judo.tatami.psm2asm.perf;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import hu.blackbelt.judo.tatami.test.util.AbstractDualComparisonTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Tag;

import java.io.FileInputStream;
import java.nio.file.Path;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.LoadArguments.psmLoadArgumentsBuilder;

/**
 * Discovery comparison test for PSM2ASM transformation.
 *
 * <p>Discovers models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}), then compares
 * ETL vs ZETA transformation outputs.
 */
@Tag("comparison")
@Tag("performance")
public class Psm2AsmDiscoveryComparisonTest extends AbstractDualComparisonTest<PsmModel, AsmModel> {

    @Override
    protected String getModuleName() {
        return "psm2asm";
    }

    @Override
    protected String getOutputLabel() {
        return "Classifiers";
    }

    @Override
    protected PsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("psm");
        return PsmModel.loadPsmModel(psmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI("source-psm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    @Override
    protected AsmModel executeEtl(PsmModel source, ExternalModelConfig config) throws Exception {
        return executeTransformation(source, TransformationMode.ETL);
    }

    @Override
    protected AsmModel executeZeta(PsmModel source, ExternalModelConfig config) throws Exception {
        return executeTransformation(source, TransformationMode.ZETA);
    }

    @Override
    protected Resource getResource(AsmModel model) {
        return model.getResourceSet().getResources().get(0);
    }

    private AsmModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("DiscoveryComparisonTest");
        context.put(psmModel);
        context.put(Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .parallel(false)
                .useCache(true)
                .build());

        Psm2AsmWork work = new Psm2AsmWork(context);
        work.execute();

        return context.getByClass(AsmModel.class)
                .orElseThrow(() -> new IllegalStateException("ASM Model not found after transformation"));
    }
}
