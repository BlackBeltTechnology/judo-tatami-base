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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.TransferObjectType;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Zeta transformation trace content verification.
 * Verifies that trace entries contain expected source-to-target mappings.
 */
@Slf4j
public class Psm2AsmZetaTraceTest {

    private static final String DEMO = "demo";

    private PsmModel psmModel;
    private AsmModel asmModel;
    private TransformationTrace trace;

    @BeforeEach
    void setUp() throws Exception {
        psmModel = new Demo().fullDemo();

        // Create empty ASM model
        asmModel = buildAsmModel().build();

        // Execute Zeta transformation
        Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .modelName(DEMO)
                .build();
        trace = transformation.execute();
    }

    @Test
    void testTraceIsNonNull() {
        assertNotNull(trace, "Zeta trace should not be null");
    }

    @Test
    void testTraceHasEntries() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();
        assertNotNull(entries, "Trace entries collection should not be null");
        assertFalse(entries.isEmpty(), "Trace should have entries");
        log.info("Trace has {} entries", entries.size());
    }

    @Test
    void testCreateEntityClassMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        boolean foundEntityMapping = entries.stream()
                .anyMatch(e -> "CreateEntityClass".equals(e.getRuleName())
                        && e.getSource() instanceof EntityType
                        && e.getTarget() instanceof EClass);

        assertTrue(foundEntityMapping, "Expected CreateEntityClass trace entry mapping EntityType to EClass");
    }

    @Test
    void testCreateAttributeMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        boolean foundAttributeMapping = entries.stream()
                .anyMatch(e -> "CreateAttribute".equals(e.getRuleName())
                        && e.getSource() instanceof Attribute
                        && e.getTarget() instanceof EAttribute);

        assertTrue(foundAttributeMapping, "Expected CreateAttribute trace entry mapping Attribute to EAttribute");
    }

    @Test
    void testCreateTransferObjectClassMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        // TransferObjectType is transformed by either CreateMappedTransferObjectTypeClass or CreateUnmappedTransferObjectTypeClass
        boolean foundTransferObjectMapping = entries.stream()
                .anyMatch(e -> ("CreateMappedTransferObjectTypeClass".equals(e.getRuleName())
                        || "CreateUnmappedTransferObjectTypeClass".equals(e.getRuleName()))
                        && e.getSource() instanceof TransferObjectType
                        && e.getTarget() instanceof EClass);

        assertTrue(foundTransferObjectMapping, "Expected CreateMappedTransferObjectTypeClass or CreateUnmappedTransferObjectTypeClass trace entry mapping TransferObjectType to EClass");
    }

    @Test
    void testTraceContainsExpectedRuleNames() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        Set<String> ruleNames = entries.stream()
                .map(ElementResolutionCache.TraceEntry::getRuleName)
                .collect(Collectors.toSet());

        log.info("Found rule names in trace: {}", ruleNames);

        // Verify key rules are present
        assertTrue(ruleNames.contains("CreateEntityClass"),
                "Trace should contain CreateEntityClass rule");
        assertTrue(ruleNames.contains("CreateAttribute"),
                "Trace should contain CreateAttribute rule");
        assertTrue(ruleNames.contains("ModelToPackage"),
                "Trace should contain ModelToPackage rule");
    }

    @Test
    void testTraceJsonExport() {
        String json = trace.toJson();

        assertNotNull(json, "JSON export should not be null");
        assertFalse(json.isEmpty(), "JSON export should not be empty");

        // Verify JSON structure
        assertTrue(json.contains("traceEntries"), "JSON should contain 'traceEntries' field");
        assertTrue(json.contains("ruleName"), "JSON should contain 'ruleName' field");
        assertTrue(json.contains("source"), "JSON should contain 'source' field");
        assertTrue(json.contains("target"), "JSON should contain 'target' field");
        assertTrue(json.contains("entryCount"), "JSON should contain 'entryCount' field");
        assertTrue(json.contains("timestamp"), "JSON should contain 'timestamp' field");

        log.info("JSON trace export size: {} characters", json.length());
    }

    @Test
    void testZetaTraceJsonRoundTrip() throws Exception {
        // Build the tatami trace wrapper with zetaTrace set
        Psm2AsmTransformationTrace tatamiTrace = Psm2AsmTransformationTrace.psm2AsmTransformationTraceBuilder()
                .psmModel(psmModel)
                .asmModel(asmModel)
                .zetaTrace(trace)
                .build();

        // Save to JSON
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tatamiTrace.save(baos);
        byte[] jsonBytes = baos.toByteArray();
        assertTrue(jsonBytes.length > 0, "Saved JSON should not be empty");

        // Load from JSON via fromModelsAndTrace
        Psm2AsmTransformationTrace loaded = Psm2AsmTransformationTrace.fromModelsAndTrace(
                DEMO, psmModel, asmModel, new ByteArrayInputStream(jsonBytes));

        // Assert zetaTrace is populated
        assertTrue(loaded.isZetaTrace(), "Loaded trace should be a Zeta trace");
        assertNotNull(loaded.getZetaTrace(), "Loaded Zeta trace should not be null");

        // Assert entries match
        int originalCount = trace.getEntries().size();
        int loadedCount = loaded.getZetaTrace().getEntries().size();
        assertEquals(originalCount, loadedCount,
                "Loaded trace should have same number of entries as original");

        // Assert legacy map is populated (via getTransformationTrace conversion)
        assertFalse(loaded.getTransformationTrace().isEmpty(),
                "getTransformationTrace() should return non-empty map for loaded Zeta trace");
    }

    @Test
    void testTraceEntriesHaveValidSourceAndTarget() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        for (ElementResolutionCache.TraceEntry entry : entries) {
            assertNotNull(entry.getSource(), "Trace entry source should not be null");
            assertNotNull(entry.getTarget(), "Trace entry target should not be null");
            assertNotNull(entry.getRuleName(), "Trace entry rule name should not be null");
            assertFalse(entry.getRuleName().isEmpty(), "Trace entry rule name should not be empty");
        }
    }
}
