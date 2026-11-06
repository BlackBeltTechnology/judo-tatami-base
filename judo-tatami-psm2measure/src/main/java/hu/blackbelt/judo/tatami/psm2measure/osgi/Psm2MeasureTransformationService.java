package hu.blackbelt.judo.tatami.psm2measure.osgi;

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
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureWork;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

/**
 * Runs the PSM to Measure transformation through {@link Psm2MeasureWork}, honouring the transformation mode
 * (ZETA by default); ETL requires the judo-tatami-psm2measure-etl bundle. See change: split-etl-artifacts.
 */

@Component(immediate = true, service = Psm2MeasureTransformationService.class)
@Slf4j
public class Psm2MeasureTransformationService {

    Map<PsmModel, ServiceRegistration<TransformationTrace>> psm2MeasureTransformationTraceRegistration = Maps.newHashMap();

    BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public MeasureModel install(PsmModel psmModel) throws Exception {

        MeasureModel measureModel = MeasureModel.buildMeasureModel()
                .name(psmModel.getName())
                .version(psmModel.getVersion())
                .uri(URI.createURI("measure:" + psmModel.getName() + ".measure"))
                .build();

        TransformationContext transformationContext = new TransformationContext(psmModel.getName());
        transformationContext.put(psmModel);
        transformationContext.put(measureModel);
        try {
            new Psm2MeasureWork(transformationContext).execute();
        } catch (Exception e) {
            log.error("PSM to Measure transformation failed for model: " + psmModel.getName(), e);
            throw e;
        }
        Psm2MeasureTransformationTrace psm2MeasureTransformationTrace = transformationContext
                .getByClass(Psm2MeasureTransformationTrace.class)
                .orElseThrow(() -> new IllegalStateException("PSM to Measure transformation produced no trace"));

        psm2MeasureTransformationTraceRegistration.put(psmModel,
                bundleContext.registerService(TransformationTrace.class, psm2MeasureTransformationTrace, new Hashtable<>()));

        return measureModel;
    }

    public void uninstall(PsmModel psmModel) {
        if (psm2MeasureTransformationTraceRegistration.containsKey(psmModel)) {
            psm2MeasureTransformationTraceRegistration.get(psmModel).unregister();
        } else {
            log.error("PSM model is not installed: " + psmModel.toString());
        }
    }
}
