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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TraceEntry;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.core.ZetaTraceLoader;
import hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache;
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


@Builder(builderMethodName = "asm2RdbmsTransformationTraceBuilder")
public class Asm2RdbmsTransformationTrace implements TransformationTrace {

    public static final String ASM_2_RDBMS_URI_POSTFIX = "asm2rdbms";
    public static final String ASM_2_RDBMS_TRACE_URI_PREFIX = "asm2rdbmsTrace:";

    @NonNull
    @Getter
    AsmModel asmModel;

    @NonNull
    @Getter
    RdbmsModel rdbmsModel;

    // ETL trace (null when Zeta used)
    Map<EObject, List<EObject>> trace;

    // Zeta trace (null when ETL used)
    hu.blackbelt.judo.zeta.transformation.core.TransformationTrace zetaTrace;

    @Override
    public List<Class> getSourceModelTypes() {
        return ImmutableList.of(AsmModel.class);
    }

    @Override
    public List<Object> getSourceModels() {
        return ImmutableList.of(asmModel);
    }

    @Override
    public <T> T getSourceModel(Class<T> sourceModelType) {
        return null;
    }

    @Override
    public <T> ResourceSet getSourceResourceSet(Class<T> sourceModelType) {
        if (sourceModelType == AsmModel.class) {
            return asmModel.getResourceSet();
        }
        throw new IllegalArgumentException("Unknown source model type: " + sourceModelType.getName());
    }

    @Override
    public <T> URI getSourceURI(Class<T> sourceModelType) {
        if (sourceModelType == AsmModel.class) {
            return asmModel.getUri();
        }
        throw new IllegalArgumentException("Unknown source model type: " + sourceModelType.getName());
    }

    @Override
    public Class getTargetModelType() {
        return RdbmsModel.class;
    }

    @Override
    public Object getTargetModel() {
        return rdbmsModel;
    }

    @Override
    public ResourceSet getTargetResourceSet() {
        return rdbmsModel.getResourceSet();
    }

    @Override
    public URI getTargetURI() {
        return rdbmsModel.getUri();
    }

    @Override
    public Class<? extends TransformationTrace> getType() {
        return Asm2RdbmsTransformationTrace.class;
    }

    @Override
    public String getTransformationTraceName() {
        return "asm2rdbms";
    }

    @Override
    public String getModelVersion() {
        return asmModel.getVersion();
    }

    @Override
    public Map<EObject, List<EObject>> getTransformationTrace() {
        if (trace != null) {
            return trace;
        }
        if (zetaTrace != null) {
            // Convert Zeta trace entries to legacy format: source → [targets]
            Map<EObject, List<EObject>> legacyTrace = new java.util.LinkedHashMap<>();
            for (hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache.TraceEntry entry : zetaTrace.getEntries()) {
                legacyTrace.computeIfAbsent(entry.getSource(), k -> new java.util.ArrayList<>())
                        .add(entry.getTarget());
            }
            return legacyTrace;
        }
        return Collections.emptyMap();
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
        return asmModel.getName();
    }


    /**
     * Create PSM 2 ASM Trace model {@link Resource} wth isolated {@link ResourceSet}
     *
     * @param uri
     * @param uriHandler
     *
     * @return the trace {@link Resource} with the registered namespace.
     */
    public static Resource createAsm2RdbmsTraceResource(org.eclipse.emf.common.util.URI uri,
                                                          URIHandler uriHandler) {
        return createTraceModelResource(ASM_2_RDBMS_URI_POSTFIX, uri, uriHandler);
    }

    /**
     * Resolves PSM 2 ASM Trace model {@link Resource} and resturns the trace {@link EObject } map
     *
     * @param traceResource
     * @param asmModel
     * @param rdbmsModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolveAsm2RdbmsTrace(Resource traceResource,
                                                                      AsmModel asmModel,
                                                                      RdbmsModel rdbmsModel) {
        return resolveAsm2RdbmsTrace(traceResource.getContents(), asmModel, rdbmsModel);
    }

    /**
     * Resolves PSM 2 ASM trace:Trace model entries returns the trace {@link EObject } map
     *
     * @param trace
     * @param asmModel
     * @param rdbmsModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolveAsm2RdbmsTrace(List<EObject> trace,
                                                                      AsmModel asmModel,
                                                                      RdbmsModel rdbmsModel) {
        return resolveTransformationTraceAsEObjectMap(trace,
                ImmutableList.of(asmModel.getResourceSet(), rdbmsModel.getResourceSet()));
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model entrie.
     *
     * @param trace
     *
     * @return the trace trace:Trace entries
     */
    public static List<EObject> getAsm2RdbmsTrace(Map<EObject, List<EObject>> trace) {
        return getTransformationTraceFromEtlExecutionContext(ASM_2_RDBMS_URI_POSTFIX, trace);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     * @param uriHandler
     * @return the trace trace:Trace entries
     */
    public static Resource getAsm2RdbmsTraceResource(Map<EObject, List<EObject>> trace,
                                                       org.eclipse.emf.common.util.URI modelUri,
                                                       URIHandler uriHandler) {
        return createTraceModelResourceFromEObjectMap(trace, ASM_2_RDBMS_URI_POSTFIX, modelUri, uriHandler);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     *
     * @return the trace trace:Trace entries
     */
    public static Resource getAsm2RdbmsTraceResource(Map<EObject, List<EObject>> trace,
                                                       org.eclipse.emf.common.util.URI modelUri) {
        return createTraceModelResourceFromEObjectMap(trace, ASM_2_RDBMS_URI_POSTFIX, modelUri, null);
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param asmModel
     * @param rdbmsModel
     * @param traceModelFile
     * @return
     * @throws IOException
     */
    public static Asm2RdbmsTransformationTrace fromModelsAndTrace(String modelName,
                                                                    AsmModel asmModel,
                                                                    RdbmsModel rdbmsModel,
                                                                    File traceModelFile) throws IOException {
        return fromModelsAndTrace(modelName, asmModel, rdbmsModel, new FileInputStream(traceModelFile));
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param asmModel
     * @param rdbmsModel
     * @param traceModelInputStream
     * @return
     * @throws IOException
     */
    public static Asm2RdbmsTransformationTrace fromModelsAndTrace(String modelName,
                                                                  AsmModel asmModel,
                                                                  RdbmsModel rdbmsModel,
                                                                  InputStream traceModelInputStream) throws IOException {

        checkArgument(asmModel.getName().equals(rdbmsModel.getName()), "Model name does not match");

        BufferedInputStream buffered = new BufferedInputStream(traceModelInputStream);
        buffered.mark(1024);

        int b;
        do {
            b = buffered.read();
        } while (b != -1 && Character.isWhitespace(b));
        buffered.reset();

        if (b == '{') {
            // JSON format (Zeta trace) — parse and reconstruct ElementResolutionCache
            ZetaTraceLoader loader = new ZetaTraceLoader();
            List<TraceEntry> entries = loader.loadTrace(buffered,
                    ImmutableList.of(asmModel.getResourceSet(), rdbmsModel.getResourceSet()));
            return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                    .rdbmsModel(rdbmsModel)
                    .asmModel(asmModel)
                    .zetaTrace(rebuildZetaTrace(entries))
                    .build();
        }

        if (b == -1) {
            // Empty stream — no trace data
            return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                    .rdbmsModel(rdbmsModel)
                    .asmModel(asmModel)
                    .build();
        }

        Resource traceResoureLoaded = createAsm2RdbmsTraceResource(
                URI.createURI(ASM_2_RDBMS_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(buffered, ImmutableMap.of());

        return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                .rdbmsModel(rdbmsModel)
                .asmModel(asmModel)
                .trace(resolveAsm2RdbmsTrace(traceResoureLoaded, asmModel, rdbmsModel)).build();

    }

    private static hu.blackbelt.judo.zeta.transformation.core.TransformationTrace rebuildZetaTrace(
            List<TraceEntry> entries) {
        ElementResolutionCache cache = new ElementResolutionCache(true);
        for (TraceEntry entry : entries) {
            for (EObject source : entry.getSources()) {
                for (EObject target : entry.getTargets()) {
                    if (entry.getDiscriminator() != null) {
                        cache.addDiscriminatedMapping(source, target,
                                entry.getRuleName(), entry.getDiscriminator());
                    } else {
                        cache.addMapping(source, entry.getRuleName(),
                                target, entry.isPrimary());
                    }
                }
            }
        }
        return new hu.blackbelt.judo.zeta.transformation.core.TransformationTrace(cache);
    }

    /**
     * Save trace to the given stream.
     *
     * @param outputStream
     * @return
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
        if (isZetaTrace()) {
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            zetaTrace.saveToJson(writer);
            writer.flush();
            return null;
        }
        Resource  traceResoureSaved = getAsm2RdbmsTraceResource(
                trace,
                URI.createURI(ASM_2_RDBMS_TRACE_URI_PREFIX + getModelName()));

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
