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
 * Tests for {@link ReferenceNode}.
 */
class ReferenceNodeTest {

    @Test
    void referenceNode_storesTargetInfo() {
        ModelNode target = new ModelNode(null, "EPackage:pkg/EClass:Base", "Base");
        target.setChecksum("abc123");

        ReferenceNode ref = new ReferenceNode(target);

        assertEquals("abc123", ref.getTargetChecksum());
        assertEquals("EPackage:pkg/EClass:Base", ref.getTargetPath());
        assertEquals("Base", ref.getTargetIdentifier());
    }

    @Test
    void referenceNode_resolvesToTargetNode() {
        // Create a tree
        ModelNode root = new ModelNode(null, "", "");
        ModelNode pkg = new ModelNode(null, "EPackage:pkg", "pkg");
        ModelNode base = new ModelNode(null, "EPackage:pkg/EClass:Base", "Base");
        base.setChecksum("checksum123");

        root.addContainment("contents", pkg);
        pkg.addContainment("eClassifiers", base);

        // Create reference to Base
        ReferenceNode ref = new ReferenceNode(base);

        // Resolve from root
        ModelNode resolved = ref.resolve(root);
        assertNotNull(resolved);
        assertSame(base, resolved);
    }

    @Test
    void referenceNode_resolveEObject_returnsSourceObject() {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName("Base");

        ModelNode root = new ModelNode(null, "", "");
        ModelNode target = new ModelNode(eClass, "EClass:Base", "Base");
        target.setChecksum("checksum");
        root.addContainment("contents", target);

        ReferenceNode ref = new ReferenceNode(target);

        assertSame(eClass, ref.resolveEObject(root));
    }

    @Test
    void referenceNode_resolve_cachesResult() {
        ModelNode root = new ModelNode(null, "", "");
        ModelNode target = new ModelNode(null, "EClass:Target", "Target");
        target.setChecksum("checksum");
        root.addContainment("contents", target);

        ReferenceNode ref = new ReferenceNode(target);

        ModelNode resolved1 = ref.resolve(root);
        ModelNode resolved2 = ref.resolve(root);

        assertSame(resolved1, resolved2);
    }

    @Test
    void referenceNode_clearResolution() {
        ModelNode root = new ModelNode(null, "", "");
        ModelNode target = new ModelNode(null, "EClass:Target", "Target");
        target.setChecksum("checksum");
        root.addContainment("contents", target);

        ReferenceNode ref = new ReferenceNode(target);
        ref.resolve(root);
        ref.clearResolution();

        // Should resolve again after clearing
        ModelNode resolved = ref.resolve(root);
        assertNotNull(resolved);
    }

    @Test
    void referenceNode_resolve_returnsNullIfNotFound() {
        ModelNode root = new ModelNode(null, "", "");
        ReferenceNode ref = new ReferenceNode("checksum", "NonExistent:path", "id");

        assertNull(ref.resolve(root));
    }

    @Test
    void referenceNode_toString() {
        ReferenceNode ref = new ReferenceNode("checksum", "EPackage:pkg/EClass:Base", "Base");

        String str = ref.toString();
        assertTrue(str.contains("Base"));
        assertTrue(str.contains("EPackage:pkg/EClass:Base"));
    }
}
