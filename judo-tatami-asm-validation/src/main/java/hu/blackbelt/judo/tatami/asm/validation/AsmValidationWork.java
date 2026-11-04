package hu.blackbelt.judo.tatami.asm.validation;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * ASM validation work. ZETA/DUAL mode runs {@link AsmValidationZetaWork} (Epsilon-free, in this artifact);
 * ETL mode delegates the EVL validation to the optional {@link AsmValidationEtlEngine} add-on ({@code judo-tatami-asm-validation-etl}).
 * The mode comes from a {@link AsmValidationWorkParameter} in the context, else {@code judo.transformation.mode}.
 * See change: split-etl-artifacts.
 */
@Slf4j
public class AsmValidationWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "asmValidationWorkParameter")
    @Getter
    public static final class AsmValidationWorkParameter {
        /** Validation engine; defaults to {@code judo.transformation.mode} (ZETA when unset). */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    public AsmValidationWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        AsmValidationWorkParameter workParam = getTransformationContext().getByClass(AsmValidationWorkParameter.class)
                .orElseGet(() -> AsmValidationWorkParameter.asmValidationWorkParameter().build());

        if (workParam.transformationMode.isZeta()) {
            log.info("Executing ASM validation using Zeta engine");
            new AsmValidationZetaWork(getTransformationContext()).execute();
        } else {
            log.info("Executing ASM validation using EVL engine");
            EtlEngineRegistry.require(AsmValidationEtlEngine.class, AsmValidationEtlEngine.ARTIFACT_COORDINATES)
                    .validate(asmModel.get(), log);
        }
    }
}
