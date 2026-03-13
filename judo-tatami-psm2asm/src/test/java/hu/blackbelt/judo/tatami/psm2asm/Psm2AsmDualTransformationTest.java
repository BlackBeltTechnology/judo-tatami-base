package hu.blackbelt.judo.tatami.psm2asm;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;

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
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dual transformation test that verifies ETL and Zeta transformations produce equivalent models.
 * <p>
 * This test runs both ETL and Zeta transformation engines on the same input model
 * and asserts that the output models are structurally equivalent.
 * </p>
 * 
 * <h2>Configuration</h2>
 * The comparison behavior can be configured via system properties:
 * <ul>
 *   <li>{@code judo.test.comparison.enabled} - Enable/disable comparison (default: true)</li>
 *   <li>{@code judo.test.comparison.mode} - Comparison mode: STRICT, SKELETON (default: STRICT)</li>
 *   <li>{@code judo.test.comparison.maxDifferences} - Max differences to report (default: 50)</li>
 *   <li>{@code judo.test.comparison.reportFile} - Output file for diff report (optional)</li>
 * </ul>
 */
@Slf4j
public class Psm2AsmDualTransformationTest {

    private static final String MODEL_NAME = "demo";

    @Test
    @DisplayName("ETL and Zeta transformations produce equivalent ASM models")
    void testEtlAndZetaProduceEquivalentModels() throws Exception {
        // Check if comparison is enabled
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Create source model
        PsmModel psmModel = new Demo().fullDemo();
        assertTrue(psmModel.isValid(), "Source PSM model should be valid");

        // Run ETL transformation
        log.info("Running ETL transformation...");
        AsmModel etlResult = buildAsmModel().build();
        long etlStart = System.currentTimeMillis();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlResult));
        long etlDuration = System.currentTimeMillis() - etlStart;
        log.info("ETL transformation completed in {}ms", etlDuration);

        // Run Zeta transformation (need fresh source model to avoid side effects)
        log.info("Running Zeta transformation...");
        PsmModel psmModelForZeta = new Demo().fullDemo();
        AsmModel zetaResult = buildAsmModel().build();
        long zetaStart = System.currentTimeMillis();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModelForZeta)
                .asmModel(zetaResult)
                .modelName(psmModelForZeta.getName())
                .build();
        zetaTransformation.execute();
        long zetaDuration = System.currentTimeMillis() - zetaStart;
        log.info("Zeta transformation completed in {}ms", zetaDuration);

        // Verify both produced valid models
        assertNotNull(etlResult.getResourceSet(), "ETL result should have a resource set");
        assertNotNull(zetaResult.getResourceSet(), "Zeta result should have a resource set");
        assertFalse(etlResult.getResourceSet().getResources().isEmpty(), "ETL result should have resources");
        assertFalse(zetaResult.getResourceSet().getResources().isEmpty(), "Zeta result should have resources");

        // Debug: Compare actor types and annotations
        hu.blackbelt.judo.meta.asm.runtime.AsmUtils etlUtils = new hu.blackbelt.judo.meta.asm.runtime.AsmUtils(etlResult.getResourceSet());
        hu.blackbelt.judo.meta.asm.runtime.AsmUtils zetaUtils = new hu.blackbelt.judo.meta.asm.runtime.AsmUtils(zetaResult.getResourceSet());
        
        log.info("=== DEBUG: ETL Actor Types ===");
        etlUtils.all(org.eclipse.emf.ecore.EClass.class).filter(c -> hu.blackbelt.judo.meta.asm.runtime.AsmUtils.annotatedAsTrue(c, "actorType")).forEach(c -> {
            log.info("  Actor type: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
        });
        
        log.info("=== DEBUG: Zeta Actor Types ===");
        zetaUtils.all(org.eclipse.emf.ecore.EClass.class).filter(c -> hu.blackbelt.judo.meta.asm.runtime.AsmUtils.annotatedAsTrue(c, "actorType")).forEach(c -> {
            log.info("  Actor type: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
        });
        
        log.info("=== DEBUG: ETL Access Points (classes with 'actor' annotation) ===");
        etlUtils.all(org.eclipse.emf.ecore.EClass.class).forEach(c -> {
            hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getExtensionAnnotationListByName(c, "actor").forEach(a -> {
                String name = a.getDetails().get("name");
                log.info("  Access point: {} -> actor name: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c), name);
            });
        });
        
        log.info("=== DEBUG: Zeta Access Points (classes with 'actor' annotation) ===");
        zetaUtils.all(org.eclipse.emf.ecore.EClass.class).forEach(c -> {
            hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getExtensionAnnotationListByName(c, "actor").forEach(a -> {
                String name = a.getDetails().get("name");
                log.info("  Access point: {} -> actor name: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c), name);
            });
        });
        
        log.info("=== DEBUG: ETL Entity Category annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EClass.class)
            .filter(c -> hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c).contains(".entities.Category"))
            .forEach(c -> {
                log.info("  Entity: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
                c.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta Entity Category annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EClass.class)
            .filter(c -> hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c).contains(".entities.Category"))
            .forEach(c -> {
                log.info("  Entity: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
                c.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL _moveProducts operation annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EOperation.class)
            .filter(o -> o.getName().equals("_moveProducts"))
            .forEach(o -> {
                log.info("  Operation: {}", o.getName());
                o.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta _moveProducts operation annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EOperation.class)
            .filter(o -> o.getName().equals("_moveProducts"))
            .forEach(o -> {
                log.info("  Operation: {}", o.getName());
                o.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL OrderInfo.comments relation annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("comments") && r.getEContainingClass().getName().equals("OrderInfo"))
            .forEach(r -> {
                log.info("  Reference: {}.{}", r.getEContainingClass().getName(), r.getName());
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta OrderInfo.comments relation annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("comments") && r.getEContainingClass().getName().equals("OrderInfo"))
            .forEach(r -> {
                log.info("  Reference: {}.{}", r.getEContainingClass().getName(), r.getName());
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL Product.category relation annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("category") && r.getEContainingClass().getName().equals("Product"))
            .forEach(r -> {
                log.info("  Reference: {} in class {}", r.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(r.getEContainingClass()));
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta Product.category relation annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("category") && r.getEContainingClass().getName().equals("Product"))
            .forEach(r -> {
                log.info("  Reference: {} in class {}", r.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(r.getEContainingClass()));
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL LastTwoWeekOrders relation annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("LastTwoWeekOrders"))
            .forEach(r -> {
                log.info("  Reference: {} in class {}", r.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(r.getEContainingClass()));
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta LastTwoWeekOrders relation annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EReference.class)
            .filter(r -> r.getName().equals("LastTwoWeekOrders"))
            .forEach(r -> {
                log.info("  Reference: {} in class {}", r.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(r.getEContainingClass()));
                r.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL _getRangeReferenceCategory operation annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EOperation.class)
            .filter(o -> o.getName().equals("_getRangeReferenceCategory"))
            .forEach(o -> {
                log.info("  Operation: {} in class {}", o.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(o.getEContainingClass()));
                o.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta _getRangeReferenceCategory operation annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EOperation.class)
            .filter(o -> o.getName().equals("_getRangeReferenceCategory"))
            .forEach(o -> {
                log.info("  Operation: {} in class {}", o.getName(), hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(o.getEContainingClass()));
                o.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: ETL _GetRangeInputProductInfoCategory class annotations ===");
        etlUtils.all(org.eclipse.emf.ecore.EClass.class)
            .filter(c -> c.getName().equals("_GetRangeInputProductInfoCategory"))
            .forEach(c -> {
                log.info("  Class: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
                c.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });
        
        log.info("=== DEBUG: Zeta _GetRangeInputProductInfoCategory class annotations ===");
        zetaUtils.all(org.eclipse.emf.ecore.EClass.class)
            .filter(c -> c.getName().equals("_GetRangeInputProductInfoCategory"))
            .forEach(c -> {
                log.info("  Class: {}", hu.blackbelt.judo.meta.asm.runtime.AsmUtils.getClassifierFQName(c));
                c.getEAnnotations().forEach(a -> log.info("    - annotation: {} -> {}", a.getSource(), a.getDetails()));
            });

        // Compare models for equivalence using configured mode
        ModelComparator.ComparisonMode mode = ModelComparator.getConfiguredMode();
        log.info("Comparing ETL and Zeta output models with mode: {}", mode);
        
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                mode
        );
        
        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models");
        } else {
            log.error("FAILURE: Models are not equivalent\n{}", result.getSummary());
            fail("Models are not equivalent:\n" + result.getDetailedReport());
        }
    }

    @Test
    @DisplayName("ETL and Zeta transformations produce equivalent ASM models (strict mode)")
    void testEtlAndZetaProduceEquivalentModelsStrict() throws Exception {
        // Check if comparison is enabled
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Create source model
        PsmModel psmModel = new Demo().fullDemo();
        assertTrue(psmModel.isValid(), "Source PSM model should be valid");

        // Run ETL transformation
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlResult));

        // Run Zeta transformation
        PsmModel psmModelForZeta = new Demo().fullDemo();
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModelForZeta)
                .asmModel(zetaResult)
                .modelName(psmModelForZeta.getName())
                .build();
        zetaTransformation.execute();

        // Compare with STRICT mode
        log.info("Comparing ETL and Zeta output models with STRICT mode");
        ModelComparator.assertEquivalent(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.ComparisonMode.STRICT
        );
        log.info("SUCCESS: ETL and Zeta transformations produced equivalent models (strict)");
    }

    @Test
    @DisplayName("V2 TransformationRegistry-based transformation executes without errors")
    void testZetaV2TransformationExecutes() throws Exception {
        // Create source model
        PsmModel psmModel = new Demo().fullDemo();
        assertTrue(psmModel.isValid(), "Source PSM model should be valid");

        // Run V2 Zeta transformation using TransformationRegistry
        log.info("Running Zeta V2 transformation (TransformationRegistry-based)...");
        AsmModel zetaV2Result = buildAsmModel().build();
        long v2Start = System.currentTimeMillis();
        Psm2AsmZetaTransformation v2Transformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaV2Result)
                .modelName(psmModel.getName())
                .build();
        v2Transformation.execute();
        long v2Duration = System.currentTimeMillis() - v2Start;
        log.info("Zeta V2 transformation completed in {}ms", v2Duration);

        // Verify V2 produced a valid model
        assertNotNull(zetaV2Result.getResourceSet(), "V2 result should have a resource set");
        assertFalse(zetaV2Result.getResourceSet().getResources().isEmpty(), "V2 result should have resources");
        
        log.info("SUCCESS: Zeta V2 transformation executed successfully");
    }
}
