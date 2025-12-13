package hu.blackbelt.judo.tatami.psm2asm;

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
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.data.AssociationEnd;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.derived.ExpressionDialect;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.Primitive;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformationV2;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newReferenceExpressionTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newStaticNavigationBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newPackageBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newCardinalityBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newStringTypeBuilder;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmInheritanceTest {


    public static final String MODEL_NAME = "inheritanceModel";
    public static final String INHERITANCE_ASM_MODEL = "inheritance-asm.model";

    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;
    AsmModel asmModel;

    @BeforeEach
    public void setUp() throws Exception {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .build();

        // When model is invalid the loader have to throw exception. This checks that invalid model cannot valid -if
        // the loading check does not run caused by some reason
        assertTrue(psmModel.isValid());

        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        // Create empty ASM model
        asmModel = buildAsmModel()
                .build();
    }

    private void transform(final String testName, final TransformationType transformationType) throws Exception {
        if (transformationType == TransformationType.ZETA) {
            log.info("Running Zeta transformation for test: {}", testName);
            Psm2AsmZetaTransformationV2 transformation = Psm2AsmZetaTransformationV2.builder()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .modelName("model")
                    .build();
            transformation.execute();
        } else {
            log.info("Running ETL transformation for test: {}", testName);
            executePsm2AsmTransformation(psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel));
        }

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .outputStream(new FileOutputStream(new File(TARGET_TEST_CLASSES, testName + "-" + transformationType + "-" + INHERITANCE_ASM_MODEL))));
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
        Psm2AsmZetaTransformationV2 zetaTransformation = Psm2AsmZetaTransformationV2.builder()
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

    @ParameterizedTest(name = "testInheritance with {0}")
    @EnumSource(TransformationType.class)
    public void testInheritance(TransformationType transformationType) throws Exception {
        log.info("testInheritance~~~~~~~~~~~~~~~~~~~~");
        Primitive string = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        EntityType personEntity = newEntityTypeBuilder().withName("Person")
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("firstName").withDataType(string).withRequired(true).build(),
                        newAttributeBuilder().withName("lastName").withDataType(string).withRequired(true).build(),
                        newAttributeBuilder().withName("title").withDataType(string).build()
                )).build();

        TransferObjectType personTransferObject = newMappedTransferObjectTypeBuilder().withName("MTO_Person")
                .withEntityType(personEntity)
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(string).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(string).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(string).build()
                ))
                .build();

        EntityType employeeEntity = newEntityTypeBuilder().withName("Employee").withSuperEntityTypes(personEntity)
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("titleOfCourtesy").withDataType(string).build()
                ))
                .build();

        MappedTransferObjectType employeeTransferObject = newMappedTransferObjectTypeBuilder().withName("MTO_Employee")
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(string).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(string).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(string).build()
                ))
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("titleOfCourtesy").withDataType(string).build()))
                .withEntityType(employeeEntity)
                .build();

        AssociationEnd ownerAssociationEnd = newAssociationEndBuilder().withName("owner")
                .withTarget(employeeEntity)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        EntityType categoryEntity = newEntityTypeBuilder().withName("Category")
                .withRelations(ImmutableList.of(
                        ownerAssociationEnd
                ))
                .build();

        TransferObjectRelation ownerTransferRelation = newTransferObjectRelationBuilder().withName("owner")
                .withTarget(employeeTransferObject)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        MappedTransferObjectType categoryTransferObject = newMappedTransferObjectTypeBuilder().withName("MTO_Category")
                .withEntityType(categoryEntity)
                .withRelations(ImmutableList.of(
                        ownerTransferRelation
                ))
                .build();

        AssociationEnd categoryAssociationEnd = newAssociationEndBuilder()
                .withName("category")
                .withTarget(categoryEntity)
                .withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build())
                .build();
        EntityType productEntity = newEntityTypeBuilder().withName("Product")
                .withRelations(ImmutableList.of(
                        categoryAssociationEnd
                ))
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("productName").withDataType(string).withRequired(true).build()
                ))
                .build();

        TransferObjectRelation categoryTransferRelation = newTransferObjectRelationBuilder()
                .withName("category")
                .withTarget(categoryTransferObject)
                .withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build())
                .build();
        MappedTransferObjectType productTransferObject = newMappedTransferObjectTypeBuilder().withName("MTO_Product")
                .withEntityType(productEntity)
                .withRelations(ImmutableList.of(
                        categoryTransferRelation
                ))
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("productName").withDataType(string).withRequired(true).build()
                ))
                .build();

        StaticNavigation productSelector = newStaticNavigationBuilder().withName("productsCategoriesOwner_selector")
                .withTarget(categoryEntity)
                .withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build())
                .withGetterExpression(newReferenceExpressionTypeBuilder().withDialect(ExpressionDialect.JQL).withExpression("model::entities::Category.owner").build())
                .build();

        UnmappedTransferObjectType accessPoint = newUnmappedTransferObjectTypeBuilder().withName("AP")
                .withRelations(
                        newTransferObjectRelationBuilder().withName("productsCategoriesOwner")
                                .withTarget(productTransferObject)
                                .withBinding(productSelector)
                                .withCardinality(newCardinalityBuilder().withLower(1).withUpper(1).build()).build()
                ).build();
        ActorType actor = newActorTypeBuilder()
                .withName("Actor")
                .withTransferObjectType(accessPoint)
                .build();

        Package entities = newPackageBuilder().withName("entities").withElements(ImmutableList.of(
                categoryEntity, employeeEntity, personEntity, productEntity
        )).build();

        Package service = newPackageBuilder().withName("service").withElements(ImmutableList.of(
                categoryTransferObject, employeeTransferObject, personTransferObject, productTransferObject
        )).build();

        Package types = newPackageBuilder().withName("types").withElements(ImmutableList.of(string)).build();

        Package navigations = newPackageBuilder().withName("navigations").withElements(ImmutableList.of(productSelector)).build();

        Model model = newModelBuilder().withName("model")
                .withPackages(ImmutableList.of(entities, service, types, navigations))
                .withElements(ImmutableList.of(accessPoint, actor))
                .build();
        psmModel.addContent(model);

        transform("testInheritance", transformationType);

        final Optional<EClass> asmEmployeeTransferObject = allAsm(EClass.class).filter(clazz -> employeeTransferObject.getName().equals(clazz.getName())).findAny();
        assertTrue(asmEmployeeTransferObject.isPresent());
//        assertTrue(AsmUtils.getExtensionAnnotationByName(asmEmployeeTransferObject.get(), "exposedBy", false).isPresent());

        final Optional<EAttribute> firstNameInEmployeeTransferObject = asmEmployeeTransferObject.get().getEAllAttributes().stream().filter(attribute -> "firstName".equals(attribute.getName())).findAny();
        assertTrue(firstNameInEmployeeTransferObject.isPresent());

        final Optional<EAnnotation> exposedByAnnotationOfFirstNameInEmployeeTransferObject = AsmUtils.getExtensionAnnotationByName(firstNameInEmployeeTransferObject.get(), "exposedBy", false);
//        assertTrue(exposedByAnnotationOfFirstNameInEmployeeTransferObject.isPresent());
//        assertTrue(exposedByAnnotationOfFirstNameInEmployeeTransferObject.get().getDetails().containsValue("model.AP"));

        final Optional<EClass> asmPersonTransferObject = allAsm(EClass.class).filter(t -> personTransferObject.getName().equals(t.getName())).findAny();
        assertTrue(asmPersonTransferObject.isPresent());

        final Optional<EAttribute> firstNameInPersonTransferObject = asmPersonTransferObject.get().getEAllAttributes().stream().filter(attribute -> "firstName".equals(attribute.getName())).findAny();
        assertTrue(firstNameInPersonTransferObject.isPresent());

        final Optional<EAnnotation> exposedByAnnotationOfFirstNameInPersonTransferObject = AsmUtils.getExtensionAnnotationByName(firstNameInPersonTransferObject.get(), "exposedBy", false);
//        assertTrue(exposedByAnnotationOfFirstNameInPersonTransferObject.isPresent());
//        assertTrue(exposedByAnnotationOfFirstNameInPersonTransferObject.get().getDetails().containsValue("model.AP"));

        compareTransformations("testInheritance");
    }

    static <T> Stream<T> asStream(Iterator<T> sourceIterator, boolean parallel) {
        Iterable<T> iterable = () -> sourceIterator;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    <T> Stream<T> allAsm() {
        return asStream((Iterator<T>) asmModel.getResourceSet().getAllContents(), false);
    }

    private <T> Stream<T> allAsm(final Class<T> clazz) {
        return allAsm().filter(e -> clazz.isAssignableFrom(e.getClass())).map(e -> (T) e);
    }

    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Build model for ETL
        Primitive stringEtl = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        EntityType personEntityEtl = newEntityTypeBuilder().withName("Person")
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("firstName").withDataType(stringEtl).withRequired(true).build(),
                        newAttributeBuilder().withName("lastName").withDataType(stringEtl).withRequired(true).build(),
                        newAttributeBuilder().withName("title").withDataType(stringEtl).build()
                )).build();
        TransferObjectType personTransferObjectEtl = newMappedTransferObjectTypeBuilder().withName("MTO_Person")
                .withEntityType(personEntityEtl)
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(stringEtl).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(stringEtl).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(stringEtl).build()
                ))
                .build();
        EntityType employeeEntityEtl = newEntityTypeBuilder().withName("Employee").withSuperEntityTypes(personEntityEtl)
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("titleOfCourtesy").withDataType(stringEtl).build()
                ))
                .build();
        MappedTransferObjectType employeeTransferObjectEtl = newMappedTransferObjectTypeBuilder().withName("MTO_Employee")
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(stringEtl).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(stringEtl).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(stringEtl).build()
                ))
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("titleOfCourtesy").withDataType(stringEtl).build()))
                .withEntityType(employeeEntityEtl)
                .build();
        AssociationEnd ownerAssociationEndEtl = newAssociationEndBuilder().withName("owner")
                .withTarget(employeeEntityEtl)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        EntityType categoryEntityEtl = newEntityTypeBuilder().withName("Category")
                .withRelations(ImmutableList.of(ownerAssociationEndEtl))
                .build();
        TransferObjectRelation ownerTransferRelationEtl = newTransferObjectRelationBuilder().withName("owner")
                .withTarget(employeeTransferObjectEtl)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        MappedTransferObjectType categoryTransferObjectEtl = newMappedTransferObjectTypeBuilder().withName("MTO_Category")
                .withEntityType(categoryEntityEtl)
                .withRelations(ImmutableList.of(ownerTransferRelationEtl))
                .build();
        Package entitiesEtl = newPackageBuilder().withName("entities").withElements(ImmutableList.of(
                categoryEntityEtl, employeeEntityEtl, personEntityEtl
        )).build();
        Package serviceEtl = newPackageBuilder().withName("service").withElements(ImmutableList.of(
                categoryTransferObjectEtl, employeeTransferObjectEtl, personTransferObjectEtl
        )).build();
        Package typesEtl = newPackageBuilder().withName("types").withElements(ImmutableList.of(stringEtl)).build();
        Model modelEtl = newModelBuilder().withName("model")
                .withPackages(ImmutableList.of(entitiesEtl, serviceEtl, typesEtl))
                .build();

        PsmModel psmModelEtl = buildPsmModel().build();
        psmModelEtl.addContent(modelEtl);
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(etlResult));

        // Build model for Zeta
        Primitive stringZeta = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        EntityType personEntityZeta = newEntityTypeBuilder().withName("Person")
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("firstName").withDataType(stringZeta).withRequired(true).build(),
                        newAttributeBuilder().withName("lastName").withDataType(stringZeta).withRequired(true).build(),
                        newAttributeBuilder().withName("title").withDataType(stringZeta).build()
                )).build();
        TransferObjectType personTransferObjectZeta = newMappedTransferObjectTypeBuilder().withName("MTO_Person")
                .withEntityType(personEntityZeta)
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(stringZeta).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(stringZeta).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(stringZeta).build()
                ))
                .build();
        EntityType employeeEntityZeta = newEntityTypeBuilder().withName("Employee").withSuperEntityTypes(personEntityZeta)
                .withAttributes(ImmutableList.of(
                        newAttributeBuilder().withName("titleOfCourtesy").withDataType(stringZeta).build()
                ))
                .build();
        MappedTransferObjectType employeeTransferObjectZeta = newMappedTransferObjectTypeBuilder().withName("MTO_Employee")
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("firstName").withDataType(stringZeta).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("lastName").withDataType(stringZeta).withRequired(true).build(),
                        newTransferAttributeBuilder().withName("title").withDataType(stringZeta).build()
                ))
                .withAttributes(ImmutableList.of(
                        newTransferAttributeBuilder().withName("titleOfCourtesy").withDataType(stringZeta).build()))
                .withEntityType(employeeEntityZeta)
                .build();
        AssociationEnd ownerAssociationEndZeta = newAssociationEndBuilder().withName("owner")
                .withTarget(employeeEntityZeta)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        EntityType categoryEntityZeta = newEntityTypeBuilder().withName("Category")
                .withRelations(ImmutableList.of(ownerAssociationEndZeta))
                .build();
        TransferObjectRelation ownerTransferRelationZeta = newTransferObjectRelationBuilder().withName("owner")
                .withTarget(employeeTransferObjectZeta)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(1).build())
                .build();
        MappedTransferObjectType categoryTransferObjectZeta = newMappedTransferObjectTypeBuilder().withName("MTO_Category")
                .withEntityType(categoryEntityZeta)
                .withRelations(ImmutableList.of(ownerTransferRelationZeta))
                .build();
        Package entitiesZeta = newPackageBuilder().withName("entities").withElements(ImmutableList.of(
                categoryEntityZeta, employeeEntityZeta, personEntityZeta
        )).build();
        Package serviceZeta = newPackageBuilder().withName("service").withElements(ImmutableList.of(
                categoryTransferObjectZeta, employeeTransferObjectZeta, personTransferObjectZeta
        )).build();
        Package typesZeta = newPackageBuilder().withName("types").withElements(ImmutableList.of(stringZeta)).build();
        Model modelZeta = newModelBuilder().withName("model")
                .withPackages(ImmutableList.of(entitiesZeta, serviceZeta, typesZeta))
                .build();

        PsmModel psmModelZeta = buildPsmModel().build();
        psmModelZeta.addContent(modelZeta);
        AsmModel zetaResult = buildAsmModel().build();
        Psm2AsmZetaTransformationV2 zetaTransformation = Psm2AsmZetaTransformationV2.builder()
                .psmModel(psmModelZeta)
                .asmModel(zetaResult)
                .modelName("model")
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
