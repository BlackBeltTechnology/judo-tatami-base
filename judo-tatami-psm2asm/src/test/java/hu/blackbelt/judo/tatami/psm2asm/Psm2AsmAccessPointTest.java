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
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.AbstractActorType;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.data.*;
import hu.blackbelt.judo.meta.psm.derived.StaticNavigation;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.service.*;
import hu.blackbelt.judo.meta.psm.type.StringType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.SaveArguments.asmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.calculatePsmValidationScriptURI;
import static hu.blackbelt.judo.meta.psm.PsmEpsilonValidator.validatePsm;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newMappedActorTypeBuilder;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Psm2AsmAccessPointTest {

    public static final String MODEL_NAME = "Test";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    public static final String EXPRESSION_SOURCE = AsmUtils.getAnnotationUri("expression");

    PsmModel psmModel;
    AsmModel asmModel;
    AsmUtils asmUtils;

    @BeforeEach
    public void setUp() {
        // Loading PSM to isolated ResourceSet, because in Tatami
        // there is no new namespace registration made.
        psmModel = buildPsmModel()
                .name(MODEL_NAME)
                .build();
        // Create empty ASM model
        asmModel = buildAsmModel()
                .name(MODEL_NAME)
                .build();
        asmUtils = new AsmUtils(asmModel.getResourceSet());
    }

    private void transform(final String testName) throws Exception {
        psmModel.savePsmModel(PsmModel.SaveArguments.psmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-psm.model"))
                .build());

        assertTrue(psmModel.isValid());
        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            validatePsm(bufferedLog, psmModel, calculatePsmValidationScriptURI());
        }

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        assertTrue(asmModel.isValid());
        asmModel.saveAsmModel(asmSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, getClass().getName() + "-" + testName + "-asm.model"))
                .build());
    }

    @Test
    void testEntityTypeAsAccessPoint() throws Exception {
        final StringType stringType = newStringTypeBuilder()
                .withName("String")
                .withMaxLength(255)
                .build();

        final Attribute nameOfRelatedEntity = newAttributeBuilder()
                .withName("name")
                .withDataType(stringType)
                .build();
        final EntityType relatedEntity = newEntityTypeBuilder()
                .withName("RelatedEntity")
                .withAttributes(nameOfRelatedEntity)
                .build();

        final Attribute textOfEntity = newAttributeBuilder()
                .withName("text")
                .withDataType(stringType)
                .build();
        final Relation relatedOfEntity = newAssociationEndBuilder()
                .withName("related")
                .withCardinality(newCardinalityBuilder().build())
                .withTarget(relatedEntity)
                .build();
        final EntityType entity = newEntityTypeBuilder()
                .withName("Entity")
                .withAttributes(textOfEntity)
                .withRelations(relatedOfEntity)
                .build();

        final TransferObjectType relatedEntityDTO = newMappedTransferObjectTypeBuilder()
                .withName("RelatedEntity")
                .withEntityType(relatedEntity)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("name")
                        .withDataType(stringType)
                        .withBinding(nameOfRelatedEntity)
                        .build())
                .build();

        final TransferObjectType entityDTO = newMappedTransferObjectTypeBuilder()
                .withName("Entity")
                .withEntityType(entity)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("text")
                        .withDataType(stringType)
                        .withBinding(textOfEntity)
                        .build())
                .withRelations(newTransferObjectRelationBuilder()
                        .withName("related")
                        .withEmbedded(true)
                        .withCardinality(newCardinalityBuilder().build())
                        .withBinding(relatedOfEntity)
                        .withTarget(relatedEntityDTO)
                        .build())
                .build();

        final AbstractActorType entityActor = newMappedActorTypeBuilder()
                .withName("EntityActor")
                .withRealm("Sandbox")
                .withTransferObjectType(entityDTO)
                .withEntityType(entity)
                .build();

        final Model model = newModelBuilder()
                .withName("demo")
                .withPackages(newPackageBuilder()
                        .withName("types")
                        .withElements(stringType)
                        .build())
                .withPackages(newPackageBuilder()
                        .withName("entities")
                        .withElements(Arrays.asList(entity, relatedEntity))
                        .build())
                .withPackages(newPackageBuilder()
                        .withName("_default_transferobjecttypes")
                        .withElements(Arrays.asList(entityDTO, relatedEntityDTO))
                        .build())
                .withPackages(newPackageBuilder()
                        .withName("services")
                        .withElements(entityActor)
                        .build())
                .build();

        psmModel.addContent(model);

        transform("testEntityTypeAsAccessPoint");

        final Optional<EClass> ap = asmUtils.all(EClass.class).filter(c -> "Entity".equals(c.getName()) && asmUtils.isMappedTransferObjectType(c)).findAny();
        assertThat(ap.isPresent(), equalTo(Boolean.TRUE));
        final Optional<EAnnotation> actorType = AsmUtils.getExtensionAnnotationByName(ap.get(), "actor", false);
        assertThat(actorType.isPresent(), equalTo(Boolean.TRUE));
        assertThat(actorType.get().getDetails().get("realm"), equalTo("Sandbox"));
    }

    @Test
    void testAccessPoint() throws Exception {

        UnmappedTransferObjectType opGroup = newUnmappedTransferObjectTypeBuilder().withName("operationGroup").build();
        TransferObjectRelation eService = newTransferObjectRelationBuilder().withName("eService")
                .withCardinality(newCardinalityBuilder().build())
                .withTarget(opGroup).build();

        EntityType entity = newEntityTypeBuilder().withName("entity").build();
        MappedTransferObjectType mappedObject = newMappedTransferObjectTypeBuilder().withName("mappedObject").withEntityType(entity).build();
        StaticNavigation selector = newStaticNavigationBuilder().withName("selector").withTarget(entity)
                .withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("M::entity").build())
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
                .build();
        TransferObjectRelation eGraph = newTransferObjectRelationBuilder().withTarget(mappedObject)
                .withName("eGraph")
                .withBinding(selector)
                .withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
                .build();

//		StaticNavigation selector2 = newStaticNavigationBuilder().withName("selector2").withTarget(entity)
//				.withGetterExpression(newReferenceExpressionTypeBuilder().withExpression("M::entity").build())
//				.withSetterExpression(newReferenceSelectorTypeBuilder().withExpression("M::entity").build())
//				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
//				.build();
//		ExposedGraph eGraph2 = newExposedGraphBuilder().withMappedTransferObjectType(mappedObject)
//				.withName("eGraph2")
//				.withSelector(selector)
//				.withCardinality(newCardinalityBuilder().withLower(0).withUpper(-1).build())
//				.build();

        UnmappedTransferObjectType accessPoint = newUnmappedTransferObjectTypeBuilder().withName("accessPoint")
                .withRelations(eService)
                .withRelations(ImmutableList.of(eGraph
                        //eGraph2
                ))
                .build();

        ActorType actor = newActorTypeBuilder()
                .withName("Actor")
                .withRealm("Sandbox")
                .withTransferObjectType(accessPoint)
                .build();

        Model model = newModelBuilder().withName("M").withElements(ImmutableList.of(actor, accessPoint, opGroup, entity, mappedObject, selector
                //, selector2
        )).build();

        psmModel.addContent(model);

        transform("testAccessPoint");

        final Optional<EClass> asmAP = asmUtils.all(EClass.class).filter(c -> c.getName().equals(accessPoint.getName())).findAny();
        assertTrue(asmAP.isPresent());

        final Optional<EReference> asmES = asmUtils.all(EReference.class).filter(r -> r.getName().equals(eService.getName())).findAny();
        assertTrue(asmES.isPresent());
        // don't care
        //assertThat(asmES.get().getLowerBound(), IsEqual.equalTo(1));
        //assertThat(asmES.get().getUpperBound(), IsEqual.equalTo(1));
        //assertTrue(asmES.get().isDerived());
        assertTrue(asmES.get().getEContainingClass().equals(asmAP.get()));
        final Optional<EClass> asmOpGroup = asmUtils.all(EClass.class).filter(c -> c.getName().equals(opGroup.getName())).findAny();
        assertTrue(asmOpGroup.isPresent());
        assertTrue(asmES.get().getEType().equals(asmOpGroup.get()));

        final Optional<EReference> asmEG = asmUtils.all(EReference.class).filter(r -> r.getName().equals(eGraph.getName())).findAny();
        assertTrue(asmEG.isPresent());
        assertThat(asmEG.get().getLowerBound(), equalTo(eGraph.getCardinality().getLower()));
        assertThat(asmEG.get().getUpperBound(), equalTo(eGraph.getCardinality().getUpper()));
        assertTrue(asmEG.get().isDerived());
        assertTrue(asmEG.get().getEContainingClass().equals(asmAP.get()));

        final Optional<EClass> asmMappedObject = asmUtils.all(EClass.class).filter(c -> c.getName().equals(mappedObject.getName())).findAny();
        assertTrue(asmMappedObject.isPresent());
        assertTrue(asmEG.get().getEType().equals(asmMappedObject.get()));
        assertThat(asmEG.get().getEAnnotation(EXPRESSION_SOURCE), notNullValue());
        final EAnnotation egExprAnnotation = asmEG.get().getEAnnotation(EXPRESSION_SOURCE);
        assertTrue(egExprAnnotation.getDetails().containsKey("getter"));
        assertTrue(egExprAnnotation.getDetails().containsKey("getter.dialect"));
        assertTrue(egExprAnnotation.getDetails().get("getter").equals(selector.getGetterExpression().getExpression()));
        assertTrue(egExprAnnotation.getDetails().get("getter.dialect").equals(selector.getGetterExpression().getDialect().toString()));
        assertFalse(egExprAnnotation.getDetails().containsKey("setter"));
        assertFalse(egExprAnnotation.getDetails().containsKey("setter.dialect"));
        assertTrue(egExprAnnotation.getEModelElement().equals(asmEG.get()));

        final Optional<EClass> ap = asmUtils.all(EClass.class).filter(c -> "accessPoint".equals(c.getName())).findAny();
        assertThat(ap.isPresent(), equalTo(Boolean.TRUE));
        final Optional<EAnnotation> actorType = AsmUtils.getExtensionAnnotationByName(ap.get(), "actor", false);
        assertThat(actorType.isPresent(), equalTo(Boolean.TRUE));
        assertThat(actorType.get().getDetails().get("realm"), equalTo("Sandbox"));

//        final Optional<EReference> asmEG2 = asmUtils.all(EReference.class).filter(r -> r.getName().equals(eGraph2.getName())).findAny();
//        assertTrue(asmEG2.isPresent());
//        assertThat(asmEG2.get().getEAnnotation(EXPRESSION_SOURCE), IsNull.notNullValue());
//        final EAnnotation egExprAnnotation2 = asmEG2.get().getEAnnotation(EXPRESSION_SOURCE);
//        assertTrue(egExprAnnotation2.getDetails().containsKey("setter"));
//        assertTrue(egExprAnnotation2.getDetails().containsKey("setter.dialect"));
//        assertTrue(egExprAnnotation2.getDetails().get("setter").equals(selector2.getSetterExpression().getExpression()));
//        assertTrue(egExprAnnotation2.getDetails().get("setter.dialect").equals(selector2.getSetterExpression().getDialect().toString()));
    }
}
