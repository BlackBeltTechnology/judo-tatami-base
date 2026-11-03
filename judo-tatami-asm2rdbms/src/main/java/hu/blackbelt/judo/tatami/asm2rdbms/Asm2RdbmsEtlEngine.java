package hu.blackbelt.judo.tatami.asm2rdbms;

/*-
 * #%L
 * Judo :: Tatami :: Asm2Rdbms
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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import org.slf4j.Logger;

/**
 * Contract of the optional ASM to RDBMS ETL engine, implemented by {@code judo-tatami-asm2rdbms-etl}
 * and located through {@link hu.blackbelt.judo.tatami.core.etl.EtlEngineRegistry}.
 * The engine reads the ETL mapping workbooks shipped in its own artifact. See change: split-etl-artifacts.
 */
public interface Asm2RdbmsEtlEngine {

    String ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-asm2rdbms-etl";

    /**
     * Runs the ETL transformation.
     *
     * @param log logger for the Epsilon execution; {@code null} uses a buffered SLF4J logger
     * @param parameter naming and execution options of the work
     */
    Asm2RdbmsTransformationTrace execute(AsmModel asmModel, RdbmsModel rdbmsModel, String dialect, Logger log,
                                         Asm2RdbmsWork.Asm2RdbmsWorkParameter parameter) throws Exception;
}
