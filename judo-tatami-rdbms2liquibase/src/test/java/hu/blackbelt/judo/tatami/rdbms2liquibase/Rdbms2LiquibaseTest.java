package hu.blackbelt.judo.tatami.rdbms2liquibase;

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
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.model.northwind.Demo;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.HsqlDatabase;
import liquibase.database.jvm.HsqlConnection;
import liquibase.resource.FileSystemResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.SaveArguments.liquibaseSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseNamespaceFixUriHandler.fixUriOutputStream;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static org.junit.jupiter.api.Assertions.fail;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import org.junit.jupiter.api.Test;

@Slf4j
public class Rdbms2LiquibaseTest {

    public static final String NORTHWIND = "demo";
    public static final String NORTHWIND_RDBMS_MODEL = "northwind-rdbms_hsqldb.model";
    public static final String NORTHWIND_LIQUIBASE_MODEL = "northwind.changelog.xml";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    RdbmsModel rdbmsModel;
    LiquibaseModel liquibaseModel;

    @BeforeEach
    public void setUp() throws Exception {
        final PsmModel psmModel = new Demo().fullDemo();

        // Create empty ASM model
        AsmModel asmModel = AsmModel.buildAsmModel()
                .build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        // Create empty RDBMS model
        rdbmsModel = RdbmsModel.buildRdbmsModel()
                .build();

        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .dialect("hsqldb"));

        // Create empty LIQUIBASE model
        liquibaseModel = buildLiquibaseModel()
                .name(NORTHWIND)
                .build();
    }

    @ParameterizedTest(name = "testRdbms2LiquibaseTransformation with {0}")
    @EnumSource(TransformationMode.class)
    public void testRdbms2LiquibaseTransformation(TransformationMode transformationMode) throws Exception {

        if (transformationMode.isZeta()) {
            log.info("Running Zeta transformation");
            Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb")
                    .build();
            transformation.execute();
        } else {
            log.info("Running ETL transformation");
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb"));
        }

        liquibaseModel.saveLiquibaseModel(liquibaseSaveArgumentsBuilder()
                                                  .outputStream(fixUriOutputStream(
                                                          new FileOutputStream(new File(TARGET_TEST_CLASSES, NORTHWIND_LIQUIBASE_MODEL)))));

        // Executing on HSQLDB
        Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:mymemdb", "SA", "");

        Database liquibaseDb = new HsqlDatabase();
        liquibaseDb.setConnection(new HsqlConnection(connection));
        Liquibase liquibase = new Liquibase(
                NORTHWIND_LIQUIBASE_MODEL,
                new FileSystemResourceAccessor(new File(TARGET_TEST_CLASSES)),
                liquibaseDb);

        liquibase.update("full,1.0.0");

        // Compare ETL and Zeta outputs
        compareTransformations("testRdbms2LiquibaseTransformation");
    }

    /**
     * Runs both ETL and Zeta transformations and compares their outputs.
     */
    private void compareTransformations(final String testName) throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL fresh
        LiquibaseModel liquibaseModelEtl = buildLiquibaseModel().name(NORTHWIND).build();
        executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(liquibaseModelEtl)
                .dialect("hsqldb"));

        // Run Zeta fresh
        LiquibaseModel liquibaseModelZeta = buildLiquibaseModel().name(NORTHWIND).build();
        Rdbms2LiquibaseZetaTransformation zetaTransformation = Rdbms2LiquibaseZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(liquibaseModelZeta)
                .dialect("hsqldb")
                .build();
        zetaTransformation.execute();

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                liquibaseModelEtl.getResourceSet().getResources().get(0).getContents().get(0),
                liquibaseModelZeta.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models for {}", testName);
        } else {
            log.warn("Models have differences for {}:\n{}", testName, result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + testName + ":\n" + result.getDetailedReport());
        }
    }

    /**
     * Test that ETL and Zeta transformations produce equivalent Liquibase models.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL transformation
        log.info("Running ETL transformation for equivalence test...");
        LiquibaseModel etlResult = buildLiquibaseModel().name(NORTHWIND).build();
        executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(etlResult)
                .dialect("hsqldb"));

        // Run Zeta transformation
        log.info("Running Zeta transformation for equivalence test...");
        LiquibaseModel zetaResult = buildLiquibaseModel().name(NORTHWIND).build();
        Rdbms2LiquibaseZetaTransformation zetaTransformation = Rdbms2LiquibaseZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(zetaResult)
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
