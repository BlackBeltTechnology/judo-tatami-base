package hu.blackbelt.judo.tatami.asm2keycloak.perf;

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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import hu.blackbelt.judo.tatami.test.util.AbstractDualComparisonTest;
import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Tag;

import java.io.FileInputStream;
import java.nio.file.Path;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;

/**
 * Discovery comparison test for ASM2Keycloak transformation.
 *
 * <p>Discovers models from auto-discovery ({@code judo.test.discovery.basedir})
 * and properties file ({@code external-model-tests.properties}), then compares
 * ETL vs ZETA transformation outputs.
 */
@Tag("comparison")
@Tag("performance")
public class Asm2KeycloakDiscoveryComparisonTest extends AbstractDualComparisonTest<AsmModel, KeycloakModel> {

    @Override
    protected String getModuleName() {
        return "asm2keycloak";
    }

    @Override
    protected String getOutputLabel() {
        return "Clients";
    }

    @Override
    protected AsmModel parseSource(ExternalModelConfig config) throws Exception {
        Path modelFile = config.getModelFile("asm");
        return AsmModel.loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createURI("source-asm.model"))
                .inputStream(new FileInputStream(modelFile.toFile())));
    }

    @Override
    protected KeycloakModel executeEtl(AsmModel source, ExternalModelConfig config) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel().name("discovery-keycloak").build();
        executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                .asmModel(source)
                .keycloakModel(keycloakModel));
        return keycloakModel;
    }

    @Override
    protected KeycloakModel executeZeta(AsmModel source, ExternalModelConfig config) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel().name("discovery-keycloak").build();
        Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                .asmModel(source)
                .keycloakModel(keycloakModel)
                .build();
        transformation.execute();
        return keycloakModel;
    }

    @Override
    protected Resource getResource(KeycloakModel model) {
        return model.getResourceSet().getResources().get(0);
    }
}
