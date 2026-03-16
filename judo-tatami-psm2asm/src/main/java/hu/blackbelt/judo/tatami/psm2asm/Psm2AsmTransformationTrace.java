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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static hu.blackbelt.judo.tatami.util.TransformationTraceExtractor.createTraceModelResource;
import static hu.blackbelt.judo.tatami.util.TransformationTraceExtractor.createTraceModelResourceFromEObjectMap;
import static hu.blackbelt.judo.tatami.util.TransformationTraceExtractor.getTransformationTraceFromEtlExecutionContext;
import static hu.blackbelt.judo.tatami.util.TransformationTraceExtractor.resolveTransformationTraceAsEObjectMap;


@Builder(builderMethodName = "psm2AsmTransformationTraceBuilder")
public class Psm2AsmTransformationTrace implements TransformationTrace {

    public static final String PSM_2_ASM_URI_POSTFIX = "psm2asm";
    public static final String PSM_2_ASM_TRACE_URI_PREFIX = "psm2asmTrace:";

    @NonNull
    @Getter
    PsmModel psmModel;

    @NonNull
    @Getter
    AsmModel asmModel;

    // ETL trace (null when Zeta used)
    Map<EObject, List<EObject>> trace;

    // Zeta trace (null when ETL used)
    hu.blackbelt.judo.zeta.transformation.core.TransformationTrace zetaTrace;

    @Override
    public List<Class> getSourceModelTypes() {
        return ImmutableList.of(PsmModel.class);
    }

    @Override
    public List<Object> getSourceModels() {
        return ImmutableList.of(psmModel);
    }

    @Override
    public <T> T getSourceModel(Class<T> sourceModelType) {
        return null;
    }

    @Override
    public <T> ResourceSet getSourceResourceSet(Class<T> sourceModelType) {
        if (sourceModelType == PsmModel.class) {
            return psmModel.getResourceSet();
        }
        throw new IllegalArgumentException("Unknown source model type: " + sourceModelType.getName());
    }

    @Override
    public <T> URI getSourceURI(Class<T> sourceModelType) {
        if (sourceModelType == PsmModel.class) {
            return psmModel.getUri();
        }
        throw new IllegalArgumentException("Unknown source model type: " + sourceModelType.getName());
    }

    @Override
    public Class getTargetModelType() {
        return AsmModel.class;
    }

    @Override
    public Object getTargetModel() {
        return asmModel;
    }

    @Override
    public ResourceSet getTargetResourceSet() {
        return asmModel.getResourceSet();
    }

    @Override
    public URI getTargetURI() {
        return asmModel.getUri();
    }

    @Override
    public Class<? extends TransformationTrace> getType() {
        return Psm2AsmTransformationTrace.class;
    }

    @Override
    public String getTransformationTraceName() {
        return "psm2asm";
    }

    @Override
    public String getModelVersion() {
        return psmModel.getVersion();
    }

    @Override
    public Map<EObject, List<EObject>> getTransformationTrace() {
        // Return ETL trace or empty map for Zeta (backward compatibility)
        return trace != null ? trace : Collections.emptyMap();
    }

    /**
     * Get the Zeta transformation trace.
     * @return the Zeta TransformationTrace, or null if ETL was used
     */
    public hu.blackbelt.judo.zeta.transformation.core.TransformationTrace getZetaTrace() {
        return zetaTrace;
    }

    /**
     * Check if this trace was produced by Zeta transformation.
     * @return true if Zeta trace is available, false if ETL trace
     */
    public boolean isZetaTrace() {
        return zetaTrace != null;
    }

    @Override
    public String getModelName() {
        return psmModel.getName();
    }

    /**
     * Create PSM 2 ASM Trace model {@link Resource} wth isolated {@link ResourceSet}
     *
     * @param uri
     * @param uriHandler
     *
     * @return the trace {@link Resource} with the registered namespace.
     */
    public static Resource createPsm2AsmTraceResource(org.eclipse.emf.common.util.URI uri,
                                                      URIHandler uriHandler) {
        return createTraceModelResource(PSM_2_ASM_URI_POSTFIX, uri, uriHandler);
    }

    /**
     * Resolves PSM 2 ASM Trace model {@link Resource} and resturns the trace {@link EObject } map
     *
     * @param traceResource
     * @param psmModel
     * @param asmModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolvePsm2AsmTrace(Resource traceResource,
                                                                  PsmModel psmModel,
                                                                  AsmModel asmModel) {
        return resolvePsm2AsmTrace(traceResource.getContents(), psmModel, asmModel);
    }

    /**
     * Resolves PSM 2 ASM trace:Trace model entries returns the trace {@link EObject } map
     *
     * @param trace
     * @param psmModel
     * @param asmModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolvePsm2AsmTrace(List<EObject> trace,
                                                                  PsmModel psmModel,
                                                                  AsmModel asmModel) {
        return resolveTransformationTraceAsEObjectMap(trace,
                ImmutableList.of(psmModel.getResourceSet(), asmModel.getResourceSet()));
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model entrie.
     *
     * @param trace
     *
     * @return the trace trace:Trace entries
     */
    public static List<EObject> getPsm2AsmTrace(Map<EObject, List<EObject>> trace) {
        return getTransformationTraceFromEtlExecutionContext(PSM_2_ASM_URI_POSTFIX, trace);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     * @param uriHandler
     * @return the trace trace:Trace entries
     */
    public static Resource getPsm2AsmTraceResource(Map<EObject, List<EObject>> trace,
                                                   org.eclipse.emf.common.util.URI modelUri,
                                                   URIHandler uriHandler) {
        return createTraceModelResourceFromEObjectMap(trace, PSM_2_ASM_URI_POSTFIX, modelUri, uriHandler);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     *
     * @return the trace trace:Trace entries
     */
    public static Resource getPsm2AsmTraceResource(Map<EObject, List<EObject>> trace,
                                                   org.eclipse.emf.common.util.URI modelUri) {
        return createTraceModelResourceFromEObjectMap(trace, PSM_2_ASM_URI_POSTFIX, modelUri, null);
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param psmModel
     * @param asmModel
     * @param traceModelFile
     * @return
     * @throws IOException
     */
    public static Psm2AsmTransformationTrace fromModelsAndTrace(String modelName,
                                                                  PsmModel psmModel,
                                                                  AsmModel asmModel,
                                                                  File traceModelFile) throws IOException {
        return fromModelsAndTrace(modelName, psmModel, asmModel, new FileInputStream(traceModelFile));
    }


    /**
     * Create transformation trace from models and trace inputstream.
     * Automatically detects format: JSON (Zeta) vs XMI (ETL) by peeking at the first non-whitespace byte.
     *
     * @param modelName
     * @param psmModel
     * @param asmModel
     * @param traceModelInputStream
     * @return
     * @throws IOException
     */
    public static Psm2AsmTransformationTrace fromModelsAndTrace(String modelName,
                                                                PsmModel psmModel,
                                                                AsmModel asmModel,
                                                                InputStream traceModelInputStream) throws IOException {

        checkArgument(psmModel.getName().equals(asmModel.getName()), "Model name does not match");

        BufferedInputStream buffered = new BufferedInputStream(traceModelInputStream);
        buffered.mark(1024);

        // Peek at first non-whitespace byte to detect format
        int b;
        do {
            b = buffered.read();
        } while (b != -1 && Character.isWhitespace(b));
        buffered.reset();

        if (b == '{' || b == -1) {
            // JSON format (Zeta trace) or empty stream — build trace without ETL map.
            // This is consistent with runtime behavior where Zeta traces return empty map
            // from getTransformationTrace().
            return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                    .asmModel(asmModel)
                    .psmModel(psmModel)
                    .build();
        }

        // XMI format - ETL trace (legacy)
        Resource traceResoureLoaded = createPsm2AsmTraceResource(
                URI.createURI(PSM_2_ASM_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(buffered, ImmutableMap.of());

        return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                .asmModel(asmModel)
                .psmModel(psmModel)
                .trace(resolvePsm2AsmTrace(traceResoureLoaded, psmModel, asmModel)).build();

    }

    /**
     * Save trace to the given stream.
     * For Zeta traces, saves as JSON format. For ETL traces, saves as XMI format.
     *
     * @param outputStream
     * @return the saved resource (null for Zeta traces)
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
        if (isZetaTrace()) {
            zetaTrace.saveToJson(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            return null;
        }
        Resource  traceResoureSaved = getPsm2AsmTraceResource(
                trace,
                URI.createURI(PSM_2_ASM_TRACE_URI_PREFIX + getModelName()));

        traceResoureSaved.save(outputStream, ImmutableMap.of());
        return traceResoureSaved;
    }

    /**
     * Save trace to the given file.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public Resource save(File file) throws IOException {
        return save(new FileOutputStream(file));
    }

}
