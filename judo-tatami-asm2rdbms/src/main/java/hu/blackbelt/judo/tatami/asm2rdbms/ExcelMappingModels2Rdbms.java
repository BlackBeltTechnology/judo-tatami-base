package hu.blackbelt.judo.tatami.asm2rdbms;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Rdbms
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

import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.meta.rdbmsDataTypes.RdbmsDataTypesFactory;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMapping;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMappings;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMapping;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMappings;
import hu.blackbelt.judo.meta.rdbmsNameMapping.RdbmsNameMappingFactory;
import hu.blackbelt.judo.meta.rdbmsRules.RdbmsRulesFactory;
import hu.blackbelt.judo.meta.rdbmsRules.Rule;
import hu.blackbelt.judo.meta.rdbmsRules.Rules;
import hu.blackbelt.judo.tatami.core.UriResolver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;

/**
 * Builds the RDBMS name-mapping, type-mapping and table-mapping-rule models from the CSV mapping tables
 * shipped under {@value #MODEL_ROOT_TATAMI_EXCEL_MAPPING}.
 *
 * <p>Plain Java, no Epsilon. Until change split-etl-artifacts this ran the {@code excelTo*.etl} scripts over
 * the {@code .xlsx} workbooks; the workbooks were converted to CSV and the three ETL rules are reproduced here
 * value-for-value (verified by {@code ExcelMappingParityTest} in {@code judo-tatami-asm2rdbms-etl}):</p>
 * <ul>
 *   <li>a numeric cell of an untyped column renders as Epsilon renders it, i.e. as a Java double
 *   ({@code 4095} becomes {@code "4095.0"});</li>
 *   <li>{@code Rule.cascade} is always {@code false}: {@code excelToRules.etl} assigns
 *   {@code t.cascade = t.cascade.asBoolean()} (target, not source). Kept for output parity.</li>
 * </ul>
 */
@Slf4j
public class ExcelMappingModels2Rdbms {

    public static final String SCRIPT_ROOT_TATAMI_EXCEL_TO_RDBMS = "tatami/asm2rdbms/transformations/";
    public static final String MODEL_ROOT_TATAMI_EXCEL_MAPPING = "tatami/asm2rdbms/model/";

    static final String NAME_MAPPING_CSV = "RDBMS_Sql_Name_Mapping.csv";
    static final String RULE_MAPPING_CSV = "RDBMS_Table_Mapping_Rules.csv";

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Adds the name mappings, type mappings and table mapping rules for the dialect to the RDBMS model resource.
     *
     * @param scriptUri ignored; kept for source compatibility (the mappings were ETL-generated before split-etl-artifacts)
     * @param excelModelUri directory URI containing the mapping CSV files
     */
    public static void injectExcelMappings(RdbmsModel rdbmsModel, Logger log,
                                           URI scriptUri, URI excelModelUri, String dialect) throws Exception {
        rdbmsModel.getResource().getContents().add(nameMappings(readCsv(UriResolver.resolve(NAME_MAPPING_CSV, excelModelUri))));
        rdbmsModel.getResource().getContents().add(typeMappings(readCsv(UriResolver.resolve(typeMappingCsv(dialect), excelModelUri))));
        rdbmsModel.getResource().getContents().add(rules(readCsv(UriResolver.resolve(RULE_MAPPING_CSV, excelModelUri))));
    }

    static String typeMappingCsv(String dialect) {
        return "RDBMS_Data_Types_" + dialect.substring(0, 1).toUpperCase() + dialect.substring(1) + ".csv";
    }

    // --- excelToNameMapping.etl -------------------------------------------------------------------------------

    static NameMappings nameMappings(List<Map<String, String>> rows) {
        NameMappings target = RdbmsNameMappingFactory.eINSTANCE.createNameMappings();
        for (Map<String, String> s : rows) {
            if (!defined(s, "FullyQualifiedName")) {
                continue;
            }
            NameMapping t = RdbmsNameMappingFactory.eINSTANCE.createNameMapping();
            t.setFullyQualifiedName(s.get("FullyQualifiedName"));
            t.setRdbmsName(untyped(s, "SqlName"));
            target.getNameMappings().add(t);
        }
        return target;
    }

    // --- excelToTypeMapping.etl -------------------------------------------------------------------------------

    static TypeMappings typeMappings(List<Map<String, String>> rows) {
        TypeMappings target = RdbmsDataTypesFactory.eINSTANCE.createTypeMappings();
        for (Map<String, String> s : rows) {
            if (!defined(s, "Judo")) {
                continue;
            }
            TypeMapping t = RdbmsDataTypesFactory.eINSTANCE.createTypeMapping();
            t.setAsmType(untyped(s, "Judo"));
            t.setRdbmsType(untyped(s, "Type"));
            t.setRdbmsSize(defined(s, "Size") ? untyped(s, "Size") : "-1");
            t.setRdbmsPrecision(defined(s, "Precision") ? untyped(s, "Precision") : "-1");
            t.setRdbmsScale(defined(s, "Scale") ? untyped(s, "Scale") : "-1");
            t.setRdbmsJdbcType(untyped(s, "JdbcType"));
            if (defined(s, "SqlType")) {
                t.setRdbmsSqlType(asInteger(s.get("SqlType")));
            }
            target.getTypeMappings().add(t);
        }
        return target;
    }

    // --- excelToRules.etl -------------------------------------------------------------------------------------

    static Rules rules(List<Map<String, String>> rows) {
        Rules target = RdbmsRulesFactory.eINSTANCE.createRules();
        for (Map<String, String> s : rows) {
            if (!defined(s, "Symbol")) {
                continue;
            }
            Rule t = RdbmsRulesFactory.eINSTANCE.createRule();
            t.setSymbol(s.get("Symbol"));
            boolean opposite = asBoolean(s.get("Opposite"));
            t.setOpposite(opposite);
            t.setLower(asInteger(s.get("Lower")));
            t.setUpper(asInteger(s.get("Upper")));
            if (opposite) {
                t.setOppositeLower(asInteger(s.get("OppositeLower")));
                t.setOppositeUpper(asInteger(s.get("OppositeUpper")));
            } else {
                t.setOppositeLower(-2);
                t.setOppositeUpper(-2);
            }
            t.setContainer(asBoolean(s.get("Container")));
            t.setContainment(asBoolean(s.get("Containment")));
            t.setFirst(asBoolean(s.get("First")));
            t.setForeignKey(asBoolean(s.get("ForeignKey")));
            t.setInverseForeignKey(asBoolean(s.get("InverseForeignKey")));
            t.setReadOnly(defined(s, "ReadOnly") && asBoolean(s.get("ReadOnly")));
            // excelToRules.etl: "t.cascade = t.cascade.asBoolean()" reads the target, so cascade is always false.
            t.setCascade(false);
            t.setMandatory(defined(s, "Mandatory") && asBoolean(s.get("Mandatory")));
            t.setRangeConstraint(asBoolean(s.get("RangeConstraint")));
            t.setInverseRangeConstraint(asBoolean(s.get("InverseRangeConstraint")));
            t.setJoinTable(asBoolean(s.get("JoinTable")));
            target.getRules().add(t);
        }
        return target;
    }

    // --- cell semantics (Epsilon spreadsheet driver) ----------------------------------------------------------

    private static boolean defined(Map<String, String> row, String column) {
        String value = row.get(column);
        return value != null && !value.trim().isEmpty();
    }

    /** Untyped column: numeric cells render as Java doubles, as the Epsilon spreadsheet driver did. */
    private static String untyped(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return NUMBER.matcher(value.trim()).matches() ? String.valueOf(Double.parseDouble(value.trim())) : value;
    }

    private static int asInteger(String value) {
        return (int) Double.parseDouble(value.trim());
    }

    private static boolean asBoolean(String value) {
        return value != null && Boolean.parseBoolean(value.trim());
    }

    // --- CSV (RFC 4180 subset: comma separator, double-quote quoting, header row) -----------------------------

    static List<Map<String, String>> readCsv(URI csv) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.toURL().openStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> header = splitCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> cells = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
                }
                rows.add(row);
            }
        }
        return rows;
    }

    static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    // --- resource location ------------------------------------------------------------------------------------

    @SneakyThrows(URISyntaxException.class)
    public static URI calculateURI(String path) {
        URI root = ExcelMappingModels2Rdbms.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (root.toString().endsWith(".jar")) {
            root = new URI("jar:" + root.toString() + "!/" + path);
        } else if (root.toString().startsWith("jar:bundle:")) {
            root = new URI(root.toString().substring(4, root.toString().indexOf("!")) + path);
        } else {
            root = new URI(root.toString() + "/" + path);
        }
        return root;
    }

    /** @deprecated the mappings are no longer ETL-generated; kept for source compatibility. */
    @Deprecated
    public static URI calculateExcelMapping2RdbmsTransformationScriptURI() {
        return calculateURI(SCRIPT_ROOT_TATAMI_EXCEL_TO_RDBMS);
    }

    public static URI calculateExcelMappingModelURI() {
        return calculateURI(MODEL_ROOT_TATAMI_EXCEL_MAPPING);
    }

    /**
     * Build-time generator (exec-maven-plugin, see pom.xml): writes {@code mapping-<dialect>-rdbms.model}.
     * Arguments: output model file, dialect.
     */
    public static void main(String[] args) throws Exception {
        File rdbmsModelFile = new File(args[0]);
        String dialect = args[1];

        RdbmsModel rdbmsModel = buildRdbmsModel().build();

        // The RDBMS model resources have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        injectExcelMappings(rdbmsModel, log, null, calculateExcelMappingModelURI(), dialect);

        rdbmsModel.saveRdbmsModel(RdbmsModel.SaveArguments.rdbmsSaveArgumentsBuilder().file(rdbmsModelFile));
    }
}
