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

import hu.blackbelt.judo.meta.rdbms.RdbmsForeignKey;
import hu.blackbelt.judo.meta.rdbms.RdbmsIdentifierField;
import hu.blackbelt.judo.meta.rdbms.RdbmsJunctionTable;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.asm.runtime.AsmUtils.addExtensionAnnotation;
import static java.lang.String.format;
import static org.eclipse.emf.ecore.util.builder.EcoreBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "JavaDoc"})
public class Asm2RdbmsRelationMappingTest extends Asm2RdbmsMappingTestBase {

    /**
     * Converts -1, 0 or 1 cardinality into human readable word
     *
     * @param cardinality -1, 0, 1
     * @return Infinite, Null or One
     */
    private String parseCardinality(int cardinality) {
        switch (cardinality) {
            case -1:
                return "Infinite";
            case 0:
                return "Null";
            case 1:
                return "One";
            default:
                throw new IllegalArgumentException("unexpected cardinality");
        }
    }

    /**
     * Concatenates 2 "readable" cardinalities for testing purposes
     *
     * @param lowerCardinality
     * @param upperCardinality
     * @return 2 "readable" cardinalities with "To" between them
     */
    private String parseCardinalities(int lowerCardinality, int upperCardinality) {
        return parseCardinality(lowerCardinality) + "To" + parseCardinality(upperCardinality);
    }


    /**
     * Concatenates 4 "readable" cardinalities for testing purposes
     *
     * @param lowerCardinality1
     * @param upperCardinality1
     * @param lowerCardinality2
     * @param upperCardinality2
     * @return 4 "readable" cardinalities with "To" and "And" between them
     */
    private String parseCardinalities(int lowerCardinality1, int upperCardinality1, int lowerCardinality2, int upperCardinality2) {
        return parseCardinality(lowerCardinality1) + "To" + parseCardinality(upperCardinality1) +
                "And" +
                parseCardinality(lowerCardinality2) + "To" + parseCardinality(upperCardinality2);
    }

    //////////////////////////////////////////////////////////////////////////
    /////////////////////////// ONE WAY RELATIONS ////////////////////////////
    //////////////////////////////////////////////////////////////////////////

    /**
     * Calls {@link #testOneWayRelation(int, int, boolean, boolean)} with both boolean parameters with false value
     *
     * @param lowerCardinality
     * @param upperCardinality
     */
    private void testOneWayRelation(int lowerCardinality, int upperCardinality) {
        testOneWayRelation(lowerCardinality, upperCardinality, false, false);
    }

    /**
     * Calls {@link #testOneWayRelation(int, int, boolean, boolean)} with isSelf parameter with false value
     *
     * @param lowerCardinality
     * @param upperCardinality
     * @param isContainment
     */
    private void testOneWayRelation(int lowerCardinality, int upperCardinality, boolean isContainment) {
        testOneWayRelation(lowerCardinality, upperCardinality, isContainment, false);
    }

    /**
     * Tests one way reference with given cardintaliy.
     * It can be a containment and/or can reference to itself.
     *
     * @param lowerCardinality
     * @param upperCardinality
     * @param isContainment
     * @param isSelf
     */
    private void testOneWayRelation(int lowerCardinality, int upperCardinality, boolean isContainment, boolean isSelf) {
        //////////////////////
        // parameter checking
        if (!((lowerCardinality == 0 && upperCardinality == 1) ||
                (lowerCardinality == 1 && upperCardinality == 1) ||
                (lowerCardinality == 0 && upperCardinality == -1) ||
                (lowerCardinality == 1 && upperCardinality == -1)))
            fail(format("Invalid cardinalities: %d, %d", lowerCardinality, upperCardinality));

        // parameter checking
        /////////////////////
        // setup asm model

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass oneWayRelation1 = newEClassBuilder()
                .withName("OneWayRelation1")
                .build();
        ePackage.getEClassifiers().add(oneWayRelation1);
        addExtensionAnnotation(oneWayRelation1, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EReference oneWayReference = newEReferenceBuilder()
                .withName("oneWayReference")
                .withLowerBound(lowerCardinality)
                .withUpperBound(upperCardinality)
                .withContainment(isContainment)
                .withEType(oneWayRelation1)
                .build();

        if (!isSelf) {
            final EClass oneWayRelation2 = newEClassBuilder()
                    .withName("OneWayRelation2")
                    .withEStructuralFeatures(oneWayReference)
                    .build();
            ePackage.getEClassifiers().add(oneWayRelation2);
            addExtensionAnnotation(oneWayRelation2, ENTITY_ANNOTATION, VALUE_ANNOTATION);
        } else {
            oneWayRelation1.getEStructuralFeatures().add(oneWayReference);
        }

        // setup asm model
        ////////////////////////
        // setup transformation

        final String transformationName;
        if (!isContainment && !isSelf) {
            transformationName = "testOneWayRelationWith" + parseCardinalities(lowerCardinality, upperCardinality) + "Cardinality";
        } else if (isContainment && !isSelf) {
            transformationName = "testOneWayContainmentWith" + parseCardinalities(lowerCardinality, upperCardinality) + "Cardinality";
        } else if (!isContainment) {
            transformationName = "testOneWaySelfRelationWidth" + parseCardinalities(lowerCardinality, upperCardinality) + "Cardinality";
        } else {
            transformationName = "testOneWaySelfContainmentWith" + parseCardinalities(lowerCardinality, upperCardinality) + "Cardinality";
        }

        executeTransformation(transformationName);

        // setup transformation
        ///////////////////////////////
        // prepare rdbms element names

        final String RDBMS_TABLE_NAME_1 = "TestEpackage.OneWayRelation1";
        final String RDBMS_TABLE_NAME_2 = isSelf ? RDBMS_TABLE_NAME_1 : "TestEpackage.OneWayRelation2";
        final String ONE_WAY_REFERENCE = isContainment
                ? "oneWayRelation" + (!isSelf ? "2" : "1") + "OneWayReference"
                : "oneWayReference";
        final String RDBMS_JUNCTION_TABLE_NAME = RDBMS_TABLE_NAME_2 + "#" + ONE_WAY_REFERENCE + " to " + RDBMS_TABLE_NAME_1;

        // prepare rdbms element names
        ///////////////////////////////////////////////
        // fill sets with required rdbms element names

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>();
        Set<String> fields2 = new HashSet<>();
        Set<String> fields3 = new HashSet<>();

        tables.add(RDBMS_TABLE_NAME_1);
        fields1.add(RDBMS_TABLE_NAME_1 + "#_type");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_version");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_username");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_user_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_timestamp");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_username");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_user_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_timestamp");
        if (!isSelf) {
            tables.add(RDBMS_TABLE_NAME_2);
            fields2.add(RDBMS_TABLE_NAME_2 + "#_type");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_version");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_username");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_user_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_timestamp");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_username");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_user_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_timestamp");
        }

        if (upperCardinality == -1 && !isContainment) {
            tables.add(RDBMS_JUNCTION_TABLE_NAME);
            fields3.add(RDBMS_JUNCTION_TABLE_NAME + "#id");
            fields3.add(ONE_WAY_REFERENCE);
            fields3.add("OneWayRelation" + (isSelf ? "1" : "2") + "#" + ONE_WAY_REFERENCE);
        } else if (isContainment) {
            fields1.add(ONE_WAY_REFERENCE);
        } else {
            (isSelf ? fields1 : fields2).add(ONE_WAY_REFERENCE);
        }

        // fill sets with required rdbms element names
        //////////////////////////////////////////////
        // compare required and actual rdbms elements

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_NAME_1);
        if (!isSelf) {
            assertFields(fields2, RDBMS_TABLE_NAME_2);
        }
        if (upperCardinality == -1 && !isContainment) {
            assertFields(fields3, RDBMS_JUNCTION_TABLE_NAME);
        }

        // compare required and actual rdbms elements
        //////////////////////////////////////////////////////////
        // "validate" model based on previously created asm model

        if (upperCardinality == -1 && !isContainment) {
            // SAVE - junction table, foreign keys and first primary key
            RdbmsJunctionTable rdbmsJunctionTable = rdbmsUtils.getRdbmsJunctionTable(RDBMS_JUNCTION_TABLE_NAME).get();
            EList<RdbmsForeignKey> rdbmsForeignKeys = rdbmsUtils.getRdbmsForeignKeys(RDBMS_JUNCTION_TABLE_NAME).get();
            RdbmsIdentifierField primaryKey1 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_NAME_1).get().getPrimaryKey();

            // ASSERTIONS - check if table1's primary key is in the junction tables foreign key "list" and is contained in field1
            assertTrue(rdbmsForeignKeys.stream().anyMatch(o -> o.getReferenceKey().equals(primaryKey1)));
            assertEquals(rdbmsJunctionTable.getField1().getReferenceKey(), primaryKey1);
            if (isSelf) {
                // ASSERTION - field2 must contain the same value
                assertEquals(rdbmsJunctionTable.getField2().getReferenceKey(), primaryKey1);
            } else {
                // SAVE - table2's primary key
                RdbmsIdentifierField primaryKey2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_NAME_2).get().getPrimaryKey();

                // ASSERTIONS - check if table2's primary key is in the junction tables foreign key "list" and is contained in field2
                assertTrue(rdbmsForeignKeys.stream().anyMatch(o -> o.getReferenceKey().equals(primaryKey2)));
                assertEquals(rdbmsJunctionTable.getField2().getReferenceKey(), primaryKey2);
            }

            // ASSERTION - field1's and field2's foreignkeysql names are not equal
            assertNotEquals(rdbmsJunctionTable.getField1().getForeignKeySqlName(),
                    rdbmsJunctionTable.getField2().getForeignKeySqlName());

        } else {
            final String contained = !isContainment || isSelf ? RDBMS_TABLE_NAME_1 : RDBMS_TABLE_NAME_2;
            final String container = !isContainment || isSelf ? RDBMS_TABLE_NAME_2 : RDBMS_TABLE_NAME_1;
            // ASSERTION - table1 (or 2) contains the other tables primary key
            assertEquals(rdbmsUtils.getRdbmsTable(contained).get().getPrimaryKey(),
                    rdbmsUtils.getRdbmsForeignKey(container, ONE_WAY_REFERENCE).get().getReferenceKey());
        }


    }

    //////////////////////////////////////////////////////////////////////////
    /////////////////////////// TWO WAY RELATIONS ////////////////////////////
    //////////////////////////////////////////////////////////////////////////

    /**
     * Calls {@link #testTwoWayRelation(int, int, int, int, boolean)} where both end of the reference is the same
     * and isSelf is false
     *
     * @param lowerCardinality
     * @param upperCardinality
     */
    private void testTwoWayRelation(int lowerCardinality, int upperCardinality) {
        testTwoWayRelation(lowerCardinality, upperCardinality, lowerCardinality, upperCardinality, false);
    }

    /**
     * Calls {@link #testTwoWayRelation(int, int, int, int, boolean)} where both end of the reference is the same.
     *
     * @param lowerCardinality
     * @param upperCardinality
     * @param isSelf
     */
    private void testTwoWayRelation(int lowerCardinality, int upperCardinality, boolean isSelf) {
        testTwoWayRelation(lowerCardinality, upperCardinality, lowerCardinality, upperCardinality, isSelf);
    }

    /**
     * Calls {@link #testTwoWayRelation(int, int, int, int, boolean)} where isSelf is false
     *
     * @param lowerCardinality1
     * @param upperCardinality1
     * @param lowerCardinality2
     * @param upperCardinality2
     */
    private void testTwoWayRelation(int lowerCardinality1, int upperCardinality1, int lowerCardinality2, int upperCardinality2) {
        testTwoWayRelation(lowerCardinality1, upperCardinality1, lowerCardinality2, upperCardinality2, false);
    }

    /**
     * Tests two way reference with given cardinalities.
     * It can reference to itself.
     *
     * @param lowerCardinality1
     * @param upperCardinality1
     * @param lowerCardinality2
     * @param upperCardinality2
     * @param isSelf
     */
    private void testTwoWayRelation(int lowerCardinality1, int upperCardinality1, int lowerCardinality2, int upperCardinality2, boolean isSelf) {
        //////////////////////
        // parameter checking
        if (!((lowerCardinality1 == 0 && upperCardinality1 == 1) ||
                (lowerCardinality1 == 1 && upperCardinality1 == 1) ||
                (lowerCardinality1 == 0 && upperCardinality1 == -1) ||
                (lowerCardinality1 == 1 && upperCardinality1 == -1) ||
                (lowerCardinality2 == 0 && upperCardinality2 == 1) ||
                (lowerCardinality2 == 1 && upperCardinality2 == 1) ||
                (lowerCardinality2 == 0 && upperCardinality2 == -1) ||
                (lowerCardinality2 == 1 && upperCardinality2 == -1))) {
            fail(format("Invalid cardinalities: %d, %d, %d, %d", lowerCardinality1, upperCardinality1, lowerCardinality2, upperCardinality2));
        }

        // parameter checking
        /////////////////////
        // setup asm model

        final EPackage ePackage = newEPackageBuilder()
                .withName("TestEpackage")
                .withNsPrefix("test")
                .withNsURI("http:///com.example.test.ecore")
                .build();
        asmModel.addContent(ePackage);

        final EClass twoWayRelation1 = newEClassBuilder()
                .withName("TwoWayRelation1")
                .build();
        ePackage.getEClassifiers().add(twoWayRelation1);
        addExtensionAnnotation(twoWayRelation1, ENTITY_ANNOTATION, VALUE_ANNOTATION);

        final EReference twoWayReference1 = newEReferenceBuilder()
                .withName("twoWayReference1")
                .withLowerBound(lowerCardinality1)
                .withUpperBound(upperCardinality1)
                .withEType(twoWayRelation1)
                .build();

        final EReference twoWayReference2 = newEReferenceBuilder()
                .withName("twoWayReference2")
                .withLowerBound(lowerCardinality2)
                .withUpperBound(upperCardinality2)
                .build();

        twoWayReference2.setEOpposite(twoWayReference1);
        twoWayRelation1.getEStructuralFeatures().add(twoWayReference2);

        if (!isSelf) {
            final EClass twoWayRelation2 = newEClassBuilder()
                    .withName("TwoWayRelation2")
                    .build();
            ePackage.getEClassifiers().add(twoWayRelation2);
            addExtensionAnnotation(twoWayRelation2, ENTITY_ANNOTATION, VALUE_ANNOTATION);

            twoWayReference2.setEType(twoWayRelation2);
            twoWayReference1.setEOpposite(twoWayReference2);
            twoWayRelation2.getEStructuralFeatures().add(twoWayReference1);
        } else {
            twoWayReference2.setEType(twoWayRelation1);
            twoWayReference1.setEOpposite(twoWayReference2);
            twoWayRelation1.getEStructuralFeatures().add(twoWayReference1);
        }

        // setup asm model
        ////////////////////////
        // setup transformation

        final String transformationName = !isSelf
                ? "testTwoWayRelationWith" + parseCardinalities(lowerCardinality1, upperCardinality1, lowerCardinality2, upperCardinality2) + "Cardinalities"
                : "testTwoWaySelfRelationWith" + parseCardinalities(lowerCardinality1, upperCardinality1, lowerCardinality2, upperCardinality2) + "Cardinalities";
        executeTransformation(transformationName);

        // setup transformation
        ///////////////////////////////
        // prepare rdbms element names

        final String RDBMS_TABLE_NAME_1 = "TestEpackage.TwoWayRelation1";
        final String RDBMS_TABLE_NAME_2 = isSelf ? RDBMS_TABLE_NAME_1 : "TestEpackage.TwoWayRelation2";
        final String TWO_WAY_REFERENCE1 = "twoWayReference1";
        final String TWO_WAY_REFERENCE2 = "twoWayReference2";
        final String RDBMS_JUNCTION_TABLE =
                RDBMS_TABLE_NAME_2 +
                        "#" +
                        TWO_WAY_REFERENCE1 +
                        " to " +
                        RDBMS_TABLE_NAME_1 +
                        "#" +
                        TWO_WAY_REFERENCE2;

        // prepare rdbms element names
        ///////////////////////////////////////////////
        // fill sets with required rdbms element names

        Set<String> tables = new HashSet<>();
        Set<String> fields1 = new HashSet<>();
        Set<String> fields2 = new HashSet<>();
        Set<String> fields3 = new HashSet<>();

        tables.add(RDBMS_TABLE_NAME_1);
        fields1.add(RDBMS_TABLE_NAME_1 + "#_type");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_version");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_username");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_user_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_create_timestamp");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_username");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_user_id");
        fields1.add(RDBMS_TABLE_NAME_1 + "#_update_timestamp");
        if (!isSelf) {
            tables.add(RDBMS_TABLE_NAME_2);
            fields2.add(RDBMS_TABLE_NAME_2 + "#_type");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_version");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_username");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_user_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_create_timestamp");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_username");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_user_id");
            fields2.add(RDBMS_TABLE_NAME_2 + "#_update_timestamp");
        }

        if (upperCardinality1 == -1 && upperCardinality2 == -1) {
            tables.add(RDBMS_JUNCTION_TABLE);
            fields3.add(RDBMS_JUNCTION_TABLE + "#id");
            fields3.add(TWO_WAY_REFERENCE2);
            fields3.add(TWO_WAY_REFERENCE1);
        } else if (upperCardinality2 == -1 || (upperCardinality1 != -1 && lowerCardinality2 == 0)) {
            (isSelf ? fields1 : fields2).add(TWO_WAY_REFERENCE1);
        } else {
            fields1.add(TWO_WAY_REFERENCE2);
        }
        final boolean decider = fields1.contains(TWO_WAY_REFERENCE2);

        // fill sets with required rdbms element names
        //////////////////////////////////////////////
        // compare required and actual rdbms elements

        assertTables(tables);
        assertFields(fields1, RDBMS_TABLE_NAME_1);
        if (!isSelf) {
            assertFields(fields2, RDBMS_TABLE_NAME_2);
        }
        if (upperCardinality1 == -1 && upperCardinality2 == -1) {
            assertFields(fields3, RDBMS_JUNCTION_TABLE);
        }

        // fill sets with required rdbms element names
        //////////////////////////////////////////////////////////
        // "validate" model based on previously created asm model

        if (upperCardinality1 == -1 && upperCardinality2 == -1) {
            // SAVE - rdbmsJunctionTable
            RdbmsJunctionTable rdbmsJunctionTable =
                    rdbmsUtils.getRdbmsJunctionTable(RDBMS_JUNCTION_TABLE)
                            .orElseThrow(() -> new RuntimeException(RDBMS_JUNCTION_TABLE + " junction table not found"));

            // SAVE - rdbmsForeignKeys
            EList<RdbmsForeignKey> rdbmsForeignKeys = rdbmsUtils.getRdbmsForeignKeys(RDBMS_JUNCTION_TABLE)
                    .orElseThrow(() -> new RuntimeException(RDBMS_JUNCTION_TABLE + " junction table not found"));

            // SAVE - primary key 1
            RdbmsIdentifierField primaryKey1 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_NAME_1)
                    .orElseThrow(() -> new RuntimeException(RDBMS_TABLE_NAME_1 + " table not found"))
                    .getPrimaryKey();

            // ASSERTION - primary key 1 can be found in junction table
            assertTrue(rdbmsForeignKeys.stream().anyMatch(o -> o.getReferenceKey().equals(primaryKey1)));

            // ASSERTION - field1 contains the correct primary key
            assertEquals(rdbmsJunctionTable.getField1().getReferenceKey(), primaryKey1);

            if (isSelf) {
                // ASSERTION - field2 contains the correct primary key
                assertEquals(rdbmsJunctionTable.getField2().getReferenceKey(), primaryKey1);
            } else {
                // SAVE - primary key 2
                RdbmsIdentifierField primaryKey2 = rdbmsUtils.getRdbmsTable(RDBMS_TABLE_NAME_2)
                        .orElseThrow(() -> new RuntimeException(RDBMS_TABLE_NAME_2 + " table not found"))
                        .getPrimaryKey();

                // ASSERTION - primary key 2 can be found in junction table
                assertTrue(rdbmsForeignKeys.stream().anyMatch(o -> o.getReferenceKey().equals(primaryKey2)));

                // ASSERTION - field2 contains the correct primary key
                assertEquals(rdbmsJunctionTable.getField2().getReferenceKey(), primaryKey2);
            }

            // ASSERTION - field1's and field2's foreignkeysqlname not equals
            assertNotEquals(rdbmsJunctionTable.getField1().getForeignKeySqlName(),
                    rdbmsJunctionTable.getField2().getForeignKeySqlName());
        } else {
            final String container = decider ? RDBMS_TABLE_NAME_1 : RDBMS_TABLE_NAME_2;
            final String contained = decider ? RDBMS_TABLE_NAME_2 : RDBMS_TABLE_NAME_1;

            // ASSERTION - check if foreign key is valid
            assertEquals(rdbmsUtils.getRdbmsTable(contained)
                            .orElseThrow(() -> new RuntimeException(contained + " table not found"))
                            .getPrimaryKey(),
                    rdbmsUtils.getRdbmsForeignKey(container, decider ? TWO_WAY_REFERENCE2 : TWO_WAY_REFERENCE1)
                            .orElseThrow(() -> new RuntimeException(decider ? TWO_WAY_REFERENCE2 : TWO_WAY_REFERENCE1 + " field not found"))
                            .getReferenceKey());
        }

    }

    //////////////////////////////////////////////////////////////////////////
    //////////////////////////////// TESTS ///////////////////////////////////
    //////////////////////////////////////////////////////////////////////////

    @Test
    @DisplayName("Test OneWayRelation With Null To Infinite Cardinality")
    public void testOneWayRelationWithNullToInfiniteCardinality() {
        testOneWayRelation(0, -1);
    }

    @Test
    @DisplayName("Test OneWayRelation With One To Infinite Cardinality")
    public void testOneWayRelationWithOneToInfiniteCardinality() {
        testOneWayRelation(1, -1);
    }

    @Test
    @DisplayName("Test OneWayRelation With Null To One Cardinality")
    public void testOneWayRelationWithNullToOneCardinality() {
        testOneWayRelation(0, 1);
    }

    @Test
    @DisplayName("Test OneWayRelation With One To One Cardinality")
    public void testOneWayRelationWithOneToOneCardinality() {
        testOneWayRelation(1, 1);
    }

    @Test
    @DisplayName("Test OneWayContainment With Null To Infinite Cardinality")
    public void testOneWayContainmentWithNullToInfiniteCardinality() {
        testOneWayRelation(0, -1, true);
    }

    @Test
    @DisplayName("Test OneWayContainment With One To Infinite Cardinality")
    public void testOneWayContainmentWithOneToInfiniteCardinality() {
        testOneWayRelation(1, -1, true);
    }

    @Test
    @DisplayName("Test OneWayContainment With Null To One Cardinality")
    public void testOneWayContainmentWithNullToOneCardinality() {
        testOneWayRelation(0, 1, true);
    }

    @Test
    @DisplayName("Test OneWayContainment With One To One Cardinality")
    public void testOneWayContainmentWithOneToOneCardinality() {
        testOneWayRelation(1, 1, true);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To Infinite Cardinalities")
    public void testTwoWayRelationWithNullToInfiniteCardinalities() {
        testTwoWayRelation(0, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To Infinite And Null To Infinite Cardinalities")
    public void testTwoWayRelationWithOneToInfiniteAndNullToInfiniteCardinalities() {
        testTwoWayRelation(1, -1, 0, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To Infinite And One To Infinite Cardinalities")
    public void testTwoWayRelationWithNullToInfiniteAndOneToInfiniteCardinalities() {
        testTwoWayRelation(0, -1, 1, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To Infinite Cardinalities")
    public void testTwoWayRelationWithOneToInfiniteCardinalities() {
        testTwoWayRelation(1, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To Infinite And Null To One Cardinalities")
    public void testTwoWayRelationWithNullToInfiniteAndNullToOneCardinalities() {
        testTwoWayRelation(0, -1, 0, 1);

    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To Infinite And One To One Cardinalities")
    public void testTwoWayRelationWithNullToInfiniteAndOneToOneCardinalities() {
        testTwoWayRelation(0, -1, 1, 1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To Infinite And Null To One Cardinalities")
    public void testTwoWayRelationWithOneToInfiniteAndNullToOneCardinalities() {
        testTwoWayRelation(1, -1, 0, 1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To Infinite And One To One Cardinalities")
    public void testTwoWayRelationWithOneToInfiniteAndOneToOneCardinalities() {
        testTwoWayRelation(1, -1, 1, 1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To One And Null To Infinite Cardinalities")
    public void testTwoWayRelationWithNullToOneAndNullToInfiniteCardinalities() {
        testTwoWayRelation(0, 1, 0, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To One And One To Infinite Cardinalities")
    public void testTwoWayRelationWithNullToOneAndOneToInfiniteCardinalities() {
        testTwoWayRelation(1, 1, 1, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With Null To One Cardinalities")
    public void testTwoWayRelationWithNullToOneCardinalities() {
        testTwoWayRelation(0, 1);
    }


    @Test
    @DisplayName("Test TwoWayRelation With Null To One And One To One Cardinalities")
    public void testTwoWayRelationWithNullToOneAndOnetoOneCardinalities() {
        testTwoWayRelation(0, 1, 1, 1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To One And Null to Infinite Cardinalities")
    public void testTwoWayRelationWithOneToOneAndNulltoInfiniteCardinalities() {
        testTwoWayRelation(1, 1, 0, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To One And One to Infinite Cardinalities")
    public void testTwoWayRelationWithOneToOneAndOnetoInfiniteCardinalities() {
        testTwoWayRelation(1, 1, 1, -1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To One And Null to One Cardinalities")
    public void testTwoWayRelationWithOneToOneAndNulltoOneCardinalities() {
        testTwoWayRelation(1, 1, 0, 1);
    }

    @Test
    @DisplayName("Test TwoWayRelation With One To One Cardinalities")
    public void testTwoWayRelationWithOneToOneCardinalities() {
        testTwoWayRelation(1, 1);
    }

    @Test
    @DisplayName("Test Self OneWayRelation With Null to One Cardinality")
    public void testSelfOneWayRelationWithNulltoOneCardinality() {
        testOneWayRelation(0, 1, false, true);
    }

    @Test
    @DisplayName("Test Self OneWayRelation With One to One Cardinality")
    public void testSelfOneWayRelationWithOnetoOneCardinality() {
        testOneWayRelation(1, 1, false, true);
    }

    @Test
    @DisplayName("Test Self OneWayRelation With Null to Infinite Cardinality")
    public void testSelfOneWayRelationWithNulltoInfiniteCardinality() {
        testOneWayRelation(0, -1, false, true);
    }

    @Test
    @DisplayName("Test Self OneWayRelation With One to Infinite Cardinality")
    public void testSelfOneWayRelationWithOnetoInfiniteCardinality() {
        testOneWayRelation(1, -1, false, true);
    }

    @Test
    @DisplayName("Test Self OneWayContainment With Null to One Cardinality")
    public void testSelfOneWayContainmentWithNulltoOneCardinality() {
        testOneWayRelation(0, 1, true, true);
    }

    @Test
    @DisplayName("Test Self OneWayContainment With One to One Cardinality")
    public void testSelfOneWayContainmentWithOnetoOneCardinality() {
        testOneWayRelation(1, 1, true, true);
    }

    @Test
    @DisplayName("Test Self OneWayContainment With Null to Infinite Cardinality")
    public void testSelfOneWayContainmentWithNulltoInfiniteCardinality() {
        testOneWayRelation(0, -1, true, true);
    }

    @Test
    @DisplayName("Test Self OneWayContainment With One to Infinite Cardinality")
    public void testSelfOneWayContainmentWithOnetoInfiniteCardinality() {
        testOneWayRelation(1, -1, true, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To One Cardinalities")
    public void testSelfTwoWayRelationWithNullToOneCardinalities() {
        testTwoWayRelation(0, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To One Cardinalities")
    public void testSelfTwoWayRelationWithOneToOneCardinalities() {
        testTwoWayRelation(1, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To One And Null To One Cardinalities")
    public void testSelfTwoWayRelationWithOneToOneAndNullToOneCardinalities() {
        testTwoWayRelation(1, 1, 0, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To One And One To One Cardinalities")
    public void testSelfTwoWayRelationWithNullToOneAndOneToOneCardinalities() {
        testTwoWayRelation(0, 1, 1, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To One And Null To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithNullToOneAndNullToInfiniteCardinalities() {
        testTwoWayRelation(0, 1, 0, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To One And One To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithNullToOneAndOneToInfiniteCardinalities() {
        testTwoWayRelation(0, 1, 1, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To One And Null To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithOneToOneAndNullToInfiniteCardinalities() {
        testTwoWayRelation(1, 1, 0, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To One And One To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithOneToOneAndOneToInfiniteCardinalities() {
        testTwoWayRelation(1, 1, 1, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To Infinite And Null To One Cardinalities")
    public void testSelfTwoWayRelationWithNullToInfiniteAndNullToOneCardinalities() {
        testTwoWayRelation(0, -1, 0, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To Infinite And One To One Cardinalities")
    public void testSelfTwoWayRelationWithNullToInfiniteAndOneToOneCardinalities() {
        testTwoWayRelation(0, -1, 1, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To Infinite And Null To One Cardinalities")
    public void testSelfTwoWayRelationWithOneToInfiniteAndNullToOneCardinalities() {
        testTwoWayRelation(1, -1, 0, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To Infinite And One To One Cardinalities")
    public void testSelfTwoWayRelationWithOneToInfiniteAndOneToOneCardinalities() {
        testTwoWayRelation(1, -1, 1, 1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithNullToInfiniteCardinalities() {
        testTwoWayRelation(0, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With Null To Infinite And One To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithNullToInfiniteAndOneToInfiniteCardinalities() {
        testTwoWayRelation(0, -1, 1, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To Infinite And Null To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithOneToInfiniteAndNullToInfiniteCardinalities() {
        testTwoWayRelation(1, -1, 0, -1, true);
    }

    @Test
    @DisplayName("Test Self TwoWayRelation With One To Infinite Cardinalities")
    public void testSelfTwoWayRelationWithOneToInfiniteCardinalities() {
        testTwoWayRelation(1, -1, true);
    }

}
