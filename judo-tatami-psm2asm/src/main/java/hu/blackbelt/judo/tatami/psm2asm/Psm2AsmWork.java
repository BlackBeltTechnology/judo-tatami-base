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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.EmfPackages;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;

import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;

/**
 * PSM to ASM transformation work. Zeta runs in this (Epsilon-free) artifact; ETL/DUAL delegate to the
 * optional {@link Psm2AsmEtlEngine} add-on. See change: split-etl-artifacts.
 */
@Slf4j
public class Psm2AsmWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "psm2AsmWorkParameter")
    public static final class Psm2AsmWorkParameter {
        @Builder.Default
        Boolean createTrace = false;
        @Builder.Default
        Boolean parallel = true;
        @Builder.Default
        Boolean useCache = true;
        
        /**
         * The transformation engine to use. Defaults to ZETA.
         * Set to ETL for backward compatibility or debugging.
         */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    public Psm2AsmWork(TransformationContext transformationContext) {
        super(transformationContext);
    }

    @Override
    public void execute() throws Exception {

        Optional<PsmModel> psmModel = getTransformationContext().getByClass(PsmModel.class);
        psmModel.orElseThrow(() -> new IllegalArgumentException("PSM Model does not found in transformation context"));

        AsmModel asmModel = getTransformationContext().getByClass(AsmModel.class)
                .orElseGet(() -> buildAsmModel()
                        .build());
        getTransformationContext().put(asmModel);

        EmfPackages.addEmfPackagesToResourceSet(asmModel.getResourceSet());

        Psm2AsmWorkParameter workParam = getTransformationContext().getByClass(Psm2AsmWorkParameter.class)
                .orElseGet(() -> Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter().build());

        Psm2AsmTransformationTrace psm2AsmTransformationTrace;
        
        if (workParam.transformationMode.isZeta()) {
            log.info("Executing PSM to ASM transformation using Zeta engine");
            psm2AsmTransformationTrace = executeZetaTransformation(psmModel.get(), asmModel, workParam);
        } else {
            log.info("Executing PSM to ASM transformation using ETL engine");
            psm2AsmTransformationTrace = executeEtlTransformation(psmModel.get(), asmModel, workParam);
        }

        getTransformationContext().put(psm2AsmTransformationTrace);
    }

    private Psm2AsmTransformationTrace executeZetaTransformation(
            PsmModel psmModel, AsmModel asmModel, Psm2AsmWorkParameter workParam) {

        Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName(psmModel.getName())
                .build();

        // Execute Zeta transformation - returns native Zeta TransformationTrace
        TransformationTrace zetaTrace = transformation.execute();

        return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .zetaTrace(zetaTrace)  // Use Zeta trace, not ETL trace field
                .build();
    }

    private Psm2AsmTransformationTrace executeEtlTransformation(
            PsmModel psmModel, AsmModel asmModel, Psm2AsmWorkParameter workParam) throws Exception {
        return EtlEngineRegistry.require(Psm2AsmEtlEngine.class, Psm2AsmEtlEngine.ARTIFACT_COORDINATES)
                .execute(psmModel, asmModel,
                        getTransformationContext().getByClass(Logger.class).orElse(null),
                        workParam.createTrace, workParam.useCache, workParam.parallel);
    }
}
