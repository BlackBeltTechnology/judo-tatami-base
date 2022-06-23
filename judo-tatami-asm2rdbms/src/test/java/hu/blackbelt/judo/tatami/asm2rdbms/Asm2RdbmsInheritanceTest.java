package hu.blackbelt.judo.tatami.asm2rdbms;

import com.google.common.collect.ImmutableList;
import hu.blackbelt.judo.meta.rdbms.RdbmsIdentifierField;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.asm.runtime.AsmUtils.addExtensionAnnotation;
import static java.lang.String.format;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.newEClassBuilder;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.newEPackageBuilder;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
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

    @Test
    @DisplayName("Test Basic Inheritance")
    public void testBasicInheritance() {
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

        executeTransformation("testBasicInheritance");

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
    }

    @Test
    @DisplayName("Test Inheritance With Two Parents")
    public void testInheritanceWithTwoParents() {
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

        executeTransformation("testInheritanceWithTwoParents");

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
    }

    @Test
    @DisplayName("Test Indirect Inheritance")
    public void testIndirectInheritance() {
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

        executeTransformation("testIndirectInheritance");

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
    }

    @Test
    @DisplayName("Test Diamond Inheritance")
    public void testDiamondInheritance() {
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

        executeTransformation("testDiamondInheritance");

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

    }


}
