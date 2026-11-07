package hu.blackbelt.judo.tatami.test.util.comparison;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StructuralModelComparator#saveJson(ComparisonResult, Path)} and
 * {@link StructuralModelComparator#loadJson(Path)}.
 */
class StructuralModelComparatorJsonTest {

    private ModelChecksumCalculator calc;
    private StructuralModelComparator comparator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        calc = new ModelChecksumCalculator();
        comparator = new StructuralModelComparator();

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());
    }

    // ==================== Positive Test Cases ====================

    @Test
    void saveJson_writesValidJsonFile() throws IOException {
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2");
        Resource model2 = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);

        assertTrue(Files.exists(outputPath));
        String content = Files.readString(outputPath);
        assertTrue(content.startsWith("{"));
        assertTrue(content.endsWith("}"));
    }

    @Test
    void saveJson_includesTimestamp() throws IOException {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);

        String content = Files.readString(outputPath);
        assertTrue(content.contains("timestamp") || content.contains("Timestamp"));
    }

    @Test
    void saveJson_includesModelChecksums() throws IOException {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class2");

        ModelNode node1 = calc.calculate(model1);
        ModelNode node2 = calc.calculate(model2);
        ComparisonResult result = comparator.compare(node1, node2);

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);

        String content = Files.readString(outputPath);
        // Should contain checksums
        assertTrue(content.contains("checksum") || content.contains("Checksum"));
    }

    @Test
    void saveJson_preservesAllDifferences() throws IOException {
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2", "Class3");
        Resource model2 = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);

        String content = Files.readString(outputPath);
        // Should contain difference info
        assertTrue(content.contains("differences") || content.contains("Differences"));
        assertTrue(content.contains("MISSING") || content.contains("missing"));
    }

    @Test
    void loadJson_reconstructsComparisonResult() throws IOException {
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2");
        Resource model2 = createSimpleModel("pkg", "Class1");

        ComparisonResult original = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(original, outputPath);

        ComparisonResult loaded = comparator.loadJson(outputPath);

        assertNotNull(loaded);
        assertEquals(original.isMatch(), loaded.isMatch());
        assertEquals(original.getDifferenceCount(), loaded.getDifferenceCount());
    }

    @Test
    void loadJson_roundTrip_saveAndLoad() throws IOException {
        Resource model1 = createComplexModel1();
        Resource model2 = createComplexModel2();

        ComparisonResult original = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(original, outputPath);
        ComparisonResult loaded = comparator.loadJson(outputPath);

        // Core properties should match
        assertEquals(original.isMatch(), loaded.isMatch());
        assertEquals(original.getDifferenceCount(), loaded.getDifferenceCount());

        // Difference types should match
        assertEquals(
                original.getDifferences(Difference.DifferenceType.MISSING).size(),
                loaded.getDifferences(Difference.DifferenceType.MISSING).size());
    }

    @Test
    void saveLoadJson_emptyDifferences() throws IOException {
        Resource model = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model),
                calc.calculate(model));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);
        ComparisonResult loaded = comparator.loadJson(outputPath);

        assertTrue(loaded.isMatch());
        assertEquals(0, loaded.getDifferenceCount());
    }

    @Test
    void saveLoadJson_manyDifferences() throws IOException {
        Resource model1 = createLargeModel(50);
        Resource model2 = createLargeModel(30);

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        Path outputPath = tempDir.resolve("comparison.json");
        comparator.saveJson(result, outputPath);
        ComparisonResult loaded = comparator.loadJson(outputPath);

        assertEquals(result.getDifferenceCount(), loaded.getDifferenceCount());
    }

    // ==================== Negative Test Cases ====================

    @Test
    void saveJson_nullResult_throwsException() {
        Path outputPath = tempDir.resolve("comparison.json");

        assertThrows(IllegalArgumentException.class,
                () -> comparator.saveJson(null, outputPath));
    }

    @Test
    void saveJson_nullPath_throwsException() {
        Resource model = createSimpleModel("pkg", "Class1");
        ComparisonResult result = comparator.compare(
                calc.calculate(model),
                calc.calculate(model));

        assertThrows(IllegalArgumentException.class,
                () -> comparator.saveJson(result, null));
    }

    @Test
    void saveJson_invalidPath_throwsIOException() {
        Resource model = createSimpleModel("pkg", "Class1");
        ComparisonResult result = comparator.compare(
                calc.calculate(model),
                calc.calculate(model));

        Path invalidPath = Path.of("/nonexistent/directory/that/does/not/exist/comparison.json");

        assertThrows(IOException.class,
                () -> comparator.saveJson(result, invalidPath));
    }

    @Test
    void loadJson_nullPath_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> comparator.loadJson(null));
    }

    @Test
    void loadJson_nonExistentFile_throwsException() {
        Path nonExistent = tempDir.resolve("does-not-exist.json");

        assertThrows(IOException.class,
                () -> comparator.loadJson(nonExistent));
    }

    @Test
    void loadJson_emptyFile_throwsException() throws IOException {
        Path emptyFile = tempDir.resolve("empty.json");
        Files.writeString(emptyFile, "");

        assertThrows(IllegalArgumentException.class,
                () -> comparator.loadJson(emptyFile));
    }

    @Test
    void loadJson_malformedJson_throwsException() throws IOException {
        Path malformedFile = tempDir.resolve("malformed.json");
        Files.writeString(malformedFile, "{ this is not valid json }}}");

        assertThrows(IllegalArgumentException.class,
                () -> comparator.loadJson(malformedFile));
    }

    @Test
    void loadJson_incompatibleVersion_throwsException() throws IOException {
        Path incompatibleFile = tempDir.resolve("incompatible.json");
        Files.writeString(incompatibleFile, """
            {
              "schemaVersion": "99.0",
              "isMatch": true,
              "differences": []
            }
            """);

        assertThrows(IllegalArgumentException.class,
                () -> comparator.loadJson(incompatibleFile));
    }

    @Test
    void loadJson_corruptedFile_throwsException() throws IOException {
        Path corruptedFile = tempDir.resolve("corrupted.json");
        Files.writeString(corruptedFile, """
            {
              "schemaVersion": "1.0",
              "isMatch": "not-a-boolean",
              "differences": "not-an-array"
            }
            """);

        assertThrows(IllegalArgumentException.class,
                () -> comparator.loadJson(corruptedFile));
    }

    // ==================== Helper Methods ====================

    private Resource createSimpleModel(String pkgName, String... classNames) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName(pkgName);
        pkg.setNsPrefix(pkgName);
        pkg.setNsURI("http://test/" + pkgName);

        for (String className : classNames) {
            EClass cls = EcoreFactory.eINSTANCE.createEClass();
            cls.setName(className);
            pkg.getEClassifiers().add(cls);
        }

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createLargeModel(int classCount) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("largepkg");
        pkg.setNsPrefix("largepkg");
        pkg.setNsURI("http://test/largepkg");

        for (int i = 0; i < classCount; i++) {
            EClass cls = EcoreFactory.eINSTANCE.createEClass();
            cls.setName("Class" + i);
            pkg.getEClassifiers().add(cls);
        }

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createComplexModel1() {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("pkg");
        pkg.setNsPrefix("pkg");
        pkg.setNsURI("http://test/pkg");

        EClass classA = EcoreFactory.eINSTANCE.createEClass();
        classA.setName("ClassA");
        classA.setAbstract(true);
        pkg.getEClassifiers().add(classA);

        EClass classB = EcoreFactory.eINSTANCE.createEClass();
        classB.setName("ClassB");
        pkg.getEClassifiers().add(classB);

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createComplexModel2() {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("pkg");
        pkg.setNsPrefix("pkg");
        pkg.setNsURI("http://test/pkg");

        EClass classA = EcoreFactory.eINSTANCE.createEClass();
        classA.setName("ClassA");
        classA.setAbstract(false);
        pkg.getEClassifiers().add(classA);

        EClass classC = EcoreFactory.eINSTANCE.createEClass();
        classC.setName("ClassC");
        pkg.getEClassifiers().add(classC);

        resource.getContents().add(pkg);
        return resource;
    }
}
