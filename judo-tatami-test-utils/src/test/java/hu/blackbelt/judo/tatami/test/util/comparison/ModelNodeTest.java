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

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModelNode}.
 */
class ModelNodeTest {

    @Test
    void modelNode_storesEObjectReference() {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName("TestClass");

        ModelNode node = new ModelNode(eClass, "EClass:TestClass", "TestClass");

        assertSame(eClass, node.getEObject());
        assertEquals("EClass", node.getType());
        assertEquals("TestClass", node.getIdentifier());
        assertEquals("EClass:TestClass", node.getPath());
    }

    @Test
    void modelNode_findByPath_findsNestedChild() {
        ModelNode root = new ModelNode(null, "", "");
        ModelNode pkg = new ModelNode(null, "EPackage:pkg", "pkg");
        ModelNode cls = new ModelNode(null, "EPackage:pkg/EClass:MyClass", "MyClass");

        root.addContainment("contents", pkg);
        pkg.addContainment("eClassifiers", cls);

        ModelNode found = root.findByPath("EPackage:pkg/EClass:MyClass");
        assertNotNull(found);
        assertEquals("MyClass", found.getIdentifier());
    }

    @Test
    void modelNode_findByPath_returnsNullIfNotFound() {
        ModelNode root = new ModelNode(null, "", "");

        ModelNode found = root.findByPath("NonExistent:path");
        assertNull(found);
    }

    @Test
    void modelNode_findChild_findsByRefAndId() {
        ModelNode pkg = new ModelNode(null, "EPackage:pkg", "pkg");
        ModelNode cls1 = new ModelNode(null, "EPackage:pkg/EClass:Class1", "Class1");
        ModelNode cls2 = new ModelNode(null, "EPackage:pkg/EClass:Class2", "Class2");

        pkg.addContainment("eClassifiers", cls1);
        pkg.addContainment("eClassifiers", cls2);

        ModelNode found = pkg.findChild("eClassifiers", "Class2");
        assertNotNull(found);
        assertEquals("Class2", found.getIdentifier());
    }

    @Test
    void modelNode_addContainment_groupsByRefName() {
        ModelNode pkg = new ModelNode(null, "EPackage:pkg", "pkg");
        ModelNode cls1 = new ModelNode(null, "EPackage:pkg/EClass:Class1", "Class1");
        ModelNode cls2 = new ModelNode(null, "EPackage:pkg/EClass:Class2", "Class2");
        ModelNode subPkg = new ModelNode(null, "EPackage:pkg/EPackage:sub", "sub");

        pkg.addContainment("eClassifiers", cls1);
        pkg.addContainment("eClassifiers", cls2);
        pkg.addContainment("eSubpackages", subPkg);

        assertEquals(2, pkg.getContainments().get("eClassifiers").size());
        assertEquals(1, pkg.getContainments().get("eSubpackages").size());
    }

    @Test
    void modelNode_addReference_groupsByRefName() {
        ModelNode cls = new ModelNode(null, "EPackage:pkg/EClass:Child", "Child");
        ReferenceNode ref1 = new ReferenceNode("checksum1", "EPackage:pkg/EClass:Base1", "Base1");
        ReferenceNode ref2 = new ReferenceNode("checksum2", "EPackage:pkg/EClass:Base2", "Base2");

        cls.addReference("eSuperTypes", ref1);
        cls.addReference("eSuperTypes", ref2);

        assertEquals(2, cls.getReferences().get("eSuperTypes").size());
    }

    @Test
    void modelNode_addAttribute() {
        ModelNode node = new ModelNode(null, "EClass:Test", "Test");
        node.addAttribute("abstract", true);
        node.addAttribute("name", "TestClass");

        assertEquals(true, node.getAttributes().get("abstract"));
        assertEquals("TestClass", node.getAttributes().get("name"));
    }

    @Test
    void modelNode_checksum() {
        ModelNode node = new ModelNode(null, "EClass:Test", "Test");
        assertNull(node.getChecksum());

        node.setChecksum("abc123");
        assertEquals("abc123", node.getChecksum());
    }
}
