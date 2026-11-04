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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.rdbms2liquibase.zeta.Rdbms2LiquibaseZetaTransformation;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.Optional;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;

/**
 * RDBMS to Liquibase transformation work. Zeta runs in this (Epsilon-free) artifact; ETL/DUAL delegate to the
 * optional {@link Rdbms2LiquibaseEtlEngine} add-on. See change: split-etl-artifacts.
 */
@Slf4j
public class Rdbms2LiquibaseWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "rdbms2LiquibaseWorkParameter")
    public static final class Rdbms2LiquibaseWorkParameter {
        @Builder.Default
        Boolean createTrace = false;
        @Builder.Default
        Boolean parallel = true;
        @Builder.Default
        Boolean useCache = true;
        
        /**
         * The transformation engine to use. Defaults to ZETA.
         * Set to ETL for backward compatibility or debugging.
         */
        @Builder.Default
        TransformationMode transformationMode = TransformationMode.fromSystemProperty();
    }

    private final String dialect;

    public Rdbms2LiquibaseWork(TransformationContext transformationContext, String dialect) {
        super(transformationContext);
        this.dialect = dialect;
    }

    public static Optional<RdbmsModel> getRdbmsModel(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(RdbmsModel.class, "rdbms:" + dialect);
    }

    public static void putLiquibaseModel(TransformationContext transformationContext, LiquibaseModel liquibaseModel, String dialect) {
        transformationContext.put("liquibase:" + dialect, liquibaseModel);
    }

    public static Optional<LiquibaseModel> getLiquibaseModel(TransformationContext transformationContext, String dialect) {
        return transformationContext.get(LiquibaseModel.class, "liquibase:" + dialect);
    }

    @Override
    public void execute() throws Exception {
        final RdbmsModel rdbmsModel = getRdbmsModel(getTransformationContext(), dialect)
                .orElseThrow(() -> new IllegalArgumentException("RDBMS Model of " + dialect + " dialect does not found in transformation context"));

        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        final LiquibaseModel liquibaseModel = getTransformationContext()
                .getByClass(LiquibaseModel.class)
                .orElseGet(() -> buildLiquibaseModel()
                        .name(rdbmsModel.getName())
                        .version(rdbmsModel.getVersion())
                        .build());
        putLiquibaseModel(getTransformationContext(), liquibaseModel, dialect);

        Rdbms2LiquibaseWorkParameter workParam = getTransformationContext().getByClass(Rdbms2LiquibaseWorkParameter.class)
                .orElseGet(() -> Rdbms2LiquibaseWorkParameter.rdbms2LiquibaseWorkParameter().build());

        if (workParam.transformationMode.isZeta()) {
            log.info("Executing RDBMS to Liquibase transformation using Zeta engine for dialect: {}", dialect);
            executeZetaTransformation(rdbmsModel, liquibaseModel, workParam);
        } else {
            log.info("Executing RDBMS to Liquibase transformation using ETL engine for dialect: {}", dialect);
            executeEtlTransformation(rdbmsModel, liquibaseModel, workParam);
        }
    }

    private void executeZetaTransformation(
            RdbmsModel rdbmsModel, LiquibaseModel liquibaseModel, Rdbms2LiquibaseWorkParameter workParam) {
        
        Rdbms2LiquibaseZetaTransformation transformation = Rdbms2LiquibaseZetaTransformation.builder()
                .rdbmsModel(rdbmsModel)
                .liquibaseModel(liquibaseModel)
                .dialect(dialect)
                .build();

        transformation.execute();
    }

    private void executeEtlTransformation(
            RdbmsModel rdbmsModel, LiquibaseModel liquibaseModel, Rdbms2LiquibaseWorkParameter workParam) throws Exception {
        
        EtlEngineRegistry.require(Rdbms2LiquibaseEtlEngine.class, Rdbms2LiquibaseEtlEngine.ARTIFACT_COORDINATES)
                .execute(rdbmsModel, liquibaseModel, dialect,
                        (Logger) getTransformationContext().get(Logger.class).orElse(null),
                        workParam.createTrace, workParam.useCache, workParam.parallel);
    }
}
