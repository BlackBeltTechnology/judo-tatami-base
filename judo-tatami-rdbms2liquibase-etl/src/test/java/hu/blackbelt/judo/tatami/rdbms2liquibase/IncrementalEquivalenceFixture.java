package hu.blackbelt.judo.tatami.rdbms2liquibase;

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
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseNamespaceFixUriHandler;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.rdbms2liquibase.etl.Rdbms2LiquibaseIncremental;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.common.util.UriUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.ProgramParameter.programParameterBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.excel.ExcelModelContext.excelModelContextBuilder;
import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.SaveArguments.liquibaseSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static java.util.Collections.singletonList;

/**
 * Test glue of the ETL-vs-Zeta incremental equivalence tests (harness copied from {@code Excel2RdbmsTest}): builds the
 * Excel original / new RDBMS model pairs, runs the incremental transformation with the ETL oracle and with the Zeta
 * entry point, and compares the eight phase models structurally.
 * See change: rdbms-incremental-without-epsilon.
 */
final class IncrementalEquivalenceFixture {

    private static final Logger LOG = LoggerFactory.getLogger(IncrementalEquivalenceFixture.class);

    static final String REVIEW_LOCATION = "src/test/resources/review";

    private IncrementalEquivalenceFixture() {
    }

    /** Original and new model of the Excel fixture. */
    static final class ModelPair {
        final RdbmsModel original;
        final RdbmsModel next;

        ModelPair(RdbmsModel original, RdbmsModel next) {
            this.original = original;
            this.next = next;
        }
    }

    /** The eight phase models of one incremental run. */
    static final class Phases {
        final LiquibaseModel dbCheckup = buildLiquibaseModel().name("DbCheckup").build();
        final LiquibaseModel dbBackup = buildLiquibaseModel().name("DbBackup").build();
        final LiquibaseModel beforeIncremental = buildLiquibaseModel().name("BeforeIncremental").build();
        final LiquibaseModel updateDataBeforeIncremental = buildLiquibaseModel().name("UpdateDataBeforeIncremental").build();
        final LiquibaseModel incremental = buildLiquibaseModel().name("IncrementalModel").build();
        final LiquibaseModel updateDataAfterIncremental = buildLiquibaseModel().name("UpdateDataAfterIncremental").build();
        final LiquibaseModel afterIncremental = buildLiquibaseModel().name("AfterIncremental").build();
        final LiquibaseModel dbDropBackup = buildLiquibaseModel().name("DbDropBackup").build();

        Map<String, LiquibaseModel> byPhase() {
            Map<String, LiquibaseModel> result = new LinkedHashMap<>();
            result.put("1 dbCheckup", dbCheckup);
            result.put("2 dbBackup", dbBackup);
            result.put("3 beforeIncremental", beforeIncremental);
            result.put("4 updateDataBeforeIncremental", updateDataBeforeIncremental);
            result.put("5 incremental", incremental);
            result.put("6 updateDataAfterIncremental", updateDataAfterIncremental);
            result.put("7 afterIncremental", afterIncremental);
            result.put("8 dbDropBackup", dbDropBackup);
            return result;
        }
    }

    /** Result of one run: phase models and the engine result. */
    static final class Run {
        final Phases phases;
        final Rdbms2LiquibaseIncrementalResult result;

        Run(Phases phases, Rdbms2LiquibaseIncrementalResult result) {
            this.phases = phases;
            this.result = result;
        }
    }

    /** Loads the Excel fixture; the type names are fitted to the dialect like in {@code Excel2RdbmsTest}. */
    static ModelPair excelPair(String dialect) throws Exception {
        return excelPair(dialect, false);
    }

    /**
     * Like {@link #excelPair(String)}; with {@code longTableNames} every table gets a 32 character suffix, so that the
     * backup table names exceed the oracle limit (30) but stay below the default limit (62).
     */
    static ModelPair excelPair(String dialect, boolean longTableNames) throws Exception {
        RdbmsModel originalModel = buildRdbmsModel().build();
        RdbmsModel newModel = buildRdbmsModel().build();
        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(LOG)) {
            ExecutionContext excelToRdbmsEtlContext = executionContextBuilder()
                    .log(bufferedLog)
                    .modelContexts(ImmutableList.of(
                            excelModelContextBuilder()
                                    .name("EXCEL")
                                    .aliases(singletonList("XLS"))
                                    .excel(getUri("RdbmsIncrementalTests.xlsx").toString())
                                    .excelConfiguration(getUri("mapping.xml").toString())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("ORIGINAL_MODEL")
                                    .aliases(singletonList("ORIGINAL"))
                                    .useCache(true)
                                    .validateModel(false)
                                    .resource(originalModel.getResource())
                                    .build(),
                            wrappedEmfModelContextBuilder()
                                    .name("NEW_MODEL")
                                    .aliases(singletonList("NEW"))
                                    .useCache(true)
                                    .validateModel(false)
                                    .resource(newModel.getResource())
                                    .build()))
                    .build();
            excelToRdbmsEtlContext.load();
            excelToRdbmsEtlContext.executeProgram(
                    etlExecutionContextBuilder()
                            .source(UriUtil.resolve("createExcelModel.etl", getUri("/")))
                            .parameters(singletonList(programParameterBuilder().name("dialect").value(dialect).build()))
                            .parallel(true)
                            .build());
            excelToRdbmsEtlContext.commit();
            excelToRdbmsEtlContext.close();
        }
        new RdbmsUtils(originalModel.getResourceSet()).getAllRdbmsField()
                .orElseThrow(() -> new RuntimeException("There are no fields in model: " + originalModel.getName()))
                .forEach(field -> replaceTypeNames(field, dialect));
        new RdbmsUtils(newModel.getResourceSet()).getAllRdbmsField()
                .orElseThrow(() -> new RuntimeException("There are no fields in model: " + newModel.getName()))
                .forEach(field -> replaceTypeNames(field, dialect));
        if (longTableNames) {
            for (RdbmsModel model : new RdbmsModel[]{originalModel, newModel}) {
                new RdbmsUtils(model.getResourceSet()).getRdbmsTables().ifPresent(tables ->
                        tables.forEach(table -> table.setSqlName(table.getSqlName() + "_LONGNAMELONGNAMELONGNAMELONGNAME")));
            }
        }
        return new ModelPair(originalModel, newModel);
    }

    /** Incremental RDBMS model of a fresh Excel pair, computed with the ETL diff. */
    static RdbmsModel etlIncrementalModel(String dialect) throws Exception {
        return etlIncrementalModel(dialect, false);
    }

    static RdbmsModel etlIncrementalModel(String dialect, boolean longTableNames) throws Exception {
        ModelPair pair = excelPair(dialect, longTableNames);
        RdbmsModel incremental = buildRdbmsModel().build();
        hu.blackbelt.judo.meta.rdbms.runtime.RdbmsIncremental.transformRdbmsIncrementalModel(
                pair.original, pair.next, incremental, dialect, true);
        return incremental;
    }

    /** Incremental RDBMS model of a fresh Excel pair, computed with the Java diff of judo-meta-rdbms. */
    static RdbmsModel javaIncrementalModel(String dialect) throws Exception {
        ModelPair pair = excelPair(dialect);
        RdbmsModel incremental = buildRdbmsModel().build();
        hu.blackbelt.judo.meta.rdbms.runtime.RdbmsIncrementalTransformation.transform(
                pair.original, pair.next, incremental, dialect, false);
        return incremental;
    }

    /** ETL oracle: the unchanged {@code Rdbms2LiquibaseIncremental}. */
    static Run runEtl(RdbmsModel incremental, String dialect, String backupPrefix, Integer tableNameMaxSize,
                      ReviewResolver reviewResolver) throws Exception {
        Phases phases = new Phases();
        Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalResult etl =
                Rdbms2LiquibaseIncremental.executeRdbms2LiquibaseIncrementalTransformation(
                        Rdbms2LiquibaseIncremental.Rdbms2LiquibaseIncrementalParameter.rdbms2LiquibaseIncrementalParameter()
                                .incrementalRdbmsModel(incremental)
                                .dbCheckupLiquibaseModel(phases.dbCheckup)
                                .dbBackupLiquibaseModel(phases.dbBackup)
                                .beforeIncrementalLiquibaseModel(phases.beforeIncremental)
                                .updateDataBeforeIncrementalLiquibaseModel(phases.updateDataBeforeIncremental)
                                .incrementalLiquibaseModel(phases.incremental)
                                .updateDataAfterIncrementalLiquibaseModel(phases.updateDataAfterIncremental)
                                .afterIncrementalLiquibaseModel(phases.afterIncremental)
                                .dbDropBackupLiquibaseModel(phases.dbDropBackup)
                                .dialect(dialect)
                                .backupPrefix(backupPrefix != null ? backupPrefix : "BACKUP")
                                .tableNameMaxSize(tableNameMaxSize != null ? tableNameMaxSize : -1)
                                .reviewResolver(reviewResolver)
                                .parallel(false));
        return new Run(phases, Rdbms2LiquibaseIncrementalResult.rdbms2LiquibaseIncrementalResult()
                .backupDataSqlFiles(etl.getBackupDataSqlFiles())
                .missingReviewScripts(etl.getMissingReviewScripts())
                .build());
    }

    /** Zeta: the new core entry point in the given mode. */
    static Run runEntryPoint(TransformationMode mode, RdbmsModel incremental, String dialect, String backupPrefix,
                             Integer tableNameMaxSize, ReviewResolver reviewResolver) throws Exception {
        Phases phases = new Phases();
        Rdbms2LiquibaseIncrementalParameter.Rdbms2LiquibaseIncrementalParameterBuilder builder =
                Rdbms2LiquibaseIncrementalParameter.rdbms2LiquibaseIncrementalParameter()
                        .incrementalRdbmsModel(incremental)
                        .dbCheckupLiquibaseModel(phases.dbCheckup)
                        .dbBackupLiquibaseModel(phases.dbBackup)
                        .beforeIncrementalLiquibaseModel(phases.beforeIncremental)
                        .updateDataBeforeIncrementalLiquibaseModel(phases.updateDataBeforeIncremental)
                        .incrementalLiquibaseModel(phases.incremental)
                        .updateDataAfterIncrementalLiquibaseModel(phases.updateDataAfterIncremental)
                        .afterIncrementalLiquibaseModel(phases.afterIncremental)
                        .dbDropBackupLiquibaseModel(phases.dbDropBackup)
                        .dialect(dialect)
                        .transformationMode(mode)
                        .parallel(false)
                        .reviewResolver(reviewResolver);
        if (backupPrefix != null) {
            builder.backupPrefix(backupPrefix);
        }
        if (tableNameMaxSize != null) {
            builder.tableNameMaxSize(tableNameMaxSize);
        }
        Rdbms2LiquibaseIncrementalResult result =
                Rdbms2LiquibaseIncrementalTransformation.executeRdbms2LiquibaseIncrementalTransformation(builder);
        return new Run(phases, result);
    }

    static ReviewResolver fileSystemResolver() {
        return new FileSystemReviewResolver(new File(REVIEW_LOCATION));
    }

    /**
     * Structural comparison of the {@code databaseChangeLog} roots of two phase models. A subtree is canonicalized to
     * a string: type, every attribute, and the canonical strings of its containment children, sorted. The change sets
     * of a phase (and the preconditions) are compared as bags. Order is deliberately not compared: the ETL visits the
     * source elements in an order that is an artefact of the Epsilon model cache (change sets of different tables and
     * rules interleave), while the Zeta engine is deterministic (see TP-21). Roots that are no {@code databaseChangeLog}
     * are ETL leftovers of rule targets that were never attached to a container and are ignored.
     * Returns the differences found (empty when equal).
     */
    static List<String> diff(String path, EObject expected, EObject actual) {
        List<String> differences = new ArrayList<>();
        Map<String, Integer> missing = bag(expected);
        Map<String, Integer> unexpected = bag(actual);
        new ArrayList<>(missing.keySet()).forEach(k -> {
            int common = Math.min(missing.get(k), unexpected.getOrDefault(k, 0));
            subtract(missing, k, common);
            subtract(unexpected, k, common);
        });
        missing.forEach((k, v) -> differences.add(path + ": missing (x" + v + ") " + k));
        unexpected.forEach((k, v) -> differences.add(path + ": unexpected (x" + v + ") " + k));
        return differences;
    }

    private static void subtract(Map<String, Integer> bag, String key, int count) {
        int left = bag.get(key) == null ? 0 : bag.get(key) - count;
        if (bag.containsKey(key)) {
            if (left <= 0) {
                bag.remove(key);
            } else {
                bag.put(key, left);
            }
        }
    }

    /** Bag of the canonical strings of the changes below a changelog; preconditions are flattened. */
    private static Map<String, Integer> bag(EObject changeLog) {
        Map<String, Integer> result = new java.util.TreeMap<>();
        collect(changeLog, result);
        return result;
    }

    private static void collect(EObject container, Map<String, Integer> result) {
        for (EObject child : container.eContents()) {
            String name = child.eClass().getName();
            if ("PreConditions".equals(name) || "Not".equals(name)) {
                result.merge("(" + name + " " + attributes(child) + ")", 1, Integer::sum);
                collect(child, result);
            } else {
                result.merge(canonical(child), 1, Integer::sum);
            }
        }
    }

    private static String attributes(EObject object) {
        List<String> values = new ArrayList<>();
        for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
            if (feature instanceof EAttribute && !feature.isDerived() && !feature.isTransient()
                    && !"mixed".equals(feature.getName())) {
                values.add(feature.getName() + "=" + object.eGet(feature));
            }
        }
        return String.join(",", values);
    }

    private static String canonical(EObject object) {
        List<String> children = new ArrayList<>();
        for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
            if (feature.isDerived() || feature.isTransient()) {
                continue;
            }
            Object value = object.eGet(feature);
            if (feature instanceof EAttribute && value instanceof org.eclipse.emf.ecore.util.FeatureMap) {
                for (org.eclipse.emf.ecore.util.FeatureMap.Entry entry : (org.eclipse.emf.ecore.util.FeatureMap) value) {
                    Object v = entry.getValue();
                    children.add("~" + entry.getEStructuralFeature().getName() + "="
                            + (v instanceof EObject ? canonical((EObject) v) : String.valueOf(v)));
                }
            } else if (feature instanceof EReference && ((EReference) feature).isContainment()) {
                List<String> items = new ArrayList<>();
                if (feature.isMany()) {
                    for (Object item : (List<?>) value) {
                        items.add(canonical((EObject) item));
                    }
                } else if (value != null) {
                    items.add(canonical((EObject) value));
                }
                java.util.Collections.sort(items);
                children.add("." + feature.getName() + items);
            }
        }
        java.util.Collections.sort(children);
        return object.eClass().getName() + "{" + attributes(object) + children + "}";
    }

    /** All differences between the equally named phases of two runs, empty when equal. */
    static List<String> diffPhases(Phases expected, Phases actual) {
        List<String> differences = new ArrayList<>();
        Map<String, LiquibaseModel> e = expected.byPhase();
        Map<String, LiquibaseModel> a = actual.byPhase();
        for (String phase : e.keySet()) {
            List<EObject> expectedRoots = changeLogs(e.get(phase));
            List<EObject> actualRoots = changeLogs(a.get(phase));
            if (expectedRoots.size() != actualRoots.size()) {
                differences.add(phase + ": databaseChangeLog count " + expectedRoots.size() + " != " + actualRoots.size());
                continue;
            }
            for (int i = 0; i < expectedRoots.size(); i++) {
                differences.addAll(diff(phase, expectedRoots.get(i), actualRoots.get(i)));
            }
        }
        return differences;
    }

    /** Failure message: differences per phase, and all of them in {@code target/equivalence-diff.txt}. */
    static String report(String title, List<String> differences) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("target/equivalence-diff-" + title + ".txt"),
                    String.join("\n", differences).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, Integer> perPhase = new java.util.TreeMap<>();
        differences.forEach(d -> perPhase.merge(d.substring(0, d.indexOf(':')), 1, Integer::sum));
        return title + ": " + differences.size() + " differences " + perPhase + "\n"
                + String.join("\n", differences.subList(0, Math.min(25, differences.size())));
    }

    private static List<EObject> changeLogs(LiquibaseModel model) {
        List<EObject> result = new ArrayList<>();
        for (EObject root : model.getResource().getContents()) {
            if ("databaseChangeLog".equals(root.eClass().getName())) {
                result.add(root);
            }
        }
        return result;
    }

    /** One line per change set (id, children) and per root-level precondition: a readable outline of a phase. */
    static String outline(LiquibaseModel model) {
        StringBuilder sb = new StringBuilder();
        for (EObject root : model.getResource().getContents()) {
            sb.append("ROOT ").append(root.eClass().getName()).append('\n');
            for (EObject child : root.eContents()) {
                sb.append("  ").append(child.eClass().getName());
                for (EStructuralFeature f : child.eClass().getEAllStructuralFeatures()) {
                    if (f instanceof EAttribute && child.eGet(f) != null && !String.valueOf(child.eGet(f)).isEmpty()
                            && !(child.eGet(f) instanceof Boolean && !((Boolean) child.eGet(f)))) {
                        sb.append(' ').append(f.getName()).append('=').append(child.eGet(f));
                    }
                }
                sb.append('\n');
                for (EObject grand : child.eContents()) {
                    sb.append("      ").append(grand.eClass().getName());
                    for (EStructuralFeature f : grand.eClass().getEAllStructuralFeatures()) {
                        if (f instanceof EAttribute && grand.eGet(f) != null && !String.valueOf(grand.eGet(f)).isEmpty()) {
                            sb.append(' ').append(f.getName()).append('=').append(grand.eGet(f));
                        }
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Serialized form of the phase models (as written to a changelog file). */
    static Map<String, String> serialize(Phases phases) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, LiquibaseModel> entry : phases.byPhase().entrySet()) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            entry.getValue().saveLiquibaseModel(
                    liquibaseSaveArgumentsBuilder().outputStream(LiquibaseNamespaceFixUriHandler.fixUriOutputStream(stream)));
            result.put(entry.getKey(), stream.toString("UTF-8"));
        }
        return result;
    }

    private static void replaceTypeNames(final RdbmsField field, final String dialect) {
        final String typeName = field.getRdbmsTypeName();
        if (typeName.equals("Number")) {
            if (dialect.equals("hsqldb")) {
                field.setRdbmsTypeName("Integer");
            } else if (dialect.equals("postgresql") || dialect.equals("oracle")) {
                field.setRdbmsTypeName(field.getPrecision() > 0 || field.getSize() > 0 ? "Decimal" : "Integer");
            } else {
                throw new RuntimeException("Unknown dialect type: " + dialect);
            }
        }
    }

    private static URI getUri(String file) throws Exception {
        URI root = IncrementalEquivalenceFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (root.toString().endsWith(".jar")) {
            return new URI("jar:" + root + "!/" + file);
        }
        return new URI(root.toString() + "/" + file);
    }
}
