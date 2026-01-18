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
 * Tests for {@link StructuralModelComparator}.
 */
class StructuralModelComparatorTest {

    private ModelChecksumCalculator calc;
    private StructuralModelComparator comparator;

    @BeforeEach
    void setUp() {
        calc = new ModelChecksumCalculator();
        comparator = new StructuralModelComparator();

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());
    }

    @Test
    void compare_identicalModels_noErrors() {
        Resource resource = createSimpleModel("pkg", "Class1", "Class2");

        ModelNode expected = calc.calculate(resource);
        ModelNode actual = calc.calculate(resource);

        ComparisonResult result = comparator.compare(expected, actual);

        assertTrue(result.isMatch());
        assertTrue(result.getDifferences().isEmpty());
    }

    @Test
    void compare_missingElement_reportsDifference() {
        Resource model1 = createSimpleModel("pkg", "ClassA", "ClassB");
        Resource model2 = createSimpleModel("pkg", "ClassA");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());
        assertEquals(1, result.getDifferences(Difference.DifferenceType.MISSING).size());

        Difference diff = result.getDifferences(Difference.DifferenceType.MISSING).get(0);
        assertNotNull(diff.getExpectedNode());
        assertNull(diff.getActualNode());
        assertTrue(diff.getDescription().contains("ClassB"));
    }

    @Test
    void compare_extraElement_reportsDifference() {
        Resource model1 = createSimpleModel("pkg", "ClassA");
        Resource model2 = createSimpleModel("pkg", "ClassA", "ClassB");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());
        assertEquals(1, result.getDifferences(Difference.DifferenceType.EXTRA).size());

        Difference diff = result.getDifferences(Difference.DifferenceType.EXTRA).get(0);
        assertNull(diff.getExpectedNode());
        assertNotNull(diff.getActualNode());
        assertTrue(diff.getDescription().contains("ClassB"));
    }

    @Test
    void compare_attributeMismatch_includesValues() {
        Resource model1 = createModelWithAbstractClass("TestClass", true);
        Resource model2 = createModelWithAbstractClass("TestClass", false);

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());

        Difference diff = result.getDifferences(Difference.DifferenceType.ATTRIBUTE_MISMATCH).get(0);
        assertTrue(diff.getDescription().contains("abstract"));
        assertTrue(diff.getDescription().contains("true"));
        assertTrue(diff.getDescription().contains("false"));
    }

    @Test
    void compare_referenceMismatch_differentTargets() {
        Resource model1 = createModelWithSuperType("Child", "Base1");
        Resource model2 = createModelWithSuperType("Child", "Base2");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());
        // Should have MISSING for Base1, EXTRA for Base2, and REFERENCE_MISMATCH for Child
        assertTrue(result.getDifferenceCount() > 0);
    }

    @Test
    void compare_multiLayerContainment_identicalStructure() {
        Resource model1 = createDeepModel("root", "sub", "MyClass");
        Resource model2 = createDeepModel("root", "sub", "MyClass");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertTrue(result.isMatch());
    }

    @Test
    void compare_multiLayerContainment_differenceAtLeaf() {
        Resource model1 = createDeepModelWithAttribute("root", "sub", "MyClass", true);
        Resource model2 = createDeepModelWithAttribute("root", "sub", "MyClass", false);

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());
        assertEquals(1, result.getDifferences(Difference.DifferenceType.ATTRIBUTE_MISMATCH).size());

        // Path should show full containment chain
        Difference diff = result.getDifferences().get(0);
        assertTrue(diff.getPath().contains("root"));
        assertTrue(diff.getPath().contains("sub"));
        assertTrue(diff.getPath().contains("MyClass"));
    }

    @Test
    void compare_inheritance_multipleSuperTypes_orderIndependent() {
        Resource model1 = createModelWithMultiInheritance("Child", "Base1", "Base2");
        Resource model2 = createModelWithMultiInheritance("Child", "Base2", "Base1");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertTrue(result.isMatch());
    }

    @Test
    void compare_checksumOptimization_identicalLargeModel() {
        Resource resource = createLargeModel(100);

        ModelNode model = calc.calculate(resource);

        long start = System.currentTimeMillis();
        ComparisonResult result = comparator.compare(model, model);
        long duration = System.currentTimeMillis() - start;

        assertTrue(result.isMatch());
        assertTrue(duration < 500, "Identical models should short-circuit immediately");
    }

    @Test
    void compare_combined_multipleDifferenceTypes() {
        Resource model1 = createComplexModel1();
        Resource model2 = createComplexModel2();

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        assertFalse(result.isMatch());
        // Should have multiple types of differences
        assertTrue(result.getDifferenceCount() > 1);
    }

    @Test
    void compare_getDifferences_filters() {
        Resource model1 = createSimpleModel("pkg", "ClassA", "ClassB");
        Resource model2 = createSimpleModel("pkg", "ClassA", "ClassC");

        ModelNode expected = calc.calculate(model1);
        ModelNode actual = calc.calculate(model2);

        ComparisonResult result = comparator.compare(expected, actual);

        // Should have MISSING (ClassB) and EXTRA (ClassC)
        assertEquals(1, result.getDifferences(Difference.DifferenceType.MISSING).size());
        assertEquals(1, result.getDifferences(Difference.DifferenceType.EXTRA).size());
    }

    // Helper methods

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

    private Resource createModelWithMultiInheritance(String childName, String... baseNames) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("testpkg");
        pkg.setNsPrefix("testpkg");
        pkg.setNsURI("http://test/testpkg");

        for (String baseName : baseNames) {
            EClass base = EcoreFactory.eINSTANCE.createEClass();
            base.setName(baseName);
            pkg.getEClassifiers().add(base);
        }

        EClass child = EcoreFactory.eINSTANCE.createEClass();
        child.setName(childName);
        for (String baseName : baseNames) {
            child.getESuperTypes().add((EClass) pkg.getEClassifier(baseName));
        }
        pkg.getEClassifiers().add(child);

        resource.getContents().add(pkg);
        return resource;
    }

    private Resource createDeepModel(String rootName, String subName, String className) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage root = EcoreFactory.eINSTANCE.createEPackage();
        root.setName(rootName);
        root.setNsPrefix(rootName);
        root.setNsURI("http://test/" + rootName);

        EPackage sub = EcoreFactory.eINSTANCE.createEPackage();
        sub.setName(subName);
        sub.setNsPrefix(subName);
        sub.setNsURI("http://test/" + rootName + "/" + subName);
        root.getESubpackages().add(sub);

        EClass cls = EcoreFactory.eINSTANCE.createEClass();
        cls.setName(className);
        sub.getEClassifiers().add(cls);

        resource.getContents().add(root);
        return resource;
    }

    private Resource createDeepModelWithAttribute(String rootName, String subName, String className, boolean isAbstract) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage root = EcoreFactory.eINSTANCE.createEPackage();
        root.setName(rootName);
        root.setNsPrefix(rootName);
        root.setNsURI("http://test/" + rootName);

        EPackage sub = EcoreFactory.eINSTANCE.createEPackage();
        sub.setName(subName);
        sub.setNsPrefix(subName);
        sub.setNsURI("http://test/" + rootName + "/" + subName);
        root.getESubpackages().add(sub);

        EClass cls = EcoreFactory.eINSTANCE.createEClass();
        cls.setName(className);
        cls.setAbstract(isAbstract);
        sub.getEClassifiers().add(cls);

        resource.getContents().add(root);
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
        classA.setAbstract(false); // Different from model1
        pkg.getEClassifiers().add(classA);

        EClass classC = EcoreFactory.eINSTANCE.createEClass();
        classC.setName("ClassC"); // Different class from model1
        pkg.getEClassifiers().add(classC);

        resource.getContents().add(pkg);
        return resource;
    }
}
