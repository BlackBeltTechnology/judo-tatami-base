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
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.expression.DataExpression;
import hu.blackbelt.judo.meta.expression.ReferenceExpression;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.query.Constant;
import hu.blackbelt.judo.meta.query.DataQueryEntry;
import hu.blackbelt.judo.meta.query.EntityTypeExpressionsEntry;
import hu.blackbelt.judo.meta.query.QueryModelInfo;
import hu.blackbelt.judo.meta.query.QueryEntry;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import hu.blackbelt.judo.tatami.asm2query.QueryModelPersistence.LoadResult;
import hu.blackbelt.judo.tatami.asm2query.QueryModelPersistence.SaveResult;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.query.util.builder.QueryBuilders.newConstantBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Save (hrefs, reference guard, stamps) and load (live resolution, constant allow-list, digests) of the persisted query
 * model, over the Northwind models.
 */
@Slf4j
public class QueryModelPersistenceTest {

    /**
     * A built query model with its saved artifact and the file digests of the models it was built from.
     */
    static final class Saved {
        final NorthwindFixture fixture;
        final Asm2QueryResult built;
        final String name;
        final String asmDigest;
        final String measureDigest;
        final String expressionDigest;
        byte[] artifact;

        Saved(final NorthwindFixture fixture) throws Exception {
            this(fixture, Asm2Query.executeAsm2Query(fixture.asmModel, fixture.measureModel, fixture.expressionModel));
        }

        Saved(final NorthwindFixture fixture, final Asm2QueryResult built) throws Exception {
            this.fixture = fixture;
            this.built = built;
            this.name = built.getQueryModel().getName();
            final ByteArrayOutputStream asm = new ByteArrayOutputStream();
            fixture.asmModel.saveAsmModel(AsmModel.SaveArguments.asmSaveArgumentsBuilder().outputStream(asm));
            this.asmDigest = QueryModelPersistence.sha256Hex(asm.toByteArray());
            final ByteArrayOutputStream expression = new ByteArrayOutputStream();
            fixture.expressionModel.saveExpressionModel(ExpressionModel.SaveArguments.expressionSaveArgumentsBuilder().outputStream(expression));
            this.expressionDigest = QueryModelPersistence.sha256Hex(expression.toByteArray());
            this.measureDigest = null;
        }

        QueryModelPersistence.SaveArguments.SaveArgumentsBuilder arguments() {
            return QueryModelPersistence.SaveArguments.saveArguments()
                    .queryModel(built.getQueryModel())
                    .asmModel(fixture.asmModel)
                    .expressionModel(fixture.expressionModel)
                    .asmDigest(asmDigest)
                    .measureDigest(measureDigest)
                    .expressionDigest(expressionDigest);
        }

        SaveResult save() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final SaveResult result = QueryModelPersistence.save(arguments().outputStream(out).build());
            artifact = result.isWritten() ? out.toByteArray() : null;
            return result;
        }

        LoadResult load() throws IOException {
            return load(artifact);
        }

        LoadResult load(final byte[] bytes) throws IOException {
            return QueryModelPersistence.load(name, new ByteArrayInputStream(bytes),
                    fixture.asmModel.getResource(), fixture.expressionModel.getResource());
        }
    }

    static NorthwindFixture shared;
    static Saved saved;

    @BeforeAll
    static void saveShared() throws Exception {
        shared = NorthwindFixture.shared();
        saved = new Saved(shared);
        assertThat(saved.save().isWritten(), equalTo(true));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Save: hrefs, stamps
    // ------------------------------------------------------------------------------------------------------------

    /**
     * TP-13: hrefs use the runtime ASM and expression file names.
     */
    @Test
    void hrefsUseRuntimeAsmAndExpressionFileNames() {
        final String xml = new String(saved.artifact, StandardCharsets.UTF_8);
        final Set<String> prefixes = new TreeSet<>();
        final Matcher attributes = Pattern.compile("=\"([^\"]*)\"").matcher(xml);
        while (attributes.find()) {
            for (String token : attributes.group(1).split("\\s+")) {
                final int hash = token.indexOf('#');
                if (hash >= 0) {
                    prefixes.add(token.substring(0, hash + 1));
                }
            }
        }
        final String asmPrefix = saved.name + "-asm.model#";
        final String expressionPrefix = saved.name + "-expression.model#";
        assertThat(prefixes, hasItem(asmPrefix));
        assertThat(prefixes, hasItem(expressionPrefix));
        for (String prefix : prefixes) {
            // Ecore nsURIs are untouched; nothing else may occur
            assertThat("href prefix " + prefix, prefix.equals(asmPrefix) || prefix.equals(expressionPrefix)
                    || prefix.startsWith(EcorePackage.eNS_URI), equalTo(true));
        }
        // the live in-memory URIs never leak (every prefix was checked above)
        assertThat(xml, not(containsString("query:in-memory")));
    }

    @Test
    void saveStampsTheRoot() throws Exception {
        final LoadResult loaded = saved.load();
        final QueryModelInfo info = loaded.getInfo();
        assertThat(info.getFormatVersion(), equalTo(Asm2Query.FORMAT_VERSION));
        assertThat(info.getModelName(), equalTo(saved.name));
        assertThat(info.getGenerator(), equalTo(QueryModelPersistence.generatorIdentity()));
        assertThat(info.getGenerator(), startsWith("judo-tatami-asm2query:"));
        assertThat(info.getAsmDigest(), equalTo(saved.asmDigest));
        assertThat(info.getExpressionDigest(), equalTo(saved.expressionDigest));
        assertThat("absent measure model", info.getMeasureDigest(), equalTo("none"));
        assertThat(QueryModelPersistence.checkStamps(info, saved.name, saved.asmDigest, null, saved.expressionDigest), empty());
    }

    @Test
    void anUnsetOrDifferentStampIsAMismatchNeverAMatch() throws Exception {
        final QueryModelInfo info = saved.load().getInfo();
        final List<String> ok = QueryModelPersistence.checkStamps(info, saved.name, saved.asmDigest, null, saved.expressionDigest);
        assertThat(ok, empty());

        assertThat(QueryModelPersistence.checkStamps(info, saved.name, "other", null, saved.expressionDigest), hasSize(1));
        assertThat(QueryModelPersistence.checkStamps(info, saved.name, saved.asmDigest, null, "other"), hasSize(1));
        assertThat(QueryModelPersistence.checkStamps(info, saved.name, saved.asmDigest, "measure", saved.expressionDigest), hasSize(1));
        assertThat(QueryModelPersistence.checkStamps(info, "other", saved.asmDigest, null, saved.expressionDigest), hasSize(1));

        // unset stamps (optional in the metamodel): mismatch, even against an unset expectation
        info.setGenerator(null);
        info.setAsmDigest(null);
        info.setMeasureDigest(null);
        info.setExpressionDigest(null);
        info.setModelName(null);
        info.setFormatVersion(0);
        assertThat(QueryModelPersistence.checkStamps(info, saved.name, saved.asmDigest, null, saved.expressionDigest), hasSize(6));
        assertThat(QueryModelPersistence.checkStamps(info, null, null, null, null), hasSize(6));
    }

    @Test
    void generatorIdentityIsReadFromItsOwnResourceOnly() throws IOException {
        assertThat(QueryModelPersistence.generatorIdentity(), notNullValue());
        assertThat("release: version only", QueryModelPersistence.generatorIdentity(() -> properties("version=1.2.3\nbuildTimestamp=2026")),
                equalTo("judo-tatami-asm2query:1.2.3"));
        assertThat("SNAPSHOT: version and build timestamp", QueryModelPersistence.generatorIdentity(() -> properties("version=1.2.3-SNAPSHOT\nbuildTimestamp=2026")),
                equalTo("judo-tatami-asm2query:1.2.3-SNAPSHOT:2026"));
        assertThat("SNAPSHOT without timestamp", QueryModelPersistence.generatorIdentity(() -> properties("version=1.2.3-SNAPSHOT")), nullValue());
        assertThat("unfiltered placeholder", QueryModelPersistence.generatorIdentity(() -> properties("version=${project.version}")), nullValue());
        assertThat("missing version", QueryModelPersistence.generatorIdentity(() -> properties("buildTimestamp=2026")), nullValue());
        assertThat("missing resource", QueryModelPersistence.generatorIdentity(() -> null), nullValue());
        assertThat("unreadable resource", QueryModelPersistence.generatorIdentity(() -> new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable");
            }
        }), nullValue());
    }

    private static InputStream properties(final String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Save: reference guard
    // ------------------------------------------------------------------------------------------------------------

    /**
     * TP-14: a duplicate xmi:id on an unreferenced annotation does not stop the artifact, one on a referenced EAttribute does.
     */
    @Test
    void duplicateIdOnAnUnreferencedAnnotationStillWritesTheArtifact() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        final XMLResource asm = (XMLResource) s.fixture.asmModel.getResource();
        final List<EAnnotation> annotations = s.fixture.asmUtils.all(EAnnotation.class).limit(2).collect(Collectors.toList());
        assertThat(annotations, hasSize(2));
        final String duplicated = asm.getID(annotations.get(0));
        asm.setID(annotations.get(1), duplicated);

        try (LogCapture log = new LogCapture(QueryModelPersistence.class)) {
            final SaveResult result = s.save();
            assertThat(result.getProblems(), empty());
            assertThat(result.isWritten(), equalTo(true));
            assertThat(log.warnings(), empty());
        }
        assertThat(s.artifact.length, greaterThan(0));
    }

    @Test
    void duplicateIdOnAReferencedAttributeWritesNothingAndWarns(@TempDir final Path directory) throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        final XMLResource asm = (XMLResource) s.fixture.asmModel.getResource();
        final EAttribute referenced = s.built.getQueryModelInfo().getDataQueries().get(0).getAttribute();
        final EAttribute other = s.fixture.asmUtils.all(EAttribute.class).filter(a -> a != referenced).findFirst().orElseThrow();
        final String duplicated = asm.getID(referenced);
        assertThat(duplicated, notNullValue());
        asm.setID(other, duplicated);

        final File stale = directory.resolve(s.name + "-query.model").toFile();
        Files.writeString(stale.toPath(), "stale");

        try (LogCapture log = new LogCapture(QueryModelPersistence.class)) {
            final SaveResult result = QueryModelPersistence.save(s.arguments().file(stale).build());
            assertThat(result.isWritten(), equalTo(false));
            assertThat(result.getProblems().get(0), containsString(duplicated));
            assertThat(log.warnings().stream().anyMatch(w -> w.contains(duplicated)), equalTo(true));
        }
        assertThat("stale artifact deleted", stale.exists(), equalTo(false));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(QueryModelPersistence.save(s.arguments().outputStream(out).build()).isWritten(), equalTo(false));
        assertThat("nothing written", out.size(), equalTo(0));
    }

    @Test
    void asmObjectWithoutIdIsRefused() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        final XMLResource asm = (XMLResource) s.fixture.asmModel.getResource();
        final EAttribute referenced = s.built.getQueryModelInfo().getDataQueries().get(0).getAttribute();
        asm.setID(referenced, null);

        final SaveResult result = s.save();
        assertThat(result.isWritten(), equalTo(false));
        assertThat(result.getProblems().get(0), containsString("without xmi:id"));
    }

    @Test
    void duplicateIdOnAReferencedExpressionIsRefused() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        final XMLResource expression = (XMLResource) s.fixture.expressionModel.getResource();
        final EObject referenced = s.built.getQueryModelInfo().getEntityTypeExpressions().stream()
                .flatMap(e -> e.getGetterAttributes().stream())
                .map(e -> e.getExpression()).findFirst().orElseThrow();
        final EObject other = expression.getContents().stream().filter(o -> o != referenced).findFirst().orElseThrow();
        expression.setID(other, expression.getID(referenced));

        final SaveResult result = s.save();
        assertThat(result.isWritten(), equalTo(false));
        assertThat(result.getProblems().get(0), containsString("expression object with duplicated xmi:id"));
    }

    /**
     * TP-15: a reference into a third resource is refused and the warning names the resource.
     */
    @Test
    void referenceIntoAForeignResourceIsRefused() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        final Resource foreign = new ResourceSetImpl().createResource(URI.createURI("foreign-types.model"));
        final EPackage foreignPackage = EcoreFactory.eINSTANCE.createEPackage();
        foreignPackage.setName("foreign");
        final EClass foreignClass = EcoreFactory.eINSTANCE.createEClass();
        foreignClass.setName("Alien");
        foreignPackage.getEClassifiers().add(foreignClass);
        foreign.getContents().add(foreignPackage);
        s.built.getQueryModelInfo().getQueries().get(0).setType(foreignClass);

        try (LogCapture log = new LogCapture(QueryModelPersistence.class)) {
            final SaveResult result = s.save();
            assertThat(result.isWritten(), equalTo(false));
            assertThat(result.getProblems().get(0), containsString("foreign-types.model"));
            assertThat(log.warnings().stream().anyMatch(w -> w.contains("foreign-types.model")), equalTo(true));
        }
        assertThat(s.artifact, nullValue());
    }

    @Test
    void saveNeedsExactlyOneTarget() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> QueryModelPersistence.save(saved.arguments().build()));
        assertThrows(IllegalArgumentException.class, () -> QueryModelPersistence.save(
                saved.arguments().outputStream(new ByteArrayOutputStream()).file(new File("x")).build()));
    }

    @Test
    void saveToAFileIsAtomicAndLeavesNoTemporaryFile(@TempDir final Path directory) throws Exception {
        final File target = directory.resolve("nested/dir/" + saved.name + "-query.model").toFile();
        assertThat(QueryModelPersistence.save(saved.arguments().file(target).build()).isWritten(), equalTo(true));
        assertThat(Files.readAllBytes(target.toPath()).length, greaterThan(0));
        assertThat(target.getParentFile().list().length, equalTo(1));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Load
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Round trip from a file, a jar URL and a nested path: 0 unresolved proxies, ASM references identical to the live objects.
     */
    @Test
    void roundTripFromFileJarUrlAndNestedPath(@TempDir final Path directory) throws Exception {
        final String file = saved.name + "-query.model";
        final Path plain = directory.resolve(file);
        Files.write(plain, saved.artifact);
        final Path nested = directory.resolve("a/b/c/" + file);
        Files.createDirectories(nested.getParent());
        Files.write(nested, saved.artifact);
        final Path jar = directory.resolve("artifact.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/query/" + file));
            out.write(saved.artifact);
            out.closeEntry();
        }

        final List<PersistedQueryModel> locators = List.of(
                new PersistedQueryModel(saved.name, () -> Files.newInputStream(plain)),
                new PersistedQueryModel(saved.name, () -> Files.newInputStream(nested)),
                new PersistedQueryModel(saved.name, () -> {
                    final URLConnection connection = new URL("jar:" + jar.toUri() + "!/META-INF/query/" + file).openConnection();
                    connection.setUseCaches(false);
                    return connection.getInputStream();
                }));
        for (PersistedQueryModel locator : locators) {
            try (InputStream in = locator.openStream()) {
                final LoadResult loaded = QueryModelPersistence.load(locator.getName(), in,
                        shared.asmModel.getResource(), shared.expressionModel.getResource());
                assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
                assertThat(loaded.getQueryModel().getResource().getURI().toString(), equalTo(file));
                assertAsmReferencesAreLive(loaded);
            }
        }
    }

    /**
     * Every cross reference of the loaded graph is the identical live ASM / expression object, an Ecore object or a loaded one.
     */
    private static void assertAsmReferencesAreLive(final LoadResult loaded) {
        final Resource queryResource = loaded.getQueryModel().getResource();
        final Resource asm = shared.asmModel.getResource();
        final Resource expression = shared.expressionModel.getResource();
        int asmReferences = 0;
        int expressionReferences = 0;
        final var contents = queryResource.getAllContents();
        while (contents.hasNext()) {
            final EObject source = contents.next();
            for (EReference reference : source.eClass().getEAllReferences()) {
                if (reference.isContainment() || reference.isContainer() || !source.eIsSet(reference)) {
                    continue;
                }
                final Object value = source.eGet(reference, false);
                final Collection<?> targets = value instanceof Collection ? (Collection<?>) value : List.of(value);
                for (Object t : targets) {
                    final EObject target = (EObject) t;
                    assertThat("no proxy", target.eIsProxy(), equalTo(false));
                    if (target.eResource() == asm) {
                        asmReferences++;
                        // identity: the live resource resolves the very same instance for its own id
                        assertThat(asm.getEObject(((XMLResource) asm).getID(target)), sameInstance(target));
                    } else if (target.eResource() == expression) {
                        expressionReferences++;
                        assertThat(expression.getEObject(((XMLResource) expression).getID(target)), sameInstance(target));
                    }
                }
            }
        }
        assertThat(asmReferences, greaterThan(0));
        assertThat(expressionReferences, greaterThan(0));

        // the typed keys are the live ASM classes
        final Set<EClass> liveTypes = saved.built.getIndex().getQueryTypes();
        for (QueryEntry entry : loaded.getInfo().getQueries()) {
            assertThat(liveTypes.contains(entry.getType()), equalTo(true));
        }
    }

    /**
     * The real runtime situation: the ASM and expression files were saved, a fresh process loads them, and the artifact is
     * resolved against those loaded resources (xmi:ids come from the files).
     */
    @Test
    void artifactResolvesAgainstReloadedAsmAndExpressionFiles() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        assertThat(s.save().isWritten(), equalTo(true));

        final ByteArrayOutputStream asmBytes = new ByteArrayOutputStream();
        s.fixture.asmModel.saveAsmModel(AsmModel.SaveArguments.asmSaveArgumentsBuilder().outputStream(asmBytes));
        final ByteArrayOutputStream expressionBytes = new ByteArrayOutputStream();
        s.fixture.expressionModel.saveExpressionModel(ExpressionModel.SaveArguments.expressionSaveArgumentsBuilder().outputStream(expressionBytes));
        final QueryModelPersistence.DigestInputStream asmStream = new QueryModelPersistence.DigestInputStream(new ByteArrayInputStream(asmBytes.toByteArray()));
        final AsmModel asm = AsmModel.loadAsmModel(AsmModel.LoadArguments.asmLoadArgumentsBuilder().inputStream(asmStream));
        final ExpressionModel expression = ExpressionModel.loadExpressionModel(ExpressionModel.LoadArguments.expressionLoadArgumentsBuilder()
                .inputStream(new ByteArrayInputStream(expressionBytes.toByteArray())).name(s.name));

        final LoadResult loaded = QueryModelPersistence.load(s.name, new ByteArrayInputStream(s.artifact), asm.getResource(), expression.getResource());
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
        // the digests of the files match the stamps (the loader digests what it parsed)
        assertThat(asmStream.digest(), equalTo(s.asmDigest));
        assertThat(QueryModelPersistence.checkStamps(loaded.getInfo(), s.name, asmStream.digest(), null, s.expressionDigest), empty());

        final QueryIndex restored = QueryIndex.restore(asm.getResourceSet(), loaded.getQueryModel());
        assertThat(restored.getQueryTypes().size(), equalTo(s.built.getIndex().getQueryTypes().size()));
        for (EClass type : restored.getQueryTypes()) {
            assertThat("a class of the reloaded ASM", type.eResource(), sameInstance(asm.getResource()));
        }
        restored.getEntityTypeExpressionsMap().values().forEach(e ->
                e.getGetterAttributeExpressions().values().forEach(x -> assertThat(x.eResource(), sameInstance(expression.getResource()))));
        assertThat(restored.getEntityTypeExpressionsMap().size(), equalTo(s.built.getIndex().getEntityTypeExpressionsMap().size()));
    }

    /**
     * QueryModelInfo to expression model references resolve to the live expression objects.
     */
    @Test
    void expressionReferencesResolveToTheLiveExpressionObjects() throws Exception {
        final LoadResult loaded = saved.load();
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
        final Resource expression = shared.expressionModel.getResource();
        int count = 0;
        for (EntityTypeExpressionsEntry entry : loaded.getInfo().getEntityTypeExpressions()) {
            for (var getter : entry.getGetterAttributes()) {
                assertThat(getter.getExpression().eIsProxy(), equalTo(false));
                assertThat(getter.getExpression().eResource(), sameInstance(expression));
                assertThat(getter.getExpression() instanceof DataExpression, equalTo(true));
                count++;
            }
            for (var getter : entry.getGetterReferences()) {
                assertThat(getter.getExpression().eIsProxy(), equalTo(false));
                assertThat(getter.getExpression().eResource(), sameInstance(expression));
                assertThat(getter.getExpression() instanceof ReferenceExpression, equalTo(true));
                count++;
            }
        }
        assertThat(count, greaterThan(0));
    }

    /**
     * A load never demand-loads or modifies the live resources.
     */
    @Test
    void loadLeavesTheLiveResourcesUntouched() throws Exception {
        final Resource asm = shared.asmModel.getResource();
        final int asmContents = asm.getContents().size();
        final boolean loadedFlag = asm.isLoaded();
        final ResourceSetImplSnapshot before = new ResourceSetImplSnapshot();
        saved.load();
        assertThat(asm.getContents().size(), equalTo(asmContents));
        assertThat(asm.isLoaded(), equalTo(loadedFlag));
        assertThat(shared.asmModel.getResourceSet().getResources().size(), equalTo(before.asmResources));
        assertThat(shared.expressionModel.getResourceSet().getResources().size(), equalTo(before.expressionResources));
    }

    private static final class ResourceSetImplSnapshot {
        final int asmResources = shared.asmModel.getResourceSet().getResources().size();
        final int expressionResources = shared.expressionModel.getResourceSet().getResources().size();
    }

    /**
     * A mismatched ASM leaves proxies unresolved.
     */
    @Test
    void mismatchedAsmGivesUnresolvedProxies() throws Exception {
        final NorthwindFixture other = NorthwindFixture.create();
        final LoadResult loaded = QueryModelPersistence.load(saved.name, new ByteArrayInputStream(saved.artifact),
                other.asmModel.getResource(), shared.expressionModel.getResource());
        assertThat(loaded.getUnresolvedProxyCount(), greaterThan(0));
        assertThat(loaded.getUnresolvedProxySamples(), not(empty()));
        assertThat(loaded.getUnresolvedProxySamples().get(0), containsString(saved.name + "-asm.model#"));
    }

    @Test
    void emptyAsmGivesUnresolvedProxies() throws Exception {
        final AsmModel empty = AsmModel.buildAsmModel().build();
        final LoadResult loaded = QueryModelPersistence.load(saved.name, new ByteArrayInputStream(saved.artifact),
                empty.getResource(), shared.expressionModel.getResource());
        assertThat(loaded.getUnresolvedProxyCount(), greaterThan(0));
    }

    /**
     * Zero or several QueryModelInfo roots fail the load.
     */
    @Test
    void zeroOrSeveralInfoRootsFailTheLoad() {
        final String xml = new String(saved.artifact, StandardCharsets.UTF_8);
        final Matcher info = Pattern.compile("<query:QueryModelInfo\\b.*?</query:QueryModelInfo>", Pattern.DOTALL).matcher(xml);
        assertThat(info.find(), equalTo(true));
        final String element = info.group();

        final String none = xml.replace(element, "");
        final IllegalStateException zero = assertThrows(IllegalStateException.class, () -> saved.load(none.getBytes(StandardCharsets.UTF_8)));
        assertThat(zero.getMessage(), containsString("found: 0"));

        final String two = xml.replace(element, element + element.replaceFirst("xmi:id=\"[^\"]*\"", ""));
        final IllegalStateException several = assertThrows(IllegalStateException.class, () -> saved.load(two.getBytes(StandardCharsets.UTF_8)));
        assertThat(several.getMessage(), containsString("found: 2"));
    }

    @Test
    void notWellFormedArtifactFailsTheLoad() {
        assertThrows(IOException.class, () -> saved.load("<xmi:XMI>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> saved.load(new byte[0]));
        final byte[] truncated = java.util.Arrays.copyOf(saved.artifact, saved.artifact.length / 2);
        assertThrows(IOException.class, () -> saved.load(truncated));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Index round trip (TP-16)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * TP-16: build, save, load, restore the index: the lookups, ordering and counters of the built index are reproduced, and
     * the entity expression entries are the identical live expression objects.
     */
    @Test
    void savedAndLoadedIndexEqualsTheBuiltIndex() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        assertThat(s.save().isWritten(), equalTo(true));
        final LoadResult loaded = s.load();
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));

        final QueryIndex built = s.built.getIndex();
        final QueryIndex restored = QueryIndex.restore(s.fixture.asmModel.getResourceSet(), loaded.getQueryModel());
        final EcoreUtil.EqualityHelper equality = new EcoreUtil.EqualityHelper();

        // queries of every mapped transfer type: equal graphs (the ASM objects they reference are identical)
        int mapped = 0;
        for (EClass c : s.fixture.asmUtils.all(EClass.class).collect(Collectors.toList())) {
            if (s.fixture.asmUtils.isMappedTransferObjectType(c)) {
                mapped++;
                final EObject expected = built.getQuery(c).orElseThrow();
                final EObject actual = restored.getQuery(c).orElse(null);
                assertThat(AsmUtils.getClassifierFQName(c), actual, notNullValue());
                assertThat(AsmUtils.getClassifierFQName(c), actual, not(sameInstance(expected)));
                assertThat(AsmUtils.getClassifierFQName(c), equality.equals(expected, actual), equalTo(true));
            }
        }
        assertThat(mapped, greaterThan(0));
        assertThat(restored.getQueryTypes(), equalTo(built.getQueryTypes()));

        // navigations, data queries and ordering for every ASM reference / attribute
        for (EReference r : s.fixture.asmUtils.all(EReference.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getReferenceFQName(r), restored.getNavigation(r).isPresent(), equalTo(built.getNavigation(r).isPresent()));
            if (built.getNavigation(r).isPresent()) {
                assertThat(AsmUtils.getReferenceFQName(r), equality.equals(built.getNavigation(r).get(), restored.getNavigation(r).get()), equalTo(true));
            }
            assertThat(AsmUtils.getReferenceFQName(r), restored.isOrdered(r), equalTo(built.isOrdered(r)));
        }
        for (EAttribute a : s.fixture.asmUtils.all(EAttribute.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getAttributeFQName(a), restored.getDataQuery(a).isPresent(), equalTo(built.getDataQuery(a).isPresent()));
            if (built.getDataQuery(a).isPresent()) {
                assertThat(AsmUtils.getAttributeFQName(a), equality.equals(built.getDataQuery(a).get(), restored.getDataQuery(a).get()), equalTo(true));
            }
        }
        assertThat(restored.getNavigationReferences(), equalTo(built.getNavigationReferences()));
        assertThat(restored.getDataQueryAttributes(), equalTo(built.getDataQueryAttributes()));
        assertThat(restored.getOrderedTransferRelations(), equalTo(built.getOrderedTransferRelations()));
        assertThat(restored.getStaticEntityAttributes(), equalTo(built.getStaticEntityAttributes()));
        assertThat(restored.getStaticEntityReferences(), equalTo(built.getStaticEntityReferences()));

        // counters
        assertThat(restored.getNextSourceIndex().get(), equalTo(built.getNextSourceIndex().get()));
        assertThat(restored.getNextTargetIndex().get(), equalTo(built.getNextTargetIndex().get()));

        // entity expressions: entry count and identity of the expression objects
        assertThat(restored.getEntityTypeExpressionsMap().size(), equalTo(built.getEntityTypeExpressionsMap().size()));
        for (Map.Entry<EClass, EntityTypeExpressions> entry : built.getEntityTypeExpressionsMap().entrySet()) {
            final EntityTypeExpressions other = restored.getEntityTypeExpressionsMap().get(entry.getKey());
            assertThat(other, notNullValue());
            assertThat(other.getGetterAttributeExpressions().size(), equalTo(entry.getValue().getGetterAttributeExpressions().size()));
            entry.getValue().getGetterAttributeExpressions().forEach((a, e) -> assertThat(other.getGetterAttributeExpressions().get(a), sameInstance(e)));
            assertThat(other.getGetterReferenceExpressions().size(), equalTo(entry.getValue().getGetterReferenceExpressions().size()));
            entry.getValue().getGetterReferenceExpressions().forEach((r, e) -> assertThat(other.getGetterReferenceExpressions().get(r), sameInstance(e)));
        }

        // every isStatic* answer for every ASM feature
        for (EAttribute a : s.fixture.asmUtils.all(EAttribute.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getAttributeFQName(a), restored.isStaticAttribute(a), equalTo(built.isStaticAttribute(a)));
        }
        for (EReference r : s.fixture.asmUtils.all(EReference.class).collect(Collectors.toList())) {
            assertThat(AsmUtils.getReferenceFQName(r), restored.isStaticReference(r), equalTo(built.isStaticReference(r)));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Digest helper
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Trailing bytes the parser did not read are still digested.
     */
    @Test
    void digestCoversTheCompleteStreamEvenWhenTheParserStopsEarly() throws Exception {
        final byte[] content = new byte[100_000];
        new java.util.Random(42).nextBytes(content);
        final QueryModelPersistence.DigestInputStream in = new QueryModelPersistence.DigestInputStream(new ByteArrayInputStream(content));
        final byte[] head = new byte[10];
        assertThat(in.read(head), equalTo(10));
        assertThat(in.skip(5), equalTo(5L));

        final String digest = in.digest();
        assertThat(digest, equalTo(QueryModelPersistence.sha256Hex(content)));
        assertThat("idempotent", in.digest(), equalTo(digest));
        assertThat(QueryModelPersistence.sha256Hex(new ByteArrayInputStream(content)), equalTo(digest));

        assertThat(QueryModelPersistence.sha256Hex(new byte[0]),
                equalTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @Test
    void digestIsTakenWhileTheModelIsLoaded() throws Exception {
        // a loader wraps the model stream, loads, then asks for the digest of all bytes
        final QueryModelPersistence.DigestInputStream in = new QueryModelPersistence.DigestInputStream(new ByteArrayInputStream(saved.artifact));
        QueryModelPersistence.load(saved.name, in, shared.asmModel.getResource(), shared.expressionModel.getResource());
        assertThat(in.digest(), equalTo(QueryModelPersistence.sha256Hex(saved.artifact)));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Constants: allow-list
    // ------------------------------------------------------------------------------------------------------------

    /**
     * A custom Serializable that the filter must reject.
     */
    static final class Evil implements Serializable {
        private static final long serialVersionUID = 1L;
        final String payload = "x";
    }

    /**
     * Saves a fresh model that carries the given constants as extra roots, and loads it. Returns the loaded constants.
     */
    private static List<Constant> roundTripConstants(final Object... values) throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        for (Object value : values) {
            s.built.getQueryModel().addContent(newConstantBuilder().withValue(value).build());
        }
        assertThat(s.save().isWritten(), equalTo(true));
        final LoadResult loaded = s.load();
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
        final List<Constant> constants = new ArrayList<>();
        final List<EObject> roots = loaded.getQueryModel().getResource().getContents();
        for (EObject root : roots.subList(roots.size() - values.length, roots.size())) {
            constants.add((Constant) root);
        }
        return constants;
    }

    /**
     * TP-37: allowed constant types load through the filter, and keep their types.
     */
    @Test
    void allowedConstantTypesLoadAndKeepTheirTypes() throws Exception {
        final Object[] values = {
                new BigInteger("123456789012345678901234567890"),
                new BigDecimal("12345.678900"),
                Boolean.TRUE,
                42,
                "text \u00e9\u4e2d",
                LocalDate.of(2026, 11, 6),
                LocalTime.of(1, 23, 45),
                LocalDateTime.of(2026, 11, 6, 1, 23, 45),
                OffsetDateTime.of(2026, 11, 6, 1, 23, 45, 0, ZoneOffset.ofHours(1)),
                Long.MAX_VALUE, (short) 3, (byte) 2, 1.5d, 2.5f,
                new byte[]{1, 2, 3},
                new int[][]{{1}, {2, 3}}
        };
        final List<Constant> constants = roundTripConstants(values);
        for (int i = 0; i < values.length; i++) {
            final Object loaded = constants.get(i).getValue();
            assertThat(loaded.getClass(), equalTo(values[i].getClass()));
            if (values[i] instanceof byte[]) {
                assertThat((byte[]) loaded, equalTo((byte[]) values[i]));
            } else if (values[i] instanceof int[][]) {
                assertThat(java.util.Arrays.deepEquals((int[][]) loaded, (int[][]) values[i]), equalTo(true));
            } else {
                assertThat(loaded, equalTo(values[i]));
            }
        }
    }

    /**
     * TP-36: a java.util.Date or a custom Serializable is rejected by the filter, and the load fails.
     */
    @Test
    void disallowedConstantTypesFailTheLoad() throws Exception {
        for (Object value : new Object[]{new Date(), new Evil(), new Object[]{"x"}, new ArrayList<>(List.of("x")), new byte[100_000]}) {
            final Saved s = new Saved(NorthwindFixture.create());
            s.built.getQueryModel().addContent(newConstantBuilder().withValue(value).build());
            assertThat(s.save().isWritten(), equalTo(true));

            final IOException failure = assertThrows(IOException.class, s::load, value.getClass().getName());
            assertThat(value.getClass().getName(), failure.getMessage(), containsString("rejected"));
        }
    }

    @Test
    void malformedConstantFailsTheLoad() throws Exception {
        final Saved s = new Saved(NorthwindFixture.create());
        s.built.getQueryModel().addContent(newConstantBuilder().withValue("x").build());
        assertThat(s.save().isWritten(), equalTo(true));
        final String xml = new String(s.artifact, StandardCharsets.UTF_8);
        final String hex = EcoreFactory.eINSTANCE.convertToString(EcorePackage.Literals.EJAVA_OBJECT, "x");
        assertThat(xml, containsString("\"" + hex + "\""));

        for (String broken : new String[]{"ACED", "XYZ", hex + "00", hex.substring(0, hex.length() - 2)}) {
            final String corrupted = xml.replace("\"" + hex + "\"", "\"" + broken + "\"");
            assertThrows(IOException.class, () -> s.load(corrupted.getBytes(StandardCharsets.UTF_8)), broken);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Measure model absent (TP-54)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * TP-54: no measure model at build and at restore. Northwind's expressions need the measure model (units), so a
     * minimal model without any is used: build + save, then restore with no measure model.
     */
    @Test
    void measureModelAbsentStampsNoneOnBothSidesAndRestores() throws Exception {
        final MinimalFixture fixture = MinimalFixture.create();
        final Asm2QueryResult built = Asm2Query.executeAsm2Query(fixture.asmModel, null, fixture.expressionModel);
        assertThat(built.getIndex().getQueryTypes(), not(empty()));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(QueryModelPersistence.save(QueryModelPersistence.SaveArguments.saveArguments()
                .queryModel(built.getQueryModel()).asmModel(fixture.asmModel).expressionModel(fixture.expressionModel)
                .asmDigest("asm").expressionDigest("expression").outputStream(out).build()).isWritten(), equalTo(true));

        final LoadResult loaded = QueryModelPersistence.load(built.getQueryModel().getName(), new ByteArrayInputStream(out.toByteArray()),
                fixture.asmModel.getResource(), fixture.expressionModel.getResource());
        assertThat(loaded.getUnresolvedProxyCount(), equalTo(0));
        assertThat(loaded.getInfo().getMeasureDigest(), equalTo("none"));
        // the runtime side has no measure model either
        assertThat(QueryModelPersistence.checkStamps(loaded.getInfo(), built.getQueryModel().getName(), "asm", null, "expression"), empty());
        final QueryIndex restored = QueryIndex.restore(fixture.asmUtils, loaded.getInfo());
        assertThat(restored.getQueryTypes(), equalTo(built.getIndex().getQueryTypes()));
        assertThat(restored.getDataQueryAttributes(), equalTo(built.getIndex().getDataQueryAttributes()));
    }
}
