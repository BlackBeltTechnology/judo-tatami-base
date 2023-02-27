package hu.blackbelt.judo.tatami.rdbms2liquibase;

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

import hu.blackbelt.judo.meta.liquibase.Column;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.LiquibaseValidationException;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseUtils;
import hu.blackbelt.judo.meta.rdbms.RdbmsJunctionTable;
import hu.blackbelt.judo.meta.rdbms.RdbmsTable;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.RdbmsValidationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.SaveArguments.liquibaseSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.SaveArguments.rdbmsSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils.newRdbmsJunctionTableBuilderInit;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsUtils.newRdbmsTableBuilderInit;
import static hu.blackbelt.judo.meta.rdbms.util.builder.RdbmsBuilders.*;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter;
import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class Rdbms2LiquibaseContentTest {
    final static String TARGET_TEST_CLASSES = "target/test-classes";

    final static String TEST_TABLE1_NAME = "TestTable1";
    final static String TEST_TABLE2_NAME = "TestTable2";
    final static String TEST_JUNCTION_TABLE_NAME = "TestJunctionTable";
    final static String ID_NAME = "_id";
    final static String NUMBER_FIELD_NAME = "NumberField";
    final static String TEST_TABLE1_FK = TEST_TABLE1_NAME + "_fk1";
    final static String TEST_TABLE2_FK = TEST_TABLE2_NAME + "_fk2";

    private LiquibaseUtils liquibaseUtils;

    private void compareChangeSet(final Set<String> expected) {
        liquibaseUtils.getChangeSets()
                .orElseThrow(() -> new RuntimeException("No ChangeSet found"))
                .forEach(e -> {
                    assertTrue(expected.contains(e.getId()), e.getId() + " ChangeSet is not expected");
                    expected.remove(e.getId());
                });
        if (!expected.isEmpty())
            fail(format("ChangeSets are missing: %s", expected.toString()));
    }

    private void compareCreateTables(final Set<String> expected, final String changeSetId) {
        liquibaseUtils.getCreateTables(changeSetId)
                .orElseThrow(() -> new RuntimeException(changeSetId + " ChangeSet cannot be found"))
                .forEach(e -> {
                    assertTrue(expected.contains(e.getTableName()), e.getTableName() + " CreateTable is not expected");
                    expected.remove(e.getTableName());
                });
        if (!expected.isEmpty())
            fail(format("Missing CreateTable from %s: %s", changeSetId, expected.toString()));
    }

    private void compareColumns(final Set<String> expected, final String changeSetId, final String tableName) {
        liquibaseUtils.getColumns(changeSetId, tableName)
                .orElseThrow(() -> new RuntimeException(tableName + " CreateTable cannot be found"))
                .forEach(e -> {
                    assertTrue(expected.contains(e.getName()), e.getName() + " Column is not expected");
                    expected.remove(e.getName());
                });
        if (!expected.isEmpty())
            fail(format("Missing Column from %s: %s", tableName, expected.toString()));
    }

    private void compareAddPrimaryKeys(final Set<String> expected, final String changeSetId, final String tableName) {
        Column column = liquibaseUtils.getColumn(changeSetId, tableName, ID_NAME)
                .orElseThrow(() -> new RuntimeException(changeSetId + " ChangeSet cannot be found"));

        assertNotNull(column.getConstraints(), "Id have no constraint");
        assertTrue(((Boolean) column.getConstraints().getPrimaryKey()), "Id is not primary key");
        assertFalse(((Boolean) column.getConstraints().getNullable()), "Id is nullable");
    }

    private void compareAddForeignKeyConstraints(final Set<String> expected, final String changeSetId, final String baseTable, final String refTable) {
        liquibaseUtils.getAddForeignKeyConstraints(changeSetId, baseTable, refTable)
                .orElseThrow(() -> new RuntimeException(changeSetId + " ChangeSet cannot be found"))
                .forEach(e -> {
                    assertTrue(expected.contains(e.getConstraintName()), e.getConstraintName() + " AddForeignKeyConstraints is not expected");
                    expected.remove(e.getConstraintName());
                });
        if (!expected.isEmpty())
            fail(format("Missing AddForeignKeyConstraints from %s: %s", changeSetId, expected.toString()));
    }

    private void compareAddNotNullConstraints(final Set<String> expected, final String changeSetId, final String tableName) {
        liquibaseUtils.getAddNotNullConstraints(changeSetId, tableName)
                .orElseThrow(() -> new RuntimeException(changeSetId + " ChangeSet cannot be found"))
                .forEach(e -> {
                    assertTrue(expected.contains(e.getColumnName()), e.getColumnName() + " AddNotNullConstraints is not expected");
                    expected.remove(e.getColumnName());
                });
        if (!expected.isEmpty())
            fail(format("Missing AddNotNullConstraints from %s: %s", changeSetId, expected.toString()));
    }

    @Test
    public void testContents() {
        /////////////////////
        // setup rdbms model

        final String MODEL_NAME = "TestModel";
        final RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();

        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        // table 1
        final RdbmsTable rdbmsTable1 = newRdbmsTableBuilderInit(TEST_TABLE1_NAME)
                .withSqlName(TEST_TABLE1_NAME)
                .withFields(
                        newRdbmsValueFieldBuilder()
                                .withName(NUMBER_FIELD_NAME)
                                .withUuid(TEST_TABLE1_NAME + "#" + NUMBER_FIELD_NAME)
                                .withSqlName(NUMBER_FIELD_NAME)
                                .withRdbmsTypeName("DECIMAL")
                                .withStorageByte(1)
                                .withScale(2)
                                .withPrecision(3)
                                .withSize(4)
                                .withMandatory(true)
                                .build()
                )
                .build();
        rdbmsTable1.getPrimaryKey().setSqlName(rdbmsTable1.getPrimaryKey().getName());
        rdbmsTable1.getPrimaryKey().setRdbmsTypeName("ID");

        // table 2
        final RdbmsTable rdbmsTable2 = newRdbmsTableBuilderInit(TEST_TABLE2_NAME)
                .withSqlName(TEST_TABLE2_NAME)
                .build();
        rdbmsTable2.getPrimaryKey().setSqlName(rdbmsTable2.getPrimaryKey().getName());
        rdbmsTable2.getPrimaryKey().setRdbmsTypeName("ID");

        // junction table
        final RdbmsJunctionTable rdbmsJunctionTable =
                newRdbmsJunctionTableBuilderInit(TEST_JUNCTION_TABLE_NAME, rdbmsTable1, rdbmsTable2).build();

        rdbmsJunctionTable.setSqlName(rdbmsJunctionTable.getName());
        rdbmsJunctionTable.getPrimaryKey().setSqlName(rdbmsJunctionTable.getPrimaryKey().getName());
        rdbmsJunctionTable.getPrimaryKey().setRdbmsTypeName("ID");
        rdbmsJunctionTable.getField1().setSqlName(rdbmsJunctionTable.getField1().getName());
        rdbmsJunctionTable.getField1().setRdbmsTypeName("FOREIGN_KEY");
        rdbmsJunctionTable.getField2().setSqlName(rdbmsJunctionTable.getField2().getName());
        rdbmsJunctionTable.getField2().setRdbmsTypeName("FOREIGN_KEY");

        rdbmsModel.addContent(
                newRdbmsModelBuilder()
                        .withName(MODEL_NAME)
                        .withRdbmsTables(rdbmsTable1)
                        .withRdbmsTables(rdbmsTable2)
                        .withRdbmsTables(rdbmsJunctionTable)
                        .withConfiguration(
                                newRdbmsConfigurationBuilder()
                                        .withDialect("hsqldb")
                                        .build()
                        )
                        .build()
        );

        // setup rdbms model
        ///////////////////////////////////////
        // build liquibase model and transform

        final LiquibaseModel liquibaseModel = buildLiquibaseModel().name(MODEL_NAME).build();

        try {
            rdbmsModel.saveRdbmsModel(
                    rdbmsSaveArgumentsBuilder()
                            .file(new File(TARGET_TEST_CLASSES, format("testContents-%s-rdbms.model", rdbmsModel.getName())))
                            .build());
            executeRdbms2LiquibaseTransformation(rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .dialect("hsqldb"));

            liquibaseModel.saveLiquibaseModel(
                    liquibaseSaveArgumentsBuilder()
                            .file(new File(TARGET_TEST_CLASSES, format("testContents-%s-liquibase.xml", liquibaseModel.getName())))
                            .build());
        } catch (URISyntaxException e) {
            fail("URISyntaxException", e);
        } catch (RdbmsValidationException e) {
            fail("Rdbms model is not valid", e);
        } catch (LiquibaseValidationException e) {
            fail("Liquibase model is not valid", e);
        } catch (IOException e) {
            log.warn("Unable to save model(s)");
        } catch (Exception e) {
            fail("Unknown exception", e);
        }

        liquibaseUtils = new LiquibaseUtils(liquibaseModel.getResourceSet());

        // build liquibase model and transform
        //////////////////////////////////////
        // initialize expected sets

        final String CHANGE_SET_CREATE_TABLE_ID = "create-table-";
        final String CHANGE_SET_CREATE_FOREIGN_KEY_ID = "create-foreignkeys-";
        final String CHANGE_SET_NOT_NULL_ID = "add-not-null-";

        Set<String> expectedChangeSets = new HashSet<>();
        Set<String> expectedCreateTable1 = new HashSet<>(); // rdbmsTable1
        Set<String> expectedCreateTable2 = new HashSet<>(); // rdbmsTable2
        Set<String> expectedCreateTable3 = new HashSet<>(); // junctiontable
        Set<String> expectedColumns1 = new HashSet<>();
        Set<String> expectedColumns2 = new HashSet<>();
        Set<String> expectedColumns3 = new HashSet<>();
        Set<String> expectedAddPrimaryKey1 = new HashSet<>();
        Set<String> expectedAddPrimaryKey2 = new HashSet<>();
        Set<String> expectedAddPrimaryKey3 = new HashSet<>();
        Set<String> expectedAddForeignKeyConstraint = new HashSet<>();
        Set<String> expectedAddForeignKeyConstraint1 = new HashSet<>();
        Set<String> expectedAddNotNullConstraint = new HashSet<>();

        // initialize expected sets
        ///////////////////////////
        // fill expected sets

        expectedChangeSets.add(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName());
        expectedChangeSets.add(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName());
        expectedChangeSets.add(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName());
        expectedChangeSets.add(CHANGE_SET_CREATE_FOREIGN_KEY_ID + rdbmsJunctionTable.getSqlName());
        expectedChangeSets.add(CHANGE_SET_NOT_NULL_ID + rdbmsTable1.getSqlName());

        expectedCreateTable1.add(rdbmsTable1.getSqlName());
        expectedCreateTable2.add(rdbmsTable2.getSqlName());
        expectedCreateTable3.add(rdbmsJunctionTable.getSqlName());

        expectedColumns1.add(ID_NAME);
        expectedColumns1.add(NUMBER_FIELD_NAME);
        expectedColumns2.add(ID_NAME);
        expectedColumns3.add(ID_NAME);
        expectedColumns3.add(TEST_TABLE1_FK);
        expectedColumns3.add(TEST_TABLE2_FK);

        expectedAddPrimaryKey1.add(ID_NAME);
        expectedAddPrimaryKey2.add(ID_NAME);
        expectedAddPrimaryKey3.add(ID_NAME);

        expectedAddForeignKeyConstraint.add(TEST_TABLE1_NAME);
        expectedAddForeignKeyConstraint1.add(TEST_TABLE2_NAME);

        expectedAddNotNullConstraint.add(NUMBER_FIELD_NAME);

        // fill expected sets
        //////////////////////////////////
        // run assertion on expected sets

        compareChangeSet(expectedChangeSets);

        compareCreateTables(expectedCreateTable1, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName());
        compareCreateTables(expectedCreateTable2, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName());
        compareCreateTables(expectedCreateTable3, CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName());

        compareColumns(expectedColumns1, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName());
        compareColumns(expectedColumns2, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName(), rdbmsTable2.getSqlName());
        compareColumns(expectedColumns3, CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName());

        compareAddPrimaryKeys(expectedAddPrimaryKey1, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName());
        compareAddPrimaryKeys(expectedAddPrimaryKey2, CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName(), rdbmsTable2.getSqlName());
        compareAddPrimaryKeys(expectedAddPrimaryKey3, CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName());

        compareAddForeignKeyConstraints(expectedAddForeignKeyConstraint,
                                        CHANGE_SET_CREATE_FOREIGN_KEY_ID + rdbmsJunctionTable.getSqlName(),
                                        rdbmsJunctionTable.getSqlName(),
                                        rdbmsTable1.getSqlName());
        compareAddForeignKeyConstraints(expectedAddForeignKeyConstraint1,
                                        CHANGE_SET_CREATE_FOREIGN_KEY_ID + rdbmsJunctionTable.getSqlName(),
                                        rdbmsJunctionTable.getSqlName(),
                                        rdbmsTable2.getSqlName());

        compareAddNotNullConstraints(expectedAddNotNullConstraint, CHANGE_SET_NOT_NULL_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName());

        // run assertion on expected sets
        /////////////////////////////////
        // check transform elements

        // TestTable1
        assertEquals(TEST_TABLE1_NAME,
                     liquibaseUtils.getCreateTable(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName())
                             .get().getRemarks());
        assertEquals("ID",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName(), ID_NAME)
                             .get().getType());
        assertEquals(TEST_TABLE1_NAME + "#" + ID_NAME,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName(), ID_NAME)
                             .get().getRemarks());
        assertEquals("DECIMAL(3, 2)",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName(), NUMBER_FIELD_NAME)
                             .get().getType());
        assertEquals(TEST_TABLE1_NAME + "#" + NUMBER_FIELD_NAME,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName(), NUMBER_FIELD_NAME)
                             .get().getRemarks());
        assertEquals("DECIMAL(3, 2)",
                     liquibaseUtils.getAddNotNullConstraint(CHANGE_SET_NOT_NULL_ID + rdbmsTable1.getSqlName(), rdbmsTable1.getSqlName(), NUMBER_FIELD_NAME)
                             .get().getColumnDataType());

        // TestTable2
        assertEquals("ID",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName(), rdbmsTable2.getSqlName(), ID_NAME)
                             .get().getType());
        assertEquals(TEST_TABLE2_NAME + "#" + ID_NAME,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsTable2.getSqlName(), rdbmsTable2.getSqlName(), ID_NAME)
                             .get().getRemarks());

        // RdbmsJunctionTable
        assertEquals("ID",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), ID_NAME)
                             .get().getType());
        assertEquals(TEST_JUNCTION_TABLE_NAME + "#" + ID_NAME,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), ID_NAME)
                             .get().getRemarks());
        assertEquals("FOREIGN_KEY",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), TEST_TABLE1_FK)
                             .get().getType());
        assertEquals(TEST_JUNCTION_TABLE_NAME + "#" + TEST_TABLE1_FK,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), TEST_TABLE1_FK)
                             .get().getRemarks());
        assertEquals("FOREIGN_KEY",
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), TEST_TABLE2_FK)
                             .get().getType());
        assertEquals(TEST_JUNCTION_TABLE_NAME + "#" + TEST_TABLE2_FK,
                     liquibaseUtils.getColumn(CHANGE_SET_CREATE_TABLE_ID + rdbmsJunctionTable.getSqlName(), rdbmsJunctionTable.getSqlName(), TEST_TABLE2_FK)
                             .get().getRemarks());

    }

}
