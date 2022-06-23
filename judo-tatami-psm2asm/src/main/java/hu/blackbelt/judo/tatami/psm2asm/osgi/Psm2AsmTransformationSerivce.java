package hu.blackbelt.judo.tatami.psm2asm.osgi;

import com.google.common.collect.Maps;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.psm2asm.Psm2Asm;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmTransformationTrace;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

import static hu.blackbelt.judo.tatami.psm2asm.Psm2Asm.executePsm2AsmTransformation;

/**
 * This service make the Psm2Asm transformation. The following functions are happens:
 *  - When a PsmModdel is installed, it calls the Psm2Asm transformation and the result AsmModel is registered
 *  as an OSGi service.
 */
@Component(immediate = true, service = Psm2AsmTransformationSerivce.class)
@Slf4j
public class Psm2AsmTransformationSerivce {

    Map<PsmModel, ServiceRegistration<TransformationTrace>> psm2AsmTransformationTraceRegistration = Maps.newHashMap();

    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public AsmModel install(PsmModel psmModel) throws Exception {
        AsmModel asmModel = AsmModel.buildAsmModel()
                .name(psmModel.getName())
                .version(psmModel.getVersion())
                .uri(URI.createURI("asm:" + psmModel.getName() + ".asm"))
                .checksum(psmModel.getChecksum())
                .tags(psmModel.getTags())
                .build();

        StringBuilderLogger logger = new StringBuilderLogger(Slf4jLog.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/psm2asm/transformations/asm/psmToAsm.etl")
                            .toURI()
                            .resolve(".");

            Psm2AsmTransformationTrace transformationTrace = executePsm2AsmTransformation(Psm2Asm.Psm2AsmParameter.psm2AsmParameter()
                    .psmModel(psmModel)
                    .asmModel(asmModel)
                    .log(logger)
                    .scriptUri(scriptUri));

            psm2AsmTransformationTraceRegistration.put(psmModel,
                    bundleContext.registerService(TransformationTrace.class, transformationTrace, new Hashtable<>()));
            log.info("\u001B[33m {}\u001B[0m", logger.getBuffer());
        } catch (Exception e) {
            log.info("\u001B[31m {}\u001B[0m", logger.getBuffer());
            throw e;
        }
        return asmModel;
    }


    public void uninstall(PsmModel psmModel) {
        if (psm2AsmTransformationTraceRegistration.containsKey(psmModel)) {
            psm2AsmTransformationTraceRegistration.get(psmModel).unregister();
        } else {
            log.error("PSM model is not installed: " + psmModel.toString());
        }
    }

}
