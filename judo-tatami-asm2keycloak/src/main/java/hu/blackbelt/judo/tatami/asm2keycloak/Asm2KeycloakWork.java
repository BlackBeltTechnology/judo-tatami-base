package hu.blackbelt.judo.tatami.asm2keycloak;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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

import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;

import java.net.URI;
import java.util.Optional;

import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Asm2KeycloakWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "asm2KeycloakWorkParameter")
    public static final class Asm2KeycloakWorkParameter {
        @Builder.Default
        Boolean createTrace = true;
        @Builder.Default
        Boolean parallel = true;
        @Builder.Default
        Boolean useCache = false;
    }

    final URI transformationScriptRoot;

    public Asm2KeycloakWork(TransformationContext transformationContext, URI transformationScriptRoot) {
        super(transformationContext);
        this.transformationScriptRoot = transformationScriptRoot;
    }

    public Asm2KeycloakWork(TransformationContext transformationContext) {
        this(transformationContext, Asm2Keycloak.calculateAsm2KeycloakTransformationScriptURI());
    }

    @Override
    public void execute() throws Exception {
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        KeycloakModel keycloakModel = getTransformationContext().getByClass(KeycloakModel.class)
                .orElseGet(() -> buildKeycloakModel()
                        .name(asmModel.get().getName())
                        .version(asmModel.get().getVersion())
                        .build());

        getTransformationContext().put(keycloakModel);

        Asm2KeycloakWorkParameter workParam = getTransformationContext().getByClass(Asm2KeycloakWorkParameter.class)
                .orElseGet(() -> Asm2KeycloakWork.Asm2KeycloakWorkParameter.asm2KeycloakWorkParameter().build());

        try (final StringBuilderLogger logger = new StringBuilderLogger(log)) {
            Asm2KeycloakTransformationTrace asm2KeycloakTransformationTrace = executeAsm2KeycloakTransformation(
                    Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter()
                            .asmModel(asmModel.get())
                            .keycloakModel(keycloakModel)
                            .log(getTransformationContext().getByClass(Logger.class).orElseGet(() -> logger))
                            .scriptUri(transformationScriptRoot)
                            .createTrace(workParam.createTrace)
                            .useCache(workParam.useCache)
                            .parallel(workParam.parallel));

            getTransformationContext().put(asm2KeycloakTransformationTrace);
        }
    }
}
