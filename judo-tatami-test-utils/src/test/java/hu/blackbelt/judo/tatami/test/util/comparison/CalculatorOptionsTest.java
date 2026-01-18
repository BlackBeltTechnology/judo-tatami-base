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
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CalculatorOptions}.
 */
class CalculatorOptionsTest {

    @Test
    void calculatorOptions_fluentChaining() {
        CalculatorOptions options = new CalculatorOptions()
                .ignore("ecore.EClass#abstract")
                .ignore("ecore.EPackage#nsURI");

        assertEquals(2, options.getIgnoredFeatures().size());
    }

    @Test
    void calculatorOptions_ignoreAll() {
        CalculatorOptions options = new CalculatorOptions()
                .ignoreAll(Arrays.asList("ecore.EClass#abstract", "ecore.EPackage#nsURI"));

        assertEquals(2, options.getIgnoredFeatures().size());
    }

    @Test
    void calculatorOptions_isIgnored_checksAllPatterns() {
        CalculatorOptions options = new CalculatorOptions()
                .ignore("ecore.EClass#abstract")
                .ignore("*.EPackage#nsURI");

        EClass eClass = EcorePackage.Literals.ECLASS;
        EClass ePackage = EcorePackage.Literals.EPACKAGE;

        assertTrue(options.isIgnored(eClass, EcorePackage.Literals.ECLASS__ABSTRACT));
        assertTrue(options.isIgnored(ePackage, EcorePackage.Literals.EPACKAGE__NS_URI));
        assertFalse(options.isIgnored(eClass, EcorePackage.Literals.ENAMED_ELEMENT__NAME));
    }

    @Test
    void calculatorOptions_customIdentifierResolver() {
        CalculatorOptions options = new CalculatorOptions()
                .withIdentifierResolver(obj -> "custom_" + obj.eClass().getName());

        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName("TestClass");

        String identifier = options.getIdentifierResolver().apply(eClass);
        assertEquals("custom_EClass", identifier);
    }

    @Test
    void calculatorOptions_defaultIdentifierUsesName() {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName("MyTestClass");

        String identifier = CalculatorOptions.defaultIdentifier(eClass);
        assertEquals("MyTestClass", identifier);
    }

    @Test
    void calculatorOptions_defaultIdentifierFallsBackToTypeIndex() {
        // Create a package to contain the class
        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("testpkg");

        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        // Don't set a name - should fall back
        pkg.getEClassifiers().add(eClass);

        String identifier = CalculatorOptions.defaultIdentifier(eClass);
        assertEquals("EClass_0", identifier);
    }

    @Test
    void calculatorOptions_immutableIgnoredFeatures() {
        CalculatorOptions options = new CalculatorOptions()
                .ignore("ecore.EClass#abstract");

        assertThrows(UnsupportedOperationException.class, () ->
                options.getIgnoredFeatures().add(new FeaturePattern("ecore.EClass#name")));
    }
}
