package hu.blackbelt.judo.tatami.asm2keycloak;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Keycloak
 * %%
 * Copyright (C) 2018 - 2023 BlackBelt Technology
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
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.Client;
import hu.blackbelt.judo.meta.keycloak.Realm;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.AbstractActorType;
import hu.blackbelt.judo.meta.psm.data.Attribute;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.type.*;
import hu.blackbelt.model.northwind.Demo;
import hu.blackbelt.judo.tatami.asm2keycloak.util.ModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.*;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.SaveArguments.keycloakSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.accesspoint.util.builder.AccesspointBuilders.newMappedActorTypeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newAttributeBuilder;
import static hu.blackbelt.judo.meta.psm.data.util.builder.DataBuilders.newEntityTypeBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newModelBuilder;
import static hu.blackbelt.judo.meta.psm.namespace.util.builder.NamespaceBuilders.newPackageBuilder;
import static hu.blackbelt.judo.meta.psm.service.util.builder.ServiceBuilders.newTransferAttributeBuilder;
import static hu.blackbelt.judo.meta.psm.type.util.builder.TypeBuilders.*;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakTransformationTrace.fromModelsAndTrace;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.Psm2AsmParameter.psm2AsmParameter;
import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Asm2KeycloakTest {
    public static final String NORTHWIND_KEYCLOAK_MODEL_POSTFIX = "-keycloak.model";
    public static final String NORTHWIND_ASM_2_KEYCLOAK_MODEL_POSTFIX = "-asm2keycloak.model";
    public static final String TARGET_TEST_CLASSES = "target/test-classes";

    AsmModel asmModel;
    KeycloakModel keycloakModel;

    @ParameterizedTest(name = "testAsm2KeycloakTransformation with {0}")
    @EnumSource(TransformationType.class)
    public void testAsm2KeycloakTransformation(TransformationType transformationType) throws Exception {
        final PsmModel psmModel = new Demo().fullDemo();

        final Map<EObject, List<EObject>> resolvedTrace = transform(psmModel, transformationType);

        // Printing trace (only for ETL which has trace)
        if (resolvedTrace != null) {
            for (EObject e : resolvedTrace.keySet()) {
                for (EObject t : resolvedTrace.get(e)) {
                    log.trace(e.toString() + " -> " + t.toString());
                }
            }
        }
    }

    @ParameterizedTest(name = "testRealmCreation with {0}")
    @EnumSource(TransformationType.class)
    public void testRealmCreation(TransformationType transformationType) throws Exception {
        final StringType stringType = newStringTypeBuilder().withName("String").withMaxLength(255).build();
        final NumericType integerType = newNumericTypeBuilder().withName("Integer").withPrecision(9).withScale(0).build();
        final BooleanType booleanType = newBooleanTypeBuilder().withName("Boolean").build();

        final AbstractActorType publicUnmapped = newActorTypeBuilder()
                .withName("PublicUnmapped")
                .build();
        final AbstractActorType protectedUnmapped = newActorTypeBuilder()
                .withName("ProtectedUnmapped")
                .withRealm("protected1")
                .withAttributes(newTransferAttributeBuilder()
                        .withName("email")
                        .withDataType(stringType)
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("idCardNumber")
                        .withDataType(stringType)
                        .withClaimType("USERNAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("fullName")
                        .withDataType(stringType)
                        .withClaimType("NAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("room")
                        .withDataType(stringType)
                        .withRequired(false)
                        .build())
                .build();

        final Attribute emailOfUser = newAttributeBuilder()
                .withDataType(stringType)
                .withName("email")
                .withRequired(true)
                .withIdentifier(false)
                .build();
        final Attribute idCardNumberOfUser = newAttributeBuilder()
                .withDataType(stringType)
                .withName("idCardNumber")
                .withRequired(true)
                .withIdentifier(true)
                .build();
        final Attribute fullNameOfUser = newAttributeBuilder()
                .withDataType(stringType)
                .withName("fullName")
                .withRequired(true)
                .build();
        final Attribute roomOfUser = newAttributeBuilder()
                .withDataType(integerType)
                .withName("room")
                .withRequired(false)
                .build();
        final EntityType user = newEntityTypeBuilder()
                .withName("User")
                .withAttributes(emailOfUser, idCardNumberOfUser, fullNameOfUser, roomOfUser)
                .build();

        final AbstractActorType publicMapped = newMappedActorTypeBuilder()
                .withName("PublicMapped")
                .withEntityType(user)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("email")
                        .withDataType(stringType)
                        .withBinding(emailOfUser)
                        .withClaimType("EMAIL")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("idCardNumber")
                        .withDataType(stringType)
                        .withBinding(idCardNumberOfUser)
                        .withClaimType("USERNAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("fullName")
                        .withDataType(stringType)
                        .withBinding(fullNameOfUser)
                        .withClaimType("NAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("room")
                        .withDataType(stringType)
                        .withBinding(roomOfUser)
                        .withRequired(false)
                        .build())
                .build();
        final AbstractActorType protectedMapped = newMappedActorTypeBuilder()
                .withName("ProtectedMapped")
                .withRealm("protected2")
                .withEntityType(user)
                .withAttributes(newTransferAttributeBuilder()
                        .withName("email")
                        .withDataType(stringType)
                        .withBinding(emailOfUser)
                        .withClaimType("EMAIL")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("idCardNumber")
                        .withDataType(stringType)
                        .withBinding(idCardNumberOfUser)
                        .withClaimType("USERNAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("fullName")
                        .withDataType(stringType)
                        .withBinding(fullNameOfUser)
                        .withClaimType("NAME")
                        .withRequired(true)
                        .build())
                .withAttributes(newTransferAttributeBuilder()
                        .withName("room")
                        .withDataType(stringType)
                        .withBinding(roomOfUser)
                        .withRequired(false)
                        .build())
                .build();

        final Model model = newModelBuilder()
                .withName("M")
                .withPackages(
                        newPackageBuilder()
                                .withName("types")
                                .withElements(booleanType, integerType, stringType)
                                .build(),
                        newPackageBuilder()
                                .withName("entities")
                                .withElements(user)
                                .build(),
                        newPackageBuilder()
                                .withName("services")
                                .withElements(publicUnmapped, protectedUnmapped, publicMapped, protectedMapped)
                                .build())
                .build();

        final PsmModel psmModel = PsmModel.buildPsmModel()
                .build();
        psmModel.getResource().getContents().add(model);

        final Map<EObject, List<EObject>> resolvedTrace = transform(psmModel, transformationType);
        final AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());

        final KeycloakUtils keycloakUtils = new KeycloakUtils(keycloakModel.getResourceSet());
        final Set<Realm> realms = keycloakUtils.all(Realm.class).collect(Collectors.toSet());

        assertTrue(realms.stream().anyMatch(r -> "protected1".equals(r.getRealm()) && r.getEnabled() && r.getLoginWithEmailAllowed()));
        assertTrue(realms.stream().anyMatch(r -> "protected2".equals(r.getRealm()) && r.getEnabled() && r.getLoginWithEmailAllowed()));

        // Trace-dependent assertions only for ETL transformation
        if (resolvedTrace != null) {
            final EMap<EObject, List<EObject>> resolvedTraceMap = ECollections.asEMap(resolvedTrace);
            asmUtils.getAllActorTypes().stream()
                    .filter(actorType -> AsmUtils.getExtensionAnnotationValue(actorType, "realm", false).isPresent())
                    .forEach(actorType -> assertTrue(resolvedTraceMap.get(actorType).contains(keycloakUtils.all(Client.class).filter(c -> AsmUtils.getClassifierFQName(actorType).replaceAll("\\.", "-").equals(c.getName())).findAny().get())));
        }
    }

    private Map<EObject, List<EObject>> transform(final PsmModel psmModel, final TransformationType transformationType) throws Exception {
        // Create empty ASM model
        asmModel = AsmModel.buildAsmModel()
                .build();

        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModel)
                .asmModel(asmModel));

        // Create empty KEYCLOAK model
        keycloakModel = KeycloakModel.buildKeycloakModel()
                .name(psmModel.getName())
                .build();

        Map<EObject, List<EObject>> resolvedTrace = null;

        if (transformationType == TransformationType.ZETA) {
            log.info("Running Zeta transformation");
            Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel)
                    .build();
            transformation.execute();
            // For Zeta transformation, trace is not available
        } else {
            log.info("Running ETL transformation");
            final Asm2KeycloakTransformationTrace asm2KeycloakTransformationTrace =
                    executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                            .asmModel(asmModel)
                            .keycloakModel(keycloakModel)
                            .createTrace(true));

            // Saving trace map
            asm2KeycloakTransformationTrace.save(new File(TARGET_TEST_CLASSES, psmModel.getName() + NORTHWIND_ASM_2_KEYCLOAK_MODEL_POSTFIX));

            // Loading trace map
            final Asm2KeycloakTransformationTrace asm2KeycloakTransformationTraceLoaded =
                    fromModelsAndTrace(psmModel.getName(), asmModel, keycloakModel, new File(TARGET_TEST_CLASSES, psmModel.getName() + NORTHWIND_ASM_2_KEYCLOAK_MODEL_POSTFIX));

            // Resolve serialized URI's as EObject map
            resolvedTrace = asm2KeycloakTransformationTraceLoaded.getTransformationTrace();
        }

        keycloakModel.saveKeycloakModel(keycloakSaveArgumentsBuilder()
                .file(new File(TARGET_TEST_CLASSES, psmModel.getName() + NORTHWIND_KEYCLOAK_MODEL_POSTFIX)));

        return resolvedTrace;
    }

    /**
     * Test that compares ETL and Zeta transformation outputs for equivalence.
     * This test runs both transformation engines on the same input and compares
     * the resulting Keycloak models to verify they produce equivalent output.
     */
    @Test
    public void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Create input models for ETL transformation
        final PsmModel psmModelEtl = new Demo().fullDemo();
        AsmModel asmModelEtl = AsmModel.buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelEtl)
                .asmModel(asmModelEtl));
        KeycloakModel etlResult = KeycloakModel.buildKeycloakModel()
                .name(psmModelEtl.getName())
                .build();

        // Run ETL transformation
        log.info("Running ETL transformation for equivalence test");
        executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                .asmModel(asmModelEtl)
                .keycloakModel(etlResult)
                .createTrace(false));

        // Create input models for Zeta transformation
        final PsmModel psmModelZeta = new Demo().fullDemo();
        AsmModel asmModelZeta = AsmModel.buildAsmModel().build();
        executePsm2AsmTransformation(psm2AsmParameter()
                .psmModel(psmModelZeta)
                .asmModel(asmModelZeta));
        KeycloakModel zetaResult = KeycloakModel.buildKeycloakModel()
                .name(psmModelZeta.getName())
                .build();

        // Run Zeta transformation
        log.info("Running Zeta transformation for equivalence test");
        Asm2KeycloakZetaTransformation zetaTransformation = Asm2KeycloakZetaTransformation.builder()
                .asmModel(asmModelZeta)
                .keycloakModel(zetaResult)
                .build();
        zetaTransformation.execute();

        // Compare models
        log.info("ETL resources: {}, contents: {}", 
                etlResult.getResourceSet().getResources().size(),
                etlResult.getResourceSet().getResources().isEmpty() ? 0 : 
                    etlResult.getResourceSet().getResources().get(0).getContents().size());
        log.info("Zeta resources: {}, contents: {}", 
                zetaResult.getResourceSet().getResources().size(),
                zetaResult.getResourceSet().getResources().isEmpty() ? 0 : 
                    zetaResult.getResourceSet().getResources().get(0).getContents().size());
        
        // Both models may be empty if the Demo doesn't have actors with realms
        boolean etlEmpty = etlResult.getResourceSet().getResources().isEmpty() ||
                          etlResult.getResourceSet().getResources().get(0).getContents().isEmpty();
        boolean zetaEmpty = zetaResult.getResourceSet().getResources().isEmpty() ||
                           zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty();
        
        if (etlEmpty && zetaEmpty) {
            log.info("SUCCESS: Both ETL and Zeta produced empty models (no actors with realms in Demo)");
            return;
        }
        
        if (etlEmpty || zetaEmpty) {
            fail("Model mismatch: ETL empty=" + etlEmpty + ", Zeta empty=" + zetaEmpty);
        }
        
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResult.getResourceSet().getResources().get(0).getContents().get(0),
                zetaResult.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent Keycloak models");
        } else {
            log.warn("Keycloak models have differences:\n{}", result.getSummary());
            fail("ETL and Zeta Keycloak models are not equivalent:\n" + result.getDetailedReport());
        }
    }
}
