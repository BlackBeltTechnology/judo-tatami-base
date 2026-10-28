package hu.blackbelt.judo.tatami.asm2rdbms;

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
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.RdbmsIdentifierField;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.util.ModelComparator;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.epsilon.common.util.UriUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.util.EcoreUtil;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel;
import static hu.blackbelt.judo.meta.asm.runtime.AsmUtils.addExtensionAnnotation;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;
import static java.lang.String.format;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.newEClassBuilder;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.newEPackageBuilder;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Slf4j
public class Asm2RdbmsInheritanceTest extends Asm2RdbmsMappingTestBase {
    private final String ID_ATTRIBUTE = "#_id";
    private final String TYPE_ATTRIBUTE = "#_type";
    private final String VERSION_ATTRIBUTE = "#_version";
    private final String CREATE_USERNAME_ATTRIBUTE = "#_create_username";
    private final String CREATE_USER_ID_ATTRIBUTE = "#_create_user_id";
    private final String CREATE_TIMESTAMP_ATTRIBUTE = "#_create_timestamp";
    private final String UPDATE_USERNAME_ATTRIBUTE = "#_update_username";
    private final String UPDATE_USER_ID_ATTRIBUTE = "#_update_user_id";
    private final String UPDATE_TIMESTAMP_ATTRIBUTE = "#_update_timestamp";

    private void assertParents(Set<String> expected, String tableName) {
        rdbmsUtils.getRdbmsTable(tableName)
                .orElseThrow(() -> new RuntimeException(tableName + " not found"))
                .getParents().forEach(o -> {
            assertTrue(expected.contains(o.getName()), o.getName() + " not found");
            expected.remove(o.getName());
        });
        if (expected.size() != 0) {
            fail(format("Parents are missing from %s: %s", tableName, expected));
        }
    }

    @ParameterizedTest(name = "Test Basic Inheritance with {0}")
    @EnumSource(TransformationMode.class)
    public void testBasicInheritance(TransformationMode transformationMode) throws Exception {
        ///////////////////
        // setup asm model
        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass fruit = newEClassBuilder()
                .withName("fruit")
                .build();
        ePackage.getEClassifiers().add(fruit);
        addExtensionAnnotation(fruit, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass apple = newEClassBuilder()
                .withName("apple")
                .withESuperTypes(fruit)
                .build();
        ePackage.getEClassifiers().add(apple);
        addExtensionAnnotation(apple, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        executeTransformation("testBasicInheritance", transformationMode);

        final String RDBMS_TABLE_FRUIT = "TestEpackage.fruit";
        final String RDBMS_TABLE_APPLE = "TestEpackage.apple";

        // setup asm model and transform
        ////////////////////////////////
        // fill expected values

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>();
        Set<String> fields2 = new HashSet<>();
        Set<String> parents = new HashSet<>();

        tables.add(RDBMS_TABLE_FRUIT);
        tables.add(RDBMS_TABLE_APPLE);

        fields1.add(RDBMS_TABLE_FRUIT + ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + TYPE_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + VERSION_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + CREATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + CREATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + CREATE_TIMESTAMP_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + UPDATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + UPDATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_FRUIT + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields2.add(RDBMS_TABLE_APPLE + ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + TYPE_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + VERSION_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + CREATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + CREATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + CREATE_TIMESTAMP_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + UPDATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + UPDATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_APPLE + UPDATE_TIMESTAMP_ATTRIBUTE);

        parents.add(RDBMS_TABLE_FRUIT);

        // fill expected values
        //////////////////////////////////////
        // compare expected and actual values

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_FRUIT);
        assertFields(fields2, RDBMS_TABLE_APPLE);
        assertParents(parents, RDBMS_TABLE_APPLE);

        // compare expected and actual values
        /////////////////////////////////////
        // "validate" rdbms model

        // ASSERTION - check if apple's parent is valid
        assertEquals(rdbmsUtils.getRdbmsTable(RDBMS_TABLE_FRUIT).get().getPrimaryKey(),
                rdbmsUtils.getRdbmsTable(RDBMS_TABLE_APPLE).get().getParents().get(0).getPrimaryKey());

        compareTransformations("testBasicInheritance");
    }

    @ParameterizedTest(name = "Test Inheritance With Two Parents with {0}")
    @EnumSource(TransformationMode.class)
    public void testInheritanceWithTwoParents(TransformationMode transformationMode) throws Exception {
        ///////////////////
        // setup asm model
        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass vegetable = newEClassBuilder()
                .withName("vegetable")
                .build();
        ePackage.getEClassifiers().add(vegetable);
        addExtensionAnnotation(vegetable, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass fruit = newEClassBuilder()
                .withName("fruit")
                .build();
        ePackage.getEClassifiers().add(fruit);
        addExtensionAnnotation(fruit, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass tomato = newEClassBuilder()
                .withName("tomato")
                .withESuperTypes(ImmutableList.of(vegetable, fruit))
                .build();
        ePackage.getEClassifiers().add(tomato);
        addExtensionAnnotation(tomato, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        executeTransformation("testInheritanceWithTwoParents", transformationMode);

        // setup asm model and transform
        ////////////////////////////////////////////////////////////
        // prepare table names and fill sets with required elements

        final String RDBMS_TABLE_VEGETABLE = "TestEpackage.vegetable";
        final String RDBMS_TABLE_FRUIT = "TestEpackage.fruit";
        final String RDBMS_TABLE_TOMATO = "TestEpackage.tomato";

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>(); //vegetable
        Set<String> fields2 = new HashSet<>(); //fruit
        Set<String> fields3 = new HashSet<>(); //apple
        Set<String> parents = new HashSet<>();

        tables.add(RDBMS_TABLE_VEGETABLE);
        tables.add(RDBMS_TABLE_FRUIT);
        tables.add(RDBMS_TABLE_TOMATO);

        fields1.add(RDBMS_TABLE_VEGETABLE + ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + TYPE_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + VERSION_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + CREATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + CREATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + CREATE_TIMESTAMP_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + UPDATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + UPDATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEGETABLE + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields2.add(RDBMS_TABLE_FRUIT + ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + TYPE_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + VERSION_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + CREATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + CREATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + CREATE_TIMESTAMP_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + UPDATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + UPDATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_FRUIT + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields3.add(RDBMS_TABLE_TOMATO + ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + TYPE_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + VERSION_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + CREATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + CREATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + CREATE_TIMESTAMP_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + UPDATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + UPDATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_TOMATO + UPDATE_TIMESTAMP_ATTRIBUTE);

        parents.add(RDBMS_TABLE_VEGETABLE);
        parents.add(RDBMS_TABLE_FRUIT);

        // prepare table names and fill sets with required elements
        ///////////////////////////////////////////////////////////
        // compare expected and actual elements

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_VEGETABLE);
        assertFields(fields2, RDBMS_TABLE_FRUIT);
        assertFields(fields3, RDBMS_TABLE_TOMATO);
        assertParents(parents, RDBMS_TABLE_TOMATO);

        // compare expected and actual elements
        ///////////////////////////////////////
        // "validate" rdbms model

        final EList<RdbmsTable> rdbmsParents = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_TOMATO).get().getParents();
        final RdbmsIdentifierField primaryKey1 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_VEGETABLE).get().getPrimaryKey();
        final RdbmsIdentifierField primaryKey2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_FRUIT).get().getPrimaryKey();
        assertTrue(rdbmsParents.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey1)),
                RDBMS_TABLE_TOMATO + "'s parent's primary key is not valid: " + primaryKey1.getName());
        assertTrue(rdbmsParents.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey2)),
                RDBMS_TABLE_TOMATO + "'s parent's primary key is not valid: " + primaryKey2.getName());

        compareTransformations("testInheritanceWithTwoParents");
    }

    @ParameterizedTest(name = "Test Indirect Inheritance with {0}")
    @EnumSource(TransformationMode.class)
    public void testIndirectInheritance(TransformationMode transformationMode) throws Exception {
        ///////////////////
        // setup asm model
        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass vehicle = newEClassBuilder()
                .withName("vehicle")
                .build();
        ePackage.getEClassifiers().add(vehicle);
        addExtensionAnnotation(vehicle, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass car = newEClassBuilder()
                .withName("car")
                .withESuperTypes(vehicle)
                .build();
        ePackage.getEClassifiers().add(car);
        addExtensionAnnotation(car, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass ev = newEClassBuilder()
                .withName("ev")
                .withESuperTypes(car)
                .build();
        ePackage.getEClassifiers().add(ev);
        addExtensionAnnotation(ev, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        executeTransformation("testIndirectInheritance", transformationMode);

        // setup asm model and transform
        ////////////////////////////////////////////////////////////
        // prepare table names and fill sets with required elements

        final String RDBMS_TABLE_VEHICLE = "TestEpackage.vehicle";
        final String RDBMS_TABLE_CAR = "TestEpackage.car";
        final String RDBMS_TABLE_ELECTRIC_CAR = "TestEpackage.ev";

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>(); //vehicle
        Set<String> fields2 = new HashSet<>(); //car
        Set<String> fields3 = new HashSet<>(); //electric car
        Set<String> parents1 = new HashSet<>(); //car's parents
        Set<String> parents2 = new HashSet<>(); //electric car's parents

        tables.add(RDBMS_TABLE_VEHICLE);
        tables.add(RDBMS_TABLE_CAR);
        tables.add(RDBMS_TABLE_ELECTRIC_CAR);

        fields1.add(RDBMS_TABLE_VEHICLE + ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + TYPE_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + VERSION_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + CREATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + CREATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + CREATE_TIMESTAMP_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + UPDATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + UPDATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_VEHICLE + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields2.add(RDBMS_TABLE_CAR + ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + TYPE_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + VERSION_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + CREATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + CREATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + CREATE_TIMESTAMP_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + UPDATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + UPDATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_CAR + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + TYPE_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + VERSION_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + CREATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + CREATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + CREATE_TIMESTAMP_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + UPDATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + UPDATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_ELECTRIC_CAR + UPDATE_TIMESTAMP_ATTRIBUTE);

        parents1.add(RDBMS_TABLE_VEHICLE);
        parents2.add(RDBMS_TABLE_CAR);

        // prepare table names and fill sets with required elements
        ///////////////////////////////////////////////////////////
        // compare expected and actual elements

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_VEHICLE);
        assertFields(fields2, RDBMS_TABLE_CAR);
        assertFields(fields3, RDBMS_TABLE_ELECTRIC_CAR);
        assertParents(parents1, RDBMS_TABLE_CAR);
        assertParents(parents2, RDBMS_TABLE_ELECTRIC_CAR);

        // compare expected and actual elements
        ///////////////////////////////////////
        // "validate" rdbms model

        final EList<RdbmsTable> rdbmsParents2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_CAR).get().getParents();
        final EList<RdbmsTable> rdbmsParents3 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_ELECTRIC_CAR).get().getParents();

        final RdbmsIdentifierField primaryKey1 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_VEHICLE).get().getPrimaryKey();
        final RdbmsIdentifierField primaryKey2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_CAR).get().getPrimaryKey();

        assertTrue(rdbmsParents2.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey1)),
                RDBMS_TABLE_CAR + "'s parent's primary key is not valid: " + primaryKey1.getName());

        assertTrue(rdbmsParents3.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey2)),
                RDBMS_TABLE_ELECTRIC_CAR + "'s parent's primary key is not valid: " + primaryKey1.getName());

        compareTransformations("testIndirectInheritance");
    }

    @ParameterizedTest(name = "Test Diamond Inheritance with {0}")
    @EnumSource(TransformationMode.class)
    public void testDiamondInheritance(TransformationMode transformationMode) throws Exception {
        ///////////////////
        // setup asm model
        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass A = newEClassBuilder()
                .withName("A")
                .build();
        ePackage.getEClassifiers().add(A);
        addExtensionAnnotation(A, ENTITY_ANNOTATION, VALUE_ANNOTATION);
        final EClass B = newEClassBuilder()
                .withName("B")
                .withESuperTypes(A)
                .build();
        ePackage.getEClassifiers().add(B);
        addExtensionAnnotation(B, ENTITY_ANNOTATION, VALUE_ANNOTATION);
        final EClass BB = newEClassBuilder()
                .withName("BB")
                .withESuperTypes(A)
                .build();
        ePackage.getEClassifiers().add(BB);
        addExtensionAnnotation(BB, ENTITY_ANNOTATION, VALUE_ANNOTATION);
        final EClass C = newEClassBuilder()
                .withName("C")
                .withESuperTypes(ImmutableList.of(B, BB))
                .build();
        ePackage.getEClassifiers().add(C);
        addExtensionAnnotation(C, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        executeTransformation("testDiamondInheritance", transformationMode);

        // setup asm model and transform
        ////////////////////////////////////////////////////////////
        // prepare table names and fill sets with required elements

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>(); //A
        Set<String> fields2 = new HashSet<>(); //B
        Set<String> fields22 = new HashSet<>(); //BB
        Set<String> fields3 = new HashSet<>(); //C
        Set<String> parents2 = new HashSet<>(); //B's parents
        Set<String> parents22 = new HashSet<>(); //BB's parents
        Set<String> parents3 = new HashSet<>(); //C's parents

        final String RDBMS_TABLE_A = "TestEpackage.A";
        final String RDBMS_TABLE_B = "TestEpackage.B";
        final String RDBMS_TABLE_BB = "TestEpackage.BB";
        final String RDBMS_TABLE_C = "TestEpackage.C";

        tables.add(RDBMS_TABLE_A);
        tables.add(RDBMS_TABLE_B);
        tables.add(RDBMS_TABLE_BB);
        tables.add(RDBMS_TABLE_C);

        fields1.add(RDBMS_TABLE_A + ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + TYPE_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + VERSION_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + CREATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + CREATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + CREATE_TIMESTAMP_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + UPDATE_USERNAME_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + UPDATE_USER_ID_ATTRIBUTE);
        fields1.add(RDBMS_TABLE_A + UPDATE_TIMESTAMP_ATTRIBUTE);

        fields2.add(RDBMS_TABLE_B + ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + TYPE_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + VERSION_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + CREATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + CREATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + CREATE_TIMESTAMP_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + UPDATE_USERNAME_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + UPDATE_USER_ID_ATTRIBUTE);
        fields2.add(RDBMS_TABLE_B + UPDATE_TIMESTAMP_ATTRIBUTE);
        parents2.add(RDBMS_TABLE_A);

        fields22.add(RDBMS_TABLE_BB + ID_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + TYPE_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + VERSION_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + CREATE_USERNAME_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + CREATE_USER_ID_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + CREATE_TIMESTAMP_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + UPDATE_USERNAME_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + UPDATE_USER_ID_ATTRIBUTE);
        fields22.add(RDBMS_TABLE_BB + UPDATE_TIMESTAMP_ATTRIBUTE);
        parents22.add(RDBMS_TABLE_A);

        fields3.add(RDBMS_TABLE_C + ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + TYPE_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + VERSION_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + CREATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + CREATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + CREATE_TIMESTAMP_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + UPDATE_USERNAME_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + UPDATE_USER_ID_ATTRIBUTE);
        fields3.add(RDBMS_TABLE_C + UPDATE_TIMESTAMP_ATTRIBUTE);
        parents3.add(RDBMS_TABLE_B);
        parents3.add(RDBMS_TABLE_BB);

        // prepare table names and fill sets with required elements
        ///////////////////////////////////////////////////////////
        // compare expected and actual elements

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_A);
        assertFields(fields2, RDBMS_TABLE_B);
        assertFields(fields22, RDBMS_TABLE_BB);
        assertFields(fields3, RDBMS_TABLE_C);
        assertParents(parents2, RDBMS_TABLE_B);
        assertParents(parents22, RDBMS_TABLE_BB);
        assertParents(parents3, RDBMS_TABLE_C);

        // compare expected and actual elements
        ///////////////////////////////////////
        // "validate" rdbms model

        final EList<RdbmsTable> rdbmsParents2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_B).get().getParents();
        final EList<RdbmsTable> rdbmsParents22 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_BB).get().getParents();
        final EList<RdbmsTable> rdbmsParents3 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_C).get().getParents();

        final RdbmsIdentifierField primaryKey1 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_A).get().getPrimaryKey();
        final RdbmsIdentifierField primaryKey2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_B).get().getPrimaryKey();
        final RdbmsIdentifierField primaryKey22 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_B).get().getPrimaryKey();

        assertTrue(rdbmsParents2.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey1)),
                RDBMS_TABLE_B + "'s parent's primary key is not valid: " + primaryKey1.getName());

        assertTrue(rdbmsParents22.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey1)),
                RDBMS_TABLE_BB + "'s parent's primary key is not valid: " + primaryKey1.getName());

        assertTrue(rdbmsParents3.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey2)),
                RDBMS_TABLE_C + "'s parent's primary key is not valid: " + primaryKey2.getName());
        assertTrue(rdbmsParents3.stream().anyMatch(o -> o.getPrimaryKey().equals(primaryKey22)),
                RDBMS_TABLE_C + "'s parent's primary key is not valid: " + primaryKey22.getName());

        compareTransformations("testDiamondInheritance");
    }

    @ParameterizedTest(name = "Test Abstract Entity produces table with {0}")
    @EnumSource(TransformationMode.class)
    public void testAbstractEntityProducesTable(TransformationMode transformationMode) throws Exception {
        ///////////////////
        // setup asm model
        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        // Abstract entity F — must still produce a table for FK references from G
        final EClass abstractF = newEClassBuilder()
                .withName("AbstractF")
                .withAbstract_(true)
                .build();
        ePackage.getEClassifiers().add(abstractF);
        addExtensionAnnotation(abstractF, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        // Concrete entity G with a reference to abstract F
        final EClass concreteG = newEClassBuilder()
                .withName("ConcreteG")
                .build();
        ePackage.getEClassifiers().add(concreteG);
        addExtensionAnnotation(concreteG, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        executeTransformation("testAbstractEntityProducesTable", transformationMode);

        final String RDBMS_TABLE_F = "TestEpackage.AbstractF";
        final String RDBMS_TABLE_G = "TestEpackage.ConcreteG";

        Set<String> tables = new HashSet<>();
        tables.add(RDBMS_TABLE_F);
        tables.add(RDBMS_TABLE_G);

        // Both abstract and concrete entity must have tables
        assertTables(tables);

        compareTransformations("testAbstractEntityProducesTable");
    }

    /**
     * Runs both ETL and Zeta transformations and compares their outputs.
     */
    private void compareTransformations(final String testName) throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Run ETL fresh
        AsmModel asmModelEtl = buildAsmModel().build();
        RdbmsModel rdbmsModelEtl = buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModelEtl.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModelEtl.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModelEtl.getResourceSet());
        asmModelEtl.getResource().getContents().addAll(asmModel.getResource().getContents().stream()
                .map(EcoreUtil::copy).collect(Collectors.toList()));
        
        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(asmModelEtl)
                .rdbmsModel(rdbmsModelEtl)
                .createTrace(false)
                .dialect("hsqldb"));

        // Run Zeta fresh
        AsmModel asmModelZeta = buildAsmModel().build();
        RdbmsModel rdbmsModelZeta = buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModelZeta.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModelZeta.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModelZeta.getResourceSet());
        asmModelZeta.getResource().getContents().addAll(asmModel.getResource().getContents().stream()
                .map(EcoreUtil::copy).collect(Collectors.toList()));

        // Load mapping model for Zeta
        String dialect = "hsqldb";
        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms-compare"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        rdbmsModelZeta.getResource().getContents().addAll(mappingModel.getResource().getContents());

        Asm2RdbmsZetaTransformation zetaTransformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(asmModelZeta)
                .rdbmsModel(rdbmsModelZeta)
                .dialect("hsqldb")
                .build();
        zetaTransformation.execute();

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                rdbmsModelEtl.getResourceSet().getResources().get(0).getContents().get(0),
                rdbmsModelZeta.getResourceSet().getResources().get(0).getContents().get(0),
                ModelComparator.getConfiguredMode()
        );

        if (result.isEquivalent()) {
            log.info("SUCCESS: ETL and Zeta transformations produced equivalent models for {}", testName);
        } else {
            log.warn("Models have differences for {}:\n{}", testName, result.getSummary());
            fail("ETL and Zeta models are not equivalent for " + testName + ":\n" + result.getDetailedReport());
        }
    }

    private AsmModel buildInheritanceAsmModel() {
        AsmModel asmModel = buildAsmModel().build();

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass fruit = newEClassBuilder()
                .withName("fruit")
                .build();
        ePackage.getEClassifiers().add(fruit);
        addExtensionAnnotation(fruit, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EClass apple = newEClassBuilder()
                .withName("apple")
                .withESuperTypes(fruit)
                .build();
        ePackage.getEClassifiers().add(apple);
        addExtensionAnnotation(apple, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        return asmModel;
    }

    @Test
    void testEtlAndZetaEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        // Build model and run ETL
        AsmModel asmModelEtl = buildInheritanceAsmModel();
        RdbmsModel rdbmsModelEtl = buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModelEtl.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModelEtl.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModelEtl.getResourceSet());

        executeAsm2RdbmsTransformation(asm2RdbmsParameter()
                .asmModel(asmModelEtl)
                .rdbmsModel(rdbmsModelEtl)
                .createTrace(false)
                .dialect("hsqldb"));

        // Build model and run Zeta
        AsmModel asmModelZeta = buildInheritanceAsmModel();
        RdbmsModel rdbmsModelZeta = buildRdbmsModel().build();
        registerRdbmsNameMappingMetamodel(rdbmsModelZeta.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModelZeta.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModelZeta.getResourceSet());

        // Load mapping model for Zeta
        String dialect = "hsqldb";
        java.net.URI excelModelUri = Asm2Rdbms.calculateAsm2RdbmsModelURI();
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms-zeta"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", excelModelUri)
                                .toURL()
                                .openStream()));
        rdbmsModelZeta.getResource().getContents().addAll(mappingModel.getResource().getContents());

        Asm2RdbmsZetaTransformation zetaTransformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(asmModelZeta)
                .rdbmsModel(rdbmsModelZeta)
                .dialect("hsqldb")
                .build();
        zetaTransformation.execute();

        // Compare models
        ModelComparator.ComparisonResult result = ModelComparator.compare(
                rdbmsModelEtl.getResourceSet().getResources().get(0).getContents().get(0),
                rdbmsModelZeta.getResourceSet().getResources().get(0).getContents().get(0),
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
