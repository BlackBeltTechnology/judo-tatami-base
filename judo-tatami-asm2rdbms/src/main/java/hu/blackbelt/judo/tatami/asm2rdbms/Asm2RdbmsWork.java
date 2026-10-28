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

import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.zeta.transformation.core.TransformationTrace;
import lombok.Builder;
import org.eclipse.emf.ecore.EObject;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.epsilon.common.util.UriUtil;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Optional;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;

@Slf4j
public class Asm2RdbmsWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "asm2RdbmsWorkParameter")
    public static final class Asm2RdbmsWorkParameter {
        @Builder.Default
        Boolean createTrace = true;
        @Builder.Default
        Boolean parallel = true;
        @Builder.Default
        Boolean useCache = true;
        @Builder.Default
        Boolean createSimpleName = false;
        @Builder.Default
        Integer nameSize = -1;
        @Builder.Default
        Integer shortNameSize = -1;
        @Builder.Default
        Integer tableNameMaxSize = -1;
        @Builder.Default
        Integer columnMaxNameSize = -1;
        @Builder.Default
        String tablePrefix = "T_";
        @Builder.Default
        String columnPrefix = "C_";
        @Builder.Default
        String foreignKeyPrefix = "FK_";
        @Builder.Default
        String inverseForeignKeyPrefix = "FK_INV_";
        @Builder.Default
        String junctionTablePrefix = "J_";
        
        /**
         * The transformation engine to use. Defaults to ZETA.
         * Set to ETL for backward compatibility or debugging.
         */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    final URI transformationScriptRoot;
    final URI modelRoot;

    private String dialect;

    public Asm2RdbmsWork(TransformationContext transformationContext, URI transformationScriptRoot, URI modelRoot, String dialect) {
        super(transformationContext);
        this.transformationScriptRoot = transformationScriptRoot;
        this.modelRoot = modelRoot;
        this.dialect = dialect;
    }

    public Asm2RdbmsWork(TransformationContext transformationContex, String dialect) {
        this(transformationContex, Asm2Rdbms.calculateAsm2RdbmsTransformationScriptURI(), Asm2Rdbms.calculateAsm2RdbmsModelURI(), dialect);
    }

    public static void putModel(TransformationContext transformationContext, RdbmsModel rdbmsModel, String dialect) {
        transformationContext.put("rdbms:" + dialect, rdbmsModel);
    }

    public static Optional<RdbmsModel> getRdbmsModel(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(RdbmsModel.class, "rdbms:" + dialect);
    }

    public static void putAsm2RdbmsTrace(TransformationContext transformationContext, Asm2RdbmsTransformationTrace trace, String dialect) {
        transformationContext.put("asm2rdbmstrace:" + dialect, trace);
    }

    public static Optional<Asm2RdbmsTransformationTrace> getAsm2RdbmsTrace(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(Asm2RdbmsTransformationTrace.class, "asm2rdbmstrace:" + dialect);
    }

    @Override
    public void execute() throws Exception {
        Optional<AsmModel> asmModel = getTransformationContext().getByClass(AsmModel.class);
        asmModel.orElseThrow(() -> new IllegalArgumentException("ASM Model does not found in transformation context"));

        Asm2RdbmsWorkParameter workParameter = getTransformationContext().getByClass(Asm2RdbmsWorkParameter.class)
                .orElseGet(() -> Asm2RdbmsWork.Asm2RdbmsWorkParameter.asm2RdbmsWorkParameter().build());

        RdbmsModel rdbmsModel = getTransformationContext().getByClass(RdbmsModel.class)
                .orElseGet(() -> buildRdbmsModel()
                        .build());

        // The RDBMS model resources have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        putModel(getTransformationContext(), rdbmsModel, dialect);

        Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace;
        
        if (workParameter.transformationMode.isZeta()) {
            log.info("Executing ASM to RDBMS transformation using Zeta engine for dialect: {}", dialect);
            asm2RdbmsTransformationTrace = executeZetaTransformation(asmModel.get(), rdbmsModel, workParameter);
        } else {
            log.info("Executing ASM to RDBMS transformation using ETL engine for dialect: {}", dialect);
            asm2RdbmsTransformationTrace = executeEtlTransformation(asmModel.get(), rdbmsModel, workParameter);
        }

        putAsm2RdbmsTrace(getTransformationContext(), asm2RdbmsTransformationTrace, dialect);
    }

    private Asm2RdbmsTransformationTrace executeZetaTransformation(
            AsmModel asmModel, RdbmsModel rdbmsModel, Asm2RdbmsWorkParameter workParameter) throws Exception {

        // Load the mapping model (rules, type mappings, name mappings) into the RDBMS resource set
        // This is equivalent to what the ETL path does in Asm2Rdbms.executeAsm2RdbmsTransformation()
        RdbmsModel mappingModel = RdbmsModel.loadRdbmsModel(
                RdbmsModel.LoadArguments.rdbmsLoadArgumentsBuilder()
                        .validateModel(false)
                        .uri(org.eclipse.emf.common.util.URI.createURI("mem:mapping-" + dialect + "-rdbms"))
                        .inputStream(UriUtil.resolve("mapping-" + dialect + "-rdbms.model", modelRoot)
                                .toURL()
                                .openStream()));
        rdbmsModel.getResource().getContents().addAll(mappingModel.getResource().getContents());

        Asm2RdbmsZetaTransformation transformation = Asm2RdbmsZetaTransformation.builder()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .dialect(dialect)
                .tablePrefix(workParameter.tablePrefix)
                .columnPrefix(workParameter.columnPrefix)
                .foreignKeyPrefix(workParameter.foreignKeyPrefix)
                .inverseForeignKeyPrefix(workParameter.inverseForeignKeyPrefix)
                .junctionTablePrefix(workParameter.junctionTablePrefix)
                .nameSize(workParameter.nameSize)
                .shortNameSize(workParameter.shortNameSize)
                .tableNameMaxSize(workParameter.tableNameMaxSize)
                .columnNameMaxSize(workParameter.columnMaxNameSize)
                .createSimpleName(workParameter.createSimpleName)
                .build();

        // Execute Zeta transformation - returns native Zeta TransformationTrace
        TransformationTrace zetaTrace = transformation.execute();

        // Convert Zeta trace to legacy format so it can be saved/loaded as XMI
        Map<EObject, List<EObject>> legacyTrace = new java.util.LinkedHashMap<>();
        for (hu.blackbelt.judo.zeta.transformation.core.ElementResolutionCache.TraceEntry entry : zetaTrace.getEntries()) {
            legacyTrace.computeIfAbsent(entry.getSource(), k -> new java.util.ArrayList<>())
                    .add(entry.getTarget());
        }

        return Asm2RdbmsTransformationTrace.asm2RdbmsTransformationTraceBuilder()
                .asmModel(asmModel)
                .rdbmsModel(rdbmsModel)
                .trace(legacyTrace)
                .build();
    }

    private Asm2RdbmsTransformationTrace executeEtlTransformation(
            AsmModel asmModel, RdbmsModel rdbmsModel, Asm2RdbmsWorkParameter workParameter) throws Exception {
        
        try (final StringBuilderLogger logger = new StringBuilderLogger(log)) {
            return executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter()
                    .asmModel(asmModel)
                    .rdbmsModel(rdbmsModel)
                    .log(getTransformationContext().getByClass(Logger.class).orElseGet(() -> logger))
                    .scriptUri(transformationScriptRoot)
                    .excelModelUri(modelRoot)
                    .dialect(dialect)
                    .parallel(workParameter.parallel)
                    .useCache(workParameter.useCache)
                    .createTrace(workParameter.createTrace)
                    .createSimpleName(workParameter.createSimpleName)
                    .nameSize(workParameter.nameSize)
                    .shortNameSize(workParameter.shortNameSize)
                    .tableNameMaxSize(workParameter.tableNameMaxSize)
                    .columnNameMaxSize(workParameter.columnMaxNameSize)
                    .tablePrefix(workParameter.tablePrefix)
                    .columnPrefix(workParameter.columnPrefix)
                    .foreignKeyPrefix(workParameter.foreignKeyPrefix)
                    .inverseForeignKeyPrefix(workParameter.inverseForeignKeyPrefix)
                    .junctionTablePrefix(workParameter.junctionTablePrefix)
            );
        }
    }
}
