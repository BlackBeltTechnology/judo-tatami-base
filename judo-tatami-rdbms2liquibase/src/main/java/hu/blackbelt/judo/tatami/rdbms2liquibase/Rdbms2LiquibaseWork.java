package hu.blackbelt.judo.tatami.rdbms2liquibase;

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.workflow.work.AbstractTransformationWork;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;

@Slf4j
public class Rdbms2LiquibaseWork extends AbstractTransformationWork {

    @Builder(builderMethodName = "rdbms2LiquibaseWorkParameter")
    public static final class Rdbms2LiquibaseWorkParameter {
        @Builder.Default
        Boolean createTrace = false;
        @Builder.Default
        Boolean parallel = true;
    }

    final URI transformationScriptRoot;

    private final String dialect;

    public Rdbms2LiquibaseWork(TransformationContext transformationContext, URI transformationScriptRoot, String dialect) {
        super(transformationContext);
        this.transformationScriptRoot = transformationScriptRoot;
        this.dialect = dialect;
    }

    public Rdbms2LiquibaseWork(TransformationContext transformationContext, String dialect) {
        this(transformationContext, Rdbms2Liquibase.calculateRdbms2LiquibaseTransformationScriptURI(), dialect);
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
                .orElseGet(() -> buildLiquibaseModel().name(rdbmsModel.getName()).build());
        putLiquibaseModel(getTransformationContext(), liquibaseModel, dialect);

        try (final Log logger = new StringBuilderLogger(log)) {
            Rdbms2Liquibase.executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .log((Log) getTransformationContext().get(Log.class).orElseGet(() -> logger))
                    .scriptUri(transformationScriptRoot)
                    .dialect(dialect));
        }
    }

}
