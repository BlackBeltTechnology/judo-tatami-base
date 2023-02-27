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
import hu.blackbelt.epsilon.runtime.execution.ExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.epsilon.common.util.UriUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import static hu.blackbelt.epsilon.runtime.execution.ExecutionContext.executionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.contexts.EtlExecutionContext.etlExecutionContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.emf.WrappedEmfModelContext.wrappedEmfModelContextBuilder;
import static hu.blackbelt.epsilon.runtime.execution.model.excel.ExcelModelContext.excelModelContextBuilder;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;

@Slf4j
public class ExcelMappingModels2Rdbms {

    public static final String SCRIPT_ROOT_TATAMI_EXCEL_TO_RDBMS = "tatami/asm2rdbms/transformations/";
    public static final String MODEL_ROOT_TATAMI_EXCEL_MAPPING = "tatami/asm2rdbms/model/";

    public static void injectExcelMappings(RdbmsModel rdbmsModel, Log log,
                                           URI scriptUri, URI excelModelUri, String dialect) throws Exception {

        // Execution context
        ExecutionContext executionContext = executionContextBuilder()
                .log(log)
                .modelContexts(ImmutableList.of(
                        wrappedEmfModelContextBuilder()
                                .log(log)
                                .name("RDBMS")
                                .resource(rdbmsModel.getResource())
                                .build(),
                        excelModelContextBuilder()
                                .name("TYPEMAPPING")
                                .excel(UriUtil.resolve("RDBMS_Data_Types_" + dialect.substring(0,1).toUpperCase() + dialect.substring(1), excelModelUri).toString() + ".xlsx")
                                .excelConfiguration(UriUtil.resolve("typemapping.xml", excelModelUri).toString())
                                .build(),
                        excelModelContextBuilder()
                                .name("RULEMAPPING")
                                .excel(UriUtil.resolve("RDBMS_Table_Mapping_Rules.xlsx", excelModelUri).toString())
                                .excelConfiguration(UriUtil.resolve("rulemapping.xml", excelModelUri).toString())
                                .build(),
                        excelModelContextBuilder()
                                .name("NAMEMAPPING")
                                .excel(UriUtil.resolve("RDBMS_Sql_Name_Mapping.xlsx", excelModelUri).toString())
                                .excelConfiguration(UriUtil.resolve("namemapping.xml", excelModelUri).toString())
                                .build()
                        )
                )
                .build();

        // run the model / metadata loading
        executionContext.load();


        EtlExecutionContext nameMappingExecutionContext = etlExecutionContextBuilder()
                .source(UriUtil.resolve("excelToNameMapping.etl", scriptUri))
                .build();

        EtlExecutionContext typeMappingExecutionContext = etlExecutionContextBuilder()
                .source(UriUtil.resolve("excelToTypeMapping.etl", scriptUri))
                .build();


        EtlExecutionContext rulesExecutionContext = etlExecutionContextBuilder()
                .source(UriUtil.resolve("excelToRules.etl", scriptUri))
                .build();


        // Transformation script
        executionContext.executeProgram(nameMappingExecutionContext);
        executionContext.executeProgram(typeMappingExecutionContext);
        executionContext.executeProgram(rulesExecutionContext);

        executionContext.commit();
        executionContext.close();
    }

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

    public static URI calculateExcelMapping2RdbmsTransformationScriptURI() {
        return calculateURI(SCRIPT_ROOT_TATAMI_EXCEL_TO_RDBMS);
    }

    public static URI calculateExcelMappingModelURI(){
        return calculateURI(MODEL_ROOT_TATAMI_EXCEL_MAPPING);
    }

    public static void main(String[] args) throws Exception {
        File rdbmsModelFile = new File(args[0]);
        String dialect = args[1];

        RdbmsModel rdbmsModel = buildRdbmsModel().build();

        // The RDBMS model resources have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());


        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            ExcelMappingModels2Rdbms.injectExcelMappings(
                    rdbmsModel,
                    bufferedLog,
                    ExcelMappingModels2Rdbms.calculateExcelMapping2RdbmsTransformationScriptURI(),
                    ExcelMappingModels2Rdbms.calculateExcelMappingModelURI(),
                    dialect);
        }

        rdbmsModel.saveRdbmsModel(RdbmsModel.SaveArguments.rdbmsSaveArgumentsBuilder().file(rdbmsModelFile));
    }

}
