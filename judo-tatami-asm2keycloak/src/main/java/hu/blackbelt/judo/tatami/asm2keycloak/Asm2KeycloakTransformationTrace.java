package hu.blackbelt.judo.tatami.asm2keycloak;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
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

@Builder(builderMethodName = "asm2KeycloakTransformationTraceBuilder")
public class Asm2KeycloakTransformationTrace implements TransformationTrace {

    public static final String ASM_2_RDBMS_URI_POSTFIX = "asm2keycloak";
    public static final String ASM_2_RDBMS_TRACE_URI_PREFIX = "asm2keycloakTrace:";

    @NonNull
    @Getter
    AsmModel asmModel;

    @NonNull
    @Getter
    KeycloakModel keycloakModel;

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
        return KeycloakModel.class;
    }

    @Override
    public Object getTargetModel() {
        return keycloakModel;
    }

    @Override
    public ResourceSet getTargetResourceSet() {
        return keycloakModel.getResourceSet();
    }

    @Override
    public URI getTargetURI() {
        return keycloakModel.getUri();
    }

    @Override
    public Class<? extends TransformationTrace> getType() {
        return Asm2KeycloakTransformationTrace.class;
    }

    @Override
    public String getTransformationTraceName() {
        return "asm2keycloak";
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
    public static Resource createAsm2KeycloakTraceResource(org.eclipse.emf.common.util.URI uri,
                                                          URIHandler uriHandler) {
        return createTraceModelResource(ASM_2_RDBMS_URI_POSTFIX, uri, uriHandler);
    }

    /**
     * Resolves PSM 2 ASM Trace model {@link Resource} and resturns the trace {@link EObject } map
     *
     * @param traceResource
     * @param asmModel
     * @param keycloakModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolveAsm2KeycloakTrace(Resource traceResource,
                                                                      AsmModel asmModel,
                                                                      KeycloakModel keycloakModel) {
        return resolveAsm2KeycloakTrace(traceResource.getContents(), asmModel, keycloakModel);
    }

    /**
     * Resolves PSM 2 ASM trace:Trace model entries returns the trace {@link EObject } map
     *
     * @param trace
     * @param asmModel
     * @param keycloakModel
     *
     * @return the trace {@link EObject} map between PSM source and ASM target.
     */
    public static Map<EObject, List<EObject>> resolveAsm2KeycloakTrace(List<EObject> trace,
                                                                      AsmModel asmModel,
                                                                      KeycloakModel keycloakModel) {
        return resolveTransformationTraceAsEObjectMap(trace,
                ImmutableList.of(asmModel.getResourceSet(), keycloakModel.getResourceSet()));
    }

    /**
     * Convert race {@link EObject } map to trace:Trace model entrie.
     *
     * @param trace
     *
     * @return the trace trace:Trace entries
     */
    public static List<EObject> getAsm2KeycloakTrace(Map<EObject, List<EObject>> trace) {
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
    public static Resource getAsm2KeycloakTraceResource(Map<EObject, List<EObject>> trace,
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
    public static Resource getAsm2KeycloakTraceResource(Map<EObject, List<EObject>> trace,
                                                       org.eclipse.emf.common.util.URI modelUri) {
        return createTraceModelResourceFromEObjectMap(trace, ASM_2_RDBMS_URI_POSTFIX, modelUri, null);
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param asmModel
     * @param keycloakModel
     * @param traceModelFile
     * @return
     * @throws IOException
     */
    public static Asm2KeycloakTransformationTrace fromModelsAndTrace(String modelName,
                                                                    AsmModel asmModel,
                                                                    KeycloakModel keycloakModel,
                                                                    File traceModelFile) throws IOException {
        return fromModelsAndTrace(modelName, asmModel, keycloakModel, new FileInputStream(traceModelFile));
    }

    /**
     * Create transformation trace from models and trace inputstream.
     * @param modelName
     * @param asmModel
     * @param keycloakModel
     * @param traceModelInputStream
     * @return
     * @throws IOException
     */
    public static Asm2KeycloakTransformationTrace fromModelsAndTrace(String modelName,
                                                                  AsmModel asmModel,
                                                                  KeycloakModel keycloakModel,
                                                                  InputStream traceModelInputStream) throws IOException {

        checkArgument(asmModel.getName().equals(keycloakModel.getName()), "Model name does not match");

        Resource traceResoureLoaded = createAsm2KeycloakTraceResource(
                URI.createURI(ASM_2_RDBMS_TRACE_URI_PREFIX + modelName),
                null);

        traceResoureLoaded.load(traceModelInputStream, ImmutableMap.of());

        return Asm2KeycloakTransformationTrace.asm2KeycloakTransformationTraceBuilder()
                .keycloakModel(keycloakModel)
                .asmModel(asmModel)
                .trace(resolveAsm2KeycloakTrace(traceResoureLoaded, asmModel, keycloakModel)).build();

    }

    /**
     * Save trace to the given stream.
     *
     * @param outputStream
     * @return
     * @throws IOException
     */
    public Resource save(OutputStream outputStream) throws IOException {
        Resource  traceResoureSaved = getAsm2KeycloakTraceResource(
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
