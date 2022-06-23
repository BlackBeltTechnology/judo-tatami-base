package hu.blackbelt.judo.tatami.asm2rdbms;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
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

    @NonNull
    Map<EObject, List<EObject>> trace;

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
        return trace;
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

        Resource traceResoureLoaded = createAsm2RdbmsTraceResource(
                URI.createURI(ASM_2_RDBMS_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(traceModelInputStream, ImmutableMap.of());

        return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                .rdbmsModel(rdbmsModel)
                .asmModel(asmModel)
                .trace(resolveAsm2RdbmsTrace(traceResoureLoaded, asmModel, rdbmsModel)).build();

    }

    /**
     * Save trace to the given stream.
     *
     * @param outputStream
     * @return
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
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
