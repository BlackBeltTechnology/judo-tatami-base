package hu.blackbelt.judo.tatami.psm2measure;

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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureZetaTransformation;

import java.io.File;
import java.util.List;
import java.util.Map;

import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.SaveArguments.measureSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;
import static hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace.fromModelsAndTrace;
import static org.junit.jupiter.api.Assertions.fail;
import hu.blackbelt.judo.tatami.psm2measure.util.ModelComparator;

@Slf4j
public class Psm2MeasureTest {
    public static final String DEMO = "demo";
    public static final String NORTHWIND_PSM_MODEL = "northwind-psm.model";
    public static final String NORTHWIND_MEASURE_MODEL = "northwind-measure.model";
    public final static String NORTHWIND_PSM_2_MEASURE_MODEL = "northwind-psm2measure.model";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;
    MeasureModel measureModel;

    @BeforeEach
    public void setUp() throws Exception {
        psmModel = new Demo().fullDemo();

        // Create empty MEASURE model
        measureModel = buildMeasureModel()
                .name(DEMO)
                .build();
    }


    @ParameterizedTest(name = "testPsm2MeasureTransformation with {0}")
    @EnumSource(TransformationType.class)
    public void testPsm2MeasureTransformation(TransformationType transformationType) throws Exception {
        Psm2MeasureTransformationTrace psm2MeasureTransformationTrace;
        
        if (transformationType == TransformationType.ZETA) {
            log.info("Running Zeta transformation");
            Psm2MeasureZetaTransformation transformation = Psm2MeasureZetaTransformation.builder()
                    .psmModel(psmModel)
                    .measureModel(measureModel)
                    .build();
            transformation.execute();
            // For Zeta transformation, trace is not available
            psm2MeasureTransformationTrace = null;
        } else {
            log.info("Running ETL transformation");
            psm2MeasureTransformationTrace = executePsm2MeasureTransformation(psm2MeasureParameter()
                    .psmModel(psmModel)
                    .measureModel(measureModel));
        }

        // Trace operations only for ETL transformation
        if (psm2MeasureTransformationTrace != null) {
            // Saving trace map
            psm2MeasureTransformationTrace.save(new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_2_MEASURE_MODEL));

            // Loading trace map
            Psm2MeasureTransformationTrace psm2MeasureTransformationTraceLoaded =
                    fromModelsAndTrace(DEMO, psmModel, measureModel, new File(TARGET_TEST_CLASSES, NORTHWIND_PSM_2_MEASURE_MODEL));

            Map<EObject, List<EObject>> resolvedTrace = psm2MeasureTransformationTraceLoaded.getTransformationTrace();

            // Printing trace
            for (EObject e : resolvedTrace.keySet()) {
                for (EObject t : resolvedTrace.get(e)) {
                    log.info(e.toString() + " -> " + t.toString());
                }
            }
        }
        
        measureModel.saveMeasureModel(measureSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, NORTHWIND_MEASURE_MODEL)));
    }

    /**
     * Test that ETL and Zeta transformations produce equivalent Measure models.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL transformation
        log.info("Running ETL transformation for equivalence test...");
        PsmModel psmModelEtl = new Demo().fullDemo();
        MeasureModel etlResult = buildMeasureModel().name(DEMO).build();
        executePsm2MeasureTransformation(psm2MeasureParameter()
                .psmModel(psmModelEtl)
                .measureModel(etlResult));

        // Run Zeta transformation
        log.info("Running Zeta transformation for equivalence test...");
        PsmModel psmModelZeta = new Demo().fullDemo();
        MeasureModel zetaResult = buildMeasureModel().name(DEMO).build();
        Psm2MeasureZetaTransformation zetaTransformation = Psm2MeasureZetaTransformation.builder()
                .psmModel(psmModelZeta)
                .measureModel(zetaResult)
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
