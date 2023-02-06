package hu.blackbelt.judo.tatami.psm2asm.osgi;

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

import static hu.blackbelt.judo.tatami.core.AnsiColor.red;
import static hu.blackbelt.judo.tatami.core.AnsiColor.yellow;
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
            log.info(yellow("{}"), logger.getBuffer());
        } catch (Exception e) {
            log.info(red("{}"), logger.getBuffer());
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
