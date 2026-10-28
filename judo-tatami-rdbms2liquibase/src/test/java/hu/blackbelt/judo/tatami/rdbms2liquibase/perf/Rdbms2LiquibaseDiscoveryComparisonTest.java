package hu.blackbelt.judo.tatami.rdbms2liquibase.perf;

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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import hu.blackbelt.judo.tatami.test.util.AbstractDualComparisonTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Tag;

import java.io.FileInputStream;
import java.nio.file.Path;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;

/**
 * Discovery comparison test for RDBMS2Liquibase transformation.
 *
 * <p>Discovers models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}), then compares
 * ETL vs ZETA transformation outputs.
 *
 * <p>RDBMS model files use dialect suffix: {@code <model-name>-rdbms_<dialect>.model}.
 * The dialect is read from {@code ExternalModelConfig} (defaults to {@code hsqldb}).
 */
@Tag("comparison")
@Tag("performance")
public class Rdbms2LiquibaseDiscoveryComparisonTest extends AbstractDualComparisonTest<RdbmsModel, LiquibaseModel> {

    @Override
    protected String getModuleName() {
        return "rdbms2liquibase";
    }

    @Override
    protected String getOutputLabel() {
        return "ChangeSets";
    }

    @Override
    protected RdbmsModel parseSource(ExternalModelConfig config) throws Exception {
        String dialect = config.getDialect();
        // RDBMS models use dialect suffix: {name}-rdbms_hsqldb.model
        Path modelFile = config.modelDirectory().resolve(config.modelName() + "-rdbms_" + dialect + ".model");

        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        return RdbmsModel.loadRdbmsModel(rdbmsLoadArgumentsBuilder()
                .resourceSet(rdbmsModel.getResourceSet())
                .uri(org.eclipse.emf.common.util.URI.createURI("source-rdbms.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    @Override
    protected LiquibaseModel executeEtl(RdbmsModel source, ExternalModelConfig config) throws Exception {
        String dialect = config != null ? config.getDialect() : "hsqldb";
        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name("discovery-liquibase")
                .build();
        executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                .rdbmsModel(source)
                .liquibaseModel(liquibaseModel)
                .dialect(dialect));
        return liquibaseModel;
    }

    @Override
    protected LiquibaseModel executeZeta(RdbmsModel source, ExternalModelConfig config) throws Exception {
        String dialect = config != null ? config.getDialect() : "hsqldb";
        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name("discovery-liquibase")
                .build();
        Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                .rdbmsModel(source)
                .liquibaseModel(liquibaseModel)
                .dialect(dialect)
                .build();
        transformation.execute();
        return liquibaseModel;
    }

    @Override
    protected Resource getResource(LiquibaseModel model) {
        return model.getResourceSet().getResources().get(0);
    }
}
