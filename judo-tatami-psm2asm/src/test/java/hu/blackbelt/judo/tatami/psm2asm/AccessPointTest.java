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

import org.slf4j.Logger;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders;
import hu.blackbelt.judo.meta.psm.derived.ExpressionDialect;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.StringType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.psm2asm.zeta.Psm2AsmZetaTransformation;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.*;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newReferenceExpressionTypeBuilder;
import static hu.blackbelt.judo.meta.psm.derived.util.builder.DerivedBuilders.newStaticNavigationBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.*;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newCardinalityBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.newStringTypeBuilder;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class AccessPointTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;
    AsmModel asmModel;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .build();
        // Create empty ASM model
        asmModel = buildAsmModel()
                .build();
    }

    private void transform(final String testName, final TransformationType transformationType) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());

        try (BufferedSlf4jLogger bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        if (transformationType == TransformationType.ZETA) {
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

        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
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

    @ParameterizedTest(name = "testGetPrincipalOperations with {0}")
    @EnumSource(TransformationType.class)
    void testGetPrincipalOperations(TransformationType transformationType) throws Exception {
        final ActorType actor1 = newActorTypeBuilder()
                .withName("Actor1")
                .withRealm("realm1")
                .build();
        final UnmappedTransferObjectType ap1 = newUnmappedTransferObjectTypeBuilder()
                .withName("AccessPoint1")
                .withActorType(actor1)
                .build();
        useUnmappedTransferObjectType(actor1)
                .withOperations(newUnboundOperationBuilder()
                        .withName("getPrincipal1")
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder()
                                        .withLower(0)
                                        .withUpper(1)
                                        .build())
                                .withType(ap1)
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.GET_PRINCIPAL)
                                .withOwner(actor1)
                                .build())
                        .build())
                .build();

        final ActorType actor2 = newActorTypeBuilder()
                .withName("Actor2")
                .withRealm("realm2")
                .build();
        final UnmappedTransferObjectType ap2 = newUnmappedTransferObjectTypeBuilder()
                .withName("AccessPoint2")
                .withActorType(actor2)
                .withRelations(newTransferObjectRelationBuilder()
                        .withName("ap1")
                        .withTarget(ap1)
                        .withEmbedded(true)
                        .withCardinality(newCardinalityBuilder()
                                .withLower(0)
                                .withUpper(1)
                                .build())
                        .build())
                .build();
        useUnmappedTransferObjectType(actor2)
                .withOperations(newUnboundOperationBuilder()
                        .withName("getPrincipal2")
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder()
                                        .withLower(0)
                                        .withUpper(1)
                                        .build())
                                .withType(ap2)
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.GET_PRINCIPAL)
                                .withOwner(actor2)
                                .build())
                        .build())
                .build();

        final Model model = newModelBuilder()
                .withName("Model")
                .withElements(Arrays.asList(actor1, actor2, ap1, ap2))
                .build();

        psmModel.addContent(model);

        transform("testGetPrincipalOperations", transformationType);

        final AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
        final Optional<EOperation> getPrincipal1 = asmUtils.all(EOperation.class)
                .filter(o -> "getPrincipal1".equals(o.getName()))
                .findAny();
        final Optional<EOperation> getPrincipal2 = asmUtils.all(EOperation.class)
                .filter(o -> "getPrincipal2".equals(o.getName()))
                .findAny();

        assertThat(getPrincipal1.isPresent(), equalTo(Boolean.TRUE));
        assertThat(getPrincipal2.isPresent(), equalTo(Boolean.TRUE));

        // ensure that getPrincipal operations are exposed by their own access points only
        assertThat(AsmUtils.getExtensionAnnotationListByName(getPrincipal1.get(), "exposedBy").stream()
                .allMatch(a -> "Model.Actor1".equals(a.getDetails().get("value"))), equalTo(Boolean.TRUE));
        assertThat(AsmUtils.getExtensionAnnotationListByName(getPrincipal1.get(), "exposedBy").stream()
                .anyMatch(a -> "Model.Actor1".equals(a.getDetails().get("value"))), equalTo(Boolean.TRUE));
        assertThat(AsmUtils.getExtensionAnnotationListByName(getPrincipal2.get(), "exposedBy").stream()
                .allMatch(a -> "Model.Actor2".equals(a.getDetails().get("value"))), equalTo(Boolean.TRUE));
        assertThat(AsmUtils.getExtensionAnnotationListByName(getPrincipal2.get(), "exposedBy").stream()
                .anyMatch(a -> "Model.Actor2".equals(a.getDetails().get("value"))), equalTo(Boolean.TRUE));

        compareTransformations("testGetPrincipalOperations");
    }

    @ParameterizedTest(name = "testExposedServicesAndGraphs with {0}")
    @EnumSource(TransformationType.class)
    void testExposedServicesAndGraphs(TransformationType transformationType) throws Exception {
        final StringType string = newStringTypeBuilder()
                .withName("String")
                .withMaxLength(255)
                .build();

        final BoundOperation delete = newBoundOperationBuilder()
                .withName("delete")
                .withImplementation(newOperationBodyBuilder()
                        .withBody("delete __this")
                        .build())
                .build();
        final BoundOperation getAuthor = newBoundOperationBuilder()
                .withName("_getAuthor")
                .build();
        final Attribute messageBody = newAttributeBuilder()
                .withName("body")
                .withDataType(string)
                .withRequired(true)
                .build();
        final AssociationEnd authorOfMessage = DataBuilders.newAssociationEndBuilder()
                .withName("author")
                .withCardinality(newCardinalityBuilder().build())
                .build();
        final EntityType message = newEntityTypeBuilder()
                .withName("Message")
                .withAttributes(messageBody)
                .withRelations(authorOfMessage)
                .withOperations(delete)
                .withOperations(getAuthor)
                .build();

        final BoundOperation getMessages = newBoundOperationBuilder()
                .withName("_getMessages")
                .build();
        final Attribute userName = newAttributeBuilder()
                .withName("name")
                .withDataType(string)
                .withRequired(true)
                .build();
        final Attribute userEmail = newAttributeBuilder()
                .withName("email")
                .withDataType(string)
                .withRequired(false)
                .build();
        final AssociationEnd messagesOfUser = newAssociationEndBuilder()
                .withName("messages")
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .withPartner(authorOfMessage)
                .withTarget(message)
                .build();
        final EntityType user = newEntityTypeBuilder()
                .withName("User")
                .withAttributes(userName)
                .withAttributes(userEmail)
                .withRelations(messagesOfUser)
                .withOperations(getMessages)
                .build();
        useAssociationEnd(authorOfMessage)
                .withTarget(user)
                .withPartner(messagesOfUser)
                .build();

        final UnmappedTransferObjectType emailDTO = newUnmappedTransferObjectTypeBuilder()
                .withName("EmailDTO")
                .withAttributes(newTransferAttributeBuilder()
                        .withName("email")
                        .withDataType(string)
                        .withRequired(true)
                        .build())
                .build();

        final MappedTransferObjectType messageDTO = newMappedTransferObjectTypeBuilder().build();
        final MappedTransferObjectType userDTO = newMappedTransferObjectTypeBuilder().build();
        useBoundOperation(delete)
                .withInstanceRepresentation(messageDTO)
                .build();
        final TransferObjectRelation authorOfMessageDTO = newTransferObjectRelationBuilder()
                .withName("author")
                .withEmbedded(true)
                .withTarget(userDTO)
                .withCardinality(newCardinalityBuilder().build())
                .withBinding(authorOfMessage)
                .build();
        useMappedTransferObjectType(messageDTO)
                .withName("MessageDTO")
                .withEntityType(message)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("body")
                        .withDataType(string)
                        .withRequired(true)
                        .withBinding(messageBody)
                        .build())
                .withRelations(authorOfMessageDTO)
                .withOperations(newBoundTransferOperationBuilder()
                        .withName("delete")
                        .withBinding(delete)
                        .build())
                .withOperations(newUnboundOperationBuilder()
                        .withName("echo")
                        .withInput(newParameterBuilder()
                                .withName("input")
                                .withCardinality(newCardinalityBuilder().withLower(1).build())
                                .withType(messageDTO)
                                .build())
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder().withLower(1).build())
                                .withType(messageDTO)
                                .build())
                        .withImplementation(newOperationBodyBuilder()
                                .withBody("return input;")
                                .build())
                        .build())
                .withOperations(newUnboundOperationBuilder()
                        .withName("getAllMessagesOf")
                        .withInput(newParameterBuilder()
                                .withName("input")
                                .withCardinality(newCardinalityBuilder().withLower(1).build())
                                .withType(userDTO)
                                .build())
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                                .withType(messageDTO)
                                .build())
                        .withImplementation(newOperationBodyBuilder()
                                .withBody("return Model::MessageDTO!filter(m | m.author == input);")
                                .build())
                        .build())
                .build();
        useBoundOperation(getMessages)
                .withInstanceRepresentation(userDTO)
                .withOutput(newParameterBuilder()
                        .withName("output")
                        .withType(messageDTO)
                        .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                        .build())
                .build();
        final TransferObjectRelation messagesOfUserDTO = newTransferObjectRelationBuilder()
                .withName("messages")
                .withTarget(messageDTO)
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .withBinding(messagesOfUser)
                .build();
        useMappedTransferObjectType(userDTO)
                .withName("UserDTO")
                .withEntityType(user)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("name")
                        .withDataType(string)
                        .withRequired(true)
                        .withBinding(userName)
                        .build())
                .withRelations(messagesOfUserDTO)
                .withOperations(newUnboundOperationBuilder()
                        .withName("checkEmailIsUnique")
                        .withInput(newParameterBuilder()
                                .withName("input")
                                .withType(emailDTO)
                                .withCardinality(newCardinalityBuilder().withLower(1).build())
                                .build())
                        .withImplementation(newOperationBodyBuilder()
                                .withCustomImplementation(true)
                                .build())
                        .build())
                .withOperations(newBoundTransferOperationBuilder()
                        .withName("_getMessages")
                        .withBinding(getMessages)
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withType(messageDTO)
                                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.LIST)
                                .withOwner(messagesOfUserDTO)
                                .build())
                        .build())
                .build();
        useBoundOperation(getAuthor)
                .withInstanceRepresentation(messageDTO)
                .withOutput(newParameterBuilder()
                        .withName("output")
                        .withType(userDTO)
                        .withCardinality(newCardinalityBuilder().build())
                        .build())
                .build();
        useMappedTransferObjectType(messageDTO)
                .withOperations(newBoundTransferOperationBuilder()
                        .withName("_getAuthor")
                        .withBinding(getAuthor)
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withType(userDTO)
                                .withCardinality(newCardinalityBuilder().build())
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.LIST)
                                .withOwner(authorOfMessageDTO)
                                .build())
                        .build())
                .build();

        final StaticNavigation allMessages = newStaticNavigationBuilder()
                .withName("AllMessages")
                .withTarget(message)
                .withGetterExpression(newReferenceExpressionTypeBuilder()
                        .withDialect(ExpressionDialect.JQL)
                        .withExpression("Model::Message")
                        .build())
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .build();

        final StaticNavigation allUsers = newStaticNavigationBuilder()
                .withName("AllUsers")
                .withTarget(user)
                .withGetterExpression(newReferenceExpressionTypeBuilder()
                        .withDialect(ExpressionDialect.JQL)
                        .withExpression("Model::User")
                        .build())
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .build();

        final UnmappedTransferObjectType accessPoint1 = newUnmappedTransferObjectTypeBuilder()
                .withName("AP1")
                .withRelations(newTransferObjectRelationBuilder()
                        .withName("messenger")
                        .withTarget(messageDTO)
                        .withCardinality(newCardinalityBuilder().build())
                        .build())
                .build();

        final ActorType actor1 = newActorTypeBuilder()
                .withName("Actor1")
                .withTransferObjectType(accessPoint1)
                .build();

        final TransferObjectRelation allMessagesGraph = newTransferObjectRelationBuilder()
                .withName("allMessages")
                .withTarget(messageDTO)
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .withBinding(allMessages)
                .build();

        final TransferObjectRelation allUsersGraph = newTransferObjectRelationBuilder()
                .withName("allUsers")
                .withTarget(userDTO)
                .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                .withBinding(allUsers)
                .build();

        final UnmappedTransferObjectType accessPoint2 = newUnmappedTransferObjectTypeBuilder()
                .withName("AP2")
                .withRelations(allMessagesGraph)
                .build();
        useUnmappedTransferObjectType(accessPoint2)
            .withOperations(newUnboundOperationBuilder()
                    .withName("_getAllMessages")
                    .withOutput(newParameterBuilder()
                            .withName("output")
                            .withType(messageDTO)
                            .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                            .build())
                    .withBehaviour(newTransferOperationBehaviourBuilder()
                            .withBehaviourType(TransferOperationBehaviourType.LIST)
                            .withOwner(allMessagesGraph)
                            .build())
                    .build())
            .build();

        final ActorType actor2 = newActorTypeBuilder()
                .withName("Actor2")
                .withTransferObjectType(accessPoint2)
                .build();

        final UnmappedTransferObjectType accessPoint3 = newUnmappedTransferObjectTypeBuilder()
                .withName("AP3")
                .withRelations(allUsersGraph)
                .build();
        useUnmappedTransferObjectType(accessPoint3)
        .withOperations(newUnboundOperationBuilder()
                .withName("_getAllUsers")
                .withOutput(newParameterBuilder()
                        .withName("output")
                        .withType(userDTO)
                        .withCardinality(newCardinalityBuilder().withUpper(-1).build())
                        .build())
                .withBehaviour(newTransferOperationBehaviourBuilder()
                        .withBehaviourType(TransferOperationBehaviourType.LIST)
                        .withOwner(allUsersGraph)
                        .build())
                .build())
        .build();

        final ActorType actor3 = newActorTypeBuilder()
                .withName("Actor3")
                .withTransferObjectType(accessPoint3)
                .build();

        final Model model = newModelBuilder()
                .withName("Model")
                .withElements(Arrays.asList(string, message, user, messageDTO, userDTO, emailDTO, allMessages, allUsers, accessPoint1, accessPoint2, accessPoint3, actor1, actor2, actor3))
                .build();

        psmModel.addContent(model);

        transform("testExposedServicesAndGraphs", transformationType);

        compareTransformations("testExposedServicesAndGraphs");
    }

    @Test
    void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Build model for ETL
        final ActorType actor1Etl = newActorTypeBuilder()
                .withName("Actor1")
                .withRealm("realm1")
                .build();
        final UnmappedTransferObjectType ap1Etl = newUnmappedTransferObjectTypeBuilder()
                .withName("AccessPoint1")
                .withActorType(actor1Etl)
                .build();
        useUnmappedTransferObjectType(actor1Etl)
                .withOperations(newUnboundOperationBuilder()
                        .withName("getPrincipal1")
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder()
                                        .withLower(0)
                                        .withUpper(1)
                                        .build())
                                .withType(ap1Etl)
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.GET_PRINCIPAL)
                                .withOwner(actor1Etl)
                                .build())
                        .build())
                .build();

        final Model modelEtl = newModelBuilder()
                .withName("Model")
                .withElements(Arrays.asList(actor1Etl, ap1Etl))
                .build();

        PsmModel psmModelEtl = buildPsmModel().build();
        psmModelEtl.addContent(modelEtl);
        AsmModel etlResult = buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(etlResult));

        // Build model for Zeta
        final ActorType actor1Zeta = newActorTypeBuilder()
                .withName("Actor1")
                .withRealm("realm1")
                .build();
        final UnmappedTransferObjectType ap1Zeta = newUnmappedTransferObjectTypeBuilder()
                .withName("AccessPoint1")
                .withActorType(actor1Zeta)
                .build();
        useUnmappedTransferObjectType(actor1Zeta)
                .withOperations(newUnboundOperationBuilder()
                        .withName("getPrincipal1")
                        .withOutput(newParameterBuilder()
                                .withName("output")
                                .withCardinality(newCardinalityBuilder()
                                        .withLower(0)
                                        .withUpper(1)
                                        .build())
                                .withType(ap1Zeta)
                                .build())
                        .withBehaviour(newTransferOperationBehaviourBuilder()
                                .withBehaviourType(TransferOperationBehaviourType.GET_PRINCIPAL)
                                .withOwner(actor1Zeta)
                                .build())
                        .build())
                .build();

        final Model modelZeta = newModelBuilder()
                .withName("Model")
                .withElements(Arrays.asList(actor1Zeta, ap1Zeta))
                .build();

        PsmModel psmModelZeta = buildPsmModel().build();
        psmModelZeta.addContent(modelZeta);
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
