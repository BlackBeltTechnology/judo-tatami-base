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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testInheritance() throws Exception {
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

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .outputStream(new FileOutputStream(new File(TARGET_TEST_CLASSES, INHERITANCE_ASM_MODEL))));

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

}
