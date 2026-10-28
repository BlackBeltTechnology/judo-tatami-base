package hu.blackbelt.judo.tatami.psm2asm;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;

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

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newPackageBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmNamespaceTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    public static final String NS_URI = "http://blackbelt.hu/judo/" + MODEL_NAME;
    public static final String NS_PREFIX = "runtime" + MODEL_NAME;
    public static final String EXTENDED_METADATA_URI = "http://blackbelt.hu/judo/meta/ExtendedMetadata";

    PsmModel psmModel;
    AsmModel asmModel;
    AsmUtils asmUtils;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .build();
        // Create empty ASM model
        asmModel = buildAsmModel()
                .build();
        asmUtils = new AsmUtils(asmModel.getResourceSet());
    }

    private void transform(final String testName, final TransformationMode transformationMode) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-" + transformationMode + "-psm.model"))
                .build());

        assertTrue(psmModel.isValid());
        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        if (transformationMode.isZeta()) {
            log.info("Running Zeta transformation for test: {}", testName);
            Psm2AsmZetaTransformation transformation = Psm2AsmZetaTransformation.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName(MODEL_NAME)
                    .build();
            transformation.execute();
        } else {
            log.info("Running ETL transformation for test: {}", testName);
            executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel));
        }

        assertTrue(asmModel.isValid());
        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-" + transformationMode + "-asm.model"))
                .build());
    }

    /**
     * Runs both ETL and Zeta transformations and compares their outputs.
     * This should be called at the end of parameterized tests to verify equivalence.
     */
    private void compareTransformations(final String testName) throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL fresh
        AsmModel etlModel = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlModel));

        // Run Zeta fresh - use psmModel.getName() to match what ETL uses
        AsmModel zetaModel = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaModel)
                .modelName(psmModel.getName())
                .build();
        zetaTransformation.execute();

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlModel.getResourceSet().getResources().get(0).getContents().get(0),
                zetaModel.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models for {}", testName);
        } else {
            log.warn("Models have differences for {}:\n{}", testName, result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + testName + ":\n" + result.getDetailedReport());
        }
    }

    @ParameterizedTest(name = "testNamespace with {0}")
    @EnumSource(TransformationMode.class)
    void testNamespace(TransformationMode transformationMode) throws Exception {

        Package packOfPack = newPackageBuilder().withName("packageB").build();

        Package packOfModel = newPackageBuilder().withPackages(packOfPack).withName("packageA").build();

        Model model = newModelBuilder().withName(MODEL_NAME).withPackages(packOfModel)
                .withElements(ImmutableList.of()).build();

        psmModel.addContent(model);

        transform("testNamespace", transformationMode);

        final String packageANameFirstUpperCase = "PackageA";
        final String packageBNameFirstUpperCase = "PackageB";

        final Optional<EPackage> asmPackOfPack = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(packOfPack.getName())).findAny();
        final Optional<EPackage> asmPackOfModel = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(packOfModel.getName())).findAny();
        final Optional<EPackage> asmModel = asmUtils.all(EPackage.class).filter(c -> c.getName().equals(model.getName())).findAny();
        assertTrue(asmPackOfPack.isPresent());
        assertTrue(asmPackOfPack.get().getNsPrefix().equals(NS_PREFIX + MODEL_NAME + packageANameFirstUpperCase + packageBNameFirstUpperCase));
        assertTrue(asmPackOfPack.get().getNsURI().equals(NS_URI + "/" + MODEL_NAME + "/" + packOfModel.getName() + "/" + packOfPack.getName()));

        assertTrue(asmPackOfModel.isPresent());
        assertTrue(asmPackOfModel.get().getNsPrefix().equals(NS_PREFIX + MODEL_NAME + packageANameFirstUpperCase));
        assertTrue(asmPackOfModel.get().getNsURI().equals(NS_URI + "/" + MODEL_NAME + "/" + packOfModel.getName()));

        assertTrue(asmModel.isPresent());
        assertTrue(asmModel.get().getNsPrefix().equals(NS_PREFIX + MODEL_NAME));
        assertTrue(asmModel.get().getNsURI().equals(NS_URI + "/" + MODEL_NAME));

        compareTransformations("testNamespace");
    }

    /**
     * Test that compares ETL and Zeta transformation outputs for equivalence.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Build model for ETL
        Package packOfPack = newPackageBuilder().withName("packageB").build();
        Package packOfModel = newPackageBuilder().withPackages(packOfPack).withName("packageA").build();
        Model model = newModelBuilder().withName(MODEL_NAME).withPackages(packOfModel).build();

        PsmModel psmModelEtl = buildPsmModel().build();
        psmModelEtl.addContent(model);
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(etlResult));

        // Build model for Zeta (fresh copy)
        Package packOfPack2 = newPackageBuilder().withName("packageB").build();
        Package packOfModel2 = newPackageBuilder().withPackages(packOfPack2).withName("packageA").build();
        Model model2 = newModelBuilder().withName(MODEL_NAME).withPackages(packOfModel2).build();

        PsmModel psmModelZeta = buildPsmModel().build();
        psmModelZeta.addContent(model2);
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModelZeta)
                .asmModel(zetaResult)
                .modelName(psmModelZeta.getName())
                .build();
        zetaTransformation.execute();

        // Compare models
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
