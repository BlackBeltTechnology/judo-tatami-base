package hu.blackbelt.judo.tatami.psm2asm.perf;

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

import com.google.common.collect.ImmutableList;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.derived.NavigationProperty;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.NumericType;
import hu.blackbelt.judo.meta.psm.type.StringType;
import hu.blackbelt.judo.meta.psm.type.BooleanType;
import hu.blackbelt.judo.meta.psm.type.EnumerationType;
import hu.blackbelt.judo.meta.psm.type.TimestampType;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.ModelComparator;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.*;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;

/**
 * Realistic performance test that generates a synthetic model with characteristics
 * similar to the RackInspect real-world model.
 * 
 * RackInspect Model Characteristics:
 * - 71 Entities (all with default representation)
 * - 837 Transfer Objects (168 mapped, 668 unmapped)
 * - ~12 Transfer Objects per Entity
 * - 315 Attributes (~4.4 per entity)
 * - 91 AssociationEnds (~1.3 per entity)
 * - 45 Containments
 * - 357 DataProperties (~5 per entity)
 * - 194 NavigationProperties (~2.7 per entity)
 * - 2397 TO Attributes (~2.9 per TO)
 * - 2138 TO Relations (~2.6 per TO)
 * 
 * Run with: mvn test -pl judo-tatami-psm2asm -Dtest=RealisticPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    // Model structure ratios based on RackInspect analysis
    private static final int ATTRIBUTES_PER_ENTITY = 4;
    private static final int ASSOCIATION_ENDS_PER_ENTITY = 1;
    private static final int DATA_PROPERTIES_PER_ENTITY = 5;
    private static final int NAV_PROPERTIES_PER_ENTITY = 3;
    
    private static final int MAPPED_TOS_PER_ENTITY = 2;
    private static final int UNMAPPED_TOS_PER_ENTITY = 9;
    
    private static final int TO_ATTRIBUTES_PER_TO = 3;
    private static final int TO_RELATIONS_PER_TO = 3;

    // Types
    private StringType stringType;
    private NumericType intType;
    private NumericType decimalType;
    private BooleanType booleanType;
    private TimestampType timestampType;
    private EnumerationType statusEnum;

    @BeforeEach
    void setUp() {
        stringType = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        intType = newNumericTypeBuilder().withName("Integer").withPrecision(9).withScale(0).build();
        decimalType = newNumericTypeBuilder().withName("Decimal").withPrecision(15).withScale(4).build();
        booleanType = newBooleanTypeBuilder().withName("Boolean").build();
        timestampType = newTimestampTypeBuilder().withName("Timestamp").build();
        statusEnum = newEnumerationTypeBuilder()
                .withName("Status")
                .withMembers(
                        newEnumerationMemberBuilder().withName("ACTIVE").withOrdinal(0).build(),
                        newEnumerationMemberBuilder().withName("INACTIVE").withOrdinal(1).build(),
                        newEnumerationMemberBuilder().withName("PENDING").withOrdinal(2).build()
                )
                .build();
    }

    @Test
    void testRealisticModelPerformance() throws Exception {
        // Generate model with ~70 entities (similar to RackInspect)
        runPerformanceTest(70, "RackInspect-like");
    }

    @Test
    void testSmallRealisticModel() throws Exception {
        runPerformanceTest(20, "Small");
    }

    @Test
    void testLargeRealisticModel() throws Exception {
        runPerformanceTest(100, "Large");
    }

    private void runPerformanceTest(int entityCount, String testName) throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("Realistic Performance Test: {} ({} entities)", testName, entityCount);
        log.info("================================================================");

        // Generate model
        long genStart = System.currentTimeMillis();
        PsmModel psmModel = generateRealisticModel(entityCount);
        long genTime = System.currentTimeMillis() - genStart;
        
        // Count elements
        int totalElements = countElements(psmModel);
        int expectedTOs = entityCount * (MAPPED_TOS_PER_ENTITY + UNMAPPED_TOS_PER_ENTITY);
        
        log.info("Model generated in {}ms", genTime);
        log.info("  Entities: {}", entityCount);
        log.info("  Expected Transfer Objects: ~{}", expectedTOs);
        log.info("  Total Elements: {}", totalElements);
        log.info("");

        // Warmup
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(psmModel, TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(psmModel, TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // ETL measurement
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = executeTransformation(psmModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlClassifiers = countClassifiers(etlResult);
        log.info("ETL completed in {}ms, produced {} classifiers", etlTime, etlClassifiers);

        // Zeta measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = executeTransformation(psmModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaClassifiers = countClassifiers(zetaResult);
        log.info("Zeta completed in {}ms, produced {} classifiers", zetaTime, zetaClassifiers);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Results
        printResults(testName, entityCount, totalElements, etlTime, zetaTime, etlClassifiers, zetaClassifiers);
    }

    private PsmModel generateRealisticModel(int entityCount) {
        PsmModel psmModel = buildPsmModel().build();

        List<EntityType> entities = new ArrayList<>();
        List<MappedTransferObjectType> mappedTOs = new ArrayList<>();
        List<UnmappedTransferObjectType> unmappedTOs = new ArrayList<>();

        // Phase 1: Create all entities with attributes
        for (int i = 0; i < entityCount; i++) {
            EntityType entity = newEntityTypeBuilder()
                    .withName("Entity" + i)
                    .build();

            // Add attributes (mix of types)
            for (int j = 0; j < ATTRIBUTES_PER_ENTITY; j++) {
                Attribute attr = createAttribute(j, i);
                entity.getAttributes().add(attr);
            }

            entities.add(entity);
        }

        // Phase 2: Add relations between entities (association ends)
        for (int i = 0; i < entityCount; i++) {
            EntityType source = entities.get(i);

            for (int j = 0; j < ASSOCIATION_ENDS_PER_ENTITY; j++) {
                int targetIndex = (i + j + 1) % entityCount;
                EntityType target = entities.get(targetIndex);

                AssociationEnd assocEnd = newAssociationEndBuilder()
                        .withName("assoc" + j + "To" + target.getName())
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(j == 0 ? 1 : -1)
                                .build())
                        .build();
                source.getRelations().add(assocEnd);
            }

            // Add containments (only for some entities)
            if (i % 2 == 0 && i + 1 < entityCount) {
                EntityType target = entities.get(i + 1);
                Containment containment = newContainmentBuilder()
                        .withName("contains" + target.getName())
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(-1)
                                .build())
                        .build();
                source.getRelations().add(containment);
            }
        }

        // Phase 3: Add derived properties
        for (int i = 0; i < entityCount; i++) {
            EntityType entity = entities.get(i);

            // Add data properties
            for (int j = 0; j < DATA_PROPERTIES_PER_ENTITY; j++) {
                DataProperty dataProp = newDataPropertyBuilder()
                        .withName("derivedAttr" + j)
                        .withDataType(j % 2 == 0 ? stringType : intType)
                        .withGetterExpression(newDataExpressionTypeBuilder()
                                .withExpression("self.strAttr0")
                                .withDialect(hu.blackbelt.judo.meta.psm.derived.ExpressionDialect.JQL)
                                .build())
                        .build();
                entity.getDataProperties().add(dataProp);
            }

            // Add navigation properties
            for (int j = 0; j < NAV_PROPERTIES_PER_ENTITY; j++) {
                int targetIndex = (i + j + 2) % entityCount;
                EntityType target = entities.get(targetIndex);

                NavigationProperty navProp = newNavigationPropertyBuilder()
                        .withName("nav" + j + "To" + target.getName())
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(-1)
                                .build())
                        .withGetterExpression(newReferenceExpressionTypeBuilder()
                                .withExpression("self.assoc0To" + entities.get((i + 1) % entityCount).getName())
                                .withDialect(hu.blackbelt.judo.meta.psm.derived.ExpressionDialect.JQL)
                                .build())
                        .build();
                entity.getNavigationProperties().add(navProp);
            }
        }

        // Phase 4: Create mapped transfer objects (for each entity)
        for (int i = 0; i < entityCount; i++) {
            EntityType entity = entities.get(i);

            for (int m = 0; m < MAPPED_TOS_PER_ENTITY; m++) {
                MappedTransferObjectType mappedTO = newMappedTransferObjectTypeBuilder()
                        .withName(entity.getName() + "TO" + m)
                        .withEntityType(entity)
                        .build();

                // Add transfer attributes
                for (int j = 0; j < TO_ATTRIBUTES_PER_TO; j++) {
                    TransferAttribute toAttr = newTransferAttributeBuilder()
                            .withName("toAttr" + j)
                            .withDataType(j % 2 == 0 ? stringType : intType)
                            .withRequired(j == 0)
                            .build();
                    
                    // Bind to entity attribute if available
                    if (j < entity.getAttributes().size()) {
                        toAttr.setBinding(entity.getAttributes().get(j));
                    }
                    
                    mappedTO.getAttributes().add(toAttr);
                }

                mappedTOs.add(mappedTO);
                
                // Set as default representation for first mapped TO
                if (m == 0) {
                    entity.setDefaultRepresentation(mappedTO);
                }
            }
        }

        // Phase 5: Create unmapped transfer objects
        for (int i = 0; i < entityCount; i++) {
            for (int u = 0; u < UNMAPPED_TOS_PER_ENTITY; u++) {
                UnmappedTransferObjectType unmappedTO = newUnmappedTransferObjectTypeBuilder()
                        .withName("Unmapped" + i + "_" + u)
                        .build();

                // Add transfer attributes
                for (int j = 0; j < TO_ATTRIBUTES_PER_TO; j++) {
                    TransferAttribute toAttr = newTransferAttributeBuilder()
                            .withName("attr" + j)
                            .withDataType(j % 3 == 0 ? stringType : (j % 3 == 1 ? intType : booleanType))
                            .build();
                    unmappedTO.getAttributes().add(toAttr);
                }

                unmappedTOs.add(unmappedTO);
            }
        }

        // Phase 6: Add transfer object relations
        for (int i = 0; i < mappedTOs.size(); i++) {
            MappedTransferObjectType mappedTO = mappedTOs.get(i);

            for (int j = 0; j < TO_RELATIONS_PER_TO && j < mappedTOs.size(); j++) {
                int targetIndex = (i + j + 1) % mappedTOs.size();
                MappedTransferObjectType target = mappedTOs.get(targetIndex);

                TransferObjectRelation toRel = newTransferObjectRelationBuilder()
                        .withName("rel" + j + "To" + target.getName())
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(j == 0 ? 1 : -1)
                                .build())
                        .withEmbedded(j % 3 == 0)
                        .build();
                mappedTO.getRelations().add(toRel);
            }
        }

        // Build packages with all elements
        List<NamespaceElement> entityElements = new ArrayList<>();
        entityElements.add(stringType);
        entityElements.add(intType);
        entityElements.add(decimalType);
        entityElements.add(booleanType);
        entityElements.add(timestampType);
        entityElements.add(statusEnum);
        entityElements.addAll(entities);

        Package entitiesPackage = newPackageBuilder()
                .withName("entities")
                .withElements(ImmutableList.copyOf(entityElements))
                .build();

        List<NamespaceElement> serviceElements = new ArrayList<>();
        serviceElements.addAll(mappedTOs);
        serviceElements.addAll(unmappedTOs);

        Package servicesPackage = newPackageBuilder()
                .withName("services")
                .withElements(ImmutableList.copyOf(serviceElements))
                .build();

        Model model = newModelBuilder()
                .withName("RealisticTestModel")
                .withPackages(ImmutableList.of(entitiesPackage, servicesPackage))
                .build();

        psmModel.addContent(model);

        return psmModel;
    }

    private Attribute createAttribute(int index, int entityIndex) {
        switch (index % 5) {
            case 0:
                return newAttributeBuilder()
                        .withName("strAttr" + index)
                        .withDataType(stringType)
                        .withRequired(true)
                        .withIdentifier(index == 0)
                        .build();
            case 1:
                return newAttributeBuilder()
                        .withName("intAttr" + index)
                        .withDataType(intType)
                        .withRequired(false)
                        .build();
            case 2:
                return newAttributeBuilder()
                        .withName("decAttr" + index)
                        .withDataType(decimalType)
                        .build();
            case 3:
                return newAttributeBuilder()
                        .withName("boolAttr" + index)
                        .withDataType(booleanType)
                        .build();
            default:
                return newAttributeBuilder()
                        .withName("tsAttr" + index)
                        .withDataType(timestampType)
                        .build();
        }
    }

    private AsmModel executeTransformation(PsmModel psmModel, TransformationMode mode) throws Exception {
        TransformationContext context = new TransformationContext("RealisticTest");
        context.put(psmModel);
        context.put(Psm2AsmWork.Psm2AsmWorkParameter.psm2AsmWorkParameter()
                .transformationMode(mode)
                .createTrace(false)
                .build());

        Psm2AsmWork work = new Psm2AsmWork(context);
        work.execute();

        return context.getByClass(AsmModel.class)
                .orElseThrow(() -> new IllegalStateException("ASM Model not found after transformation"));
    }

    private int countElements(PsmModel psmModel) {
        int count = 0;
        for (var resource : psmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private int countClassifiers(AsmModel asmModel) {
        int count = 0;
        for (var resource : asmModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof EPackage) {
                    count += countClassifiersRecursive((EPackage) content);
                }
            }
        }
        return count;
    }

    private int countClassifiersRecursive(EPackage pkg) {
        int count = pkg.getEClassifiers().size();
        for (EPackage subPkg : pkg.getESubpackages()) {
            count += countClassifiersRecursive(subPkg);
        }
        return count;
    }

    private void compareModels(AsmModel etlResult, AsmModel zetaResult) {
        int etlClassifiers = countClassifiers(etlResult);
        int zetaClassifiers = countClassifiers(zetaResult);

        if (etlClassifiers != zetaClassifiers) {
            log.warn("Classifier count mismatch: ETL={}, Zeta={}", etlClassifiers, zetaClassifiers);
        } else {
            log.info("Classifier count: {} (both match)", etlClassifiers);
        }

        // Structural comparison
        if (!etlResult.getResourceSet().getResources().isEmpty() &&
            !zetaResult.getResourceSet().getResources().isEmpty()) {

            EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

            ModelComparator.ComparisonResult result = ModelComparator.compare(
                    etlRoot, zetaRoot, ModelComparator.ComparisonMode.STRUCTURAL);

            if (result.isEquivalent()) {
                log.info("SUCCESS: ETL and Zeta models are structurally equivalent");
            } else {
                log.warn("Models have {} difference(s)", result.getDifferenceCount());
                int count = 0;
                for (ModelComparator.Difference diff : result.getDifferenceList()) {
                    if (count++ >= 10) {
                        log.warn("  ... and {} more differences", result.getDifferenceCount() - 10);
                        break;
                    }
                    log.warn("  - {}", diff.describe());
                }
            }
        }
    }

    private void printResults(String testName, int entityCount, int totalElements,
                              long etlTime, long zetaTime, int etlClassifiers, int zetaClassifiers) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} entities, {} elements)", testName, entityCount, totalElements);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTime),
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double) (zetaTime - etlTime) / etlTime) * 100));
        log.info("Classifiers:  {}       {}",
                String.format("%8d", etlClassifiers),
                String.format("%8d", zetaClassifiers));
        log.info("");

        double etlThroughput = totalElements / (etlTime / 1000.0);
        double zetaThroughput = totalElements / (zetaTime / 1000.0);
        log.info("Throughput:   {}/s       {}/s",
                String.format("%8.0f", etlThroughput),
                String.format("%8.0f", zetaThroughput));
        log.info("");

        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / zetaTime;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTime > etlTime) {
            double slowdown = (double) zetaTime / etlTime;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");
    }
}
