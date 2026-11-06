package hu.blackbelt.judo.tatami.psm2measure.etl;

/*-
 * #%L
 * Judo :: Tatami :: Psm2Measure :: ETL
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureEtlEngine;
import hu.blackbelt.judo.tatami.psm2measure.Psm2MeasureTransformationTrace;
import org.slf4j.Logger;

/**
 * {@link Psm2MeasureEtlEngine} backed by the Epsilon ETL scripts of this artifact.
 * Discovered via META-INF/services (plain Java) or registered by {@code osgi.Psm2MeasureEtlEngineComponent} (Karaf).
 */
public class Psm2MeasureEtlEngineImpl implements Psm2MeasureEtlEngine {

    @Override
    public Psm2MeasureTransformationTrace execute(PsmModel psmModel, MeasureModel measureModel, Logger log,
                                              boolean createTrace, boolean useCache, boolean parallel) throws Exception {
        return Psm2Measure.executePsm2MeasureTransformation(Psm2Measure.Psm2MeasureParameter.psm2MeasureParameter()
                .psmModel(psmModel)
                .measureModel(measureModel)
                .log(log)
                .createTrace(createTrace)
                .useCache(useCache)
                .parallel(parallel));
    }
}
