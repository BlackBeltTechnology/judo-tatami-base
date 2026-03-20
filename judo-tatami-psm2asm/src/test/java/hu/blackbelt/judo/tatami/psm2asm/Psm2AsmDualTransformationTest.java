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
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.derived.NavigationProperty;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.MappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.service.TransferAttribute;
import hu.blackbelt.judo.meta.psm.service.TransferObjectRelation;
import hu.blackbelt.judo.meta.psm.service.UnmappedTransferObjectType;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.*;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;
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

        // Run Zeta transformation using the same PSM model so XMI IDs are comparable
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaResult)
                .modelName(psmModel.getName())
                .build();
        zetaTransformation.execute();

        // Compare with STRICT mode
        log.info("Comparing ETL and Zeta output models with STRICT mode");
        ModelComparator.assertEquivalent(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.ComparisonMode.STRICT
        );

        // Verify XMI IDs are equivalent (bypasses system property gate).
        // Uses flexible matching (exactMatch=false) because ETL and Zeta rule names
        // legitimately differ (e.g., ETL "BoundOperationAnnotation" vs Zeta "BoundAnnotationForBoundTransferOperation").
        var etlResource = etlResult.getResourceSet().getResources().get(0);
        var zetaResource = zetaResult.getResourceSet().getResources().get(0);
        var xmiDifferences = ModelComparator.compareXmiIds(etlResource, zetaResource, null, false);
        if (!xmiDifferences.isEmpty()) {
            StringBuilder sb = new StringBuilder("XMI ID comparison failed:\n");
            sb.append(xmiDifferences.size()).append(" XMI ID difference(s):\n");
            xmiDifferences.forEach(diff -> sb.append("  ").append(diff.describe()).append("\n"));
            fail(sb.toString());
        }
        log.info("SUCCESS: ETL and Zeta transformations produced equivalent models (strict)");
    }

    @Test
    @DisplayName("TransferAttribute with parameterized PrimitiveAccessor getter produces parameterized annotation in both ETL and Zeta")
    void testParameterizedTransferAttributeAnnotation() throws Exception {
        // Build a minimal PSM: UnmappedTransferObjectType with a TransferAttribute
        // whose binding is a DataProperty (implements PrimitiveAccessor) with a parameterType
        StringType strType = newStringTypeBuilder().withName("String").withMaxLength(256).build();

        UnmappedTransferObjectType paramType = newUnmappedTransferObjectTypeBuilder().withName("InputParameter").build();

        DataProperty dataProperty = newDataPropertyBuilder()
                .withName("derivedAttr")
                .withDataType(strType)
                .withGetterExpression(
                        newDataExpressionTypeBuilder()
                                .withExpression("self.name")
                                .withParameterType(paramType)
                                .build())
                .build();

        EntityType entity = newEntityTypeBuilder().withName("Entity")
                .withDataProperties(dataProperty)
                .build();

        TransferAttribute transferAttr = newTransferAttributeBuilder()
                .withName("derivedAttr")
                .withDataType(strType)
                .withBinding(dataProperty)
                .build();

        UnmappedTransferObjectType transferObject = newUnmappedTransferObjectTypeBuilder()
                .withName("MyTransferObject")
                .withAttributes(transferAttr)
                .build();

        Model model = newModelBuilder().withName("TestModel")
                .withElements(entity, strType, paramType, transferObject)
                .build();

        PsmModel psmModel = buildPsmModel().build();
        psmModel.addContent(model);

        // Run ETL
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlResult));

        // Run Zeta (same PSM — ETL does not modify the PSM model)
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaResult)
                .modelName(psmModel.getName())
                .build()
                .execute();

        // STRICT comparison — should FAIL until Zeta rule is implemented (TDD red phase)
        ModelComparator.assertEquivalent(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.ComparisonMode.STRICT
        );
    }

    @Test
    @DisplayName("TransferObjectRelation with parameterized ReferenceAccessor getter produces parameterized annotation in both ETL and Zeta")
    void testParameterizedTransferObjectRelationAnnotation() throws Exception {
        // Build a minimal PSM: UnmappedTransferObjectType with a TransferObjectRelation
        // whose binding is a NavigationProperty (implements ReferenceAccessor) with a parameterType
        UnmappedTransferObjectType paramType = newUnmappedTransferObjectTypeBuilder().withName("InputParameter")
                .build();

        EntityType targetEntity = newEntityTypeBuilder().withName("TargetEntity").build();
        EntityType sourceEntity = newEntityTypeBuilder().withName("SourceEntity").build();

        NavigationProperty navProp = newNavigationPropertyBuilder()
                .withName("derivedRel")
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .withTarget(targetEntity)
                .withGetterExpression(
                        newReferenceExpressionTypeBuilder()
                                .withExpression("self.related")
                                .withParameterType(paramType)
                                .build())
                .build();
        sourceEntity.getNavigationProperties().add(navProp);

        UnmappedTransferObjectType targetTO = newUnmappedTransferObjectTypeBuilder()
                .withName("TargetTO")
                .build();

        TransferObjectRelation transferRel = newTransferObjectRelationBuilder()
                .withName("derivedRel")
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .withTarget(targetTO)
                .withBinding(navProp)
                .build();

        UnmappedTransferObjectType sourceTO = newUnmappedTransferObjectTypeBuilder()
                .withName("SourceTO")
                .withRelations(transferRel)
                .build();

        Model model = newModelBuilder().withName("TestModel2")
                .withElements(sourceEntity, targetEntity, paramType, sourceTO, targetTO)
                .build();

        PsmModel psmModel = buildPsmModel().build();
        psmModel.addContent(model);

        // Run ETL
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlResult));

        // Run Zeta (reuse same PSM model — no ETL side effects on PSM)
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation zetaTransformation = Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaResult)
                .modelName(psmModel.getName())
                .build();
        zetaTransformation.execute();

        // STRICT comparison — should FAIL until Zeta rule is implemented (TDD red phase)
        ModelComparator.assertEquivalent(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.ComparisonMode.STRICT
        );
    }

    @Test
    @DisplayName("Abstract entity and its concrete subclass produce equivalent ASM models in ETL and Zeta")
    void testAbstractEntityDualTransformation() throws Exception {
        // Build a PSM model with:
        //   - AbstractF (abstract entity, no direct transfer object)
        //   - ConcreteG (concrete entity, has a single relation to AbstractF)
        //   - MappedF (mapped transfer object for AbstractF)
        //   - MappedG (mapped transfer object for ConcreteG with relation referencing MappedF)
        StringType strType = newStringTypeBuilder().withName("String").withMaxLength(256).build();

        EntityType abstractF = newEntityTypeBuilder()
                .withName("AbstractF")
                .withAbstract_(true)
                .build();

        EntityType concreteG = newEntityTypeBuilder()
                .withName("ConcreteG")
                .build();

        AssociationEnd relationGOnFSingle = newAssociationEndBuilder()
                .withName("relationGOnFSingle")
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .withTarget(abstractF)
                .build();
        concreteG.getRelations().add(relationGOnFSingle);

        MappedTransferObjectType mappedF = newMappedTransferObjectTypeBuilder()
                .withName("MappedF")
                .withEntityType(abstractF)
                .build();

        TransferObjectRelation toRelation = newTransferObjectRelationBuilder()
                .withName("relationGOnFSingle")
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .withTarget(mappedF)
                .withBinding(relationGOnFSingle)
                .build();

        MappedTransferObjectType mappedG = newMappedTransferObjectTypeBuilder()
                .withName("MappedG")
                .withEntityType(concreteG)
                .withRelations(toRelation)
                .build();

        Model model = newModelBuilder().withName("AbstractModel")
                .withElements(strType, abstractF, concreteG, mappedF, mappedG)
                .build();

        PsmModel psmModel = buildPsmModel().build();
        psmModel.addContent(model);

        // Run ETL
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(etlResult));

        // Run Zeta (reuse same PSM — ETL does not modify PSM)
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformation.builder()
                .psmModel(psmModel)
                .asmModel(zetaResult)
                .modelName(psmModel.getName())
                .build()
                .execute();

        // STRICT comparison: both must produce the same EClasses including abstract ones
        ModelComparator.assertEquivalent(
                etlResult.getResourceSet().getResources().get(0),
                zetaResult.getResourceSet().getResources().get(0),
                ModelComparator.ComparisonMode.STRICT
        );
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
