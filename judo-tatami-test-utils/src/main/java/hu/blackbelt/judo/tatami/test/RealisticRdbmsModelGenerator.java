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

import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbms.util.builder.RdbmsBuilders.*;

/**
 * Generates realistic RDBMS models with characteristics similar to the RackInspect real-world model.
 *
 * RackInspect RDBMS Model Characteristics (after ASM2RDBMS transformation):
 * - Multiple tables with fields, indexes, and foreign keys
 * - Tables for entities and transfer objects
 * - Junction tables for many-to-many relationships
 *
 * The generator creates RDBMS models that match the output of ASM2RDBMS transformation
 * from a RackInspect-like ASM model.
 */
public class RealisticRdbmsModelGenerator {

    // RackInspect RDBMS ratios
    public static final int FIELDS_PER_TABLE = 6;
    public static final int INDEXES_PER_TABLE = 2;
    public static final int FK_PER_TABLE = 1;

    @Getter
    @Builder
    public static class GeneratorConfig {
        @Builder.Default
        private int tableCount = 100;

        @Builder.Default
        private String modelName = "RealisticTestRdbms";

        @Builder.Default
        private boolean includeJunctionTables = true;

        @Builder.Default
        private boolean includeIndexes = true;

        @Builder.Default
        private boolean includeForeignKeys = true;
    }

    /**
     * Generate an RDBMS model with the specified number of tables.
     * Uses RackInspect-like ratios for fields, indexes, and foreign keys.
     */
    public RdbmsModel generate(int tableCount) {
        return generate(GeneratorConfig.builder().tableCount(tableCount).build());
    }

    /**
     * Generate an RDBMS model with full configuration.
     */
    public RdbmsModel generate(GeneratorConfig config) {
        RdbmsModel rdbmsModel = buildRdbmsModel().build();

        List<RdbmsTable> tables = new ArrayList<>();

        // Create main tables
        for (int i = 0; i < config.getTableCount(); i++) {
            RdbmsTable table = createTable(i, config);
            tables.add(table);
        }

        // Add foreign keys between tables
        if (config.isIncludeForeignKeys()) {
            addForeignKeys(tables, config);
        }

        // Create junction tables for many-to-many relationships
        List<RdbmsJunctionTable> junctionTables = new ArrayList<>();
        if (config.isIncludeJunctionTables()) {
            for (int i = 0; i < config.getTableCount() / 3; i++) {
                RdbmsJunctionTable junctionTable = createJunctionTable(i, tables, config);
                junctionTables.add(junctionTable);
            }
        }

        // Combine all tables
        List<RdbmsTable> allTables = new ArrayList<>();
        allTables.addAll(tables);
        allTables.addAll(junctionTables);

        // Create RDBMS model root
        hu.blackbelt.judo.meta.rdbms.RdbmsModel rdbmsRoot = newRdbmsModelBuilder()
                .withName(config.getModelName())
                .withVersion("1.0.0")
                .withRdbmsTables(allTables)
                .build();

        rdbmsModel.addContent(rdbmsRoot);

        return rdbmsModel;
    }

    private RdbmsTable createTable(int index, GeneratorConfig config) {
        List<RdbmsField> fields = new ArrayList<>();

        // Add ID field
        RdbmsIdentifierField idField = newRdbmsIdentifierFieldBuilder()
                .withName("ID")
                .withUuid("id_" + index)
                .withRdbmsTypeName("BIGINT")
                .withMandatory(true)
                .build();
        fields.add(idField);

        // Add value fields with mixed types
        for (int j = 0; j < FIELDS_PER_TABLE - 1; j++) {
            RdbmsField field = createValueField(index, j);
            fields.add(field);
        }

        // Create indexes
        List<RdbmsIndex> indexes = new ArrayList<>();
        if (config.isIncludeIndexes()) {
            for (int j = 0; j < INDEXES_PER_TABLE && j < fields.size(); j++) {
                RdbmsIndex idx = createIndex(index, j, fields.get(j + 1)); // Skip ID field
                indexes.add(idx);
            }
        }

        RdbmsTable table = newRdbmsTableBuilder()
                .withName("T_ENTITY" + index)
                .withUuid("table_" + index)
                .withSqlName("T_ENTITY" + index)
                .withFields(fields)
                .withIndexes(indexes)
                .withPrimaryKey(idField)
                .build();

        return table;
    }

    private RdbmsField createValueField(int tableIndex, int fieldIndex) {
        String fieldName;
        String rdbmsType;
        Integer size = null;
        Integer precision = null;
        Integer scale = null;

        switch (fieldIndex % 5) {
            case 0:
                fieldName = "str_field" + fieldIndex;
                rdbmsType = "VARCHAR(255)";
                size = 255;
                break;
            case 1:
                fieldName = "int_field" + fieldIndex;
                rdbmsType = "INTEGER";
                precision = 9;
                break;
            case 2:
                fieldName = "dec_field" + fieldIndex;
                rdbmsType = "DECIMAL(15,4)";
                precision = 15;
                scale = 4;
                break;
            case 3:
                fieldName = "bool_field" + fieldIndex;
                rdbmsType = "BOOLEAN";
                break;
            default:
                fieldName = "ts_field" + fieldIndex;
                rdbmsType = "TIMESTAMP";
                break;
        }

        var builder = newRdbmsValueFieldBuilder()
                .withName(fieldName)
                .withUuid("field_" + tableIndex + "_" + fieldIndex)
                .withRdbmsTypeName(rdbmsType)
                .withMandatory(fieldIndex == 0);

        if (size != null) {
            builder.withSize(size);
        }
        if (precision != null) {
            builder.withPrecision(precision);
        }
        if (scale != null) {
            builder.withScale(scale);
        }

        return builder.build();
    }

    private RdbmsIndex createIndex(int tableIndex, int indexNumber, RdbmsField field) {
        return newRdbmsIndexBuilder()
                .withName("IDX_ENTITY" + tableIndex + "_" + indexNumber)
                .withUuid("idx_" + tableIndex + "_" + indexNumber)
                .withUnique(indexNumber == 0)
                .withFields(field)
                .build();
    }

    private void addForeignKeys(List<RdbmsTable> tables, GeneratorConfig config) {
        for (int i = 0; i < tables.size(); i++) {
            RdbmsTable sourceTable = tables.get(i);

            for (int j = 0; j < FK_PER_TABLE && j + 1 < tables.size(); j++) {
                int targetIndex = (i + j + 1) % tables.size();
                RdbmsTable targetTable = tables.get(targetIndex);

                // Create FK field
                RdbmsForeignKey fkField = newRdbmsForeignKeyBuilder()
                        .withName("fk_" + targetTable.getName())
                        .withUuid("fk_" + i + "_" + j)
                        .withRdbmsTypeName("BIGINT")
                        .withMandatory(false)
                        .withReferenceKey(targetTable.getPrimaryKey())
                        .build();

                sourceTable.getFields().add(fkField);
            }
        }
    }

    private RdbmsJunctionTable createJunctionTable(int index, List<RdbmsTable> tables, GeneratorConfig config) {
        // Select two tables to join
        int table1Index = index * 2 % tables.size();
        int table2Index = (index * 2 + 1) % tables.size();

        RdbmsTable table1 = tables.get(table1Index);
        RdbmsTable table2 = tables.get(table2Index);

        // Create FK fields
        RdbmsForeignKey fk1 = newRdbmsForeignKeyBuilder()
                .withName("fk_" + table1.getName())
                .withUuid("jt_fk1_" + index)
                .withRdbmsTypeName("BIGINT")
                .withMandatory(true)
                .withReferenceKey(table1.getPrimaryKey())
                .build();

        RdbmsForeignKey fk2 = newRdbmsForeignKeyBuilder()
                .withName("fk_" + table2.getName())
                .withUuid("jt_fk2_" + index)
                .withRdbmsTypeName("BIGINT")
                .withMandatory(true)
                .withReferenceKey(table2.getPrimaryKey())
                .build();

        List<RdbmsField> fields = new ArrayList<>();
        fields.add(fk1);
        fields.add(fk2);

        return newRdbmsJunctionTableBuilder()
                .withName("J_" + table1.getName() + "_" + table2.getName())
                .withUuid("jt_" + index)
                .withSqlName("J_" + table1.getName() + "_" + table2.getName())
                .withFields(fields)
                .withField1(fk1)
                .withField2(fk2)
                .build();
    }

    /**
     * Count all tables in an RDBMS model.
     */
    public static int countTables(RdbmsModel rdbmsModel) {
        int count = 0;
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof hu.blackbelt.judo.meta.rdbms.RdbmsModel) {
                    count += ((hu.blackbelt.judo.meta.rdbms.RdbmsModel) content).getRdbmsTables().size();
                }
            }
        }
        return count;
    }

    /**
     * Count all fields across all tables in an RDBMS model.
     */
    public static int countFields(RdbmsModel rdbmsModel) {
        int count = 0;
        for (var resource : rdbmsModel.getResourceSet().getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof hu.blackbelt.judo.meta.rdbms.RdbmsModel) {
                    for (var table : ((hu.blackbelt.judo.meta.rdbms.RdbmsModel) content).getRdbmsTables()) {
                        count += table.getFields().size();
                    }
                }
            }
        }
        return count;
    }
}
