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

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel.AsmValidationException;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.RdbmsValidationException;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.SaveArguments.rdbmsSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class Asm2RdbmsMappingTestBase {
    protected static final String ASM_MODEL_NAME = "TestAsmModel";
    protected static final String RDBMS_MODEL_NAME = "TestRdbmsModel";

    protected static final String TARGET_TEST_CLASSES = "target/test-classes";

    protected static final String ENTITY_ANNOTATION = "entity";
    protected static final String VALUE_ANNOTATION = "true";

    protected Log logger;

    protected AsmModel asmModel;
    protected RdbmsModel rdbmsModel;

    protected RdbmsUtils rdbmsUtils;

    @BeforeEach
    protected void setUp() {
        logger = new BufferedSlf4jLogger(log);

        logger.debug("Building models");
        asmModel = buildAsmModel().name(ASM_MODEL_NAME).build();
        rdbmsModel = buildRdbmsModel().name(RDBMS_MODEL_NAME).build();

        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());
    }

    @AfterEach
    protected void tearDown() throws Exception {
        logger.close();
    }

    protected void executeTransformation(final String testName) {
        Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace = null;
        try {
            logger.debug("Executing asm2rdbms transformation");
            asm2RdbmsTransformationTrace = executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .createTrace(true)
                    .dialect("hsqldb"));

        } catch (Exception e) {
            fail("Unable to execute transformation", e);
        }

        logger.debug("Extracting models from transformation trace");
        rdbmsModel = asm2RdbmsTransformationTrace.getRdbmsModel();

        logger.debug("Initializing rdbmsUtils");
        rdbmsUtils = new RdbmsUtils(rdbmsModel.getResourceSet());

        try {
            logger.debug("Saving models and transformation trace");
            asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                    .file(new File(TARGET_TEST_CLASSES, format("%s-%s.model", testName, ASM_MODEL_NAME))));
            rdbmsModel.saveRdbmsModel(rdbmsSaveArgumentsBuilder()
                    .file(new File(TARGET_TEST_CLASSES, format("%s-%s.model", testName, RDBMS_MODEL_NAME))));
            asm2RdbmsTransformationTrace.save(new File(TARGET_TEST_CLASSES, format("%s-Asm2RdbmsTransformationTrace.model", testName)));
        } catch (AsmValidationException e) {
            fail("AsmModel is not valid", e);
        } catch (RdbmsValidationException e) {
            fail("RdbmsModel is not valid", e);
        } catch (IOException e) {
            logger.warn("Model(s) and/or transformation trace cannot be saved", e);
        }
    }

    protected void assertTables(final Set<String> expected) {
        rdbmsUtils.getRdbmsTables()
                .orElseThrow(() -> new RuntimeException("Tables not found"))
                .forEach(o -> {
                    assertTrue(expected.contains(o.getName()), o.getName() + " not found");
                    expected.remove(o.getName());
                });
        if (expected.size() != 0) {
            fail(format("Tables are missing: %s", expected));
        }
    }

    protected void assertFields(final Set<String> expected, final String tableName) {
        rdbmsUtils.getRdbmsFields(tableName)
                .orElseThrow(() -> new RuntimeException(tableName + " not found"))
                .forEach(o -> {
                    assertTrue(expected.contains(o.getName()), o.getName() + " not found");
                    expected.remove(o.getName());
                });
        if (expected.size() != 0) {
            fail(format("Fields are missing from %s: %s", tableName, expected));
        }
    }

}
