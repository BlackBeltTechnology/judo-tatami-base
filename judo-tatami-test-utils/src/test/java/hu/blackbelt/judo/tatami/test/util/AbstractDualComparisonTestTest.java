package hu.blackbelt.judo.tatami.test.util;

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

import hu.blackbelt.judo.tatami.test.util.ExternalModelConfig;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AbstractDualComparisonTest} using a concrete in-memory subclass.
 *
 * <p>Does not test {@code @TestFactory compareExternalModels()} because that requires
 * external model files. Tests the inline convenience methods instead.
 */
class AbstractDualComparisonTestTest {

    /**
     * Concrete test subclass that transforms a "source" string into an EPackage.
     * ETL and ZETA produce identical models for equivalent inputs.
     */
    static class IdenticalDualTest extends AbstractDualComparisonTest<String, Resource> {

        @Override
        protected String parseSource(ExternalModelConfig config) {
            return config.modelName();
        }

        @Override
        protected Resource executeEtl(String source, ExternalModelConfig config) {
            return buildModel(source, "etl");
        }

        @Override
        protected Resource executeZeta(String source, ExternalModelConfig config) {
            return buildModel(source, "zeta");
        }

        @Override
        protected Resource getResource(Resource model) {
            return model;
        }

        @Override
        protected String getModuleName() {
            return "test";
        }

        private Resource buildModel(String name, String suffix) {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            pkg.setName(name);
            pkg.setNsURI("http://test/" + name);
            pkg.setNsPrefix(name);

            EClass cls = EcoreFactory.eINSTANCE.createEClass();
            cls.setName("Entity");
            pkg.getEClassifiers().add(cls);

            Resource resource = new XMIResourceImpl(URI.createURI("test://" + suffix + "/" + name));
            resource.getContents().add(pkg);
            return resource;
        }
    }

    /**
     * Subclass where ZETA produces a different model than ETL.
     */
    static class DifferentDualTest extends AbstractDualComparisonTest<String, Resource> {

        @Override
        protected String parseSource(ExternalModelConfig config) {
            return config.modelName();
        }

        @Override
        protected Resource executeEtl(String source, ExternalModelConfig config) {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            pkg.setName(source);
            pkg.setNsURI("http://test/" + source);
            pkg.setNsPrefix(source);

            EClass cls = EcoreFactory.eINSTANCE.createEClass();
            cls.setName("EntityA");
            pkg.getEClassifiers().add(cls);

            Resource resource = new XMIResourceImpl(URI.createURI("test://etl/" + source));
            resource.getContents().add(pkg);
            return resource;
        }

        @Override
        protected Resource executeZeta(String source, ExternalModelConfig config) {
            EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
            pkg.setName(source);
            pkg.setNsURI("http://test/" + source);
            pkg.setNsPrefix(source);

            // Different class name → comparison should fail
            EClass cls = EcoreFactory.eINSTANCE.createEClass();
            cls.setName("EntityB");
            pkg.getEClassifiers().add(cls);

            Resource resource = new XMIResourceImpl(URI.createURI("test://zeta/" + source));
            resource.getContents().add(pkg);
            return resource;
        }

        @Override
        protected Resource getResource(Resource model) {
            return model;
        }

        @Override
        protected String getModuleName() {
            return "test-diff";
        }
    }

    @Test
    void assertDualEquivalent_identicalModels_passes() throws Exception {
        IdenticalDualTest test = new IdenticalDualTest();
        // Should not throw — models are identical
        test.assertDualEquivalent("MyModel", "testIdentical");
    }

    @Test
    void assertDualEquivalent_differentModels_fails() {
        DifferentDualTest test = new DifferentDualTest();
        AssertionError error = assertThrows(AssertionError.class, () ->
                test.assertDualEquivalent("MyModel", "testDifferent"));
        assertTrue(error.getMessage().contains("test-diff"),
                "Error should mention module name");
        assertTrue(error.getMessage().contains("testDifferent"),
                "Error should mention test name");
    }

    @Test
    void assertDualEquivalent_withSupplier_identicalModels_passes() throws Exception {
        IdenticalDualTest test = new IdenticalDualTest();
        test.assertDualEquivalent(() -> "MyModel", "testSupplierIdentical");
    }

    @Test
    void assertDualEquivalent_withSupplier_differentModels_fails() {
        DifferentDualTest test = new DifferentDualTest();
        AssertionError error = assertThrows(AssertionError.class, () ->
                test.assertDualEquivalent(() -> "MyModel", "testSupplierDifferent"));
        assertTrue(error.getMessage().contains("test-diff"));
    }

    @Test
    void assertDualEquivalent_emptyModels_passes() throws Exception {
        AbstractDualComparisonTest<String, Resource> test = new AbstractDualComparisonTest<>() {
            @Override protected String parseSource(ExternalModelConfig config) { return ""; }
            @Override protected Resource executeEtl(String source, ExternalModelConfig config) { return emptyResource("etl"); }
            @Override protected Resource executeZeta(String source, ExternalModelConfig config) { return emptyResource("zeta"); }
            @Override protected Resource getResource(Resource model) { return model; }
            @Override protected String getModuleName() { return "test-empty"; }

            private Resource emptyResource(String suffix) {
                return new XMIResourceImpl(URI.createURI("test://" + suffix + "/empty"));
            }
        };
        // Empty resources should be equivalent
        test.assertDualEquivalent("empty", "testEmpty");
    }

    @Test
    void getModuleName_returnsSubclassValue() {
        IdenticalDualTest test = new IdenticalDualTest();
        assertEquals("test", test.getModuleName());
    }

    @Test
    void getOutputLabel_defaultIsElements() {
        IdenticalDualTest test = new IdenticalDualTest();
        assertEquals("elements", test.getOutputLabel());
    }

    @Test
    void shouldFailOnDiff_defaultIsFalse() {
        IdenticalDualTest test = new IdenticalDualTest();
        assertFalse(test.shouldFailOnDiff());
    }
}
