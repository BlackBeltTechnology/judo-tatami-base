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

import hu.blackbelt.epsilon.runtime.execution.EmfUtils;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.slf4j.Logger;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;

@Slf4j
public class Psm2AsmWork extends AbstractTransformationWork {

    final URI transformationScriptRoot;

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
                        .build());
        getTransformationContext().put(asmModel);

        EmfUtils.addEmfPackagesToResourceSet(asmModel.getResourceSet());

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

        Map<EObject, List<EObject>> trace = transformation.execute();
        
        return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                .trace(trace)
                .build();
    }

    private Psm2AsmTransformationTrace executeEtlTransformation(
            PsmModel psmModel, AsmModel asmModel, Psm2AsmWorkParameter workParam) throws Exception {
        
        try (final StringBuilderLogger logger = new StringBuilderLogger(log)) {
            return executePsm2AsmTransformation(Psm2Asm.Psm2AsmParameter.psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .log(getTransformationContext().getByClass(Logger.class).orElseGet(() -> logger))
                    .scriptUri(transformationScriptRoot)
                    .createTrace(workParam.createTrace)
                    .useCache(workParam.useCache)
                    .parallel(workParam.parallel));
        }
    }
}
