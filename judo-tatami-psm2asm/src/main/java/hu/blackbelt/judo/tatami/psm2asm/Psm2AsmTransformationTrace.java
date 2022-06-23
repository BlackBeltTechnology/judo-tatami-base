package hu.blackbelt.judo.tatami.psm2asm;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.createTraceModelResource;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.createTraceModelResourceFromEObjectMap;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.getTransformationTraceFromEtlExecutionContext;
import static hu.blackbelt.judo.tatami.core.TransformationTraceUtil.resolveTransformationTraceAsEObjectMap;


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

    @NonNull
    Map<EObject, List<EObject>> trace;

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
        return trace;
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

        Resource traceResoureLoaded = createPsm2AsmTraceResource(
                URI.createURI(PSM_2_ASM_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(traceModelInputStream, ImmutableMap.of());

        return Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                .asmModel(asmModel)
                .psmModel(psmModel)
                .trace(resolvePsm2AsmTrace(traceResoureLoaded, psmModel, asmModel)).build();

    }

    /**
     * Save trace to the given stream.
     *
     * @param outputStream
     * @return
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
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
