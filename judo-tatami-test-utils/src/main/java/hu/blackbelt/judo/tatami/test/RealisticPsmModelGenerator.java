package hu.blackbelt.judo.tatami.test;

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
import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.derived.DataProperty;
import hu.blackbelt.judo.meta.psm.derived.NavigationProperty;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.DurationType;
import hu.blackbelt.judo.meta.psm.measure.Measure;
import hu.blackbelt.judo.meta.psm.measure.MeasuredType;
import hu.blackbelt.judo.meta.psm.measure.Unit;
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
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.*;
import static hu.blackbelt.judo.meta.psm.measure.util.builder.MeasureBuilders.*;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.*;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;

/**
 * Generates realistic PSM models with characteristics similar to the RackInspect real-world model.
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
 */
public class RealisticPsmModelGenerator {

    // Model structure ratios based on RackInspect analysis
    public static final int ATTRIBUTES_PER_ENTITY = 4;
    public static final int ASSOCIATION_ENDS_PER_ENTITY = 1;
    public static final int DATA_PROPERTIES_PER_ENTITY = 5;
    public static final int NAV_PROPERTIES_PER_ENTITY = 3;

    public static final int MAPPED_TOS_PER_ENTITY = 2;
    public static final int UNMAPPED_TOS_PER_ENTITY = 9;

    public static final int TO_ATTRIBUTES_PER_TO = 3;
    public static final int TO_RELATIONS_PER_TO = 3;

    // Types
    private StringType stringType;
    private NumericType intType;
    private NumericType decimalType;
    private BooleanType booleanType;
    private TimestampType timestampType;
    private EnumerationType statusEnum;

    // Measure types (when includeMeasures is true)
    private Measure lengthMeasure;
    private Unit meterUnit;
    private Unit centimeterUnit;
    private Measure massMeasure;
    private Unit kilogramUnit;
    private DerivedMeasure areaMeasure;
    private Unit squareMeterUnit;
    private MeasuredType lengthType;
    private MeasuredType massType;
    private MeasuredType areaType;

    @Getter
    @Builder
    public static class GeneratorConfig {
        @Builder.Default
        private int entityCount = 70;

        @Builder.Default
        private boolean includeMeasures = false;

        @Builder.Default
        private String modelName = "RealisticTestModel";
    }

    /**
     * Generate a PSM model with the specified number of entities.
     * Uses RackInspect-like ratios for attributes, relations, and transfer objects.
     */
    public PsmModel generate(int entityCount) {
        return generate(GeneratorConfig.builder().entityCount(entityCount).build());
    }

    /**
     * Generate a PSM model with measures included.
     */
    public PsmModel generateWithMeasures(int entityCount) {
        return generate(GeneratorConfig.builder()
                .entityCount(entityCount)
                .includeMeasures(true)
                .build());
    }

    /**
     * Generate a PSM model with full configuration.
     */
    public PsmModel generate(GeneratorConfig config) {
        PsmModel psmModel = buildPsmModel().build();

        initializeTypes();

        List<NamespaceElement> measureElements = new ArrayList<>();
        if (config.isIncludeMeasures()) {
            initializeMeasures();
            measureElements.addAll(createMeasureElements());
        }

        List<EntityType> entities = new ArrayList<>();
        List<MappedTransferObjectType> mappedTOs = new ArrayList<>();
        List<UnmappedTransferObjectType> unmappedTOs = new ArrayList<>();

        // Phase 1: Create all entities with attributes
        for (int i = 0; i < config.getEntityCount(); i++) {
            EntityType entity = newEntityTypeBuilder()
                    .withName("Entity" + i)
                    .build();

            // Add attributes (mix of types)
            for (int j = 0; j < ATTRIBUTES_PER_ENTITY; j++) {
                Attribute attr = createAttribute(j, i, config.isIncludeMeasures());
                entity.getAttributes().add(attr);
            }

            entities.add(entity);
        }

        // Phase 2: Add relations between entities (association ends)
        for (int i = 0; i < config.getEntityCount(); i++) {
            EntityType source = entities.get(i);

            for (int j = 0; j < ASSOCIATION_ENDS_PER_ENTITY; j++) {
                int targetIndex = (i + j + 1) % config.getEntityCount();
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
            if (i % 2 == 0 && i + 1 < config.getEntityCount()) {
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
        for (int i = 0; i < config.getEntityCount(); i++) {
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
                int targetIndex = (i + j + 2) % config.getEntityCount();
                EntityType target = entities.get(targetIndex);

                NavigationProperty navProp = newNavigationPropertyBuilder()
                        .withName("nav" + j + "To" + target.getName())
                        .withTarget(target)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(-1)
                                .build())
                        .withGetterExpression(newReferenceExpressionTypeBuilder()
                                .withExpression("self.assoc0To" + entities.get((i + 1) % config.getEntityCount()).getName())
                                .withDialect(hu.blackbelt.judo.meta.psm.derived.ExpressionDialect.JQL)
                                .build())
                        .build();
                entity.getNavigationProperties().add(navProp);
            }
        }

        // Phase 4: Create mapped transfer objects (for each entity)
        for (int i = 0; i < config.getEntityCount(); i++) {
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
        for (int i = 0; i < config.getEntityCount(); i++) {
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
        if (config.isIncludeMeasures()) {
            entityElements.add(lengthType);
            entityElements.add(massType);
            entityElements.add(areaType);
        }
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

        List<Package> packages = new ArrayList<>();
        packages.add(entitiesPackage);
        packages.add(servicesPackage);

        if (config.isIncludeMeasures() && !measureElements.isEmpty()) {
            Package measuresPackage = newPackageBuilder()
                    .withName("measures")
                    .withElements(ImmutableList.copyOf(measureElements))
                    .build();
            packages.add(measuresPackage);
        }

        Model model = newModelBuilder()
                .withName(config.getModelName())
                .withPackages(ImmutableList.copyOf(packages))
                .build();

        psmModel.addContent(model);

        return psmModel;
    }

    private void initializeTypes() {
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

    private void initializeMeasures() {
        // Length measure with units
        meterUnit = newUnitBuilder()
                .withName("meter")
                .withSymbol("m")
                .withRateDividend(1.0)
                .withRateDivisor(1.0)
                .build();

        centimeterUnit = newUnitBuilder()
                .withName("centimeter")
                .withSymbol("cm")
                .withRateDividend(1.0)
                .withRateDivisor(100.0)
                .build();

        lengthMeasure = newMeasureBuilder()
                .withName("Length")
                .withUnits(meterUnit, centimeterUnit)
                .build();

        // Mass measure
        kilogramUnit = newUnitBuilder()
                .withName("kilogram")
                .withSymbol("kg")
                .withRateDividend(1.0)
                .withRateDivisor(1.0)
                .build();

        massMeasure = newMeasureBuilder()
                .withName("Mass")
                .withUnits(kilogramUnit)
                .build();

        // Derived area measure (Length * Length)
        squareMeterUnit = newUnitBuilder()
                .withName("squareMeter")
                .withSymbol("m2")
                .withRateDividend(1.0)
                .withRateDivisor(1.0)
                .build();

        areaMeasure = newDerivedMeasureBuilder()
                .withName("Area")
                .withUnits(squareMeterUnit)
                .withTerms(
                        newMeasureDefinitionTermBuilder()
                                .withUnit(meterUnit)
                                .withExponent(2)
                                .build()
                )
                .build();

        // Create measured types
        lengthType = newMeasuredTypeBuilder()
                .withName("LengthType")
                .withPrecision(15)
                .withScale(4)
                .withStoreUnit(meterUnit)
                .build();

        massType = newMeasuredTypeBuilder()
                .withName("MassType")
                .withPrecision(15)
                .withScale(4)
                .withStoreUnit(kilogramUnit)
                .build();

        areaType = newMeasuredTypeBuilder()
                .withName("AreaType")
                .withPrecision(15)
                .withScale(4)
                .withStoreUnit(squareMeterUnit)
                .build();
    }

    private List<NamespaceElement> createMeasureElements() {
        List<NamespaceElement> elements = new ArrayList<>();
        elements.add(lengthMeasure);
        elements.add(massMeasure);
        elements.add(areaMeasure);
        return elements;
    }

    private Attribute createAttribute(int index, int entityIndex, boolean includeMeasures) {
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
                if (includeMeasures && entityIndex % 3 == 0) {
                    return newAttributeBuilder()
                            .withName("lengthAttr" + index)
                            .withDataType(lengthType)
                            .build();
                }
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

    /**
     * Count all elements in a PSM model.
     */
    public static int countElements(PsmModel psmModel) {
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
}
