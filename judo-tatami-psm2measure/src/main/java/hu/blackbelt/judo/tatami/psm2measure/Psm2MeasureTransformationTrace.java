package hu.blackbelt.judo.tatami.psm2measure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
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


@Builder(builderMethodName = "psm2MeasureTransformationTraceBuilder")
public class Psm2MeasureTransformationTrace implements TransformationTrace {

    public static final String PSM_2_MEASURE_URI_POSTFIX = "psm2measure";
    public static final String PSM_2_MEASURE_TRACE_URI_PREFIX = "psm2measureTrace:";

    @NonNull
    @Getter
    PsmModel psmModel;

    @NonNull
    @Getter
    MeasureModel measureModel;

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
        return MeasureModel.class;
    }

    @Override
    public Object getTargetModel() {
        return measureModel;
    }

    @Override
    public ResourceSet getTargetResourceSet() {
        return measureModel.getResourceSet();
    }

    @Override
    public URI getTargetURI() {
        return measureModel.getUri();
    }

    @Override
    public Class<? extends TransformationTrace> getType() {
        return Psm2MeasureTransformationTrace.class;
    }

    @Override
    public String getTransformationTraceName() {
        return "psm2measure";
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
     * Create PSM 2 MEASURE Trace model {@link Resource} wth isolated {@link ResourceSet}
     *
     * @param uri
     * @param uriHandler
     *
     * @return the trace {@link Resource} with the registered namespace.
     */
    public static Resource createPsm2MeasureTraceResource(org.eclipse.emf.common.util.URI uri,
                                                      URIHandler uriHandler) {
        return createTraceModelResource(PSM_2_MEASURE_URI_POSTFIX, uri, uriHandler);
    }

    /**
     * Resolves PSM 2 MEASURE Trace model {@link Resource} and resturns the trace {@link EObject } map
     *
     * @param traceResource
     * @param psmModel
     * @param measureModel
     *
     * @return the trace {@link EObject} map between PSM source and MEASURE target.
     */
    public static Map<EObject, List<EObject>> resolvePsm2MeasureTrace(Resource traceResource,
                                                                  PsmModel psmModel,
                                                                  MeasureModel measureModel) {
        return resolvePsm2MeasureTrace(traceResource.getContents(), psmModel, measureModel);
    }

    /**
     * Resolves PSM 2 MEASURE trace:Trace model entries returns the trace {@link EObject } map
     *
     * @param trace
     * @param psmModel
     * @param measureModel
     *
     * @return the trace {@link EObject} map between PSM source and MEASURE target.
     */
    public static Map<EObject, List<EObject>> resolvePsm2MeasureTrace(List<EObject> trace,
                                                                  PsmModel psmModel,
                                                                  MeasureModel measureModel) {
        return resolveTransformationTraceAsEObjectMap(trace,
                ImmutableList.of(psmModel.getResourceSet(), measureModel.getResourceSet()));
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model entrie.
     *
     * @param trace
     *
     * @return the trace trace:Trace entries
     */
    public static List<EObject> getPsm2MeasureTrace(Map<EObject, List<EObject>> trace) {
        return getTransformationTraceFromEtlExecutionContext(PSM_2_MEASURE_URI_POSTFIX, trace);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     * @param uriHandler
     * @return the trace trace:Trace entries
     */
    public static Resource getPsm2MeasureTraceResource(Map<EObject, List<EObject>> trace,
                                                   org.eclipse.emf.common.util.URI modelUri,
                                                   URIHandler uriHandler) {
        return createTraceModelResourceFromEObjectMap(trace, PSM_2_MEASURE_URI_POSTFIX, modelUri, uriHandler);
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model {@link Resource} with isolated {@link ResourceSet}.
     *
     * @param trace
     * @param modelUri
     *
     * @return the trace trace:Trace entries
     */
    public static Resource getPsm2MeasureTraceResource(Map<EObject, List<EObject>> trace,
                                                   org.eclipse.emf.common.util.URI modelUri) {
        return createTraceModelResourceFromEObjectMap(trace, PSM_2_MEASURE_URI_POSTFIX, modelUri, null);
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param psmModel
     * @param measureModel
     * @param traceModelFile
     * @return
     * @throws IOException
     */
    public static Psm2MeasureTransformationTrace fromModelsAndTrace(String modelName,
                                                                PsmModel psmModel,
                                                                MeasureModel measureModel,
                                                                File traceModelFile) throws IOException {
        return fromModelsAndTrace(modelName, psmModel, measureModel, new FileInputStream(traceModelFile));
    }


    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param psmModel
     * @param measureModel
     * @param traceModelInputStream
     * @return
     * @throws IOException
     */
    public static Psm2MeasureTransformationTrace fromModelsAndTrace(String modelName,
                                                                    PsmModel psmModel,
                                                                    MeasureModel measureModel,
                                                                    InputStream traceModelInputStream) throws IOException {

        checkArgument(psmModel.getName().equals(measureModel.getName()), "Model name does not match");

        Resource traceResoureLoaded = createPsm2MeasureTraceResource(
                URI.createURI(PSM_2_MEASURE_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(traceModelInputStream, ImmutableMap.of());

        return Psm2MeasureTransformationTrace.psm2MeasureTransformationTraceBuilder()
                .measureModel(measureModel)
                .psmModel(psmModel)
                .trace(resolvePsm2MeasureTrace(traceResoureLoaded, psmModel, measureModel)).build();

    }

    /**
     * Save trace to the given stream.
     *
     * @param outputStream
     * @return
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
        Resource  traceResoureSaved = getPsm2MeasureTraceResource(
                trace,
                URI.createURI(PSM_2_MEASURE_TRACE_URI_PREFIX + getModelName()));

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
