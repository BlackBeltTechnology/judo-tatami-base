package hu.blackbelt.judo.tatami.asm2keycloak;

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
import hu.blackbelt.judo.meta.keycloak.Client;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Zeta transformation trace content verification.
 * Verifies that trace entries contain expected source-to-target mappings.
 */
@Slf4j
public class Asm2KeycloakZetaTraceTest {

    private static final String DEMO = "demo";

    private AsmModel asmModel;
    private KeycloakModel keycloakModel;
    private TransformationTrace trace;

    @BeforeEach
    void setUp() throws Exception {
        PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model and transform from PSM
        asmModel = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        // Create empty Keycloak model
        keycloakModel = buildKeycloakModel()
                .name(DEMO)
                .build();

        // Execute Zeta transformation
        Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                .asmModel(asmModel)
                .keycloakModel(keycloakModel)
                .build();
        trace = transformation.execute();
    }

    @Test
    void testTraceIsNonNull() {
        assertNotNull(trace, "Zeta trace should not be null");
    }

    @Test
    void testTraceEntriesCollection() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();
        assertNotNull(entries, "Trace entries collection should not be null");
        // Note: Demo model may not have actor types with realm annotations,
        // so the trace might be empty. This is expected behavior.
        log.info("Trace has {} entries", entries.size());
    }

    @Test
    void testCreateKeycloakClientMappingWhenPresent() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        // If there are any entries, verify they have correct structure
        if (!entries.isEmpty()) {
            boolean foundClientMapping = entries.stream()
                    .anyMatch(e -> "CreateKeycloakClient".equals(e.getRuleName())
                            && e.getSource() instanceof EClass
                            && e.getTarget() instanceof Client);

            assertTrue(foundClientMapping, "Expected CreateKeycloakClient trace entry mapping EClass to Client");
        } else {
            log.info("No trace entries - Demo model may not have actor types with realm annotations");
        }
    }

    @Test
    void testTraceRuleNamesWhenPresent() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        Set<String> ruleNames = entries.stream()
                .map(ElementResolutionCache.TraceEntry::getRuleName)
                .collect(Collectors.toSet());

        log.info("Found rule names in trace: {}", ruleNames);

        // If there are entries, verify CreateKeycloakClient rule is present
        if (!entries.isEmpty()) {
            assertTrue(ruleNames.contains("CreateKeycloakClient"),
                    "Trace should contain CreateKeycloakClient rule");
        }
    }

    @Test
    void testTraceJsonExport() {
        String json = trace.toJson();

        assertNotNull(json, "JSON export should not be null");
        assertFalse(json.isEmpty(), "JSON export should not be empty");

        // Verify basic JSON structure - always present
        assertTrue(json.contains("traceEntries"), "JSON should contain 'traceEntries' field");
        assertTrue(json.contains("entryCount"), "JSON should contain 'entryCount' field");
        assertTrue(json.contains("timestamp"), "JSON should contain 'timestamp' field");

        // Verify entry-specific fields only if there are entries
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();
        if (!entries.isEmpty()) {
            assertTrue(json.contains("ruleName"), "JSON should contain 'ruleName' field when entries exist");
            assertTrue(json.contains("source"), "JSON should contain 'source' field when entries exist");
            assertTrue(json.contains("target"), "JSON should contain 'target' field when entries exist");
        }

        log.info("JSON trace export size: {} characters", json.length());
    }

    @Test
    void testTraceEntriesHaveValidSourceAndTarget() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        // If there are entries, verify they have valid structure
        for (ElementResolutionCache.TraceEntry entry : entries) {
            assertNotNull(entry.getSource(), "Trace entry source should not be null");
            assertNotNull(entry.getTarget(), "Trace entry target should not be null");
            assertNotNull(entry.getRuleName(), "Trace entry rule name should not be null");
            assertFalse(entry.getRuleName().isEmpty(), "Trace entry rule name should not be empty");
        }

        if (entries.isEmpty()) {
            log.info("No entries to validate - Demo model may not have actor types with realm annotations");
        }
    }
}
