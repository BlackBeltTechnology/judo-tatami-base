package hu.blackbelt.judo.tatami.psm2measure;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureUtils;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;
import org.eclipse.epsilon.emc.emf.EmfModel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.getTransformationTraceFromEtlExecutionContext;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace.PSM_2_MEASURE_URI_POSTFIX;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace.resolvePsm2MeasureTrace;

@Slf4j
public class Psm2Measure {

    public static final String SCRIPT_ROOT_TATAMI_PSM_2_MEASURE = "tatami/psm2measure/transformations/measure/";

    @Builder(builderMethodName = "psm2MeasureParameter")
    public static final class Psm2MeasureParameter {

        @NonNull
        PsmModel psmModel;

        @NonNull
        MeasureModel measureModel;

        @Builder.Default
        java.net.URI scriptUri = calculatePsm2MeasureTransformationScriptURI();

        Log log;

        @Builder.Default
        Boolean createTrace = false;

        @Builder.Default
        Boolean parallel = true;
    }

    public static Psm2MeasureTransformationTrace executePsm2MeasureTransformation(Psm2MeasureParameter.Psm2MeasureParameterBuilder builder) throws Exception {
        return executePsm2MeasureTransformation(builder.build());
    }

    public static Psm2MeasureTransformationTrace executePsm2MeasureTransformation(Psm2MeasureParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                 () -> {
                                                     loggerToBeClosed.set(true);
                                                     return new BufferedSlf4jLogger(Psm2Measure.log);
                                                 });

        try {
            WrappedEmfModelContext psmModelContext = wrappedEmfModelContextBuilder()
                    .log(log)
                    .name("JUDOPSM")
                    .resource(parameter.psmModel.getResource())
                    .build();

            // Execution context
            ExecutionContext executionContext = executionContextBuilder()
                    .log(log)
                    .resourceSet(parameter.measureModel.getResourceSet())
                    .modelContexts(ImmutableList.of(
                            psmModelContext,
                            wrappedEmfModelContextBuilder()
                                    .log(log)
                                    .name("MEASURES")
                                    .resource(parameter.measureModel.getResource())
                                    .build()))
                    .injectContexts(ImmutableMap.of("psmUtils", new PsmUtils(),
                            "measureUtils", new MeasureUtils(parameter.measureModel.getResourceSet())))
                    .build();

            // run the model / metadata loading
            executionContext.load();

            // Use cache
            ((EmfModel) executionContext.getProjectModelRepository()
                    .getModelByName(psmModelContext.getName())).setCachingEnabled(true);

            EtlExecutionContext etlExecutionContext =
                    etlExecutionContextBuilder()
                            .source(UriUtil.resolve("psmToMeasure.etl", parameter.scriptUri))
                            .parallel(parameter.parallel)
                            .build();

            // Transformation script
            executionContext.executeProgram(etlExecutionContext);

            executionContext.commit();
            executionContext.close();

            Map<EObject, List<EObject>> traceMap = new ConcurrentHashMap<>();
            if (parameter.createTrace) {
                List<EObject> traceModel = getTransformationTraceFromEtlExecutionContext(PSM_2_MEASURE_URI_POSTFIX, etlExecutionContext);
                traceMap = resolvePsm2MeasureTrace(traceModel, parameter.psmModel, parameter.measureModel);
            }

            return Psm2MeasureTransformationTrace.psm2MeasureTransformationTraceBuilder()
                    .measureModel(parameter.measureModel)
                    .psmModel(parameter.psmModel)
                    .trace(traceMap).build();
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    @SneakyThrows(URISyntaxException.class)
    public static URI calculatePsm2MeasureTransformationScriptURI() {
        URI psmRoot = Psm2Measure.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (psmRoot.toString().endsWith(".jar")) {
            psmRoot = new URI("jar:" + psmRoot.toString() + "!/" + SCRIPT_ROOT_TATAMI_PSM_2_MEASURE);
        } else if (psmRoot.toString().startsWith("jar:bundle:")) {
            psmRoot = new URI(psmRoot.toString().substring(4, psmRoot.toString().indexOf("!")) + SCRIPT_ROOT_TATAMI_PSM_2_MEASURE);
        } else {
            psmRoot = new URI(psmRoot.toString() + "/" + SCRIPT_ROOT_TATAMI_PSM_2_MEASURE);
        }
        return psmRoot;
    }

}
