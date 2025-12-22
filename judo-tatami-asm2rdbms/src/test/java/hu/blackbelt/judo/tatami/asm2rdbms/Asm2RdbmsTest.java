package hu.blackbelt.judo.tatami.asm2rdbms;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.epsilon.common.util.UriUtil;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;

import java.io.File;
import java.util.List;
import java.util.Map;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.SaveArguments.rdbmsSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsTransformationTrace.fromModelsAndTrace;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.fail;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;

@Slf4j
public class Asm2RdbmsTest {
    public static final String NORTHWIND = "demo";
    public static final String NORTHWIND_RDBMS_MODEL = "northwind-rdbms.model";
    public static final String NORTHWIND_ASM_2_RDBMS_MODEL = "northwind-asm2rdbms.model";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    AsmModel asmModel;
    RdbmsModel rdbmsModel;

    @BeforeEach
    public void setUp() throws Exception {
        PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        asmModel = AsmModel.buildAsmModel()
                .build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        // Create empty RDBMS model
        rdbmsModel = RdbmsModel.buildRdbmsModel()
                .build();

        // The RDBMS model resourceset have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
    }

    @ParameterizedTest(name = "testAsm2RdbmsTransformation with {0}")
    @EnumSource(TransformationType.class)
    public void testAsm2RdbmsTransformation(TransformationType transformationType) throws Exception {

        Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace;
        
        if (transformationType == TransformationType.ZETA) {
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

            log.info("Running Zeta transformation");
            Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .dialect("hsqldb")
                    .build();
            transformation.execute();
            // For Zeta transformation, trace is not available
            asm2RdbmsTransformationTrace = null;
        } else {
            log.info("Running ETL transformation");
            asm2RdbmsTransformationTrace = executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .createTrace(true)
                    .dialect("hsqldb"));
        }

        // Trace operations only for ETL transformation
        if (asm2RdbmsTransformationTrace != null) {
            // Saving trace map
            asm2RdbmsTransformationTrace.save(new File(TARGET_TEST_CLASSES, NORTHWIND_ASM_2_RDBMS_MODEL));

            // Loading trace map
            Asm2RdbmsTransformationTrace asm2RdbmsTransformationTraceLoaded =
                    fromModelsAndTrace(NORTHWIND, asmModel, rdbmsModel, new File(TARGET_TEST_CLASSES, NORTHWIND_ASM_2_RDBMS_MODEL));

            // Resolve serialized URI's as EObject map
            Map<EObject, List<EObject>> resolvedTrace = asm2RdbmsTransformationTraceLoaded.getTransformationTrace();

            // Printing trace
            for (EObject e : resolvedTrace.keySet()) {
                for (EObject t : resolvedTrace.get(e)) {
                    log.trace(e.toString() + " -> " + t.toString());
                }
            }
        }

        rdbmsModel.saveRdbmsModel(rdbmsSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_RDBMS_MODEL)));
    }

    /**
     * Test that ETL and Zeta transformations produce equivalent RDBMS models.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Prepare ASM model (same for both transformations)
        PsmModel psmModel = new Demo().fullDemo();
        AsmModel sharedAsmModel = AsmModel.buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(sharedAsmModel));

        // Run ETL transformation
        log.info("Running ETL transformation for equivalence test...");
        RdbmsModel etlResult = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(etlResult.getResourceSet());
        registerRdbmsDataTypesMetamodel(etlResult.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(etlResult.getResourceSet());
        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(sharedAsmModel)
                .rdbmsModel(etlResult)
                .createTrace(false)
                .dialect("hsqldb"));

        // Run Zeta transformation
        log.info("Running Zeta transformation for equivalence test...");
        RdbmsModel zetaResult = RdbmsModel.buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(zetaResult.getResourceSet());
        registerRdbmsDataTypesMetamodel(zetaResult.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(zetaResult.getResourceSet());
        
        // Load mapping model for Zeta transformation
        String dialect = "hsqldb";
        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms-zeta"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        zetaResult.getResource().getContents().addAll(mappingModel.getResource().getContents());

        Asm2RdbmsZetaTransformation zetaTransformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(sharedAsmModel)
                .rdbmsModel(zetaResult)
                .dialect("hsqldb")
                .build();
        zetaTransformation.execute();

        // Compare models
        log.info("Comparing ETL and Zeta output models...");
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models");
        } else {
            log.warn("Models have differences:\n{}", result.getSummary());
            fail("ETL and Zeta models are not equivalent:\n" + result.getDetailedReport());
        }
    }
}
