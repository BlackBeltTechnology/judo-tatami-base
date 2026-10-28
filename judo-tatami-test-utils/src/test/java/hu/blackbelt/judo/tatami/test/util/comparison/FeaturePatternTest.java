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
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FeaturePattern}.
 */
class FeaturePatternTest {

    @Test
    void featurePattern_parsesValidPattern() {
        FeaturePattern pattern = new FeaturePattern("ecore.EClass#abstract");

        assertEquals("ecore", pattern.getPackagePattern());
        assertEquals("EClass", pattern.getClassPattern());
        assertEquals("abstract", pattern.getFeaturePattern());
    }

    @Test
    void featurePattern_rejectsInvalidPattern_noHash() {
        assertThrows(IllegalArgumentException.class, () -> new FeaturePattern("ecore.EClass.abstract"));
    }

    @Test
    void featurePattern_rejectsInvalidPattern_noDot() {
        assertThrows(IllegalArgumentException.class, () -> new FeaturePattern("EClass#abstract"));
    }

    @Test
    void featurePattern_rejectsInvalidPattern_emptyFeature() {
        assertThrows(IllegalArgumentException.class, () -> new FeaturePattern("ecore.EClass#"));
    }

    @Test
    void featurePattern_matchesExactPattern() {
        FeaturePattern pattern = new FeaturePattern("ecore.EClass#abstract");
        EClass eClass = EcorePackage.Literals.ECLASS;

        assertTrue(pattern.matches(eClass, EcorePackage.Literals.ECLASS__ABSTRACT));
        assertFalse(pattern.matches(eClass, EcorePackage.Literals.ECLASS__INTERFACE));
    }

    @Test
    void featurePattern_wildcardPackage_matchesAnyPackage() {
        FeaturePattern pattern = new FeaturePattern("*.EClass#abstract");
        EClass eClass = EcorePackage.Literals.ECLASS;

        assertTrue(pattern.matches(eClass, EcorePackage.Literals.ECLASS__ABSTRACT));
    }

    @Test
    void featurePattern_wildcardClass_matchesAnyClass() {
        FeaturePattern pattern = new FeaturePattern("ecore.*#name");
        EClass eClass = EcorePackage.Literals.ECLASS;
        EClass ePackage = EcorePackage.Literals.EPACKAGE;

        assertTrue(pattern.matches(eClass, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
        assertTrue(pattern.matches(ePackage, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
    }

    @Test
    void featurePattern_globalWildcard_matchesAll() {
        FeaturePattern pattern = new FeaturePattern("*.*#name");
        EClass eClass = EcorePackage.Literals.ECLASS;

        assertTrue(pattern.matches(eClass, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
    }

    @Test
    void featurePattern_equalsAndHashCode() {
        FeaturePattern p1 = new FeaturePattern("ecore.EClass#abstract");
        FeaturePattern p2 = new FeaturePattern("ecore.EClass#abstract");
        FeaturePattern p3 = new FeaturePattern("ecore.EClass#name");

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }

    @Test
    void featurePattern_toString() {
        FeaturePattern pattern = new FeaturePattern("ecore.EClass#abstract");
        assertEquals("ecore.EClass#abstract", pattern.toString());
    }
}
