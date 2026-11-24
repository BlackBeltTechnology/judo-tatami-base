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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.rdbms2liquibase.etl.Rdbms2LiquibaseIncremental;
import hu.blackbelt.judo.tatami.rdbms2liquibase.etl.Rdbms2LiquibaseIncrementalEtlEngineImpl;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseIncrementalZetaTransformationV2;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.IncrementalEquivalenceFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremental RDBMS to Liquibase: the Zeta engine (new core entry point) is equal to the ETL oracle
 * ({@link Rdbms2LiquibaseIncremental}) on the Excel fixture: the eight phase models, the review script decisions, the
 * backup-data SQL and the table name limits. See change: rdbms-incremental-without-epsilon
 * (TP-15, TP-16, TP-17, TP-20, TP-21, TP-22).
 */
class Rdbms2LiquibaseIncrementalEquivalenceTest {

    /** Review script store that serves the given scripts and knows nothing else. */
    private static final class MapReviewResolver implements ReviewResolver {
        private final Map<String, String> scripts;

        MapReviewResolver(Map<String, String> scripts) {
            this.scripts = scripts;
        }

        @Override
        public boolean exists(String name) {
            return scripts.containsKey(name);
        }

        @Override
        public String resolve(String name) {
            return scripts.get(name);
        }
    }

    private static long count(LiquibaseModel model, String eClassName) {
        long count = 0;
        for (EObject root : model.getResource().getContents()) {
            if (root.eClass().getName().equals(eClassName)) {
                count++;
            }
            for (java.util.Iterator<EObject> it = root.eAllContents(); it.hasNext(); ) {
                if (it.next().eClass().getName().equals(eClassName)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Set<String> sqlFilePaths(LiquibaseModel model) {
        Set<String> paths = new TreeSet<>();
        for (EObject root : model.getResource().getContents()) {
            for (java.util.Iterator<EObject> it = root.eAllContents(); it.hasNext(); ) {
                EObject next = it.next();
                if (next.eClass().getName().equals("SqlFile")) {
                    paths.add((String) next.eGet(next.eClass().getEStructuralFeature("path")));
                }
            }
        }
        return paths;
    }

    private static void assertNoDifference(String title, Run expected, Run actual) {
        List<String> differences = diffPhases(expected.phases, actual.phases);
        assertTrue(differences.isEmpty(), report(title, differences));
        assertEquals(expected.result.getMissingReviewScripts(), actual.result.getMissingReviewScripts(),
                title + ": missingReviewScripts");
        assertEquals(expected.result.getBackupDataSqlFiles(), actual.result.getBackupDataSqlFiles(),
                title + ": backupDataSqlFiles");
    }

    // TP-15
    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql"})
    void allEightPhasesEqual(String dialect) throws Exception {
        Run etl = runEtl(etlIncrementalModel(dialect), dialect, null, null, fileSystemResolver());
        Run zeta = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                fileSystemResolver());

        // the fixture produces content in every phase (the comparison is not vacuous)
        assertEquals(8, etl.phases.byPhase().size());
        etl.phases.byPhase().forEach((phase, model) ->
                assertTrue(count(model, "TableExists") + count(model, "ChangeSet") > 0, phase + " is empty"));

        // ... and exercises every kind of change of the phases
        Map<String, Long> kinds = new TreeMap<>();
        for (String kind : new String[]{"TableExists", "ColumnExists", "ForeignKeyConstraintExists", "IndexExists",
                "DropIndex", "DropUniqueConstraint", "DropNotNullConstraint", "DropForeignKeyConstraint", "SqlFile",
                "DropTable", "CreateTable", "RenameTable", "RenameColumn", "DropColumn", "AddColumn", "ModifyDataType",
                "AddForeignKeyConstraint", "AddNotNullConstraint", "AddUniqueConstraint", "CreateIndex"}) {
            kinds.put(kind, etl.phases.byPhase().values().stream().mapToLong(m -> count(m, kind)).sum());
        }
        kinds.forEach((kind, n) -> assertTrue(n > 0, kind + " is not exercised by the fixture: " + kinds));

        assertNoDifference(dialect, etl, zeta);
    }

    // TP-15: the Zeta engine on the incremental model of the Java diff of judo-meta-rdbms (the production input)
    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql"})
    void allEightPhasesEqualOnJavaDiff(String dialect) throws Exception {
        Run etl = runEtl(etlIncrementalModel(dialect), dialect, null, null, fileSystemResolver());
        Run zeta = runEntryPoint(TransformationMode.ZETA, javaIncrementalModel(dialect), dialect, null, null,
                fileSystemResolver());

        assertNoDifference(dialect + "-java-diff", etl, zeta);
    }

    // guard of the comparator: a missing change is reported
    @Test
    void comparatorDetectsADifference() throws Exception {
        Run etl = runEtl(etlIncrementalModel("hsqldb"), "hsqldb", null, null, fileSystemResolver());
        Run zeta = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel("hsqldb"), "hsqldb", null, null,
                fileSystemResolver());
        assertTrue(diffPhases(etl.phases, zeta.phases).isEmpty());

        EObject changeLog = zeta.phases.incremental.getResource().getContents().get(0);
        EObject changeSet = changeLog.eContents().stream().filter(e -> e.eClass().getName().equals("ChangeSet"))
                .findFirst().orElseThrow(AssertionError::new);
        EObject firstChange = changeSet.eContents().get(0);
        org.eclipse.emf.ecore.util.EcoreUtil.remove(firstChange);

        assertFalse(diffPhases(etl.phases, zeta.phases).isEmpty());
    }

    // TP-16
    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql"})
    void reviewScriptDecisionTable(String dialect) throws Exception {
        // no script at all: every review script is missing
        Run etlNone = runEtl(etlIncrementalModel(dialect), dialect, null, null,
                new MapReviewResolver(Collections.emptyMap()));
        Run zetaNone = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                new MapReviewResolver(Collections.emptyMap()));
        assertNoDifference(dialect + "-none", etlNone, zetaNone);
        assertFalse(zetaNone.result.getMissingReviewScripts().isEmpty());
        assertTrue(sqlFilePaths(zetaNone.phases.updateDataBeforeIncremental).isEmpty());
        assertTrue(sqlFilePaths(zetaNone.phases.updateDataAfterIncremental).isEmpty());

        // the review scripts of the fixture, in a fixed order, split into absent / empty / present
        List<String> names = new ArrayList<>(zetaNone.result.getMissingReviewScripts().keySet());
        Collections.sort(names);
        assertTrue(names.size() >= 6, "the fixture has review scripts: " + names);
        Map<String, String> scripts = new HashMap<>();
        Set<String> absent = new TreeSet<>();
        Set<String> empty = new TreeSet<>();
        Set<String> present = new TreeSet<>();
        for (int i = 0; i < names.size(); i++) {
            switch (i % 3) {
                case 0:
                    absent.add(names.get(i));
                    break;
                case 1:
                    empty.add(names.get(i));
                    scripts.put(names.get(i), "  \n\t\n");
                    break;
                default:
                    present.add(names.get(i));
                    scripts.put(names.get(i), "SELECT 1;\n");
            }
        }

        Run etlMixed = runEtl(etlIncrementalModel(dialect), dialect, null, null, new MapReviewResolver(scripts));
        Run zetaMixed = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                new MapReviewResolver(scripts));
        assertNoDifference(dialect + "-mixed", etlMixed, zetaMixed);

        // absent: recorded as missing; empty and present: not missing
        assertEquals(absent, new TreeSet<>(zetaMixed.result.getMissingReviewScripts().keySet()));
        // only a present non-empty script gets a SqlFile
        Set<String> sqlFiles = new TreeSet<>(sqlFilePaths(zetaMixed.phases.updateDataBeforeIncremental));
        sqlFiles.addAll(sqlFilePaths(zetaMixed.phases.updateDataAfterIncremental));
        assertEquals(present, sqlFiles);
        assertTrue(Collections.disjoint(sqlFiles, empty));
        // the messages are the ones of the ETL
        zetaMixed.result.getMissingReviewScripts().forEach((name, message) ->
                assertTrue(message.startsWith("Sql script not found for "), message));

        // all present: nothing is missing
        Map<String, String> all = names.stream().collect(Collectors.toMap(n -> n, n -> "SELECT 1;\n"));
        Run etlAll = runEtl(etlIncrementalModel(dialect), dialect, null, null, new MapReviewResolver(all));
        Run zetaAll = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                new MapReviewResolver(all));
        assertNoDifference(dialect + "-all", etlAll, zetaAll);
        assertTrue(zetaAll.result.getMissingReviewScripts().isEmpty());
        Set<String> allSqlFiles = new TreeSet<>(sqlFilePaths(zetaAll.phases.updateDataBeforeIncremental));
        allSqlFiles.addAll(sqlFilePaths(zetaAll.phases.updateDataAfterIncremental));
        assertEquals(new TreeSet<>(names), allSqlFiles);
    }

    // TP-17
    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql"})
    void backupDataSqlEqual(String dialect) throws Exception {
        for (String prefix : new String[]{null, "SAVE"}) {
            Run etl = runEtl(etlIncrementalModel(dialect), dialect, prefix, null, fileSystemResolver());
            Run zeta = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, prefix, null,
                    fileSystemResolver());
            String title = dialect + "-" + prefix;

            assertNoDifference(title, etl, zeta);

            String effectivePrefix = prefix == null ? "BACKUP" : prefix;
            Map<String, String> files = new TreeMap<>(zeta.result.getBackupDataSqlFiles());
            // deleted tables 4 and 6, modified tables 1, 2, 3, 7 (previous name) and 9
            assertEquals(7, files.size(), title + ": " + files.keySet());
            files.keySet().forEach(name -> assertTrue(
                    name.startsWith(effectivePrefix.toLowerCase() + "_table_") && name.endsWith("_data_" + dialect + ".sql"),
                    name));
            String content = files.get(effectivePrefix.toLowerCase() + "_table_4_data_" + dialect + ".sql");
            assertNotNull(content, title);
            assertTrue(content.contains("CREATE TABLE " + effectivePrefix + "_TABLE_4 AS (SELECT * FROM TABLE_4)"), content);
            assertEquals("hsqldb".equals(dialect), content.contains(" WITH DATA;"), content);
        }
    }

    // TP-20
    @Test
    void tableNameMaxSizeBoundaries() throws Exception {
        // oracle without override: 30; postgresql without override: 62; explicit override: the override
        Object[][] cases = {{"oracle", null, 30}, {"postgresql", null, 62}, {"postgresql", 40, 40}};
        for (Object[] c : cases) {
            String dialect = (String) c[0];
            Integer override = (Integer) c[1];
            int expectedSize = (Integer) c[2];
            String title = dialect + "-" + override;

            Run etl = runEtl(etlIncrementalModel(dialect, true), dialect, null, override, fileSystemResolver());
            Run zeta = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect, true), dialect, null,
                    override, fileSystemResolver());
            assertNoDifference(title, etl, zeta);

            // effective size of the Zeta engine
            LiquibaseModelSet models = new LiquibaseModelSet();
            Rdbms2LiquibaseIncrementalZetaTransformationV2 v2 = Rdbms2LiquibaseIncrementalZetaTransformationV2.builder()
                    .rdbmsModel(etlIncrementalModel(dialect, true))
                    .dbCheckupLiquibaseModel(models.next()).dbBackupLiquibaseModel(models.next())
                    .beforeIncrementalLiquibaseModel(models.next()).dataUpdateBeforeIncrementalLiquibaseModel(models.next())
                    .incrementalLiquibaseModel(models.next()).dataUpdateAfterIncrementalLiquibaseModel(models.next())
                    .afterIncrementalLiquibaseModel(models.next()).dbDropBackupLiquibaseModel(models.next())
                    .dialect(dialect).tableNameMaxSize(override).reviewResolver(fileSystemResolver())
                    .build();
            assertEquals(expectedSize, v2.getTableNameMaxSize(), title);

            // the limit is visible in the names of the backup tables to drop
            Set<String> backupTables = new TreeSet<>();
            for (EObject root : zeta.phases.dbDropBackup.getResource().getContents()) {
                for (java.util.Iterator<EObject> it = root.eAllContents(); it.hasNext(); ) {
                    EObject next = it.next();
                    if (next.eClass().getName().equals("DropTable")) {
                        backupTables.add((String) next.eGet(next.eClass().getEStructuralFeature("tableName")));
                    }
                }
            }
            assertFalse(backupTables.isEmpty(), title);
            backupTables.forEach(name -> assertTrue(name.length() <= expectedSize, title + ": " + name));
            if (expectedSize >= 62) {
                // 6 (prefix) + 1 + 7 + 33 (suffix) = 47: not abbreviated
                assertTrue(backupTables.stream().anyMatch(name -> name.length() > 40), title + ": " + backupTables);
            }
        }
    }

    // TP-21
    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "postgresql"})
    void zetaIncrementalIsDeterministic(String dialect) throws Exception {
        Run first = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                fileSystemResolver());
        Run second = runEntryPoint(TransformationMode.ZETA, etlIncrementalModel(dialect), dialect, null, null,
                fileSystemResolver());

        assertEquals(serialize(first.phases), serialize(second.phases));
        assertEquals(first.result.getBackupDataSqlFiles(), second.result.getBackupDataSqlFiles());
        assertEquals(first.result.getMissingReviewScripts(), second.result.getMissingReviewScripts());
        assertFalse(first.result.getBackupDataSqlFiles().isEmpty());
    }

    // TP-22
    @Test
    void etlModeDelegatesToTheIncrementalEtlEngine() throws Exception {
        String dialect = "hsqldb";
        Run direct = runEtl(etlIncrementalModel(dialect), dialect, null, null, fileSystemResolver());

        // discovered like in plain Java: META-INF/services of this artifact
        Run discovered = runEntryPoint(TransformationMode.ETL, etlIncrementalModel(dialect), dialect, null, null,
                fileSystemResolver());
        assertNoDifference("etl-discovered", direct, discovered);

        // a registered engine (OSGi component) is the one that is called
        AtomicReference<Rdbms2LiquibaseIncrementalParameter> seen = new AtomicReference<>();
        Rdbms2LiquibaseIncrementalEtlEngine real = new Rdbms2LiquibaseIncrementalEtlEngineImpl();
        Rdbms2LiquibaseIncrementalEtlEngine spy = parameter -> {
            seen.set(parameter);
            return real.execute(parameter);
        };
        EtlEngineRegistry.register(Rdbms2LiquibaseIncrementalEtlEngine.class, spy);
        try {
            Run registered = runEntryPoint(TransformationMode.ETL, etlIncrementalModel(dialect), dialect, null, null,
                    fileSystemResolver());
            assertNotNull(seen.get(), "the registered engine has to be called");
            assertEquals(TransformationMode.ETL, seen.get().getTransformationMode());
            assertNoDifference("etl-registered", direct, registered);
        } finally {
            EtlEngineRegistry.unregister(Rdbms2LiquibaseIncrementalEtlEngine.class, spy);
        }
    }

    /** Eight fresh phase models for a direct {@link Rdbms2LiquibaseIncrementalZetaTransformationV2} build. */
    private static final class LiquibaseModelSet {
        private int index = 0;

        LiquibaseModel next() {
            return hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel().name("m" + index++).build();
        }
    }
}
