package hu.blackbelt.judo.tatami.test.util.trace;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2025 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DualTraceComparator}.
 *
 * <p>Uses minimal EMF EObjects (EClass instances from a dynamic EPackage)
 * with XMI IDs assigned via XMLResource to test trace alignment logic.
 */
class DualTraceComparatorTest {

    private ResourceSet resourceSet;
    private Resource resource;
    private EPackage testPackage;
    private EClass testClass;

    @BeforeEach
    void setUp() {
        // Create a dynamic EPackage for test EObjects
        testPackage = EcoreFactory.eINSTANCE.createEPackage();
        testPackage.setName("testPkg");
        testPackage.setNsPrefix("test");
        testPackage.setNsURI("http://test/dual-trace");

        testClass = EcoreFactory.eINSTANCE.createEClass();
        testClass.setName("TestElement");
        testPackage.getEClassifiers().add(testClass);

        // Set up ResourceSet with XMI support (use protocol map for urn: scheme)
        resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap()
                .put("urn", new XMIResourceFactoryImpl());
        resource = resourceSet.createResource(URI.createURI("urn:test-trace"));
    }

    @Test
    @DisplayName("Empty traces produce no discrepancies")
    void testEmptyTraces() {
        Map<EObject, List<EObject>> etlTrace = new LinkedHashMap<>();
        Map<EObject, List<EObject>> zetaTrace = new LinkedHashMap<>();

        List<TraceDiscrepancy> result = DualTraceComparator.compare(etlTrace, zetaTrace);

        assertTrue(result.isEmpty(), "Empty traces should produce no discrepancies");
    }

    @Test
    @DisplayName("Identical traces produce no discrepancies")
    void testIdenticalTraces() {
        EObject source = createEObjectWithId("src-1");
        EObject etlTarget = createEObjectWithId("etl-t-1");
        EObject zetaTarget = createEObjectWithId("zeta-t-1");

        Map<EObject, List<EObject>> etlTrace = new LinkedHashMap<>();
        etlTrace.put(source, List.of(etlTarget));

        Map<EObject, List<EObject>> zetaTrace = new LinkedHashMap<>();
        zetaTrace.put(source, List.of(zetaTarget));

        List<TraceDiscrepancy> result = DualTraceComparator.compare(etlTrace, zetaTrace);

        assertTrue(result.isEmpty(),
                "Identical traces (same source + same target type) should produce no discrepancies");
    }

    @Test
    @DisplayName("MISSING_IN_ZETA detected when ETL has target that Zeta doesn't")
    void testMissingInZeta() {
        EObject source = createEObjectWithId("src-1");
        EObject etlTarget = createEObjectWithId("etl-t-1");

        Map<EObject, List<EObject>> etlTrace = new LinkedHashMap<>();
        etlTrace.put(source, List.of(etlTarget));

        Map<EObject, List<EObject>> zetaTrace = new LinkedHashMap<>();
        // Zeta has no entry for this source

        List<TraceDiscrepancy> result = DualTraceComparator.compare(etlTrace, zetaTrace);

        assertEquals(1, result.size(), "Should detect exactly 1 discrepancy");
        TraceDiscrepancy disc = result.get(0);
        assertEquals(TraceDiscrepancy.Type.MISSING_IN_ZETA, disc.type());
        assertEquals("src-1", disc.sourceId());
        assertNotNull(disc.etlTargetId());
        assertNull(disc.zetaTargetId());
    }

    @Test
    @DisplayName("MISSING_IN_ETL detected when Zeta has target that ETL doesn't")
    void testMissingInEtl() {
        EObject source = createEObjectWithId("src-1");
        EObject zetaTarget = createEObjectWithId("zeta-t-1");

        Map<EObject, List<EObject>> etlTrace = new LinkedHashMap<>();
        // ETL has no entry for this source

        Map<EObject, List<EObject>> zetaTrace = new LinkedHashMap<>();
        zetaTrace.put(source, List.of(zetaTarget));

        List<TraceDiscrepancy> result = DualTraceComparator.compare(etlTrace, zetaTrace);

        assertEquals(1, result.size(), "Should detect exactly 1 discrepancy");
        TraceDiscrepancy disc = result.get(0);
        assertEquals(TraceDiscrepancy.Type.MISSING_IN_ETL, disc.type());
        assertEquals("src-1", disc.sourceId());
        assertNull(disc.etlTargetId());
        assertNotNull(disc.zetaTargetId());
    }

    @Test
    @DisplayName("Multiple discrepancies detected across different sources")
    void testMultipleDiscrepancies() {
        EObject source1 = createEObjectWithId("src-1");
        EObject source2 = createEObjectWithId("src-2");
        EObject etlTarget1 = createEObjectWithId("etl-t-1");
        EObject zetaTarget2 = createEObjectWithId("zeta-t-2");

        Map<EObject, List<EObject>> etlTrace = new LinkedHashMap<>();
        etlTrace.put(source1, List.of(etlTarget1));
        // source2 not in ETL

        Map<EObject, List<EObject>> zetaTrace = new LinkedHashMap<>();
        // source1 not in Zeta
        zetaTrace.put(source2, List.of(zetaTarget2));

        List<TraceDiscrepancy> result = DualTraceComparator.compare(etlTrace, zetaTrace);

        assertEquals(2, result.size(), "Should detect 2 discrepancies");

        long missingInZeta = result.stream()
                .filter(d -> d.type() == TraceDiscrepancy.Type.MISSING_IN_ZETA).count();
        long missingInEtl = result.stream()
                .filter(d -> d.type() == TraceDiscrepancy.Type.MISSING_IN_ETL).count();

        assertEquals(1, missingInZeta, "Should have 1 MISSING_IN_ZETA");
        assertEquals(1, missingInEtl, "Should have 1 MISSING_IN_ETL");
    }

    @Test
    @DisplayName("describe() produces human-readable output")
    void testDescribe() {
        TraceDiscrepancy disc = new TraceDiscrepancy(
                "src-1", "etl-t-1", null, "CreateTable", null,
                TraceDiscrepancy.Type.MISSING_IN_ZETA);

        String description = disc.describe();

        assertTrue(description.contains("MISSING_IN_ZETA"));
        assertTrue(description.contains("src-1"));
        assertTrue(description.contains("etl-t-1"));
    }

    /**
     * Creates a dynamic EObject instance with an XMI ID assigned.
     * All objects use the same EClass (TestElement) so alignment is by (sourceId, targetTypeName).
     */
    private EObject createEObjectWithId(String xmiId) {
        EObject obj = testPackage.getEFactoryInstance().create(testClass);
        resource.getContents().add(obj);
        ((org.eclipse.emf.ecore.xmi.XMLResource) resource).setID(obj, xmiId);
        return obj;
    }
}
