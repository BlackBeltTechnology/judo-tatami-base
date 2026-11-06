package hu.blackbelt.judo.tatami.psm.validation;

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

import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import org.slf4j.Logger;

/**
 * EVL (Epsilon) PSM validation engine, contributed by the optional {@code judo-tatami-psm-validation-etl} add-on.
 * PsmValidationWork looks it up via {@link hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry} in ETL mode only.
 * See change: split-etl-artifacts.
 */
public interface PsmValidationEtlEngine {

    /** Maven coordinates of the add-on implementing this engine (named in EtlEngineMissingException). */
    String ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-psm-validation-etl";

    /**
     * Runs the EVL validation. Throws when the model is invalid.
     *
     * @param log logger receiving the validation output; {@code null} = the implementation's own logger
     */
    void validate(PsmModel psmModel, Logger log) throws Exception;
}
