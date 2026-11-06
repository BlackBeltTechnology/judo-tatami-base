package hu.blackbelt.judo.tatami.asm2rdbms.osgi;

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
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsTransformationTrace;
import hu.blackbelt.judo.tatami.asm2rdbms.Asm2RdbmsWork;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

/**
 * Runs the ASM to RDBMS transformation through {@link Asm2RdbmsWork}, honouring the transformation mode
 * (ZETA by default); ETL requires the judo-tatami-asm2rdbms-etl bundle. See change: split-etl-artifacts.
 */

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
                .uri(URI.createURI("rdbms:" + asmModel.getName() + ".model"))
                .build();

        // Asm2RdbmsWork registers the mapping metamodels on the RDBMS resource set
        TransformationContext transformationContext = new TransformationContext(asmModel.getName());
        transformationContext.put(asmModel);
        transformationContext.put(rdbmsModel);
        try {
            new Asm2RdbmsWork(transformationContext, dialect).execute();
        } catch (Exception e) {
            log.error("ASM to RDBMS transformation failed for model: " + asmModel.getName(), e);
            throw e;
        }
        Asm2RdbmsTransformationTrace asm2RdbmsTransformationTrace = Asm2RdbmsWork
                .getAsm2RdbmsTrace(transformationContext, dialect)
                .orElseThrow(() -> new IllegalStateException("ASM to RDBMS transformation produced no trace"));

        asm2rdbmsTransformationTraceRegistration.put(asmModel,
                bundleContext.registerService(TransformationTrace.class, asm2RdbmsTransformationTrace, new Hashtable<>()));
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
