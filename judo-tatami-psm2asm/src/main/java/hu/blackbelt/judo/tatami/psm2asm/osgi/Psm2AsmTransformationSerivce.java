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
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmTransformationTrace;
import hu.blackbelt.judo.tatami.psm2asm.Psm2AsmWork;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

/**
 * This service make the Psm2Asm transformation. The following functions are happens:
 *  - When a PsmModdel is installed, it calls the Psm2Asm transformation and the result AsmModel is registered
 *  as an OSGi service.
 *
 * The transformation runs through {@link Psm2AsmWork}, so it honours the transformation mode (ZETA by default);
 * ETL requires the judo-tatami-psm2asm-etl bundle. See change: split-etl-artifacts.
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
                .uri(URI.createURI("asm:" + psmModel.getName() + ".asm"))
                .build();

        TransformationContext transformationContext = new TransformationContext(psmModel.getName());
        transformationContext.put(psmModel);
        transformationContext.put(asmModel);
        try {
            new Psm2AsmWork(transformationContext).execute();
        } catch (Exception e) {
            log.error("PSM to ASM transformation failed for model: " + psmModel.getName(), e);
            throw e;
        }
        Psm2AsmTransformationTrace transformationTrace = transformationContext
                .getByClass(Psm2AsmTransformationTrace.class)
                .orElseThrow(() -> new IllegalStateException("PSM to ASM transformation produced no trace"));

        psm2AsmTransformationTraceRegistration.put(psmModel,
                bundleContext.registerService(TransformationTrace.class, transformationTrace, new Hashtable<>()));
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
