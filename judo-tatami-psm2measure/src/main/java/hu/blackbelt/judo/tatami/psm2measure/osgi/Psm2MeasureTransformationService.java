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
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.Slf4jLog;
import hu.blackbelt.epsilon.runtime.execution.impl.StringBuilderLogger;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.core.TransformationTrace;
import hu.blackbelt.judo.tatami.psm2measure.Psm2Measure;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.util.Hashtable;
import java.util.Map;

import static hu.blackbelt.judo.tatami.psm2measure.Psm2Measure.executePsm2MeasureTransformation;


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
                .checksum(psmModel.getChecksum())
                .tags(psmModel.getTags())
                .build();

        StringBuilderLogger logger = new StringBuilderLogger(Slf4jLog.determinateLogLevel(log));
        try {
            java.net.URI scriptUri =
                    bundleContext.getBundle()
                            .getEntry("/tatami/psm2measure/transformations/measure/psmToMeasure.etl")
                            .toURI()
                            .resolve(".");

            Psm2MeasureTransformationTrace psm2MeasureTransformationTrace =
                    executePsm2MeasureTransformation(Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter()
                            .psmModel(psmModel)
                            .measureModel(measureModel)
                            .log(logger)
                            .scriptUri(scriptUri));

            psm2MeasureTransformationTraceRegistration.put(psmModel,
                    bundleContext.registerService(TransformationTrace.class, psm2MeasureTransformationTrace, new Hashtable<>()));

            log.info("\u001B[33m {}\u001B[0m", logger.getBuffer());
        } catch (Exception e) {
            log.info("\u001B[31m {}\u001B[0m", logger.getBuffer());
            throw e;
        }

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
