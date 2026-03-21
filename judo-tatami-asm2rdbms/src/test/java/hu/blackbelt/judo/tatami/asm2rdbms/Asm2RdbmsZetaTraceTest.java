package hu.blackbelt.judo.tatami.asm2rdbms;

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
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbms.RdbmsForeignKey;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Zeta transformation trace content verification.
 * Verifies that trace entries contain expected source-to-target mappings.
 */
@Slf4j
public class Asm2RdbmsZetaTraceTest {

    private AsmModel asmModel;
    private RdbmsModel rdbmsModel;
    private TransformationTrace trace;

    @BeforeEach
    void setUp() throws Exception {
        PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        asmModel = AsmModel.buildAsmModel().build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        // Create empty RDBMS model
        rdbmsModel = RdbmsModel.buildRdbmsModel().build();

        // Register mapping metamodels
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        // Load mapping model for Zeta transformation
        String dialect = "hsqldb";
        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());

        // Execute Zeta transformation
        Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .dialect(dialect)
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
    void testEClassToRdbmsTableMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        boolean foundTableMapping = entries.stream()
                .anyMatch(e -> Asm2RdbmsRuleNames.ECLASS_TO_RDBMS_TABLE.equals(e.getRuleName())
                        && e.getSource() instanceof EClass
                        && e.getTarget() instanceof RdbmsTable);

        assertTrue(foundTableMapping, "Expected " + Asm2RdbmsRuleNames.ECLASS_TO_RDBMS_TABLE + " trace entry mapping EClass to RdbmsTable");
    }

    @Test
    void testEAttributeToFieldMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        boolean foundFieldMapping = entries.stream()
                .anyMatch(e -> Asm2RdbmsRuleNames.EATTRIBUTE_TO_TABLE_VALUE_FIELD.equals(e.getRuleName())
                        && e.getSource() instanceof EAttribute
                        && e.getTarget() instanceof RdbmsField);

        assertTrue(foundFieldMapping, "Expected " + Asm2RdbmsRuleNames.EATTRIBUTE_TO_TABLE_VALUE_FIELD + " trace entry mapping EAttribute to RdbmsField");
    }

    @Test
    void testEReferenceToForeignKeyMapping() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        boolean foundForeignKeyMapping = entries.stream()
                .anyMatch(e -> Asm2RdbmsRuleNames.EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY.equals(e.getRuleName())
                        && e.getSource() instanceof EReference
                        && e.getTarget() instanceof RdbmsForeignKey);

        assertTrue(foundForeignKeyMapping, "Expected " + Asm2RdbmsRuleNames.EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY + " trace entry mapping EReference to RdbmsForeignKey");
    }

    @Test
    void testTraceContainsExpectedRuleNames() {
        Collection<ElementResolutionCache.TraceEntry> entries = trace.getEntries();

        Set<String> ruleNames = entries.stream()
                .map(ElementResolutionCache.TraceEntry::getRuleName)
                .collect(Collectors.toSet());

        log.info("Found rule names in trace: {}", ruleNames);

        // Verify key rules are present
        assertTrue(ruleNames.contains(Asm2RdbmsRuleNames.ECLASS_TO_RDBMS_TABLE),
                "Trace should contain " + Asm2RdbmsRuleNames.ECLASS_TO_RDBMS_TABLE + " rule");
        assertTrue(ruleNames.contains(Asm2RdbmsRuleNames.EATTRIBUTE_TO_TABLE_VALUE_FIELD),
                "Trace should contain " + Asm2RdbmsRuleNames.EATTRIBUTE_TO_TABLE_VALUE_FIELD + " rule");
        assertTrue(ruleNames.contains(Asm2RdbmsRuleNames.ECLASS_TO_TABLE_ID_FIELD),
                "Trace should contain " + Asm2RdbmsRuleNames.ECLASS_TO_TABLE_ID_FIELD + " rule");
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
        Asm2RdbmsTransformationTrace tatamiTrace = Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .zetaTrace(trace)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tatamiTrace.save(baos);
        byte[] jsonBytes = baos.toByteArray();
        assertTrue(jsonBytes.length > 0, "Saved JSON should not be empty");

        Asm2RdbmsTransformationTrace loaded = Asm2RdbmsTransformationTrace.fromModelsAndTrace(
                asmModel.getName(), asmModel, rdbmsModel, new ByteArrayInputStream(jsonBytes));

        assertTrue(loaded.isZetaTrace(), "Loaded trace should be a Zeta trace");
        assertNotNull(loaded.getZetaTrace(), "Loaded Zeta trace should not be null");

        int originalCount = trace.getEntries().size();
        int loadedCount = loaded.getZetaTrace().getEntries().size();
        assertEquals(originalCount, loadedCount,
                "Loaded trace should have same number of entries as original");

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
