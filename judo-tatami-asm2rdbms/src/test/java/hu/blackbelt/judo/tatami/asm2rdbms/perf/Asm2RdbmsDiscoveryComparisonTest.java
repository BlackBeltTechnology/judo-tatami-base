package hu.blackbelt.judo.tatami.asm2rdbms.perf;

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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import hu.blackbelt.judo.tatami.test.util.AbstractDualComparisonTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.Tag;

import java.io.FileInputStream;
import java.nio.file.Path;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;

/**
 * Discovery comparison test for ASM2RDBMS transformation.
 *
 * <p>Discovers models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}), then compares
 * ETL vs ZETA transformation outputs.
 *
 * <p>The dialect is read from {@code ExternalModelConfig} (properties key {@code dialect},
 * defaults to {@code hsqldb}).
 */
@Tag("comparison")
@Tag("performance")
public class Asm2RdbmsDiscoveryComparisonTest extends AbstractDualComparisonTest<AsmModel, RdbmsModel> {

    @Override
    protected String getModuleName() {
        return "asm2rdbms";
    }

    @Override
    protected String getOutputLabel() {
        return "Tables";
    }

    @Override
    protected AsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("asm");
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI("source-asm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    @Override
    protected RdbmsModel executeEtl(AsmModel source, ExternalModelConfig config) throws Exception {
        String dialect = config != null ? config.getDialect() : "hsqldb";
        RdbmsModel rdbmsModel = buildEmptyRdbmsModel();
        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(source)
                .rdbmsModel(rdbmsModel)
                .dialect(dialect));
        return rdbmsModel;
    }

    @Override
    protected RdbmsModel executeZeta(AsmModel source, ExternalModelConfig config) throws Exception {
        String dialect = config != null ? config.getDialect() : "hsqldb";
        RdbmsModel rdbmsModel = buildEmptyRdbmsModel();

        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());

        Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(source)
                .rdbmsModel(rdbmsModel)
                .dialect(dialect)
                .build();
        transformation.execute();
        return rdbmsModel;
    }

    @Override
    protected Resource getResource(RdbmsModel model) {
        return model.getResourceSet().getResources().get(0);
    }

    private RdbmsModel buildEmptyRdbmsModel() {
        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
        return rdbmsModel;
    }
}
