package hu.blackbelt.judo.tatami.psm2measure.zeta;

/*-
 * #%L
 * JUDO Tatami parent
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

/**
 * Constants for PSM to Measure transformation rule names.
 * These constants are used for tracing transformations and identifying
 * the source rules that created target elements.
 */
public final class Psm2MeasureRuleNames {

    private Psm2MeasureRuleNames() {
        // Utility class - prevent instantiation
    }

    // Measure transformation rules (measure.etl)
    /** @abstract - base rule for measure transformation */
    public static final String CREATE_MEASURE = "CreateMeasure";
    /** extends CreateMeasure - for non-derived measures */
    public static final String CREATE_BASE_MEASURE = "CreateBaseMeasure";
    /** extends CreateMeasure - for derived measures */
    public static final String CREATE_DERIVED_MEASURE = "CreateDerivedMeasure";
    /** helper - term for derived measure definition */
    public static final String CREATE_BASE_MEASURE_TERM = "CreateBaseMeasureTerm";

    // Unit transformation rules
    public static final String CREATE_UNIT = "CreateUnit";
    public static final String CREATE_DURATION_UNIT = "CreateDurationUnit";
}
