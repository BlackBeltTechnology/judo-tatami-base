package hu.blackbelt.judo.tatami.psm2asm;

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

import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmHelper;
import hu.blackbelt.model.northwind.Demo;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Psm2AsmHelper.getId() to ensure XMI fragment ID parity with ETL.
 */
public class Psm2AsmHelperTest {

    private PsmModel psmModel;

    @BeforeEach
    void setUp() throws Exception {
        psmModel = new Demo().fullDemo();
    }

    @Test
    void testGetIdReturnsXmiFragmentForNamespaceElement() {
        // Find any EntityType (which is a NamespaceElement)
        EntityType entityType = null;
        for (Resource r : psmModel.getResourceSet().getResources()) {
            var it = r.getAllContents();
            while (it.hasNext()) {
                var obj = it.next();
                if (obj instanceof EntityType et) {
                    entityType = et;
                    break;
                }
            }
            if (entityType != null) break;
        }
        assertNotNull(entityType, "Demo model should contain at least one EntityType");

        // Clear the static cache to ensure fresh ID computation
        Psm2AsmHelper.clearCaches();

        // EntityType is a NamespaceElement
        assertTrue(entityType instanceof NamespaceElement, "EntityType should be a NamespaceElement");

        // Get the XMI fragment that ETL would use (eResource.getId(self))
        Resource resource = entityType.eResource();
        assertNotNull(resource, "EntityType should be in a resource");
        String expectedXmiFragment = resource.getURIFragment(entityType);
        assertNotNull(expectedXmiFragment, "XMI fragment should not be null");
        assertFalse(expectedXmiFragment.startsWith("/"), "XMI fragment should not be a positional path");

        // Psm2AsmHelper.getId() should return the same XMI fragment
        String actualId = Psm2AsmHelper.getId(entityType);

        assertEquals(expectedXmiFragment, actualId,
                "getId() should return XMI fragment for NamespaceElement, not qualified name. " +
                "Expected XMI fragment '" + expectedXmiFragment + "' but got '" + actualId + "'");
    }
}
