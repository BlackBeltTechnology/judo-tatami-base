package hu.blackbelt.judo.tatami.rdbms2liquibase.osgi;

import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.epsilon.runtime.osgi.BundleURIHandler;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseNamespaceFixUriHandler;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.URIHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import static hu.blackbelt.judo.tatami.rdbms2liquibase.Rdbms2Liquibase.executeRdbms2LiquibaseTransformation;

@Component(immediate = true, service = Rdbms2LiquibaseTranformationSerivce.class)
@Slf4j
public class Rdbms2LiquibaseTranformationSerivce {

    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public LiquibaseModel install(RdbmsModel rdbmsModel) throws Exception {
        URIHandler liquibaseNamespaceFixUriHandlerFromBundle =
                new LiquibaseNamespaceFixUriHandler(
                        new BundleURIHandler("liquibase", "", bundleContext.getBundle())
                );

        URI liquibasUri = URI.createURI("liquibase:" + rdbmsModel.getName() + ".changlelog.xml");

        LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel()
                .name(rdbmsModel.getName())
                .version(rdbmsModel.getVersion())
                .uri(liquibasUri)
                .checksum(rdbmsModel.getChecksum())
                .tags(rdbmsModel.getTags())
                .uriHandler(liquibaseNamespaceFixUriHandlerFromBundle)
                .build();

        StringBuilderLogger logger = new StringBuilderLogger(Slf4jLog.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/rdbms2liquibase/transformations/rdbmsToLiquibase.etl")
                            .toURI()
                            .resolve(".");

            executeRdbms2LiquibaseTransformation(Rdbms2Liquibase.Rdbms2LiquibaseParameter.rdbms2LiquibaseParameter()
                    .rdbmsModel(rdbmsModel)
                    .liquibaseModel(liquibaseModel)
                    .log(logger)
                    .scriptUri(scriptUri)
                    .dialect("hsqldb"));

            log.info("\u001B[33m {}\u001B[0m", logger.getBuffer());
        } catch (Exception e) {
            log.info("\u001B[31m {}\u001B[0m", logger.getBuffer());
            throw e;
        }

        return liquibaseModel;
    }
}
