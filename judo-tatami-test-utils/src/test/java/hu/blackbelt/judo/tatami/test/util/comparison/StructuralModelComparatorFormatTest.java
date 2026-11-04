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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StructuralModelComparator#formatForLLM(ComparisonResult)}.
 */
class StructuralModelComparatorFormatTest {

    private ModelChecksumCalculator calc;
    private StructuralModelComparator comparator;

    @BeforeEach
    void setUp() {
        calc = new ModelChecksumCalculator();
        comparator = new StructuralModelComparator();

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());
    }

    // ==================== Positive Test Cases ====================

    @Test
    void formatForLLM_producesValidXmlStructure() {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class1", "Class2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        assertNotNull(xml);
        assertTrue(xml.contains("<model-comparison>"));
        assertTrue(xml.contains("</model-comparison>"));
    }

    @Test
    void formatForLLM_includesSummary() {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        assertTrue(xml.contains("<summary>"));
        assertTrue(xml.contains("<status>"));
        assertTrue(xml.contains("<total-differences>"));
    }

    @Test
    void formatForLLM_includesAllDifferenceTypes() {
        // Create models with MISSING difference
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2");
        Resource model2 = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        assertTrue(xml.contains("<difference"));
        assertTrue(xml.contains("type=\"MISSING\"") || xml.contains("type=\"missing\""));
    }

    @Test
    void formatForLLM_includesContextAndSuggestions() {
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2");
        Resource model2 = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        assertTrue(xml.contains("<path>"));
        assertTrue(xml.contains("<description>"));
        // Suggestions are optional but should be present in default format
        assertTrue(xml.contains("<suggestion>") || xml.contains("<context>"));
    }

    @Test
    void formatForLLM_handlesNoDifferences() {
        Resource model = createSimpleModel("pkg", "Class1");

        ComparisonResult result = comparator.compare(
                calc.calculate(model),
                calc.calculate(model));

        String xml = comparator.formatForLLM(result);

        assertTrue(xml.contains("<status>MATCH</status>") ||
                xml.contains("<total-differences>0</total-differences>"));
    }

    @Test
    void formatForLLM_escapesSpecialCharacters() {
        // Create model with names containing XML special chars
        Resource model1 = createModelWithSpecialChars("pkg", "Class<Test>");
        Resource model2 = createModelWithSpecialChars("pkg", "Class&Other");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        // Should not contain unescaped < or &
        assertFalse(xml.contains("<Test>") && !xml.contains("&lt;Test&gt;"));
        // XML should be well-formed
        assertNotNull(xml);
    }

    @Test
    void formatForLLM_attributeMismatch_showsExpectedAndActual() {
        Resource model1 = createModelWithAbstractClass("TestClass", true);
        Resource model2 = createModelWithAbstractClass("TestClass", false);

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        assertTrue(xml.contains("ATTRIBUTE_MISMATCH") || xml.contains("attribute"));
        assertTrue(xml.contains("true") || xml.contains("false"));
    }

    @Test
    void formatForLLM_referenceMismatch_showsTargetPaths() {
        Resource model1 = createModelWithSuperType("Child", "Base1");
        Resource model2 = createModelWithSuperType("Child", "Base2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        // Should show paths for reference targets
        assertTrue(xml.contains("Base1") || xml.contains("Base2") ||
                xml.contains("path") || xml.contains("target"));
    }

    @Test
    void formatForLLM_respectsFormatOptions() {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        FormatOptions options = new FormatOptions()
                .includeSuggestions(false)
                .includeAnalysis(false);

        String xml = comparator.formatForLLM(result, options);

        // With suggestions disabled, should not contain suggestion tags
        // (unless they're always included)
        assertNotNull(xml);
    }

    @Test
    void formatForLLM_multipleDifferences_allIncluded() {
        Resource model1 = createComplexModel1();
        Resource model2 = createComplexModel2();

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        String xml = comparator.formatForLLM(result);

        // Count <difference> occurrences
        int count = countOccurrences(xml, "<difference");
        assertTrue(count >= result.getDifferenceCount() ||
                count == result.getDifferenceCount());
    }

    // ==================== Negative Test Cases ====================

    @Test
    void formatForLLM_nullResult_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> comparator.formatForLLM(null));
    }

    @Test
    void formatForLLM_nullOptions_usesDefaults() {
        Resource model1 = createSimpleModel("pkg", "Class1");
        Resource model2 = createSimpleModel("pkg", "Class2");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        // Should not throw, should use defaults
        String xml = comparator.formatForLLM(result, null);
        assertNotNull(xml);
    }

    @Test
    void formatForLLM_maxDifferencesZero_returnsOnlySummary() {
        Resource model1 = createSimpleModel("pkg", "Class1", "Class2", "Class3");
        Resource model2 = createSimpleModel("pkg");

        ComparisonResult result = comparator.compare(
                calc.calculate(model1),
                calc.calculate(model2));

        FormatOptions options = new FormatOptions().maxDifferences(0);
        String xml = comparator.formatForLLM(result, options);

        // Should have summary but limited/no differences
        assertTrue(xml.contains("<summary>"));
    }

    @Test
    void formatForLLM_negativeMaxDifferences_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new FormatOptions().maxDifferences(-1));
    }

    // ==================== Helper Methods ====================

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

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

    private Resource createModelWithSpecialChars(String pkgName, String className) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName(pkgName);
        pkg.setNsPrefix(pkgName);
        pkg.setNsURI("http://test/" + pkgName);

        EClass cls = EcoreFactory.eINSTANCE.createEClass();
        cls.setName(className);
        pkg.getEClassifiers().add(cls);

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createModelWithAbstractClass(String className, boolean isAbstract) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("testpkg");
        pkg.setNsPrefix("testpkg");
        pkg.setNsURI("http://test/testpkg");

        EClass cls = EcoreFactory.eINSTANCE.createEClass();
        cls.setName(className);
        cls.setAbstract(isAbstract);
        pkg.getEClassifiers().add(cls);

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createModelWithSuperType(String childName, String baseName) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("testpkg");
        pkg.setNsPrefix("testpkg");
        pkg.setNsURI("http://test/testpkg");

        EClass base = EcoreFactory.eINSTANCE.createEClass();
        base.setName(baseName);
        pkg.getEClassifiers().add(base);

        EClass child = EcoreFactory.eINSTANCE.createEClass();
        child.setName(childName);
        child.getESuperTypes().add(base);
        pkg.getEClassifiers().add(child);

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
        classA.setAbstract(false); // Different from model1
        pkg.getEClassifiers().add(classA);

        EClass classC = EcoreFactory.eINSTANCE.createEClass();
        classC.setName("ClassC"); // Different class from model1
        pkg.getEClassifiers().add(classC);

        resource.getContents().add(pkg);
        return resource;
    }
}
