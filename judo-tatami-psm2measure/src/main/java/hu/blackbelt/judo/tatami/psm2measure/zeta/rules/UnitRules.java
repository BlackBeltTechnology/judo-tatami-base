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

import hu.blackbelt.judo.meta.measure.BaseMeasure;
import hu.blackbelt.judo.meta.measure.DurationType;
import hu.blackbelt.judo.meta.measure.Measure;
import hu.blackbelt.judo.meta.measure.Unit;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.DurationUnit;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformGuard;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EObject;

import org.eclipse.emf.ecore.xmi.XMLResource;

import java.math.BigDecimal;

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
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = hu.blackbelt.judo.meta.psm.measure.Unit.class, target = Unit.class)
public class UnitRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public UnitRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard for CreateUnit: not s.isKindOf(JUDOPSM!DurationUnit)
     */
    public TransformGuard isNotDurationUnit() {
        return (source, ctx) -> !(source instanceof DurationUnit);
    }

    // =========================================================================
    // TRANSFORMATION RULES
    // =========================================================================

    /**
     * rule CreateUnit
     *     transform s : JUDOPSM!Unit
     *     to t : MEASURES!Unit
     */
    @TransformRule(name = UNIT, description = "Transform PSM Unit to Measure Unit")
    @Guard(method = "isNotDurationUnit")
    @Transform(type = hu.blackbelt.judo.meta.psm.measure.Unit.class)
    @To(type = Unit.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.measure.Unit, Unit> createUnit() {
        return (s, ctx) -> {
            Unit t = ctx.createTarget(Unit.class);
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            t.setRateDividend(new BigDecimal(String.valueOf(s.getRateDividend())));
            t.setRateDivisor(new BigDecimal(String.valueOf(s.getRateDivisor())));

            // Store XMI ID to match ETL format: (psm/<sourceId>)/Unit
            String sourceId = getSourceXmiId(s);
            if (sourceId != null) {
                String xmiId = "(psm/" + sourceId + ")/" + UNIT;
                storeCustomXmiId(ctx, t, xmiId);
            }

            // Find parent measure and add unit to it
            Measure parentMeasure = findEquivalentMeasure(s, ctx);
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
    @TransformRule(name = DURATION_UNIT, description = "Transform DurationUnit with duration type mapping")
    @Transform(type = DurationUnit.class)
    @To(type = hu.blackbelt.judo.meta.measure.DurationUnit.class)
    public TransformFunction<DurationUnit, hu.blackbelt.judo.meta.measure.DurationUnit> createDurationUnit() {
        return (s, ctx) -> {
            hu.blackbelt.judo.meta.measure.DurationUnit t = ctx.createTarget(hu.blackbelt.judo.meta.measure.DurationUnit.class);
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            t.setRateDividend(new BigDecimal(String.valueOf(s.getRateDividend())));
            t.setRateDivisor(new BigDecimal(String.valueOf(s.getRateDivisor())));

            // Map duration type
            mapDurationType(t, s.getUnitType());

            // Store custom XMI ID with type suffix to match ETL format
            // ETL format: (psm/<sourceId>)/DurationUnit<Type>
            String sourceId = getSourceXmiId(s);
            if (sourceId != null) {
                String typeSuffix = getDurationTypeSuffix(s.getUnitType());
                String customId = "(psm/" + sourceId + ")/" + DURATION_UNIT + typeSuffix;
                storeCustomXmiId(ctx, t, customId);
            }

            // Find parent measure and add unit to it
            Measure parentMeasure = findEquivalentMeasure(s, ctx);
            if (parentMeasure != null) {
                parentMeasure.getUnits().add(t);
            }

            return t;
        };
    }

    /**
     * Gets the XMI ID of a source PSM element from its resource.
     */
    private String getSourceXmiId(EObject source) {
        if (source.eResource() instanceof XMLResource) {
            return ((XMLResource) source.eResource()).getID(source);
        }
        return null;
    }

    /**
     * Gets the suffix for DurationUnit XMI ID based on the duration type.
     * Matches ETL format: DurationUnitNanosecond, DurationUnitSecond, etc.
     */
    private String getDurationTypeSuffix(hu.blackbelt.judo.meta.psm.measure.DurationType durationType) {
        switch (durationType) {
            case NANOSECOND: return "Nanosecond";
            case MICROSECOND: return "Microsecond";
            case MILLISECOND: return "Millisecond";
            case SECOND: return "Second";
            case MINUTE: return "Minute";
            case HOUR: return "Hour";
            case DAY: return "Day";
            case WEEK: return "Week";
            case MONTH: return "Month";
            case YEAR: return "Year";
            default: return "";
        }
    }

    /**
     * Stores a custom XMI ID for an element in the context's customXmiIds map.
     * This ID will be applied in post-processing.
     */
    @SuppressWarnings("unchecked")
    private void storeCustomXmiId(TransformationContext ctx, EObject element, String xmiId) {
        java.util.Map<EObject, String> customXmiIds = (java.util.Map<EObject, String>) ctx.getAttribute("customXmiIds");
        if (customXmiIds == null) {
            customXmiIds = new java.util.HashMap<>();
            ctx.setAttribute("customXmiIds", customXmiIds);
        }
        customXmiIds.put(element, xmiId);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Find the equivalent measure for a unit.
     * Uses direct EMF containment relationship instead of stream search to ensure
     * correct measure-unit association regardless of iteration order.
     */
    private Measure findEquivalentMeasure(hu.blackbelt.judo.meta.psm.measure.Unit unit, TransformationContext ctx) {
        // Use direct EMF containment - the unit's parent is always its measure
        org.eclipse.emf.ecore.EObject container = unit.eContainer();
        if (container instanceof hu.blackbelt.judo.meta.psm.measure.Measure) {
            hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure =
                    (hu.blackbelt.judo.meta.psm.measure.Measure) container;
            // Get the equivalent measure from transformation context
            Measure result;
            if (psmMeasure instanceof DerivedMeasure) {
                result = ctx.equivalent(psmMeasure, hu.blackbelt.judo.meta.measure.DerivedMeasure.class);
            } else {
                result = ctx.equivalent(psmMeasure, BaseMeasure.class);
            }
            return result;
        }
        return null;
    }

    /**
     * Map PSM duration type to Measure duration type.
     */
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
