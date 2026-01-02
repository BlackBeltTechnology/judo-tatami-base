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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import lombok.Builder;
import lombok.Getter;
import org.eclipse.emf.ecore.*;

/**
 * Generates realistic ASM (Ecore) models with characteristics similar to the RackInspect real-world model.
 *
 * RackInspect ASM Model Characteristics (after PSM2ASM transformation):
 * - ~1245 classifiers total
 * - Entity classes with 'entity' annotation
 * - Transfer object classes with 'transferObjectType' annotation
 * - Various EAttributes and EReferences
 * - Actor/principal classes for Keycloak tests
 *
 * The generator creates ASM models that match the output of PSM2ASM transformation
 * from a RackInspect-like PSM model.
 */
public class RealisticAsmModelGenerator {

    private static final String EXTENDED_METADATA_URI = "http://blackbelt.hu/judo/meta/ExtendedMetadata";

    // RackInspect ASM ratios (derived from PSM2ASM transformation output)
    public static final int ATTRIBUTES_PER_CLASS = 5;
    public static final int REFERENCES_PER_CLASS = 3;
    public static final int TRANSFER_OBJECTS_PER_ENTITY = 11; // 2 mapped + 9 unmapped

    @Getter
    @Builder
    public static class GeneratorConfig {
        @Builder.Default
        private int entityCount = 70;

        @Builder.Default
        private boolean includeActors = false;

        @Builder.Default
        private int actorCount = 3;

        @Builder.Default
        private String modelName = "RealisticTestAsm";

        @Builder.Default
        private String realm = "TestRealm";
    }

    /**
     * Generate an ASM model with the specified number of entities.
     * Uses RackInspect-like ratios for attributes, references, and transfer objects.
     */
    public AsmModel generate(int entityCount) {
        return generate(GeneratorConfig.builder().entityCount(entityCount).build());
    }

    /**
     * Generate an ASM model with actors included (for Keycloak tests).
     */
    public AsmModel generateWithActors(int entityCount, int actorCount) {
        return generate(GeneratorConfig.builder()
                .entityCount(entityCount)
                .includeActors(true)
                .actorCount(actorCount)
                .build());
    }

    /**
     * Generate an ASM model with full configuration.
     */
    public AsmModel generate(GeneratorConfig config) {
        AsmModel asmModel = AsmModel.buildAsmModel().build();

        // Root package
        EPackage rootPackage = EcoreFactory.eINSTANCE.createEPackage();
        rootPackage.setName(config.getModelName().toLowerCase());
        rootPackage.setNsPrefix(config.getModelName().toLowerCase());
        rootPackage.setNsURI("http://" + config.getModelName().toLowerCase());

        // Create entity classes package
        EPackage entitiesPackage = createEntitiesPackage(config.getEntityCount());
        rootPackage.getESubpackages().add(entitiesPackage);

        // Create transfer objects package
        EPackage servicesPackage = createTransferObjectsPackage(config.getEntityCount(), entitiesPackage);
        rootPackage.getESubpackages().add(servicesPackage);

        // Create actors if requested
        if (config.isIncludeActors()) {
            EPackage actorsPackage = createActorsPackage(config.getActorCount(), config.getRealm());
            rootPackage.getESubpackages().add(actorsPackage);
        }

        asmModel.addContent(rootPackage);

        return asmModel;
    }

    private EPackage createEntitiesPackage(int entityCount) {
        EPackage entitiesPackage = EcoreFactory.eINSTANCE.createEPackage();
        entitiesPackage.setName("entities");
        entitiesPackage.setNsPrefix("entities");
        entitiesPackage.setNsURI("http://entities");

        // Create entity classes
        for (int i = 0; i < entityCount; i++) {
            EClass entityClass = createEntityClass(i);
            entitiesPackage.getEClassifiers().add(entityClass);
        }

        // Add references between entities
        for (int i = 0; i < entityCount; i++) {
            EClass sourceClass = (EClass) entitiesPackage.getEClassifiers().get(i);
            addEntityReferences(sourceClass, entitiesPackage, i, entityCount);
        }

        return entitiesPackage;
    }

    private EClass createEntityClass(int index) {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        eClass.setName("Entity" + index);

        // Add entity annotation
        addAnnotation(eClass, EXTENDED_METADATA_URI + "/entity", "value", "true");

        // Add ID attribute
        EAttribute idAttr = EcoreFactory.eINSTANCE.createEAttribute();
        idAttr.setName("id");
        idAttr.setEType(EcorePackage.Literals.ESTRING);
        idAttr.setID(true);
        addAnnotation(idAttr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "36");
        eClass.getEStructuralFeatures().add(idAttr);

        // Add other attributes
        for (int j = 0; j < ATTRIBUTES_PER_CLASS; j++) {
            EAttribute attr = createAttribute(j);
            eClass.getEStructuralFeatures().add(attr);
        }

        return eClass;
    }

    private EAttribute createAttribute(int index) {
        EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();

        switch (index % 5) {
            case 0:
                attr.setName("strAttr" + index);
                attr.setEType(EcorePackage.Literals.ESTRING);
                addAnnotation(attr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "255");
                break;
            case 1:
                attr.setName("intAttr" + index);
                attr.setEType(EcorePackage.Literals.EINT);
                addAnnotation(attr, EXTENDED_METADATA_URI + "/constraints", "precision", "9");
                break;
            case 2:
                attr.setName("decAttr" + index);
                attr.setEType(EcorePackage.Literals.EDOUBLE);
                addAnnotation(attr, EXTENDED_METADATA_URI + "/constraints", "precision", "15");
                addAnnotationDetail(attr, EXTENDED_METADATA_URI + "/constraints", "scale", "4");
                break;
            case 3:
                attr.setName("boolAttr" + index);
                attr.setEType(EcorePackage.Literals.EBOOLEAN);
                break;
            default:
                attr.setName("tsAttr" + index);
                attr.setEType(EcorePackage.Literals.EDATE);
                break;
        }

        return attr;
    }

    private void addEntityReferences(EClass sourceClass, EPackage entitiesPackage, int sourceIndex, int entityCount) {
        for (int j = 0; j < REFERENCES_PER_CLASS; j++) {
            int targetIndex = (sourceIndex + j + 1) % entityCount;
            EClass targetClass = (EClass) entitiesPackage.getEClassifiers().get(targetIndex);

            EReference ref = EcoreFactory.eINSTANCE.createEReference();
            ref.setName("ref" + j + "To" + targetClass.getName());
            ref.setEType(targetClass);
            ref.setLowerBound(0);
            ref.setUpperBound(j == 0 ? 1 : -1);
            ref.setContainment(j % 3 == 0);

            sourceClass.getEStructuralFeatures().add(ref);
        }
    }

    private EPackage createTransferObjectsPackage(int entityCount, EPackage entitiesPackage) {
        EPackage servicesPackage = EcoreFactory.eINSTANCE.createEPackage();
        servicesPackage.setName("services");
        servicesPackage.setNsPrefix("services");
        servicesPackage.setNsURI("http://services");

        // Create transfer objects for each entity
        for (int i = 0; i < entityCount; i++) {
            EClass entityClass = (EClass) entitiesPackage.getEClassifiers().get(i);

            // Create mapped transfer objects
            for (int m = 0; m < 2; m++) {
                EClass mappedTO = createMappedTransferObject(entityClass, m);
                servicesPackage.getEClassifiers().add(mappedTO);
            }

            // Create unmapped transfer objects
            for (int u = 0; u < 9; u++) {
                EClass unmappedTO = createUnmappedTransferObject(i, u);
                servicesPackage.getEClassifiers().add(unmappedTO);
            }
        }

        // Add references between transfer objects
        addTransferObjectReferences(servicesPackage);

        return servicesPackage;
    }

    private EClass createMappedTransferObject(EClass entityClass, int index) {
        EClass toClass = EcoreFactory.eINSTANCE.createEClass();
        toClass.setName(entityClass.getName() + "TO" + index);

        // Add transferObjectType annotation
        addAnnotation(toClass, EXTENDED_METADATA_URI + "/transferObjectType", "value", "true");
        addAnnotation(toClass, EXTENDED_METADATA_URI + "/mappedEntityType", "value", entityClass.getName());

        // Add transfer attributes
        for (int j = 0; j < 3; j++) {
            EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();
            attr.setName("toAttr" + j);
            attr.setEType(j % 2 == 0 ? EcorePackage.Literals.ESTRING : EcorePackage.Literals.EINT);
            if (j % 2 == 0) {
                addAnnotation(attr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "255");
            }
            toClass.getEStructuralFeatures().add(attr);
        }

        return toClass;
    }

    private EClass createUnmappedTransferObject(int entityIndex, int toIndex) {
        EClass toClass = EcoreFactory.eINSTANCE.createEClass();
        toClass.setName("Unmapped" + entityIndex + "_" + toIndex);

        // Add transferObjectType annotation
        addAnnotation(toClass, EXTENDED_METADATA_URI + "/transferObjectType", "value", "true");

        // Add transfer attributes
        for (int j = 0; j < 3; j++) {
            EAttribute attr = EcoreFactory.eINSTANCE.createEAttribute();
            attr.setName("attr" + j);
            attr.setEType(j % 3 == 0 ? EcorePackage.Literals.ESTRING :
                    (j % 3 == 1 ? EcorePackage.Literals.EINT : EcorePackage.Literals.EBOOLEAN));
            if (j % 3 == 0) {
                addAnnotation(attr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "255");
            }
            toClass.getEStructuralFeatures().add(attr);
        }

        return toClass;
    }

    private void addTransferObjectReferences(EPackage servicesPackage) {
        int toCount = servicesPackage.getEClassifiers().size();

        for (int i = 0; i < toCount; i++) {
            EClass sourceTO = (EClass) servicesPackage.getEClassifiers().get(i);

            // Add 3 references per transfer object
            for (int j = 0; j < 3 && j + 1 < toCount; j++) {
                int targetIndex = (i + j + 1) % toCount;
                EClass targetTO = (EClass) servicesPackage.getEClassifiers().get(targetIndex);

                EReference ref = EcoreFactory.eINSTANCE.createEReference();
                ref.setName("rel" + j + "To" + targetTO.getName());
                ref.setEType(targetTO);
                ref.setLowerBound(0);
                ref.setUpperBound(j == 0 ? 1 : -1);
                ref.setContainment(j % 3 == 0);

                sourceTO.getEStructuralFeatures().add(ref);
            }
        }
    }

    private EPackage createActorsPackage(int actorCount, String realm) {
        EPackage actorsPackage = EcoreFactory.eINSTANCE.createEPackage();
        actorsPackage.setName("actors");
        actorsPackage.setNsPrefix("actors");
        actorsPackage.setNsURI("http://actors");

        for (int i = 0; i < actorCount; i++) {
            EClass actorClass = createActorClass(i, realm);
            actorsPackage.getEClassifiers().add(actorClass);
        }

        return actorsPackage;
    }

    private EClass createActorClass(int index, String realm) {
        EClass actorClass = EcoreFactory.eINSTANCE.createEClass();
        actorClass.setName("Actor" + index);

        // Add principal annotation
        addAnnotation(actorClass, EXTENDED_METADATA_URI + "/principal", "value", "true");

        // Add realm annotation
        addAnnotation(actorClass, EXTENDED_METADATA_URI + "/realm", "value", realm);

        // Add actor type annotation
        addAnnotation(actorClass, EXTENDED_METADATA_URI + "/actorType", "value", "true");

        // Add some attributes for the actor
        EAttribute nameAttr = EcoreFactory.eINSTANCE.createEAttribute();
        nameAttr.setName("username");
        nameAttr.setEType(EcorePackage.Literals.ESTRING);
        addAnnotation(nameAttr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "255");
        actorClass.getEStructuralFeatures().add(nameAttr);

        EAttribute emailAttr = EcoreFactory.eINSTANCE.createEAttribute();
        emailAttr.setName("email");
        emailAttr.setEType(EcorePackage.Literals.ESTRING);
        addAnnotation(emailAttr, EXTENDED_METADATA_URI + "/constraints", "maxLength", "255");
        actorClass.getEStructuralFeatures().add(emailAttr);

        return actorClass;
    }

    private void addAnnotation(EModelElement element, String source, String key, String value) {
        EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
        annotation.setSource(source);
        annotation.getDetails().put(key, value);
        element.getEAnnotations().add(annotation);
    }

    private void addAnnotationDetail(EModelElement element, String source, String key, String value) {
        for (EAnnotation existing : element.getEAnnotations()) {
            if (source.equals(existing.getSource())) {
                existing.getDetails().put(key, value);
                return;
            }
        }
        // If no existing annotation with this source, create new one
        addAnnotation(element, source, key, value);
    }

    /**
     * Count all classifiers in an ASM model.
     */
    public static int countClassifiers(AsmModel asmModel) {
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

    private static int countClassifiersRecursive(EPackage pkg) {
        int count = pkg.getEClassifiers().size();
        for (EPackage subPkg : pkg.getESubpackages()) {
            count += countClassifiersRecursive(subPkg);
        }
        return count;
    }
}
