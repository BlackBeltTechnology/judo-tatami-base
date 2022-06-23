package hu.blackbelt.judo.tatami.asm2rdbms.osgi;

import com.google.common.collect.Maps;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsTransformationTrace;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

import static hu.blackbelt.judo.meta.rdbmsDataTypes.support.RdbmsDataTypesModelResourceSupport.registerRdbmsDataTypesMetamodel;
import static hu.blackbelt.judo.meta.rdbmsNameMapping.support.RdbmsNameMappingModelResourceSupport.registerRdbmsNameMappingMetamodel;
import static hu.blackbelt.judo.meta.rdbmsRules.support.RdbmsTableMappingRulesModelResourceSupport.registerRdbmsTableMappingRulesMetamodel;
import static hu.blackbelt.judo.tatami.asm2rdbms.Asm2Rdbms.executeAsm2RdbmsTransformation;


@Component(immediate = true, service = Asm2RdbmsTransformationSerivce.class)
@Slf4j
public class Asm2RdbmsTransformationSerivce {

    Map<AsmModel, ServiceRegistration<TransformationTrace>> asm2rdbmsTransformationTraceRegistration = Maps.newHashMap();
    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }


    public RdbmsModel install(AsmModel asmModel, String dialect) throws Exception {

        RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel()
                .name(asmModel.getName())
                .version(asmModel.getVersion())
                .uri(URI.createURI("rdbms:" + asmModel.getName() + ".model"))
                .checksum(asmModel.getChecksum())
                .tags(asmModel.getTags())
                .build();

        // The RDBMS model resourceset have to know the mapping models
        registerRdbmsNameMappingMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsDataTypesMetamodel(rdbmsModel.getResourceSet());
        registerRdbmsTableMappingRulesMetamodel(rdbmsModel.getResourceSet());

        StringBuilderLogger logger = new StringBuilderLogger(Slf4jLog.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/asm2rdbms/transformations/asmToRdbms.etl")
                            .toURI()
                            .resolve(".");

            java.net.URI excelModelUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/asm2rdbms/model/typemapping.xml")
                            .toURI()
                            .resolve(".");

            Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace = executeAsm2RdbmsTransformation(Asm2Rdbms.Asm2RdbmsParameter.asm2RdbmsParameter()
                            .asmModel(asmModel)
                            .rdbmsModel(rdbmsModel)
                            .log(logger)
                            .scriptUri(scriptUri)
                            .excelModelUri(excelModelUri)
                            .dialect(dialect));

            asm2rdbmsTransformationTraceRegistration.put(asmModel,
                    bundleContext.registerService(TransformationTrace.class, asm2RdbmsTransformationTrace, new Hashtable<>()));

            log.info("\u001B[33m {}\u001B[0m", logger.getBuffer());
        } catch (Exception e) {
            log.info("\u001B[31m {}\u001B[0m", logger.getBuffer());
            throw e;
        }
        return rdbmsModel;
    }

    public void uninstall(AsmModel asmModel) {
        if (asm2rdbmsTransformationTraceRegistration.containsKey(asmModel)) {
            asm2rdbmsTransformationTraceRegistration.get(asmModel).unregister();
        } else {
            log.error("ASM model is not installed: " + asmModel.toString());
        }
    }
}
