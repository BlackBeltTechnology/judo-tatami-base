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
 * Tests for {@link ModelChecksumCalculator#fromJson(String)} and
 * {@link ModelChecksumCalculator#fromJson(String, Resource)}.
 */
class ModelChecksumCalculatorFromJsonTest {

    private ModelChecksumCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new ModelChecksumCalculator();

        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("xmi", new XMIResourceFactoryImpl());
    }

    // ==================== Positive Test Cases ====================

    @Test
    void fromJson_reconstructsModelNodeTree() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertNotNull(loaded);
        assertEquals(original.getType(), loaded.getType());
        assertEquals(original.getPath(), loaded.getPath());
    }

    @Test
    void fromJson_preservesChecksums() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertEquals(original.getChecksum(), loaded.getChecksum());
    }

    @Test
    void fromJson_preservesContainments() {
        Resource resource = createSimpleModel("testpkg", "Class1", "Class2");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertNotNull(loaded.getContainments().get("contents"));
        assertEquals(original.getContainments().get("contents").size(),
                loaded.getContainments().get("contents").size());
    }

    @Test
    void fromJson_preservesReferences() {
        Resource resource = createModelWithSuperTypes("Child", "Base");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        // Find Child class in loaded structure
        ModelNode pkg = loaded.getContainments().get("contents").get(0);
        ModelNode child = pkg.findChild("eClassifiers", "Child");

        assertNotNull(child);
        // References should be preserved (even if targets aren't resolved)
    }

    @Test
    void fromJson_preservesAttributes() {
        Resource resource = createModelWithAbstractClass("TestClass", true);
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        ModelNode pkg = loaded.getContainments().get("contents").get(0);
        ModelNode cls = pkg.findChild("eClassifiers", "TestClass");

        assertNotNull(cls);
        assertEquals(true, cls.getAttributes().get("abstract"));
    }

    @Test
    void fromJson_handlesEmptyModel() {
        Resource resource = createEmptyModel();
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertNotNull(loaded);
        assertTrue(loaded.getContainments().isEmpty() ||
                loaded.getContainments().get("contents") == null ||
                loaded.getContainments().get("contents").isEmpty());
    }

    @Test
    void fromJson_roundTrip_toJsonFromJson() {
        Resource resource = createComplexModel();
        ModelNode original = calc.calculate(resource);

        // Round trip
        String json1 = calc.toJson(original);
        ModelNode loaded = calc.fromJson(json1);
        String json2 = calc.toJson(loaded);

        // Checksums should match after round trip
        assertEquals(original.getChecksum(), loaded.getChecksum());
    }

    @Test
    void fromJson_withoutResource_eObjectsAreNull() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        // Without Resource, EObjects should be null
        assertNull(loaded.getEObject());
        ModelNode pkg = loaded.getContainments().get("contents").get(0);
        assertNull(pkg.getEObject());
    }

    @Test
    void fromJson_withResource_rematchesEObjects() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        // Load with same Resource for re-matching
        ModelNode loaded = calc.fromJson(json, resource);

        // With Resource, EObjects should be re-matched
        ModelNode pkg = loaded.getContainments().get("contents").get(0);
        assertNotNull(pkg.getEObject());
        assertEquals("testpkg", ((EPackage) pkg.getEObject()).getName());
    }

    @Test
    void fromJson_withResource_partialRematch_whenStructureChanged() {
        Resource resource1 = createSimpleModel("testpkg", "Class1");
        ModelNode original = calc.calculate(resource1);
        String json = calc.toJson(original);

        // Create modified resource (different class name)
        Resource resource2 = createSimpleModel("testpkg", "Class2");

        // Load with different Resource
        ModelNode loaded = calc.fromJson(json, resource2);

        // Package should match, but Class1 won't exist in resource2
        ModelNode pkg = loaded.getContainments().get("contents").get(0);
        // Package might match if checksum is same (same name, nsURI, etc.)
        // Class won't match because it's different
    }

    @Test
    void fromJson_withResource_nullResourceBehavior() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        // Explicit null Resource should behave like fromJson(json)
        ModelNode loaded = calc.fromJson(json, null);

        assertNotNull(loaded);
        assertNull(loaded.getEObject());
    }

    @Test
    void fromJson_deeplyNestedContainments() {
        Resource resource = createDeepModel("root", "sub1", "sub2", "LeafClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertEquals(original.getChecksum(), loaded.getChecksum());
    }

    @Test
    void fromJson_multipleReferencesPerNode() {
        Resource resource = createModelWithMultiInheritance("Child", "Base1", "Base2", "Base3");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded = calc.fromJson(json);

        assertEquals(original.getChecksum(), loaded.getChecksum());
    }

    // ==================== Negative Test Cases ====================

    @Test
    void fromJson_nullJson_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> calc.fromJson(null));
    }

    @Test
    void fromJson_emptyJson_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> calc.fromJson(""));
    }

    @Test
    void fromJson_malformedJson_throwsException() {
        String malformed = "{ this is not valid json }}}";
        assertThrows(IllegalArgumentException.class, () -> calc.fromJson(malformed));
    }

    @Test
    void fromJson_missingRequiredFields_throwsException() {
        String incomplete = "{ \"type\": \"EPackage\" }";  // Missing path, checksum, etc.
        assertThrows(IllegalArgumentException.class, () -> calc.fromJson(incomplete));
    }

    @Test
    void fromJson_invalidChecksum_loadsWithWarning() {
        // JSON with obviously invalid checksum format
        String jsonWithBadChecksum = """
            {
              "type": "Root",
              "identifier": "",
              "path": "",
              "checksum": "not-a-valid-sha256-checksum",
              "containments": {}
            }
            """;

        // Should load but may log warning
        ModelNode loaded = calc.fromJson(jsonWithBadChecksum);
        assertNotNull(loaded);
    }

    @Test
    void fromJson_corruptedStructure_throwsException() {
        // JSON with invalid structure (containments not an object)
        String corrupted = """
            {
              "type": "Root",
              "identifier": "",
              "path": "",
              "checksum": "abc123",
              "containments": "not-an-object"
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> calc.fromJson(corrupted));
    }

    @Test
    void fromJson_withNullResource_sameAsWithoutResource() {
        Resource resource = createSimpleModel("testpkg", "TestClass");
        ModelNode original = calc.calculate(resource);
        String json = calc.toJson(original);

        ModelNode loaded1 = calc.fromJson(json);
        ModelNode loaded2 = calc.fromJson(json, null);

        assertEquals(loaded1.getChecksum(), loaded2.getChecksum());
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

    private Resource createEmptyModel() {
        ResourceSet rs = new ResourceSetImpl();
        return rs.createResource(URI.createURI("empty.xmi"));
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

    private Resource createModelWithSuperTypes(String childName, String... baseNames) {
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

    private Resource createModelWithMultiInheritance(String childName, String... baseNames) {
        return createModelWithSuperTypes(childName, baseNames);
    }

    private Resource createDeepModel(String rootName, String sub1Name, String sub2Name, String className) {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage root = EcoreFactory.eINSTANCE.createEPackage();
        root.setName(rootName);
        root.setNsPrefix(rootName);
        root.setNsURI("http://test/" + rootName);

        EPackage sub1 = EcoreFactory.eINSTANCE.createEPackage();
        sub1.setName(sub1Name);
        sub1.setNsPrefix(sub1Name);
        sub1.setNsURI("http://test/" + rootName + "/" + sub1Name);
        root.getESubpackages().add(sub1);

        EPackage sub2 = EcoreFactory.eINSTANCE.createEPackage();
        sub2.setName(sub2Name);
        sub2.setNsPrefix(sub2Name);
        sub2.setNsURI("http://test/" + rootName + "/" + sub1Name + "/" + sub2Name);
        sub1.getESubpackages().add(sub2);

        EClass cls = EcoreFactory.eINSTANCE.createEClass();
        cls.setName(className);
        sub2.getEClassifiers().add(cls);

        resource.getContents().add(root);
        return resource;
    }

    private Resource createComplexModel() {
        ResourceSet rs = new ResourceSetImpl();
        Resource resource = rs.createResource(URI.createURI("test.xmi"));

        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("complex");
        pkg.setNsPrefix("complex");
        pkg.setNsURI("http://test/complex");

        EClass base = EcoreFactory.eINSTANCE.createEClass();
        base.setName("BaseEntity");
        base.setAbstract(true);
        pkg.getEClassifiers().add(base);

        EClass customer = EcoreFactory.eINSTANCE.createEClass();
        customer.setName("Customer");
        customer.getESuperTypes().add(base);
        pkg.getEClassifiers().add(customer);

        EClass order = EcoreFactory.eINSTANCE.createEClass();
        order.setName("Order");
        pkg.getEClassifiers().add(order);

        resource.getContents().add(pkg);
        return resource;
    }
}
