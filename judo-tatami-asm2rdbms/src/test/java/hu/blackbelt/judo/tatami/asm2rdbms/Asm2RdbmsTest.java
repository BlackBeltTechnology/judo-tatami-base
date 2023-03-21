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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testAsm2RdbmsTransformation() throws Exception {

        Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace =
                executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                        .asmModel(asmModel)
                        .rdbmsModel(rdbmsModel)
                        .createTrace(true)
                        .dialect("hsqldb"));

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

        rdbmsModel.saveRdbmsModel(rdbmsSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_RDBMS_MODEL)));
    }
}
