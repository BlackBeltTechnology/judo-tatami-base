package hu.blackbelt.judo.tatami.psm2measure.zeta.rules;

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

import hu.blackbelt.judo.meta.measure.DurationType;
import hu.blackbelt.judo.meta.measure.Measure;
import hu.blackbelt.judo.meta.measure.MeasureFactory;
import hu.blackbelt.judo.meta.measure.Unit;
import hu.blackbelt.judo.meta.psm.measure.DurationUnit;
import hu.blackbelt.judo.zeta.annotation.Extends;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;

import java.math.BigDecimal;
import java.util.function.Function;

import static hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureRuleNames.*;

/**
 * Unit transformation rules from unit.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateUnit - transforms PSM Unit to Measure Unit</li>
 *   <li>CreateDurationUnit - @Extends CreateUnit, maps duration types</li>
 * </ul>
 */
@RequiredArgsConstructor
public class UnitRules {

    private final MeasureFactory measureFactory;
    private final Function<hu.blackbelt.judo.meta.psm.measure.Unit, Measure> parentMeasureResolver;

    /**
     * Guard for CreateUnit: not s.isKindOf(JUDOPSM!DurationUnit)
     */
    public boolean isNotDurationUnit(hu.blackbelt.judo.meta.psm.measure.Unit unit) {
        return !(unit instanceof DurationUnit);
    }

    /**
     * rule CreateUnit
     *     transform s : JUDOPSM!Unit
     *     to t : MEASURES!Unit
     */
    @TransformRule(name = CREATE_UNIT, description = "Transform PSM Unit to Measure Unit")
    @Guard(method = "isNotDurationUnit")
    @Transform(type = hu.blackbelt.judo.meta.psm.measure.Unit.class)
    @To(type = Unit.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.measure.Unit, Unit> createUnit() {
        return (s, ctx) -> {
            Unit t = measureFactory.createUnit();
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            t.setRateDividend(new BigDecimal(String.valueOf(s.getRateDividend())));
            t.setRateDivisor(new BigDecimal(String.valueOf(s.getRateDivisor())));

            // Find parent measure and add unit to it
            Measure parentMeasure = parentMeasureResolver.apply(s);
            if (parentMeasure != null) {
                parentMeasure.getUnits().add(t);
            }

            return t;
        };
    }

    /**
     * rule CreateDurationUnit
     *     transform s : JUDOPSM!DurationUnit
     *     to t : MEASURES!DurationUnit
     *     extends CreateUnit
     */
    @TransformRule(name = CREATE_DURATION_UNIT, description = "Transform DurationUnit with duration type mapping")
    @Extends(CREATE_UNIT)
    @Transform(type = DurationUnit.class)
    @To(type = hu.blackbelt.judo.meta.measure.DurationUnit.class)
    public TransformFunction<DurationUnit, hu.blackbelt.judo.meta.measure.DurationUnit> createDurationUnit() {
        return (s, ctx) -> {
            hu.blackbelt.judo.meta.measure.DurationUnit t = measureFactory.createDurationUnit();
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            t.setRateDividend(new BigDecimal(String.valueOf(s.getRateDividend())));
            t.setRateDivisor(new BigDecimal(String.valueOf(s.getRateDivisor())));

            // Map duration type
            mapDurationType(t, s.getUnitType());

            // Find parent measure and add unit to it
            Measure parentMeasure = parentMeasureResolver.apply(s);
            if (parentMeasure != null) {
                parentMeasure.getUnits().add(t);
            }

            return t;
        };
    }

    private void mapDurationType(hu.blackbelt.judo.meta.measure.DurationUnit t,
                                  hu.blackbelt.judo.meta.psm.measure.DurationType psmType) {
        switch (psmType) {
            case NANOSECOND:
                t.setType(DurationType.NANOSECOND);
                break;
            case MICROSECOND:
                t.setType(DurationType.MICROSECOND);
                break;
            case MILLISECOND:
                t.setType(DurationType.MILLISECOND);
                break;
            case SECOND:
                t.setType(DurationType.SECOND);
                break;
            case MINUTE:
                t.setType(DurationType.MINUTE);
                break;
            case HOUR:
                t.setType(DurationType.HOUR);
                break;
            case DAY:
                t.setType(DurationType.DAY);
                break;
            case WEEK:
                t.setType(DurationType.WEEK);
                break;
            case MONTH:
                t.setType(DurationType.MONTH);
                break;
            case YEAR:
                t.setType(DurationType.YEAR);
                break;
            default:
                throw new IllegalArgumentException("Missing or unsupported unit type: " + psmType);
        }
    }
}
