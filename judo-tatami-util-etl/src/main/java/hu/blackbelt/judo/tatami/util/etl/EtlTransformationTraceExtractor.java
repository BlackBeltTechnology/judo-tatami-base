package hu.blackbelt.judo.tatami.util.etl;

/*-
 * #%L
 * Judo :: Tatami :: Util :: ETL
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

import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.exceptions.ScriptExecutionException;
import hu.blackbelt.judo.tatami.util.TransformationTraceExtractor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.etl.trace.TransformationTrace;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts transformation traces from an Epsilon ETL execution.
 * Moved verbatim from {@link TransformationTraceExtractor}; see change: split-etl-artifacts.
 */
public final class EtlTransformationTraceExtractor {

    private EtlTransformationTraceExtractor() {
    }

    /**
     * Get list of trace:Trace entries from an Epsilon {@link EtlExecutionContext} which contains information
     * about a model to model transformation.
     *
     * @param nameSpace the logical name of trace. Its a postfix for NSUri too.
     * @param etlExecutionContext the {@link EtlExecutionContext} which represents a transformation execution.
     * @return List of trace:Trace EObjects which contain source : targets mapping
     */
    public static List<EObject> getTransformationTraceFromEtlExecutionContext(String nameSpace, EtlExecutionContext etlExecutionContext) {
        return TransformationTraceExtractor.getTransformationTraceFromEtlExecutionContext(nameSpace,
                getTraceEObjectMapFromEtlExecutionContext(etlExecutionContext));
    }

    /**
     * Get flattened EObject trace map from Epsilon {@link EtlExecutionContext}.
     * @param etlExecutionContext the {@link EtlExecutionContext} which represents a transformation execution.
     * @return trace {@link EObject} map
     */
    public static Map<EObject, List<EObject>> getTraceEObjectMapFromEtlExecutionContext(EtlExecutionContext etlExecutionContext) {
        try {
            TransformationTrace transformationTrace = ((EtlModule) etlExecutionContext
                    .getModule(ImmutableMap.of()))
                    .getContext()
                    .getTransformationTrace();

            return transformationTrace.getTransformations().stream()
                    .filter(tr -> tr.getSource() instanceof EObject)
                    .collect(Collectors.toMap(
                            tr -> (EObject) tr.getSource(),
                            tr -> tr.getTargets().stream()
                                    .filter(EObject.class :: isInstance)
                                    .map(EObject.class :: cast)
                                    .collect(Collectors.toList()),
                            (ls1, ls2) -> Stream.concat(ls1.stream(), ls2.stream()).collect(Collectors.toList())));
        } catch (ScriptExecutionException ignore) {
            // Never happening in this context
            throw new IllegalStateException("It is a serious bug. In theory it can never happen.");
        }
    }
}
