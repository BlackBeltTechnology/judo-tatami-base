package hu.blackbelt.judo.tatami.psm2measure;

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

import hu.blackbelt.judo.meta.measure.Measure;
import hu.blackbelt.judo.meta.measure.Unit;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureZetaTransformation;
import hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Zeta transformation trace content verification.
 * Verifies that trace entries contain expected source-to-target mappings.
 */
@Slf4j
public class Psm2MeasureZetaTraceTest {

    private static final String DEMO = "demo";

    private PsmModel psmModel;
    private MeasureModel measureModel;
    private TransformationTrace trace;

    @BeforeEach
    void setUp() throws Exception {
        psmModel = new Demo().fullDemo();

        // Create empty MEASURE model
        measureModel = buildMeasureModel()
                .name(DEMO)
                .build();

        // Execute Zeta transformation
        Psm2MeasureZetaTransformation transformation = Psm2MeasureZetaTransformation.builder()
                .psmModel(psmModel)
                .measureModel(measureModel)
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
    void testCreateMeasureMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        // Look for BaseMeasure or DerivedMeasure rules
        boolean foundMeasureMapping = entries.stream()
                .anyMatch(e -> ("BaseMeasure".equals(e.getRuleName())
                        || "DerivedMeasure".equals(e.getRuleName()))
                        && e.getSource() instanceof hu.blackbelt.judo.meta.psm.measure.Measure
                        && e.getTarget() instanceof Measure);

        assertTrue(foundMeasureMapping, "Expected BaseMeasure or DerivedMeasure trace entry mapping PSM Measure to Measure");
    }

    @Test
    void testCreateUnitMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        // Look for Unit or DurationUnit rules
        boolean foundUnitMapping = entries.stream()
                .anyMatch(e -> ("Unit".equals(e.getRuleName())
                        || "DurationUnit".equals(e.getRuleName()))
                        && e.getSource() instanceof hu.blackbelt.judo.meta.psm.measure.Unit
                        && e.getTarget() instanceof Unit);

        assertTrue(foundUnitMapping, "Expected Unit or DurationUnit trace entry mapping PSM Unit to Unit");
    }

    @Test
    void testTraceContainsExpectedRuleNames() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        Set<String> ruleNames = entries.stream()
                .map(ElementResolutionCache.TraceEntry::getRuleName)
                .collect(Collectors.toSet());

        log.info("Found rule names in trace: {}", ruleNames);

        // Verify at least some measure-related rules are present
        boolean hasMeasureRules = ruleNames.stream()
                .anyMatch(name -> name.contains("Measure") || name.contains("Unit"));

        assertTrue(hasMeasureRules, "Trace should contain measure-related rules");
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
