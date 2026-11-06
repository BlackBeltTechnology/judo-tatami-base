package hu.blackbelt.judo.tatami.asm2query;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.query.QueryModelInfo;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import hu.blackbelt.judo.meta.query.support.QueryModelResourceSupport;
import hu.blackbelt.judo.meta.query.util.QueryResourceFactoryImpl;
import hu.blackbelt.judo.meta.query.util.QueryResourceImpl;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryRegistryImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLHelper;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.URIHandlerImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLHelperImpl;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Save and load of the persisted query model artifact {@code <name>-query.model}.
 *
 * <p><b>Save.</b> The query graph and its {@link QueryModelInfo} root are written with EMF hrefs into the ASM and
 * expression resources. Only the URIs of those two resources are rewritten, to {@code <name>-asm.model} and
 * {@code <name>-expression.model}, so every reference is relative to the sibling files; references into Ecore (package
 * {@code nsURI}) are untouched. A reference guard runs first: every non-containment target must be an ASM object whose
 * {@code xmi:id} occurs exactly once (counted over the whole ASM resource), an expression object with a non-null id
 * that occurs once, an Ecore object or an object of the query resource. Otherwise nothing is written, a stale file is
 * deleted and the reason is logged: the build never fails because of the artifact. The root is stamped with the owner-only
 * generator identity and the digests of the ASM, measure and expression files.</p>
 *
 * <p><b>Load.</b> The artifact stream is read under the logical URI {@code <name>-query.model} into a dedicated
 * {@link ResourceSet} whose {@code uriResourceMap} maps {@code <name>-asm.model} and {@code <name>-expression.model} onto the
 * live resources, so resolved references are the identical live objects. The set is resolved once with
 * {@link EcoreUtil#resolveAll(ResourceSet)} and the unresolved proxies are counted. {@code EJavaObject} values are decoded
 * through {@link ConstantDecoder}'s allow-list.</p>
 */
@Slf4j
public final class QueryModelPersistence {

    /**
     * Digest of an absent measure model.
     */
    public static final String NO_DIGEST = "none";

    /**
     * Logical file name suffix of the persisted query model.
     */
    public static final String QUERY_SUFFIX = "-query.model";
    public static final String ASM_SUFFIX = "-asm.model";
    public static final String EXPRESSION_SUFFIX = "-expression.model";

    static final String GENERATOR_RESOURCE = "asm2query-generator.properties";
    static final String GENERATOR_PREFIX = "judo-tatami-asm2query:";

    private static final int MAX_PROBLEMS = 20;
    private static final String SHA_256 = "SHA-256";

    private QueryModelPersistence() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Arguments of {@link #save(SaveArguments)}. Exactly one of {@code file} and {@code outputStream} must be set.
     */
    @Builder(builderMethodName = "saveArguments")
    @Getter
    @ToString(exclude = {"queryModel", "asmModel", "expressionModel"})
    public static final class SaveArguments {
        /**
         * The built query model (resource {@code <name>-query.model}). Its {@link QueryModelInfo} root gets the stamps.
         */
        @NonNull
        private final QueryModel queryModel;

        /**
         * The live ASM model the query model was built from.
         */
        @NonNull
        private final AsmModel asmModel;

        /**
         * The live expression model the query model was built from (extracted in the same run).
         */
        @NonNull
        private final ExpressionModel expressionModel;

        /**
         * SHA-256 hex of the complete bytes of the ASM file saved in the same run.
         */
        @NonNull
        private final String asmDigest;

        /**
         * SHA-256 hex of the measure file, {@code null} when there is no measure model (stamped as {@code none}).
         */
        private final String measureDigest;

        /**
         * SHA-256 hex of the complete bytes of the expression file saved in the same run.
         */
        @NonNull
        private final String expressionDigest;

        /**
         * Target file. It is written through a temporary sibling and moved over the target, deleted when the guard
         * refuses the model.
         */
        private final File file;

        /**
         * Target stream (not closed by the save). The caller deletes a stale artifact when the result is not written.
         */
        private final OutputStream outputStream;
    }

    /**
     * Outcome of {@link #save(SaveArguments)}.
     */
    @Getter
    @ToString
    public static final class SaveResult {
        private final boolean written;
        private final List<String> problems;
        private final String generator;

        private SaveResult(final boolean written, final List<String> problems, final String generator) {
            this.written = written;
            this.problems = Collections.unmodifiableList(problems);
            this.generator = generator;
        }
    }

    /**
     * Save the query model artifact, unless the reference guard refuses it.
     *
     * @param arguments arguments
     * @return outcome; {@code isWritten() == false} means nothing was written and a stale file was deleted
     * @throws IOException if writing fails (a partial target file is removed)
     */
    public static SaveResult save(@NonNull final SaveArguments arguments) throws IOException {
        if ((arguments.file == null) == (arguments.outputStream == null)) {
            throw new IllegalArgumentException("Exactly one of file and outputStream must be set");
        }
        final QueryModel queryModel = arguments.queryModel;
        final String name = queryModel.getName();
        final Resource queryResource = queryModel.getResource();
        final Resource asmResource = arguments.asmModel.getResource();
        final Resource expressionResource = arguments.expressionModel.getResource();

        final List<String> problems = guard(queryResource, asmResource, expressionResource);
        final String generator = generatorIdentity();
        if (generator == null) {
            problems.add("generator identity of judo-tatami-asm2query is not readable (" + GENERATOR_RESOURCE + ")");
        }
        final QueryModelInfo info = problems.isEmpty() ? QueryIndex.getQueryModelInfo(queryResource) : null;
        if (info != null && !name.equals(info.getModelName())) {
            problems.add("model name of the query model info is " + info.getModelName() + ", expected " + name);
        }
        if (!problems.isEmpty()) {
            log.warn("Query model {} is not persisted, no artifact is written: {}", name, problems);
            if (arguments.file != null) {
                Files.deleteIfExists(arguments.file.toPath());
            }
            return new SaveResult(false, problems, generator);
        }

        info.setGenerator(generator);
        info.setAsmDigest(arguments.asmDigest);
        info.setMeasureDigest(arguments.measureDigest != null ? arguments.measureDigest : NO_DIGEST);
        info.setExpressionDigest(arguments.expressionDigest);

        final Map<Object, Object> saveOptions = QueryModelResourceSupport.getQueryModelDefaultSaveOptions();
        saveOptions.put(XMLResource.OPTION_URI_HANDLER, new RewritingUriHandler(
                asmResource.getURI(), URI.createURI(name + ASM_SUFFIX),
                expressionResource.getURI(), URI.createURI(name + EXPRESSION_SUFFIX)));

        try {
            if (arguments.outputStream != null) {
                saveTo(queryModel, arguments.outputStream, saveOptions);
            } else {
                saveToFile(queryModel, arguments.file, saveOptions);
            }
        } catch (QueryModel.QueryValidationException e) {
            throw new IllegalStateException("Query model is not valid: " + e.getMessage(), e);
        }
        return new SaveResult(true, List.of(), generator);
    }

    private static void saveTo(final QueryModel queryModel, final OutputStream outputStream, final Map<Object, Object> saveOptions)
            throws IOException, QueryModel.QueryValidationException {
        queryModel.saveQueryModel(QueryModel.SaveArguments.querySaveArgumentsBuilder()
                .outputStream(outputStream)
                .saveOptions(saveOptions)
                .validateModel(false));
    }

    private static void saveToFile(final QueryModel queryModel, final File file, final Map<Object, Object> saveOptions)
            throws IOException, QueryModel.QueryValidationException {
        final Path target = file.toPath().toAbsolutePath();
        Files.createDirectories(target.getParent());
        // a plain sibling (not createTempFile: its owner-only permissions would survive the move)
        final Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                saveTo(queryModel, out, saveOptions);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Rewrites only the ASM and expression resource URIs. The target URI keeps the fragment.
     */
    private static final class RewritingUriHandler extends URIHandlerImpl {
        private final URI asmUri;
        private final URI asmTarget;
        private final URI expressionUri;
        private final URI expressionTarget;

        private RewritingUriHandler(final URI asmUri, final URI asmTarget, final URI expressionUri, final URI expressionTarget) {
            this.asmUri = asmUri;
            this.asmTarget = asmTarget;
            this.expressionUri = expressionUri;
            this.expressionTarget = expressionTarget;
        }

        @Override
        public URI deresolve(final URI uri) {
            final URI resource = uri.trimFragment();
            if (resource.equals(asmUri)) {
                return rewrite(asmTarget, uri);
            }
            if (resource.equals(expressionUri)) {
                return rewrite(expressionTarget, uri);
            }
            // same-resource references of an opaque (non hierarchical) URI are written as #fragment
            if (uri.hasFragment() && uri.hasOpaquePart() && baseURI != null && baseURI.hasOpaquePart()
                    && uri.opaquePart().equals(baseURI.opaquePart())) {
                return URI.createURI("#" + uri.fragment());
            }
            return super.deresolve(uri);
        }

        private static URI rewrite(final URI target, final URI uri) {
            return uri.hasFragment() ? target.appendFragment(uri.fragment()) : target;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Reference guard
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Check every non-containment target of the query resource. Package visible for tests.
     *
     * @return the problems found, an empty (mutable) list when every reference can be written and resolved
     */
    static List<String> guard(final Resource queryResource, final Resource asmResource, final Resource expressionResource) {
        final List<String> problems = new ArrayList<>();
        final Map<String, Integer> asmIds = countIds(asmResource);
        final Map<String, Integer> expressionIds = countIds(expressionResource);
        final Map<EClass, List<EReference>> referencesByClass = new HashMap<>();
        final Map<Resource, Boolean> reportedForeign = new HashMap<>();

        final var contents = queryResource.getAllContents();
        while (contents.hasNext() && problems.size() < MAX_PROBLEMS) {
            final EObject source = contents.next();
            for (EReference reference : referencesByClass.computeIfAbsent(source.eClass(), QueryModelPersistence::crossReferences)) {
                if (!source.eIsSet(reference)) {
                    continue;
                }
                final Object value = source.eGet(reference, false);
                if (value instanceof Collection) {
                    for (Object target : (Collection<?>) value) {
                        checkTarget(source, reference, (EObject) target, queryResource, asmResource, expressionResource,
                                asmIds, expressionIds, reportedForeign, problems);
                    }
                } else if (value != null) {
                    checkTarget(source, reference, (EObject) value, queryResource, asmResource, expressionResource,
                            asmIds, expressionIds, reportedForeign, problems);
                }
            }
        }
        return problems;
    }

    private static List<EReference> crossReferences(final EClass eClass) {
        final List<EReference> references = new ArrayList<>();
        for (EReference reference : eClass.getEAllReferences()) {
            if (!reference.isContainment() && !reference.isContainer() && !reference.isTransient()
                    && !reference.isDerived() && !reference.isVolatile()) {
                references.add(reference);
            }
        }
        return references;
    }

    private static void checkTarget(final EObject source, final EReference reference, final EObject target,
                                    final Resource queryResource, final Resource asmResource, final Resource expressionResource,
                                    final Map<String, Integer> asmIds, final Map<String, Integer> expressionIds,
                                    final Map<Resource, Boolean> reportedForeign, final List<String> problems) {
        if (problems.size() >= MAX_PROBLEMS) {
            return;
        }
        if (target.eIsProxy()) {
            problems.add("unresolved proxy " + ((org.eclipse.emf.ecore.InternalEObject) target).eProxyURI()
                    + " referenced by " + describe(source) + "." + reference.getName());
            return;
        }
        final Resource targetResource = target.eResource();
        if (targetResource == queryResource) {
            return;
        }
        if (targetResource == asmResource) {
            checkId("ASM", asmResource, target, source, reference, asmIds, problems);
        } else if (targetResource == expressionResource) {
            checkId("expression", expressionResource, target, source, reference, expressionIds, problems);
        } else if (isEcore(target)) {
            return;
        } else if (targetResource == null) {
            problems.add("reference to an object outside of any resource: " + describe(target)
                    + " referenced by " + describe(source) + "." + reference.getName());
        } else if (reportedForeign.put(targetResource, Boolean.TRUE) == null) {
            problems.add("reference into the foreign resource " + targetResource.getURI() + ": " + describe(target)
                    + " referenced by " + describe(source) + "." + reference.getName());
        }
    }

    private static void checkId(final String kind, final Resource resource, final EObject target, final EObject source,
                                final EReference reference, final Map<String, Integer> counts, final List<String> problems) {
        final String id = idOf(resource, target);
        if (id == null) {
            problems.add(kind + " object without xmi:id: " + describe(target) + " referenced by "
                    + describe(source) + "." + reference.getName());
        } else if (counts.getOrDefault(id, 0) != 1) {
            problems.add(kind + " object with duplicated xmi:id " + id + " (" + counts.getOrDefault(id, 0) + " times): "
                    + describe(target) + " referenced by " + describe(source) + "." + reference.getName());
        }
    }

    /**
     * Counts the ids over the whole resource contents. The in-memory id map is not trusted: its winner may differ from the
     * object a reload picks.
     */
    private static Map<String, Integer> countIds(final Resource resource) {
        final Map<String, Integer> counts = new HashMap<>();
        final var contents = resource.getAllContents();
        while (contents.hasNext()) {
            final String id = idOf(resource, contents.next());
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String idOf(final Resource resource, final EObject object) {
        return resource instanceof XMLResource ? ((XMLResource) resource).getID(object) : null;
    }

    private static boolean isEcore(final EObject target) {
        final EObject root = EcoreUtil.getRootContainer(target);
        if (!(root instanceof EPackage)) {
            return false;
        }
        final EPackage ePackage = (EPackage) root;
        return ePackage == EcorePackage.eINSTANCE
                || ePackage.getNsURI() != null && EPackage.Registry.INSTANCE.getEPackage(ePackage.getNsURI()) == ePackage;
    }

    private static String describe(final EObject object) {
        final StringBuilder description = new StringBuilder(object.eClass().getName());
        if (object instanceof ENamedElement) {
            description.append(' ').append(((ENamedElement) object).getName());
        }
        return description.toString();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Load
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Outcome of {@link #load(String, InputStream, Resource, Resource)}.
     */
    @Getter
    @ToString(exclude = {"queryModel", "info"})
    public static final class LoadResult {
        private final QueryModel queryModel;
        private final QueryModelInfo info;
        private final int unresolvedProxyCount;
        private final List<String> unresolvedProxySamples;

        private LoadResult(final QueryModel queryModel, final QueryModelInfo info, final int unresolvedProxyCount,
                           final List<String> unresolvedProxySamples) {
            this.queryModel = queryModel;
            this.info = info;
            this.unresolvedProxyCount = unresolvedProxyCount;
            this.unresolvedProxySamples = Collections.unmodifiableList(unresolvedProxySamples);
        }
    }

    /**
     * Load the artifact into a dedicated resource set, resolve it against the live models.
     *
     * <p>The caller decides on the result: stamps ({@link #checkStamps}) and {@code unresolvedProxyCount > 0} make it
     * unusable. The stream is read to the end of the XML document and not closed.</p>
     *
     * @param name               logical model name; the stream is loaded under {@code <name>-query.model}
     * @param stream             artifact stream (any physical origin)
     * @param asmResource        live ASM resource, mapped as {@code <name>-asm.model}
     * @param expressionResource live expression resource, mapped as {@code <name>-expression.model}
     * @return the loaded query model, its single {@link QueryModelInfo} root and the unresolved proxy count
     * @throws IOException           when the stream is unreadable or not well-formed, or a constant is rejected by the filter
     * @throws IllegalStateException when the resource has none or several {@link QueryModelInfo} roots
     */
    public static LoadResult load(@NonNull final String name, @NonNull final InputStream stream,
                                  @NonNull final Resource asmResource, @NonNull final Resource expressionResource)
            throws IOException {
        final ConstantDecoder decoder = new ConstantDecoder();
        final LiveResourceSet resourceSet = new LiveResourceSet();
        QueryModelResourceSupport.registerQueryMetamodel(resourceSet);
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(ResourceFactoryRegistryImpl.DEFAULT_EXTENSION, new DecodingQueryFactory(decoder));
        resourceSet.getLive().put(URI.createURI(name + ASM_SUFFIX), asmResource);
        resourceSet.getLive().put(URI.createURI(name + EXPRESSION_SUFFIX), expressionResource);

        final QueryModel queryModel;
        try {
            queryModel = QueryModel.loadQueryModel(QueryModel.LoadArguments.queryLoadArgumentsBuilder()
                    .uri(URI.createURI(name + QUERY_SUFFIX))
                    .name(name)
                    .resourceSet(resourceSet)
                    .inputStream(stream)
                    .validateModel(false));
        } catch (QueryModel.QueryValidationException e) {
            throw new IOException("Query model is not valid: " + e.getMessage(), e);
        } catch (IOException | RuntimeException e) {
            throw loadFailure(decoder, e);
        }

        final Resource resource = queryModel.getResource();
        if (decoder.getFailure() != null) {
            throw loadFailure(decoder, decoder.getFailure());
        }
        if (!resource.getErrors().isEmpty()) {
            throw new IOException("Query model has load errors: " + resource.getErrors());
        }
        final QueryModelInfo info = QueryIndex.getQueryModelInfo(resource);

        // single-threaded, before publication: no lazy proxy resolution later
        EcoreUtil.resolveAll(resourceSet);
        final Map<EObject, Collection<org.eclipse.emf.ecore.EStructuralFeature.Setting>> unresolved =
                EcoreUtil.UnresolvedProxyCrossReferencer.find(resourceSet);
        final List<String> samples = new ArrayList<>();
        for (EObject proxy : unresolved.keySet()) {
            if (samples.size() >= 10) {
                break;
            }
            samples.add(String.valueOf(((org.eclipse.emf.ecore.InternalEObject) proxy).eProxyURI()));
        }
        return new LoadResult(queryModel, info, unresolved.size(), samples);
    }

    /**
     * A constant rejected by the filter is reported as such, whatever EMF wraps it into ("Value ... is not legal").
     */
    private static IOException loadFailure(final ConstantDecoder decoder, final Exception cause) {
        final Exception rejected = decoder.getFailure();
        if (rejected != null) {
            return new IOException("Query model cannot be loaded, constant value rejected: " + rejected.getMessage(), rejected);
        }
        if (cause instanceof IOException) {
            return (IOException) cause;
        }
        if (cause != null && cause.getCause() instanceof IOException) {
            return (IOException) cause.getCause();
        }
        return new IOException("Query model cannot be loaded: " + cause, cause);
    }

    /**
     * Resource set where the live ASM and expression resources are served from the {@code uriResourceMap} without
     * demand loading: a resource built in memory is not "loaded" and would be re-read from its URI otherwise.
     */
    private static final class LiveResourceSet extends ResourceSetImpl {
        private final Map<URI, Resource> live = new HashMap<>();

        private LiveResourceSet() {
            setURIResourceMap(live);
        }

        private Map<URI, Resource> getLive() {
            return live;
        }

        @Override
        public Resource getResource(final URI uri, final boolean loadOnDemand) {
            final Resource resource = live.get(uri);
            return resource != null ? resource : super.getResource(uri, loadOnDemand);
        }
    }

    /**
     * The query resource factory of {@link QueryModelResourceSupport} with an XML helper that decodes
     * {@code EJavaObject} values through the allow-list.
     */
    private static final class DecodingQueryFactory extends QueryResourceFactoryImpl {
        private final ConstantDecoder decoder;

        private DecodingQueryFactory(final ConstantDecoder decoder) {
            this.decoder = decoder;
        }

        @Override
        public Resource createResource(final URI uri) {
            return new QueryResourceImpl(uri) {
                @Override
                protected boolean useUUIDs() {
                    return true;
                }

                @Override
                protected XMLHelper createXMLHelper() {
                    return new XMLHelperImpl(this) {
                        @Override
                        protected Object createFromString(final EFactory factory, final EDataType dataType, final String value) {
                            if (dataType == EcorePackage.Literals.EJAVA_OBJECT && value != null) {
                                return decoder.decode(value);
                            }
                            return super.createFromString(factory, dataType, value);
                        }
                    };
                }
            };
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Stamps
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Compare the stamps of a loaded artifact with the live inputs.
     *
     * <p>The stamp fields are optional in the metamodel: an unset stamp is a mismatch, never a match.</p>
     *
     * @param info             stamped root of the artifact
     * @param modelName        name of the live ASM model
     * @param asmDigest        SHA-256 hex of the live ASM file
     * @param measureDigest    SHA-256 hex of the live measure file, {@code null} (or {@code none}) when there is none
     * @param expressionDigest SHA-256 hex of the live expression file
     * @return the mismatches, empty when the artifact matches the inputs and this generator
     */
    public static List<String> checkStamps(@NonNull final QueryModelInfo info, final String modelName, final String asmDigest,
                                           final String measureDigest, final String expressionDigest) {
        final List<String> mismatches = new ArrayList<>();
        if (info.getFormatVersion() != Asm2Query.FORMAT_VERSION) {
            mismatches.add("formatVersion: artifact " + info.getFormatVersion() + ", expected " + Asm2Query.FORMAT_VERSION);
        }
        final String generator = generatorIdentity();
        if (generator == null) {
            mismatches.add("generator: the identity of this judo-tatami-asm2query is not readable, artifact "
                    + info.getGenerator());
        } else if (!generator.equals(info.getGenerator())) {
            mismatches.add("generator: artifact " + info.getGenerator() + ", expected " + generator);
        }
        compare(mismatches, "modelName", info.getModelName(), modelName);
        compare(mismatches, "asmDigest", info.getAsmDigest(), asmDigest);
        compare(mismatches, "measureDigest", info.getMeasureDigest(), measureDigest != null ? measureDigest : NO_DIGEST);
        compare(mismatches, "expressionDigest", info.getExpressionDigest(), expressionDigest);
        return mismatches;
    }

    private static void compare(final List<String> mismatches, final String field, final String artifact, final String expected) {
        if (artifact == null || expected == null || !artifact.equals(expected)) {
            mismatches.add(field + ": artifact " + artifact + ", expected " + expected);
        }
    }

    /**
     * Identity of the generator: the version of this module, plus its build timestamp when the version is a SNAPSHOT.
     * It is read only from the build-filtered {@code asm2query-generator.properties} of this bundle or jar.
     *
     * @return the identity, {@code null} when the resource is missing or unreadable
     */
    public static String generatorIdentity() {
        return generatorIdentity(() -> QueryModelPersistence.class.getResourceAsStream(GENERATOR_RESOURCE));
    }

    static String generatorIdentity(final Supplier<InputStream> resource) {
        try (InputStream in = resource.get()) {
            if (in == null) {
                return null;
            }
            final Properties properties = new Properties();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            final String version = clean(properties.getProperty("version"));
            if (version == null) {
                return null;
            }
            if (version.endsWith("-SNAPSHOT")) {
                final String timestamp = clean(properties.getProperty("buildTimestamp"));
                return timestamp != null ? GENERATOR_PREFIX + version + ":" + timestamp : null;
            }
            return GENERATOR_PREFIX + version;
        } catch (IOException | RuntimeException e) {
            log.debug("Generator identity is not readable", e);
            return null;
        }
    }

    private static String clean(final String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return null;
        }
        return value.trim();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Digests
    // ------------------------------------------------------------------------------------------------------------

    /**
     * An input stream that digests every byte read and, on {@link #digest()}, drains the rest of the stream first, so a
     * parser that stops reading before EOF (read-ahead, trailing whitespace) cannot make the digest cover less than the
     * complete file.
     */
    public static final class DigestInputStream extends FilterInputStream {
        private final MessageDigest messageDigest = newDigest();
        private String digest;

        public DigestInputStream(@NonNull final InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            final int b = super.read();
            if (b >= 0) {
                messageDigest.update((byte) b);
            }
            return b;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int n = super.read(b, off, len);
            if (n > 0) {
                messageDigest.update(b, off, n);
            }
            return n;
        }

        @Override
        public long skip(final long n) throws IOException {
            // skipped bytes still belong to the digest
            final byte[] buffer = new byte[(int) Math.min(8192, Math.max(n, 0))];
            long skipped = 0;
            while (skipped < n) {
                final int read = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
                if (read < 0) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(final int readlimit) {
            // no mark: a replay would digest bytes twice
        }

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("mark/reset not supported");
        }

        /**
         * Drain the stream to EOF and return the SHA-256 hex of all its bytes (idempotent).
         *
         * @return SHA-256 hex
         * @throws IOException when the stream cannot be read to the end
         */
        public String digest() throws IOException {
            if (digest == null) {
                final byte[] buffer = new byte[8192];
                while (read(buffer, 0, buffer.length) >= 0) {
                    // drained into the digest by read
                }
                digest = toHex(messageDigest.digest());
            }
            return digest;
        }
    }

    /**
     * Digest a complete stream.
     *
     * @param in stream, drained to EOF and not closed
     * @return SHA-256 hex of all its bytes
     * @throws IOException when the stream cannot be read
     */
    public static String sha256Hex(@NonNull final InputStream in) throws IOException {
        return new DigestInputStream(in).digest();
    }

    /**
     * @param bytes content
     * @return SHA-256 hex of the bytes
     */
    public static String sha256Hex(@NonNull final byte[] bytes) {
        return toHex(newDigest().digest(bytes));
    }

    /**
     * @return a new SHA-256 digest, for savers that digest while writing ({@link java.security.DigestOutputStream})
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(SHA_256 + " is not available", e);
        }
    }

    /**
     * @param bytes digest bytes
     * @return lower case hex
     */
    public static String toHex(final byte[] bytes) {
        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
