package hu.blackbelt.judo.tatami.asm2rdbms;

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

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;

import java.net.URI;
import java.util.Optional;

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Asm2RdbmsWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "asm2RdbmsWorkParameter")
    public static final class Asm2RdbmsWorkParameter {
        @Builder.Default
        Boolean createTrace = true;
        @Builder.Default
        Boolean parallel = true;
        @Builder.Default
        Boolean useCache = false;
    }

    final URI transformationScriptRoot;
    final URI modelRoot;

    private String dialect;

    public Asm2RdbmsWork(TransformationContext transformationContext, URI transformationScriptRoot, URI modelRoot, String dialect) {
        super(transformationContext);
        this.transformationScriptRoot = transformationScriptRoot;
        this.modelRoot = modelRoot;
        this.dialect = dialect;
    }

    public Asm2RdbmsWork(TransformationContext transformationContex, String dialect) {
        this(transformationContex, Asm2Rdbms.calculateAsm2RdbmsTransformationScriptURI(), Asm2Rdbms.calculateAsm2RdbmsModelURI(), dialect);
    }

    public static void putModel(TransformationContext transformationContext, RdbmsModel rdbmsModel, String dialect) {
        transformationContext.put("rdbms:" + dialect, rdbmsModel);
    }

    public static Optional<RdbmsModel> getRdbmsModel(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(RdbmsModel.class, "rdbms:" + dialect);
    }

    public static void putAsm2RdbmsTrace(TransformationContext transformationContext, Asm2RdbmsTransformationTrace trace, String dialect) {
        transformationContext.put("asm2rdbmstrace:" + dialect, trace);
    }

    public static Optional<Asm2RdbmsTransformationTrace> getAsm2RdbmsTrace(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(Asm2RdbmsTransformationTrace.class, "asm2rdbmstrace:" + dialect);
    }

    @Override
    public void execute() throws Exception {
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        Asm2RdbmsWorkParameter workParameter = getTransformationContext().getByClass(Asm2RdbmsWorkParameter.class)
                .orElseGet(() -> Asm2RdbmsWork.Asm2RdbmsWorkParameter.asm2RdbmsWorkParameter().build());

        RdbmsModel rdbmsModel = getTransformationContext().getByClass(RdbmsModel.class)
                .orElseGet(() -> buildRdbmsModel()
                        .build());

        // The RDBMS model resources have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        // Load mapping model

        putModel(getTransformationContext(), rdbmsModel, dialect);

        try (final Log logger = new StringBuilderLogger(log)) {

            Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace = executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter()
                    .asmModel(asmModel.get())
                    .rdbmsModel(rdbmsModel)
                    .log(getTransformationContext().getByClass(Log.class).orElseGet(() -> logger))
                    .scriptUri(transformationScriptRoot)
                    .excelModelUri(modelRoot)
                    .dialect(dialect)
                    .parallel(workParameter.parallel)
                    .useCache(workParameter.useCache)
                    .createTrace(workParameter.createTrace));

            putAsm2RdbmsTrace(getTransformationContext(), asm2RdbmsTransformationTrace, dialect);
        }
    }
}
